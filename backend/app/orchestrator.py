from __future__ import annotations

import base64
import re
from pathlib import Path

from app.models import AnalyzeResponse, VisionResult
from app.services.cache import CompsCache
from app.services.corrections import CorrectionStore
from app.services.ebay_client import EbayClient, EbayClientError
from app.services.valuation import ValuationService
from app.services.vision import VisionService


class ListingPipeline:
    def __init__(
        self,
        vision: VisionService,
        ebay: EbayClient,
        valuation: ValuationService,
        corrections: CorrectionStore,
        cache: CompsCache,
        fallback_ebay: EbayClient | None = None,
    ) -> None:
        self.vision = vision
        self.ebay = ebay
        self.valuation = valuation
        self.corrections = corrections
        self.cache = cache
        self.fallback_ebay = fallback_ebay

    async def run(self, image_path: str, override_keywords: str | None = None) -> AnalyzeResponse:
        if not override_keywords or not override_keywords.strip():
            ebay_image_result = await self._try_ebay_image_identification(image_path)
            if ebay_image_result is not None:
                return ebay_image_result

        vision = await self.vision.analyze(image_path)
        vision = self._enrich_vision_with_override(vision, override_keywords)
        normalized_keywords = self._normalize_keywords(vision, override_keywords)

        if self._is_unidentified_query(normalized_keywords):
            comps = self.valuation.summarize(
                [],
                source="Needs better photo or keywords",
                cached=False,
                sold_total_count=0,
                active_count=0,
            )
            return AnalyzeResponse(vision=vision, normalized_keywords=normalized_keywords, comps=comps)

        corrected = self.corrections.get(vision.model)
        if corrected:
            vision = VisionResult(
                **{**vision.model_dump(), "model": corrected, "draft_title": self._replace_title_model(vision.draft_title, vision.model, corrected)}
            )
            normalized_keywords = self._normalize_keywords(vision, override_keywords)

        cached_payload = self.cache.get(normalized_keywords)

        try:
            listings = await self.ebay.sold_listings(normalized_keywords, limit=20)
            active_count, sold_total_count = await self.ebay.market_counts(normalized_keywords)
            comps = self.valuation.summarize(
                listings,
                source=self.ebay.source_name(),
                cached=False,
                sold_total_count=sold_total_count,
                active_count=active_count,
            )
            self.cache.set(normalized_keywords, comps.model_dump())
            return AnalyzeResponse(vision=vision, normalized_keywords=normalized_keywords, comps=comps)
        except EbayClientError:
            if cached_payload:
                cached = self.valuation.summarize([], source="Estimated Market Value", cached=True)
                cached = cached.model_copy(update=cached_payload)
                return AnalyzeResponse(vision=vision, normalized_keywords=normalized_keywords, comps=cached)

            if self.fallback_ebay is None:
                raise

            listings = await self.fallback_ebay.sold_listings(normalized_keywords, limit=20)
            active_count, sold_total_count = await self.fallback_ebay.market_counts(normalized_keywords)
            comps = self.valuation.summarize(
                listings,
                source="Estimated Market Value",
                cached=False,
                sold_total_count=sold_total_count,
                active_count=active_count,
            )
            self.cache.set(normalized_keywords, comps.model_dump())
            return AnalyzeResponse(vision=vision, normalized_keywords=normalized_keywords, comps=comps)

    async def _try_ebay_image_identification(self, image_path: str) -> AnalyzeResponse | None:
        try:
            image_base64 = self._to_base64(image_path)
            image_result = await self.ebay.search_by_image(image_base64=image_base64, limit=20)
        except (EbayClientError, OSError, ValueError):
            return None

        top_title = image_result.top_title.strip() if image_result.top_title else "Unknown item"
        tokens = [t for t in re.split(r"\s+", top_title) if t]
        brand = tokens[0].title() if tokens else "Unknown"

        vision = VisionResult(
            brand=brand,
            model=top_title,
            category=image_result.category or "General",
            condition_guess="Unknown",
            suggested_keywords=image_result.keywords or [top_title.lower()],
            draft_title=top_title,
            listing_description="Identified from eBay live image search results.",
            confidence=0.82,
            provider="ebay-browse",
            model_name="search_by_image",
        )
        normalized_keywords = self._normalize_keywords(vision, override_keywords=None)

        if self._is_unidentified_query(normalized_keywords):
            return None

        comps = self.valuation.summarize(
            image_result.listings,
            source="eBay Browse Image Search",
            cached=False,
            sold_total_count=0,
            active_count=image_result.active_count,
        )
        self.cache.set(normalized_keywords, comps.model_dump())
        return AnalyzeResponse(vision=vision, normalized_keywords=normalized_keywords, comps=comps)

    def _to_base64(self, image_path: str) -> str:
        image_bytes = Path(image_path).read_bytes()
        return base64.b64encode(image_bytes).decode("ascii")

    def save_correction(self, predicted: str, corrected: str) -> None:
        self.corrections.save(predicted, corrected)

    def _normalize_keywords(self, vision: VisionResult, override_keywords: str | None) -> str:
        if override_keywords and override_keywords.strip():
            return " ".join(override_keywords.split())

        chunks = [vision.brand, vision.model] + vision.suggested_keywords[:3]
        joined = " ".join([c for c in chunks if c and c.lower() != "unknown"])
        return " ".join(joined.split()).strip()

    def _replace_title_model(self, title: str, old_model: str, new_model: str) -> str:
        if old_model and old_model in title:
            return title.replace(old_model, new_model)
        return title

    def _is_unidentified_query(self, normalized_keywords: str) -> bool:
        value = normalized_keywords.strip().lower()
        if not value:
            return True
        tokens = [t for t in re.split(r"\s+", value) if t]
        if not tokens:
            return True

        unknown_tokens = {
            "unknown",
            "item",
            "general",
            "misc",
            "product",
            "object",
            "thing",
            "stuff",
        }
        generic_single_terms = {
            "food",
            "book",
            "clothes",
            "clothing",
            "toy",
            "electronics",
            "device",
            "household",
            "kitchen",
            "furniture",
            "accessory",
        }

        # A lone generic category (for example "food") is too broad for pricing.
        if len(tokens) == 1 and tokens[0] in generic_single_terms:
            return True

        meaningful = [t for t in tokens if t not in unknown_tokens]
        return len(meaningful) == 0

    def _enrich_vision_with_override(self, vision: VisionResult, override_keywords: str | None) -> VisionResult:
        if not override_keywords or not override_keywords.strip():
            return vision

        looks_unknown = vision.model.strip().lower() in {"", "unknown", "unknown item"}
        if not looks_unknown and vision.confidence >= 0.35:
            return vision

        cleaned = " ".join(override_keywords.split())
        if not cleaned:
            return vision

        tokens = cleaned.split(" ")
        brand_guess = tokens[0].title() if tokens else "Unknown"
        model_guess = " ".join(tokens[1:]).strip() if len(tokens) > 1 else cleaned

        # Keep this lightweight: if vision cannot identify the item, prefer explicit user keywords.
        return vision.model_copy(
            update={
                "brand": brand_guess,
                "model": model_guess or cleaned,
                "suggested_keywords": [cleaned],
                "draft_title": cleaned,
                "confidence": max(vision.confidence, 0.6),
                "provider": f"{vision.provider}+override" if vision.provider else "override",
                "model_name": f"{vision.model_name}+override" if vision.model_name else "override",
            }
        )

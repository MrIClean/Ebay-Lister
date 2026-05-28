from __future__ import annotations

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
        vision = await self.vision.analyze(image_path)
        normalized_keywords = self._normalize_keywords(vision, override_keywords)

        corrected = self.corrections.get(vision.model)
        if corrected:
            vision = VisionResult(
                **{**vision.model_dump(), "model": corrected, "draft_title": self._replace_title_model(vision.draft_title, vision.model, corrected)}
            )
            normalized_keywords = self._normalize_keywords(vision, override_keywords)

        cached_payload = self.cache.get(normalized_keywords)

        try:
            listings = await self.ebay.sold_listings(normalized_keywords)
            active_count, sold_total_count = await self.ebay.market_counts(normalized_keywords)
            comps = self.valuation.summarize(
                listings,
                source="ebay-api",
                cached=False,
                sold_total_count=sold_total_count,
                active_count=active_count,
            )
            self.cache.set(normalized_keywords, comps.model_dump())
            return AnalyzeResponse(vision=vision, normalized_keywords=normalized_keywords, comps=comps)
        except EbayClientError:
            if cached_payload:
                cached = self.valuation.summarize([], source="cache", cached=True)
                cached = cached.model_copy(update=cached_payload)
                return AnalyzeResponse(vision=vision, normalized_keywords=normalized_keywords, comps=cached)

            if self.fallback_ebay is None:
                raise

            listings = await self.fallback_ebay.sold_listings(normalized_keywords)
            active_count, sold_total_count = await self.fallback_ebay.market_counts(normalized_keywords)
            comps = self.valuation.summarize(
                listings,
                source="mock-fallback",
                cached=False,
                sold_total_count=sold_total_count,
                active_count=active_count,
            )
            self.cache.set(normalized_keywords, comps.model_dump())
            return AnalyzeResponse(vision=vision, normalized_keywords=normalized_keywords, comps=comps)

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

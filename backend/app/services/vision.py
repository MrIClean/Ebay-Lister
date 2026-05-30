from __future__ import annotations

import json
from pathlib import Path
import re
from urllib.parse import urlparse

import httpx

from app.models import VisionResult


class VisionService:
    async def analyze(self, image_path: str) -> VisionResult:
        raise NotImplementedError


class MockVisionService(VisionService):
    async def analyze(self, image_path: str) -> VisionResult:
        stem = Path(image_path).stem.lower()
        if "broom" in stem:
            return VisionResult(
                brand="Unknown",
                model="Push Broom",
                category="Cleaning Supplies",
                condition_guess="Used",
                suggested_keywords=["push broom", "cleaning broom", "long handle broom"],
                draft_title="Push Broom Long Handle Cleaning Tool",
                confidence=0.72,
                provider="mock",
                model_name="mock-filename-rules",
            )
        if "console" in stem or "game" in stem:
            return VisionResult(
                brand="Unknown",
                model="Handheld Game Console",
                category="Video Game Consoles",
                condition_guess="Used",
                suggested_keywords=["handheld game console", "portable gaming device", "retro console"],
                draft_title="Handheld Game Console Portable Gaming Device",
                confidence=0.7,
                provider="mock",
                model_name="mock-filename-rules",
            )

        return VisionResult(
            brand="Unknown",
            model="Unknown item",
            category="General",
            condition_guess="Unknown",
            suggested_keywords=["unknown item"],
            draft_title="Unknown Item",
            confidence=0.2,
            provider="mock",
            model_name="mock-filename-rules",
        )


class GeminiVisionService(VisionService):
    def __init__(self, api_key: str, model_name: str = "gemini-2.0-flash") -> None:
        self.api_key = api_key
        self.model_name = model_name

    async def analyze(self, image_path: str) -> VisionResult:
        if not self.api_key:
            raise RuntimeError("GEMINI_API_KEY is not set")

        try:
            from google import genai
        except Exception as exc:  # pragma: no cover
            raise RuntimeError("google-genai SDK is unavailable") from exc

        client = genai.Client(api_key=self.api_key)
        prompt = (
            "You are an expert product identifier specializing in resale, thrift, and e-commerce. "
            "Your PRIMARY task is to OCR every piece of text visible on the item: model numbers, serial codes, "
            "FCC IDs, UPC/EAN barcodes, regulatory markings, and manufacturer branding (e.g. 'E9000G', 'G703 Lightspeed', 'FCC ID: XXXXX'). "
            "Use that text as the ground truth for the exact retail product name and variant. "
            "Strip out extraneous serial formatting and internal warehouse tracking tags unless they "
            "directly identify the specific retail variant being sold. "
            "Ignore hands, tables, flooring, and background clutter. "
            "Only fall back to shape, materials, and logos if no readable text is present. "
            "If uncertain, keep model generic but still useful for eBay search. "
            "Return ONLY valid JSON with exact keys: "
            "brand, model, category, condition_guess, suggested_keywords, draft_title, confidence. "
            "Rules: suggested_keywords must be an array of 3 to 6 short search phrases optimized for eBay; "
            "draft_title must be the exact manufacturer and model name, concise and searchable; "
            "brand and model must never contain warehouse codes or sticker prices; "
            "confidence must be a number from 0.0 to 1.0."
        )

        with open(image_path, "rb") as f:
            image_bytes = f.read()

        model_names = [
            self.model_name,
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite",
            "gemini-2.0-flash-001",
        ]

        last_error: Exception | None = None
        response = None
        for model_name in model_names:
            try:
                response = client.models.generate_content(
                    model=model_name,
                    contents=[
                        {"text": prompt},
                        {
                            "inline_data": {
                                "mime_type": self._guess_mime_type(image_path),
                                "data": image_bytes,
                            }
                        },
                    ],
                    config={"response_mime_type": "application/json"},
                )
                self.model_name = model_name
                break
            except Exception as exc:
                last_error = exc

        if response is None:
            raise RuntimeError(f"Gemini generation failed for all supported models: {last_error}")

        parsed = self._parse_json_object(response.text or "")
        parsed["provider"] = "gemini"
        parsed["model_name"] = self.model_name
        parsed["confidence"] = max(0.0, min(1.0, float(parsed.get("confidence", 0.0))))
        return VisionResult(**parsed)

    def _guess_mime_type(self, image_path: str) -> str:
        suffix = Path(image_path).suffix.lower()
        if suffix == ".png":
            return "image/png"
        if suffix == ".webp":
            return "image/webp"
        return "image/jpeg"

    def _parse_json_object(self, text: str) -> dict:
        stripped = text.strip()
        try:
            return json.loads(stripped)
        except json.JSONDecodeError:
            pass

        match = re.search(r"\{[\s\S]*\}", stripped)
        if match:
            return json.loads(match.group(0))

        raise ValueError("Gemini did not return valid JSON")


class GoogleLensVisionService(VisionService):
    def __init__(self, api_key: str, fallback: VisionService | None = None) -> None:
        self.api_key = api_key
        self.fallback = fallback

    async def analyze(self, image_path: str) -> VisionResult:
        if not self.api_key:
            raise RuntimeError("SERPAPI_API_KEY is not set")

        try:
            image_url = await self._upload_image(image_path)
            lens = await self._search_google_lens(image_url)
            return self._to_vision_result(lens)
        except Exception:
            if self.fallback:
                return await self.fallback.analyze(image_path)
            raise

    async def _upload_image(self, image_path: str) -> str:
        with open(image_path, "rb") as fp:
            files = {
                "fileToUpload": (Path(image_path).name, fp, self._guess_mime_type(image_path)),
            }
            data = {"reqtype": "fileupload"}
            async with httpx.AsyncClient(timeout=30.0) as client:
                response = await client.post("https://catbox.moe/user/api.php", data=data, files=files)

        if response.status_code >= 400:
            raise RuntimeError(f"Image upload failed: {response.status_code} {response.text}")

        image_url = response.text.strip()
        parsed = urlparse(image_url)
        if not parsed.scheme.startswith("http"):
            raise RuntimeError("Image upload response did not return a valid URL")
        return image_url

    async def _search_google_lens(self, image_url: str) -> dict:
        params = {
            "engine": "google_lens",
            "url": image_url,
            "api_key": self.api_key,
            "type": "products",
        }
        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.get("https://serpapi.com/search.json", params=params)

        if response.status_code >= 400:
            raise RuntimeError(f"Google Lens search failed: {response.status_code} {response.text}")

        payload = response.json()
        status = payload.get("search_metadata", {}).get("status", "")
        if status and status.lower() == "error":
            raise RuntimeError(payload.get("error", "Google Lens returned an error"))
        return payload

    def _to_vision_result(self, payload: dict) -> VisionResult:
        matches = payload.get("visual_matches", [])
        related = payload.get("related_content", [])

        best_title = "Unknown item"
        for match in matches:
            title = (match.get("title") or "").strip()
            if title:
                best_title = self._normalize_title(title)
                break

        if best_title == "Unknown item" and related:
            guess = (related[0].get("query") or "").strip()
            if guess:
                best_title = guess

        keywords: list[str] = []
        for match in matches[:4]:
            title = (match.get("title") or "").strip()
            if title:
                keywords.append(self._normalize_title(title).lower())

        if not keywords and best_title != "Unknown item":
            keywords = [best_title.lower()]

        deduped_keywords = list(dict.fromkeys(k for k in keywords if k))[:6]
        category = "General"
        if any("shoe" in k or "sneaker" in k for k in deduped_keywords):
            category = "Shoes"
        elif any("console" in k or "game" in k for k in deduped_keywords):
            category = "Video Game Consoles"

        return VisionResult(
            brand="Unknown",
            model=best_title,
            category=category,
            condition_guess="Unknown",
            suggested_keywords=deduped_keywords or ["unknown item"],
            draft_title=best_title,
            confidence=0.78 if best_title != "Unknown item" else 0.2,
            provider="google-lens",
            model_name="serpapi-google_lens",
        )

    def _normalize_title(self, title: str) -> str:
        clean = re.split(r"\s[-|:]\s", title.strip())[0]
        return " ".join(clean.split())[:120] or "Unknown item"

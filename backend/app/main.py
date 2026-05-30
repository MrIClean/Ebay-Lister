from __future__ import annotations

import base64
import binascii
from pathlib import Path
from tempfile import NamedTemporaryFile

from dotenv import load_dotenv
from fastapi import FastAPI, Header, HTTPException

from app.config import get_settings
from app.models import AnalyzeRequest, AnalyzeResponse, CorrectionRequest, SimpleAnalyzeResponse
from app.orchestrator import ListingPipeline
from app.services.cache import CompsCache
from app.services.corrections import CorrectionStore
from app.services.ebay_client import EbayCredentials, MockEbayClient, RealEbayClient
from app.services.valuation import ValuationService
from app.services.vision import GeminiVisionService, GoogleLensVisionService, MockVisionService

base_dir = Path(__file__).resolve().parent.parent
load_dotenv(base_dir / ".env", override=True)
settings = get_settings()
state_dir = base_dir / ".state"

corrections = CorrectionStore(state_dir / "corrections.db")
cache = CompsCache(state_dir / "cache.db", ttl_hours=settings.cache_ttl_hours)
valuation = ValuationService()

gemini_or_mock = (
    GeminiVisionService(settings.gemini_api_key, settings.gemini_model)
    if settings.gemini_api_key
    else MockVisionService()
)
vision = (
    GoogleLensVisionService(settings.serpapi_api_key, fallback=gemini_or_mock)
    if settings.use_google_lens and settings.serpapi_api_key
    else gemini_or_mock
)
fallback_ebay = MockEbayClient() if settings.allow_mock_fallback else None

if settings.use_real_ebay:
    ebay = RealEbayClient(
        EbayCredentials(
            client_id=settings.ebay_client_id,
            client_secret=settings.ebay_client_secret,
            refresh_token=settings.ebay_refresh_token,
            environment=settings.ebay_environment,
            marketplace_id=settings.ebay_marketplace_id,
        )
    )
else:
    ebay = MockEbayClient()

pipeline = ListingPipeline(
    vision=vision,
    ebay=ebay,
    valuation=valuation,
    corrections=corrections,
    cache=cache,
    fallback_ebay=fallback_ebay,
)

app = FastAPI(title="PixelProfit Listing Backend", version="0.1.0")


def _authorize(api_key: str | None) -> None:
    if settings.backend_api_token and api_key != settings.backend_api_token:
        raise HTTPException(status_code=401, detail="Unauthorized")


@app.get("/health")
def health(x_api_key: str | None = Header(default=None, alias="X-Api-Key")) -> dict[str, str | bool]:
    _authorize(x_api_key)
    return {
        "status": "ok",
        "vision_mode": "google-lens" if settings.use_google_lens and settings.serpapi_api_key else "gemini-or-mock",
        "use_real_ebay": settings.use_real_ebay,
        "ebay_environment": settings.ebay_environment,
        "auth_required": bool(settings.backend_api_token),
    }


@app.post("/analyze", response_model=AnalyzeResponse)
async def analyze(request: AnalyzeRequest, x_api_key: str | None = Header(default=None, alias="X-Api-Key")) -> AnalyzeResponse:
    _authorize(x_api_key)
    image_path: str
    temp_file_path: Path | None = None

    if request.image_base64:
        if len(request.image_base64) > 20_000_000:
            raise HTTPException(status_code=400, detail="image_base64 payload is too large")

        try:
            image_bytes = base64.b64decode(request.image_base64, validate=True)
        except (ValueError, binascii.Error) as exc:
            raise HTTPException(status_code=400, detail="image_base64 is invalid") from exc

        suffix = ".jpg"
        if request.image_mime_type == "image/png":
            suffix = ".png"
        elif request.image_mime_type == "image/webp":
            suffix = ".webp"

        with NamedTemporaryFile(delete=False, suffix=suffix) as temp_file:
            temp_file.write(image_bytes)
            image_path = temp_file.name
            temp_file_path = Path(temp_file.name)
    elif request.image_path:
        image = Path(request.image_path)
        if not image.exists():
            raise HTTPException(status_code=400, detail="image_path does not exist")
        image_path = request.image_path
    else:
        raise HTTPException(status_code=400, detail="Provide image_base64 or image_path")

    try:
        return await pipeline.run(image_path, request.override_keywords)
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Analysis failed: {exc}") from exc
    finally:
        if temp_file_path and temp_file_path.exists():
            temp_file_path.unlink(missing_ok=True)


@app.post("/analyze/simple", response_model=SimpleAnalyzeResponse)
async def analyze_simple(request: AnalyzeRequest, x_api_key: str | None = Header(default=None, alias="X-Api-Key")) -> SimpleAnalyzeResponse:
    _authorize(x_api_key)
    image_path: str
    temp_file_path: Path | None = None

    if request.image_base64:
        if len(request.image_base64) > 20_000_000:
            raise HTTPException(status_code=400, detail="image_base64 payload is too large")

        try:
            image_bytes = base64.b64decode(request.image_base64, validate=True)
        except (ValueError, binascii.Error) as exc:
            raise HTTPException(status_code=400, detail="image_base64 is invalid") from exc

        suffix = ".jpg"
        if request.image_mime_type == "image/png":
            suffix = ".png"
        elif request.image_mime_type == "image/webp":
            suffix = ".webp"

        with NamedTemporaryFile(delete=False, suffix=suffix) as temp_file:
            temp_file.write(image_bytes)
            image_path = temp_file.name
            temp_file_path = Path(temp_file.name)
    elif request.image_path:
        image = Path(request.image_path)
        if not image.exists():
            raise HTTPException(status_code=400, detail="image_path does not exist")
        image_path = request.image_path
    else:
        raise HTTPException(status_code=400, detail="Provide image_base64 or image_path")

    try:
        result = await pipeline.run(image_path, request.override_keywords)
        source = result.comps.source
        if source not in ("eBay Sold Comps", "PriceCharting"):
            source = "Estimated Market Value"
        return SimpleAnalyzeResponse(
            success=True,
            detected_title=result.vision.draft_title,
            median_price=result.comps.median_price,
            source_label=source,
            confidence_score=result.vision.confidence,
            identified_keywords=result.vision.suggested_keywords,
        )
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Analysis failed: {exc}") from exc
    finally:
        if temp_file_path and temp_file_path.exists():
            temp_file_path.unlink(missing_ok=True)


@app.post("/corrections")
def save_correction(payload: CorrectionRequest, x_api_key: str | None = Header(default=None, alias="X-Api-Key")) -> dict[str, str]:
    _authorize(x_api_key)
    pipeline.save_correction(payload.predicted, payload.corrected)
    return {"status": "saved"}

from __future__ import annotations

import base64
import binascii
import hashlib
from pathlib import Path
from tempfile import NamedTemporaryFile

from dotenv import load_dotenv
from fastapi import FastAPI, Header, HTTPException, Request

from app.config import get_settings
from app.models import (
    AccountOptionsResponse,
    AnalyzeRequest,
    AnalyzeResponse,
    BarcodeLookupRequest,
    BarcodeLookupResponse,
    CorrectionRequest,
    PublishListingRequest,
    PublishListingResponse,
    PolicyOption,
    SimpleAnalyzeResponse,
)
from app.orchestrator import ListingPipeline
from app.services.cache import CompsCache
from app.services.corrections import CorrectionStore
from app.services.ebay_client import EbayClientError, EbayCredentials, MockEbayClient, RealEbayClient
from app.services.listing_publish import ListingPublishService
from app.services.valuation import ValuationService
from app.services.vision import GeminiVisionService, GoogleLensVisionService, MockVisionService

base_dir = Path(__file__).resolve().parent.parent
load_dotenv(base_dir / ".env", override=False)
settings = get_settings()
state_dir = base_dir / ".state"

corrections = CorrectionStore(state_dir / "corrections.db")
cache = CompsCache(state_dir / "cache.db", ttl_hours=settings.cache_ttl_hours)
valuation = ValuationService()
listing_publish = ListingPublishService()

gemini_or_mock = (
    GeminiVisionService(settings.gemini_api_key, settings.gemini_model, fallback=MockVisionService())
    if settings.gemini_api_key
    else MockVisionService()
)
vision = (
    GoogleLensVisionService(settings.serpapi_api_key, fallback=gemini_or_mock)
    if settings.use_google_lens and settings.serpapi_api_key
    else gemini_or_mock
)
fallback_ebay = MockEbayClient() if settings.allow_mock_fallback else None


def _pick_ebay_value(environment: str, sandbox_value: str, production_value: str, default_value: str) -> str:
    if environment == "sandbox" and sandbox_value:
        return sandbox_value
    if environment == "production" and production_value:
        return production_value
    return default_value


def _build_ebay_credentials() -> EbayCredentials:
    environment = settings.ebay_environment.strip().lower()
    return EbayCredentials(
        client_id=_pick_ebay_value(
            environment,
            settings.ebay_sandbox_client_id,
            settings.ebay_production_client_id,
            settings.ebay_client_id,
        ),
        client_secret=_pick_ebay_value(
            environment,
            settings.ebay_sandbox_client_secret,
            settings.ebay_production_client_secret,
            settings.ebay_client_secret,
        ),
        refresh_token=_pick_ebay_value(
            environment,
            settings.ebay_sandbox_refresh_token,
            settings.ebay_production_refresh_token,
            settings.ebay_refresh_token,
        ),
        environment=settings.ebay_environment,
        marketplace_id=_pick_ebay_value(
            environment,
            settings.ebay_sandbox_marketplace_id,
            settings.ebay_production_marketplace_id,
            settings.ebay_marketplace_id,
        ),
    )

if settings.use_real_ebay:
    ebay = RealEbayClient(_build_ebay_credentials())
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


def _authorize(api_key: str | None, client_host: str | None = None) -> None:
    if client_host in {"127.0.0.1", "::1", "localhost"}:
        return
    if settings.backend_api_token and api_key != settings.backend_api_token:
        raise HTTPException(status_code=401, detail="Unauthorized")


def _account_deletion_endpoint_url(request: Request) -> str:
    configured = settings.ebay_notification_endpoint_url.strip()
    if configured:
        return configured
    return str(request.url.replace(query=""))


def _account_deletion_challenge_response(challenge_code: str, verification_token: str, endpoint_url: str) -> str:
    payload = f"{challenge_code}{verification_token}{endpoint_url}"
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


@app.get("/health")
def health(
    request: Request,
    x_api_key: str | None = Header(default=None, alias="X-Api-Key"),
) -> dict[str, str | bool]:
    _authorize(x_api_key, request.client.host if request.client else None)
    return {
        "status": "ok",
        "vision_mode": "google-lens" if settings.use_google_lens and settings.serpapi_api_key else "gemini-or-mock",
        "use_real_ebay": settings.use_real_ebay,
        "ebay_environment": settings.ebay_environment,
        "auth_required": bool(settings.backend_api_token),
    }


@app.get("/ebay/notifications/account-deletion")
def ebay_account_deletion_challenge(request: Request, challenge_code: str) -> dict[str, str]:
    verification_token = settings.ebay_notification_verification_token.strip()
    if not verification_token:
        raise HTTPException(status_code=500, detail="EBAY_NOTIFICATION_VERIFICATION_TOKEN is not configured")

    endpoint_url = _account_deletion_endpoint_url(request)
    return {
        "challengeResponse": _account_deletion_challenge_response(
            challenge_code=challenge_code,
            verification_token=verification_token,
            endpoint_url=endpoint_url,
        )
    }


@app.post("/ebay/notifications/account-deletion")
async def ebay_account_deletion_notification(request: Request) -> dict[str, str]:
    # eBay requires a live HTTPS endpoint. Store payload for now; full deletion workflow can be added later.
    _ = await request.body()
    return {"status": "accepted"}


@app.post("/analyze", response_model=AnalyzeResponse)
async def analyze(
    request: Request,
    payload: AnalyzeRequest,
    x_api_key: str | None = Header(default=None, alias="X-Api-Key"),
) -> AnalyzeResponse:
    _authorize(x_api_key, request.client.host if request.client else None)
    image_path: str
    temp_file_path: Path | None = None

    if payload.image_base64:
        if len(payload.image_base64) > 20_000_000:
            raise HTTPException(status_code=400, detail="image_base64 payload is too large")

        try:
            image_bytes = base64.b64decode(payload.image_base64, validate=True)
        except (ValueError, binascii.Error) as exc:
            raise HTTPException(status_code=400, detail="image_base64 is invalid") from exc

        suffix = ".jpg"
        if payload.image_mime_type == "image/png":
            suffix = ".png"
        elif payload.image_mime_type == "image/webp":
            suffix = ".webp"

        with NamedTemporaryFile(delete=False, suffix=suffix) as temp_file:
            temp_file.write(image_bytes)
            image_path = temp_file.name
            temp_file_path = Path(temp_file.name)
    elif payload.image_path:
        image = Path(payload.image_path)
        if not image.exists():
            raise HTTPException(status_code=400, detail="image_path does not exist")
        image_path = payload.image_path
    else:
        raise HTTPException(status_code=400, detail="Provide image_base64 or image_path")

    try:
        return await pipeline.run(image_path, payload.override_keywords)
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Analysis failed: {exc}") from exc
    finally:
        if temp_file_path and temp_file_path.exists():
            temp_file_path.unlink(missing_ok=True)


@app.post("/analyze/simple", response_model=SimpleAnalyzeResponse)
async def analyze_simple(
    request: Request,
    payload: AnalyzeRequest,
    x_api_key: str | None = Header(default=None, alias="X-Api-Key"),
) -> SimpleAnalyzeResponse:
    _authorize(x_api_key, request.client.host if request.client else None)
    image_path: str
    temp_file_path: Path | None = None

    if payload.image_base64:
        if len(payload.image_base64) > 20_000_000:
            raise HTTPException(status_code=400, detail="image_base64 payload is too large")

        try:
            image_bytes = base64.b64decode(payload.image_base64, validate=True)
        except (ValueError, binascii.Error) as exc:
            raise HTTPException(status_code=400, detail="image_base64 is invalid") from exc

        suffix = ".jpg"
        if payload.image_mime_type == "image/png":
            suffix = ".png"
        elif payload.image_mime_type == "image/webp":
            suffix = ".webp"

        with NamedTemporaryFile(delete=False, suffix=suffix) as temp_file:
            temp_file.write(image_bytes)
            image_path = temp_file.name
            temp_file_path = Path(temp_file.name)
    elif payload.image_path:
        image = Path(payload.image_path)
        if not image.exists():
            raise HTTPException(status_code=400, detail="image_path does not exist")
        image_path = payload.image_path
    else:
        raise HTTPException(status_code=400, detail="Provide image_base64 or image_path")

    try:
        result = await pipeline.run(image_path, payload.override_keywords)
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
def save_correction(
    request: Request,
    payload: CorrectionRequest,
    x_api_key: str | None = Header(default=None, alias="X-Api-Key"),
) -> dict[str, str]:
    _authorize(x_api_key, request.client.host if request.client else None)
    pipeline.save_correction(payload.predicted, payload.corrected)
    return {"status": "saved"}


@app.get("/account/options", response_model=AccountOptionsResponse)
async def account_options(
    request: Request,
    x_api_key: str | None = Header(default=None, alias="X-Api-Key"),
) -> AccountOptionsResponse:
    _authorize(x_api_key, request.client.host if request.client else None)
    try:
        options = await ebay.account_options()
        return AccountOptionsResponse(
            connected=options.connected,
            marketplace_id=options.marketplace_id,
            source=options.source,
            message=options.message,
            fulfillment_policies=[
                PolicyOption(id=policy.id, name=policy.name, description=policy.description)
                for policy in options.fulfillment_policies
            ],
            return_policies=[
                PolicyOption(id=policy.id, name=policy.name, description=policy.description)
                for policy in options.return_policies
            ],
        )
    except EbayClientError as exc:
        return AccountOptionsResponse(
            connected=False,
            marketplace_id=settings.ebay_marketplace_id,
            source="ebay-account-api",
            message=str(exc),
        )


@app.post("/publish", response_model=PublishListingResponse)
def publish_listing(
    request: Request,
    payload: PublishListingRequest,
    x_api_key: str | None = Header(default=None, alias="X-Api-Key"),
) -> PublishListingResponse:
    _authorize(x_api_key, request.client.host if request.client else None)
    return listing_publish.prepare(payload, real_publish_enabled=False)


@app.post("/identify/barcode", response_model=BarcodeLookupResponse)
async def identify_barcode(
    request: Request,
    payload: BarcodeLookupRequest,
    x_api_key: str | None = Header(default=None, alias="X-Api-Key"),
) -> BarcodeLookupResponse:
    _authorize(x_api_key, request.client.host if request.client else None)

    try:
        product = await ebay.catalog_by_barcode(payload.barcode)
    except EbayClientError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Barcode lookup failed: {exc}") from exc

    return BarcodeLookupResponse(
        title=product.title,
        brand=product.brand,
        category=product.category,
        epid=product.epid,
        product_web_url=product.product_web_url,
        image_url=product.image_url,
        gtins=product.gtins,
        source="eBay Catalog",
    )

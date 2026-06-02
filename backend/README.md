# Python Listing Backend

Modular Python backend for image-to-comps workflow:

1. Identify from image using eBay Browse `search_by_image` (production eBay mode).
2. Fall back to multimodal AI (Gemini, then mock) only when image search is unavailable.
3. Normalize listing keywords.
4. Build a market snapshot from eBay results.
5. Cache comps and support local correction memory.

## Why this design

- Free-first connectors only.
- No scraping fallback.
- Cache fallback and local corrections to improve reliability.
- Clean API surface for Android integration.

## Structure

- `app/main.py`: FastAPI app and dependency wiring
- `app/orchestrator.py`: end-to-end pipeline
- `app/services/vision.py`: Gemini and mock vision services
- `app/services/ebay_client.py`: real and mock eBay clients
- `app/services/valuation.py`: stats and outlier filtering
- `app/services/corrections.py`: sqlite correction memory
- `app/services/cache.py`: sqlite TTL cache
- `app/models.py`: request/response and domain models

## Setup

```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
Copy-Item .env.example .env
```

## Run

```powershell
uvicorn app.main:app --reload --port 8000
```

## API

### `GET /health`
Returns basic status.

### `POST /analyze`

Payload:

```json
{
  "image_path": "C:/path/to/item.jpg",
  "image_base64": "optional base64 image bytes",
  "image_mime_type": "optional mime type such as image/jpeg",
  "override_keywords": "optional override keywords"
}
```

Notes:

- Provide `image_base64` when the backend cannot access the local file path directly (for example, Android phone to desktop backend).
- `image_path` is still supported for local desktop testing.
- In production eBay mode, this endpoint first uses Browse image search so item details come directly from eBay listing matches.

### `POST /identify/barcode`

Payload:

```json
{
  "barcode": "045496590421"
}
```

Returns top eBay Catalog product match (title, brand, ePID, image URL, product page URL, GTIN list).

### `GET /account/options`

Loads seller business policies for the Android listing editor dropdowns:

- fulfillment policies for shipping
- return policies

This uses eBay Sell Account API business policies for the configured marketplace.
Your eBay refresh token must include one of the Account API scopes, preferably:

```text
https://api.ebay.com/oauth/api_scope/sell.account.readonly
```

If the endpoint returns `invalid_scope`, reconnect/regenerate the eBay refresh token with that scope included.

### `POST /corrections`

Payload:

```json
{
  "predicted": "chair",
  "corrected": "broom"
}
```

### `POST /publish`

Validates and packages an edited listing draft from the Android listing editor.
This endpoint does not submit to eBay yet; it is the backend handoff point for the next Sell API integration.

Payload:

```json
{
  "title": "Sony Walkman WM-FX101 Portable Cassette Player",
  "description": "Tested portable cassette player with visible cosmetic wear.",
  "price": "$58.00",
  "photo_paths": ["C:/path/to/photo.jpg"],
  "condition": "Pre-owned",
  "category": "Portable Audio & Headphones",
  "shipping_profile": "USPS Ground Advantage",
  "return_policy": "30 day returns",
  "quantity": 1,
  "channel": "ebay"
}
```

Returns `success=false` with `validation_errors` until required seller details are complete.

## Real eBay mode

Set in `.env`:

- `USE_REAL_EBAY=true`
- `EBAY_CLIENT_ID=...`
- `EBAY_CLIENT_SECRET=...`
- `EBAY_REFRESH_TOKEN=...`
- `EBAY_ENVIRONMENT=sandbox` (or `production`)
- `EBAY_MARKETPLACE_ID=EBAY_US`

If you want to keep both keysets in one file and switch quickly, you can also set:

- `EBAY_SANDBOX_CLIENT_ID=...`
- `EBAY_SANDBOX_CLIENT_SECRET=...`
- `EBAY_SANDBOX_REFRESH_TOKEN=...`
- `EBAY_SANDBOX_MARKETPLACE_ID=EBAY_US` (optional)
- `EBAY_PRODUCTION_CLIENT_ID=...`
- `EBAY_PRODUCTION_CLIENT_SECRET=...`
- `EBAY_PRODUCTION_REFRESH_TOKEN=...`
- `EBAY_PRODUCTION_MARKETPLACE_ID=EBAY_US` (optional)

Then switch only:

- `EBAY_ENVIRONMENT=sandbox` or `EBAY_ENVIRONMENT=production`

Selection rules:

- If environment-specific vars exist, they are used.
- If they are missing, backend falls back to generic `EBAY_CLIENT_ID`, `EBAY_CLIENT_SECRET`, `EBAY_REFRESH_TOKEN`, and `EBAY_MARKETPLACE_ID`.

If real eBay fails, cached comps are used first, then optional mock fallback.

Quick verification after restart:

- `GET /health` should show `"use_real_ebay": true`
- `GET /health` should show `"ebay_environment": "sandbox"` (or your selected env)
- `/analyze` should return `comps.source` as `ebay-api` for live pricing

Comps source labels returned by `/analyze`:

- `eBay Browse Image Search`: live listing prices from Browse `search_by_image`
- `eBay Sold Comps`: sold comps from Browse keyword search
- `Estimated Market Value`: cache or mock fallback valuation

## Real AI mode (Gemini)

Set in `.env`:

- `GEMINI_API_KEY=...`
- `GEMINI_MODEL=gemini-2.5-flash`

If `GEMINI_API_KEY` is empty, the backend uses mock vision results.

## Google Lens mode (SerpApi)

Set in `.env`:

- `USE_GOOGLE_LENS=true`
- `SERPAPI_API_KEY=...`

Notes:

- SerpApi offers a free plan tier for Google Lens requests.
- Backend uploads the image to a temporary public URL before Lens lookup, then uses Lens matches to form listing keywords.
- If Lens fails and Gemini is configured, backend falls back to Gemini.

## Public deployment security

Set `BACKEND_API_TOKEN` in `.env` for simple app-to-backend authentication.

- When set, clients must send header: `X-Api-Key: <BACKEND_API_TOKEN>`
- `GET /health`, `POST /analyze`, and `POST /corrections` enforce this token.

## Production prerequisite: account deletion notifications

eBay production keysets require a public HTTPS callback for Marketplace Account Deletion notifications.

Set in `.env`:

- `EBAY_NOTIFICATION_VERIFICATION_TOKEN=...` (any random string you choose)
- `EBAY_NOTIFICATION_ENDPOINT_URL=https://your-public-host/ebay/notifications/account-deletion`

Backend endpoints:

- `GET /ebay/notifications/account-deletion?challenge_code=...`
- `POST /ebay/notifications/account-deletion`

Setup flow in eBay Developer Portal (Production -> Alerts & Notifications):

- Email to notify: your email
- Marketplace account deletion notification endpoint: `EBAY_NOTIFICATION_ENDPOINT_URL`
- Verification token: `EBAY_NOTIFICATION_VERIFICATION_TOKEN`

If using a temporary free tunnel, start your backend and expose it with Cloudflare quick tunnel, then set `EBAY_NOTIFICATION_ENDPOINT_URL` to that tunnel URL + `/ebay/notifications/account-deletion`.

## Snapshot fields returned by `/analyze`

- `comps.active_count`: active listings count
- `comps.sold_total_count`: sold listings count
- `comps.median_price`: median sold price

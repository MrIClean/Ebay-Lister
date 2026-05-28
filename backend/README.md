# Python Listing Backend

Modular Python backend for image-to-comps workflow:

1. Analyze image with multimodal AI (Gemini when key exists, mock otherwise).
2. Normalize listing keywords.
3. Query eBay sold comps (real API when enabled, mock otherwise).
4. Build a market snapshot: active listed count, sold count, and sold price summary.
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

### `POST /corrections`

Payload:

```json
{
  "predicted": "chair",
  "corrected": "broom"
}
```

## Real eBay mode

Set in `.env`:

- `USE_REAL_EBAY=true`
- `EBAY_CLIENT_ID=...`
- `EBAY_CLIENT_SECRET=...`
- `EBAY_REFRESH_TOKEN=...`

If real eBay fails, cached comps are used first, then optional mock fallback.

## Real AI mode (Gemini)

Set in `.env`:

- `GEMINI_API_KEY=...`
- `GEMINI_MODEL=gemini-1.5-flash`

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

## Deploy to Render (cell-data ready)

This repo includes a Render blueprint config at `render.yaml`.

1. Push this repository to GitHub.
2. In Render, choose `New +` -> `Blueprint` and select your repo.
3. Confirm service settings:
  - `Root Directory`: `backend`
  - `Build Command`: `pip install -r requirements.txt`
  - `Start Command`: `uvicorn app.main:app --host 0.0.0.0 --port $PORT`
4. Add secrets in Render dashboard:
  - `BACKEND_API_TOKEN`
  - `SERPAPI_API_KEY` (if using Lens)
  - `GEMINI_API_KEY` (optional fallback)
  - `EBAY_CLIENT_ID`, `EBAY_CLIENT_SECRET`, `EBAY_REFRESH_TOKEN` (for real eBay)
5. Deploy, then copy your HTTPS URL, for example `https://ebay-lister-backend.onrender.com`.
6. In the Android app:
  - Switch backend mode to `Cloud`
  - Set `Backend URL` to the Render URL
  - Set `Backend API Token` to the same `BACKEND_API_TOKEN`
  - Tap `Test connection`

Notes:

- Free Render instances may sleep; first request can take longer.
- If `GET /health` returns 401, check token mismatch in app vs Render env var.

## Snapshot fields returned by `/analyze`

- `comps.active_count`: active listings count
- `comps.sold_total_count`: sold listings count
- `comps.median_price`: median sold price

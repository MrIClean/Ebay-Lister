# Ebay Lister

Android starter project for an item-identification and eBay listing workflow.

## What this scaffold includes

- Kotlin + Jetpack Compose Android app shell
- Main screen with CameraX preview and photo capture, plus item analysis, marketplace stats, and listing actions
- Captured-photo thumbnail preview directly in the main flow
- On-device ML Kit image labeling for free item analysis from the captured photo
- ViewModel-driven UI state with placeholder service seams for camera, image recognition, marketplace search, OAuth, and listing publishing
- Fake data sources that simulate item detection, eBay stats, and draft publication
- Workspace custom instructions for Copilot in [.github/copilot-instructions.md](.github/copilot-instructions.md)

## Planned feature areas

- Capture or import a photo of an item
- Identify the item from the image
- Fetch eBay stats such as:
  - how many are listed
  - how many sold
  - median sold price
- Connect to an eBay account
- Publish a prebuilt listing draft to eBay

## Project status

This is a scaffold, not a finished production app. The current code provides a clean starting point for adding:

- advanced camera integration (gallery import, multiple lenses, autofocus tuning)
- better image recognition quality and product-specific model tuning
- eBay OAuth
- eBay marketplace search
- listing publishing workflows

## Connector policy

- Use free options only for connectors and integrations.
- Prefer open-source or free-tier services before any paid alternatives.
- Do not switch to paid connectors unless explicitly approved.

## Build

Use the included Gradle wrapper from the project root:

```powershell
.\gradlew.bat build
```

## Test on Samsung S25 Ultra

1. Enable Developer Options and USB debugging on the phone.
2. Connect the phone by USB and approve the debugging prompt.
3. Confirm device detection with:

```powershell
adb devices
```

4. Install and run debug build from project root:

```powershell
.\gradlew.bat installDebug
```

5. Open Ebay Lister on the phone, allow camera permission, and tap Take photo.

## Next step

Open the Android project in Android Studio, then connect the placeholder actions to real services.

## Python backend option

A modular Python backend is now included in [backend/README.md](backend/README.md).
It provides a free-first image-to-comps workflow with:

- multimodal vision analysis (Gemini when configured, mock fallback)
- eBay sold comps retrieval (real API or mock)
- local correction memory
- cached fallback for resilient valuation

### Android to backend wiring

The Android app now calls `POST /analyze` and `POST /corrections` from the backend.

1. Start backend on your computer:

```powershell
cd backend
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

2. In the app, set `Backend URL` to your computer LAN address, for example:

```text
http://192.168.1.10:8000
```

3. Capture a photo and tap `Analyze`.

The app now uploads the captured image bytes to the backend for analysis, so physical-device testing works even when the backend cannot read the phone file path.

To use real AI instead of mock vision, set `GEMINI_API_KEY` in `backend/.env` before starting the backend. In the app, the Item section now shows `Vision` provider and model so you can verify you are using Gemini.

To use Google Lens-based identification, set `USE_GOOGLE_LENS=true` and `SERPAPI_API_KEY` in `backend/.env`.
The app Market Snapshot now shows:

- Active listed count
- Sold count
- Median sold price

## Using the app over cellular data

- Deploy backend to a public HTTPS host (for example Render or Fly).
- Set your cloud URL in the app and switch mode to `Cloud`.
- (Recommended) set `BACKEND_API_TOKEN` on backend and paste the same token in app `Backend API Token`.
- Tap `Test connection` to verify health before running Analyze.

### Quick Cloud Checklist

1. Deploy backend to Render using `render.yaml` in the repo root.
2. In Render set secrets: `BACKEND_API_TOKEN`, `SERPAPI_API_KEY`, and eBay credentials.
3. Open app, switch mode to `Cloud`, paste Render URL and token.
4. Tap `Test connection`.
5. Capture and Analyze over cell data.

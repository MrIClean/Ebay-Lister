# PixelProfit

Snap a photo. Generate a draft. Maximize profit.

## Go-Live Store Listing Draft

PixelProfit is the ultimate automation tool for resellers. Just snap a photo of your item, and our AI instantly identifies the brand, checks market comps, and builds a fully optimized listing draft ready for publication.

Android starter project for an item-identification and eBay listing workflow.

## What this scaffold includes

- Kotlin + Jetpack Compose Android app shell
- Main screen focused on CameraX photo capture and a large Analyze action
- Dedicated Draft Library screen for storing multiple analyzed items
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

## Current app flow

- `Main` screen: capture photo, tap `Analyze`, then tap `Save Draft` for keeper items.
- `Drafts` screen: build a thrifting-run queue of multiple drafts, then open `Finish listing` for any draft when you are ready to complete it.
- `Listing details` screen: edit title, price, description, add more photos, publish to eBay from inside the app flow, then view or share the published listing link.
- `Listing details` now captures publish-required seller fields: condition, category, shipping profile, return policy, and quantity.
- Publish now validates a listing package locally and through the backend `/publish` endpoint when a backend URL is configured. Real eBay Sell API submission is still the next integration step.
- Re-analyzing and saving the same detected item updates the existing draft entry.
- `Analyze` stays disabled until a photo is captured to prevent empty-analysis requests.
- In `Connection`, entering only `host:port` auto-expands to `http://host:port`.

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

5. Open PixelProfit on the phone, allow camera permission, and tap Take photo.

## Next step

Open the Android project in Android Studio, then connect the placeholder actions to real services.

## Python backend option

A modular Python backend is now included in [backend/README.md](backend/README.md).
It provides a free-first image-to-comps workflow with:

- eBay Browse image-based item identification (`search_by_image`)
- multimodal vision fallback (Gemini when configured, mock fallback)
- eBay sold comps retrieval (real API or mock)
- barcode-to-catalog lookup (`POST /identify/barcode`)
- local correction memory
- cached fallback for resilient valuation

### Android to backend wiring

The Android app now calls `POST /analyze` and `POST /corrections` from the backend.

1. Start backend on your computer:

```powershell
cd scripts
.\start_backend.ps1
```

`start_backend.ps1` now also watches for USB Android devices via adb.
When a device connects, it automatically:

- applies `adb reverse tcp:8000 tcp:8000`
- builds `:app:assembleDebug` and compares APK hash for that device
- runs `:app:installDebug` only when the APK changed (or app is missing on device)
- launches the app on the connected device

2. In the app, set `Backend URL` to your computer LAN address, for example:

```text
http://192.168.1.10:8000
```

For USB testing, the app pre-fills local mode with `http://127.0.0.1:8000`.
This works with `adb reverse tcp:8000 tcp:8000`.

Cloud URL is intentionally blank until you set your tunnel URL.

Leave `Backend API Token` blank unless your backend sets `BACKEND_API_TOKEN`.

3. Capture a photo and tap `Analyze`.

The app now uploads the captured image bytes to the backend for analysis, so physical-device testing works even when the backend cannot read the phone file path.

To use real AI instead of mock vision, set `GEMINI_API_KEY` in `backend/.env` before starting the backend. In the app, the Item section now shows `Vision` provider and model so you can verify you are using Gemini.

To use Google Lens-based identification, set `USE_GOOGLE_LENS=true` and `SERPAPI_API_KEY` in `backend/.env`.
The app Market Snapshot now shows:

- Active listed count
- Sold count
- Median sold price

## Using the app over cellular data

- Run the backend on your computer and expose it through a free Cloudflare quick tunnel.
- Set your tunnel URL in the app and switch mode to `Cloud`.
- (Recommended) set `BACKEND_API_TOKEN` on backend and paste the same token in app `Backend API Token`.
- Tap `Test connection` to verify health before running Analyze.

## Free computer-hosted mode (no hosting fees)

You can run the backend on your own computer and expose it through a free Cloudflare quick tunnel.

Requirements:

- Keep your computer powered on while using the app remotely.
- Keep both backend and tunnel terminal windows open.

Start it:

```powershell
cd scripts
.\start_cloud_mode.ps1
```

The script starts the backend, opens a Cloudflare quick tunnel, and saves:

- `backend/.last-cloud-url`: the current `https://*.trycloudflare.com` backend URL
- `backend/.last-cloud-link`: a `pixelprofit://connection?...` setup link that switches the phone app to Cloud mode

If your phone is connected by USB, the script also updates the app settings directly. If it is not connected, open the setup link on your phone, enter the backend API token if the app does not already have it saved, then tap `Test connection`.

Notes:

- Quick tunnel URLs change each time you restart the tunnel.
- This mode is free, but your backend is only available while your computer is running.
- For security, the setup link does not include `BACKEND_API_TOKEN`.

## Keep local backend always ready (Windows)

If local connection keeps failing because backend is not running, install autostart once:

```powershell
cd scripts
.\install_backend_autostart.ps1
```

What this does:

- creates a Windows Scheduled Task named `PixelProfitLocalBackend`
- starts backend in hidden background at sign-in
- keeps USB testing support from `start_backend.ps1` (adb reverse + auto install/launch checks)

Useful commands:

```powershell
# Start backend now in hidden background (without waiting for next sign-in)
cd scripts
.\start_backend_background.ps1

# Remove autostart task
cd scripts
.\uninstall_backend_autostart.ps1
```

# MenuLens v2

MenuLens v2 is an Android app and FastAPI backend for reading Japanese restaurant menus. Take or upload a menu photo, reveal dishes one by one, and get concise English guidance with an optional AI-generated reference image after reveal.

The design direction is intentionally quiet: warm paper surfaces, charcoal text, restrained vermilion, and a minimal Japanese editorial feel.

## Demo

A short walkthrough of the current app flow: scan a Japanese menu, review locked results, reveal a dish, view an AI-generated reference image, and show the Japanese-only staff card.

https://github.com/user-attachments/assets/8311a799-83b2-44c0-8a68-541393eb94a4

## What it does

- Scans Japanese menu photos with Google Cloud Vision OCR.
- Uses Gemini to extract dish names, prices, tags, and short English explanations.
- Keeps locked dishes private: Japanese names and prices stay visible, but English details and images are hidden until reveal.
- Generates a realistic AI reference image only after a dish is revealed.
- Caches generated dish images locally in development or in Google Cloud Storage in production.
- Shows a staff-facing screen with Japanese-only ordering text.
- Enforces scan/reveal quotas with local subscription/debug controls.

## Project structure

```text
android/   Android app built with Kotlin and Jetpack Compose
backend/   FastAPI service, Gemini/Vision integration, image generation, tests
docs/      Product notes, request flow, theme notes, and worklogs
```

## Backend setup

Python 3.12 is recommended.

```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env
```

Edit `backend/.env` and configure at least:

```env
GOOGLE_CLOUD_VISION_API_KEY=...
GEMINI_API_KEY=...
IMAGE_TOKEN_SECRET=replace-with-a-long-random-secret
```

For live Gemini image generation:

```env
IMAGE_GENERATION_ENABLED=true
GEMINI_IMAGE_MODEL=gemini-3.1-flash-image
IMAGE_PROMPT_VERSION=dish_reference_v1
```

Run the local backend:

```powershell
cd backend
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

API docs:

```text
http://127.0.0.1:8000/docs
```

## Android setup

Requirements:

- JDK 17
- Android SDK
- Android Studio, emulator, or physical Android device

The backend URL is configured in:

```text
android/app/build.gradle.kts
```

Use one of these values for `API_BASE_URL`:

```text
Emulator:        http://10.0.2.2:8000/
Physical device: http://<your-computer-lan-ip>:8000/
Cloud Run:       https://<your-cloud-run-service-url>/
```

Install a debug build:

```powershell
cd android
.\gradlew.bat installDebug
```

## Tests

Backend:

```powershell
cd backend
.\.venv\Scripts\python.exe -m pytest -q
```

Android unit tests:

```powershell
cd android
.\gradlew.bat testDebugUnitTest
```

Android instrumentation build:

```powershell
cd android
.\gradlew.bat assembleDebugAndroidTest
```

## Image generation flow

Menu scans do not fetch external food photos. Instead:

1. `POST /v1/scan_menu` returns dish text and a short-lived signed image-generation token per valid dish.
2. Locked dishes never request images.
3. After reveal, the Android detail screen opens immediately.
4. The app calls `POST /v1/generate_dish_image` with the signed token.
5. The backend returns compressed WebP bytes.
6. The app stores the image in cache and reuses it when reopening the dish.

Generated images are always labeled:

```text
AI-generated reference · Actual presentation may vary.
```

## Deployment notes

The backend is designed for Google Cloud Run.

Recommended production settings:

- Use Firebase Auth for production requests.
- Use Firestore for quota and entitlement state instead of local SQLite.
- Verify Google Play purchase tokens on the backend before granting Pro.
- Keep `IMAGE_GENERATION_ENABLED` behind an environment flag for rollback.
- Use `GENERATED_IMAGE_BUCKET` for Cloud Storage image caching.
- Use a strong `IMAGE_TOKEN_SECRET`.
- Monitor image-generation latency, cache hit rate, failures, and generated image count.

For the first product-readiness backend milestone, see:

```text
docs/BACKEND_PRODUCT_MILESTONE.md
```

Do not commit real `.env` files, API keys, local databases, generated image caches, or Android build outputs.

## Repository

GitHub:

```text
https://github.com/Damen-C/MenuLens-v2
```

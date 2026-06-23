# MenuLens FastAPI Backend

## Run

```powershell
cd backend
py -3.12 -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Required scan settings:

- `GOOGLE_CLOUD_VISION_API_KEY`
- `GEMINI_API_KEY`

Image generation settings:

- `IMAGE_GENERATION_ENABLED=false` keeps scans operational without issuing image tokens.
- `GEMINI_IMAGE_MODEL=gemini-3.1-flash-image`
- `IMAGE_TOKEN_SECRET` must contain at least 32 random characters.
- `IMAGE_PROMPT_VERSION=dish_reference_v1`
- `GENERATED_IMAGE_BUCKET` enables production Google Cloud Storage caching.
- `GENERATED_IMAGE_CACHE_DIR=.generated_images` is used when no bucket is configured.

The scan endpoint never generates or searches for images. It returns legacy
`preview.images: []` plus an `image_generation_token` for each readable dish
when image generation is enabled and configured.

## Endpoints

### `POST /v1/scan_menu`

Multipart fields:

- `image`
- `target_lang`
- `device_id`
- `app_version`
- `timezone`
- optional `request_id`

The endpoint retains the existing Firebase-aware monthly quota and idempotency behavior.

### `POST /v1/generate_dish_image`

JSON body:

```json
{
  "image_generation_token": "<signed token returned by scan_menu>"
}
```

The endpoint accepts no prompt or dish fields. It validates the short-lived
signed token, reuses a normalized dish/prompt/model cache key, and returns
compressed `image/webp` bytes. Unreadable dishes return `422`; generation
failures and timeouts return `502`.

Generated images use a fixed 4:3, 1K prompt and are illustrative references.
The Android client labels them: “AI-generated reference · Actual presentation may vary.”

## Tests

```powershell
.\.venv\Scripts\python.exe -m pytest -q tests evals\tests
```

Tests cover token expiry/tampering, prompt and model configuration, WebP
conversion, cache hits, malformed/blocked responses, timeouts, and removal of
the old internet image-search path.

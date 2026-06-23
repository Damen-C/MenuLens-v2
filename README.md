# MenuLens

MenuLens is an Android app plus FastAPI backend for scanning Japanese menus and returning English-friendly dish results.

## Demo Video

[![Watch MenuLens demo](docs/menulens-0225-ezgif.gif)](https://example.com/menulens-demo)

## Project Status

This project is effectively complete.

Current implemented state:

- Android client built with Kotlin, Compose, and an MVVM-style flow
- FastAPI backend with `POST /v1/scan_menu`
- OCR via Google Cloud Vision
- Dish extraction, translation, and short description generation via Gemini
- On-demand Gemini dish-image generation after reveal, with signed tokens and two-level caching
- Firebase anonymous auth wiring and backend token verification scaffold
- Monthly scan quota enforcement with idempotent request handling
- Offline eval dataset, runner, scoring, and run ledger for prompt iteration

Current default prompt versions:

- `menu_parse_v2`
- `ocr_normalize_v1`

## Repo Structure

- `android/` Android application
- `backend/` FastAPI service and eval tooling
- `docs/` worklogs, product notes, and reference docs

## Backend Pipeline

The backend pipeline is:

1. OCR the uploaded menu image with Google Cloud Vision.
2. Optionally normalize OCR text with Gemini.
3. Extract Japanese menu items with a versioned Gemini prompt.
4. Translate and summarize dishes for English-speaking users.
5. Return empty legacy `preview.images` arrays plus short-lived image-generation tokens.
6. After a reveal, generate one labeled 4:3 WebP reference image and cache it locally or in Cloud Storage.

## Evaluation Setup

MenuLens includes an offline eval loop under `backend/evals/`:

- Dataset: `backend/evals/dataset/examples.json`
- Fixtures: `backend/evals/fixtures/`
- Runner: `python -m evals.run_evals`
- Reports: `backend/evals/results/eval_report_*.json`
- Run ledger: `backend/evals/results/eval_runs.csv`

Prompt versions are tracked in both the JSON report and CSV ledger.

Current adopted eval result for `menu_parse_v2` from `backend/evals/results/eval_report_20260506T013708Z.json`:

- Dataset size: `8`
- Model: `gemini-2.5-flash`
- Parse success rate: `1.0`
- Item recall: `0.8708`
- Item precision proxy: `0.8430`
- Hallucinated item rate: `0.1570`
- Coverage ratio: `1.0`
- Mean latency: `20041.9 ms`
- P95 latency: `45657.8 ms`

Compared with the prior `menu_parse_v1` baseline, `v2` reduced hallucinated items and improved precision without reducing recall, so it is now the default app prompt.

Run evals locally:

```powershell
cd d:\Project_MenuLens\backend
.\.venv\Scripts\python.exe -m evals.run_evals
```

Run evals with a specific prompt version:

```powershell
cd d:\Project_MenuLens\backend
$env:MENU_PARSE_PROMPT_VERSION = "menu_parse_v2"
.\.venv\Scripts\python.exe -m evals.run_evals
Remove-Item Env:MENU_PARSE_PROMPT_VERSION
```

## Backend Setup

Python `3.12` is recommended.

```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env
```

Required keys in `backend/.env`:

- `GOOGLE_CLOUD_VISION_API_KEY`
- `GEMINI_API_KEY`

Common optional settings:

- `GEMINI_MODEL` default `gemini-2.5-flash`
- `OCR_PIPELINE_MODE=hybrid|vision_only`
- `MAX_MENU_ITEMS=1..20`
- `IMAGE_GENERATION_ENABLED=false|true`
- `GEMINI_IMAGE_MODEL=gemini-3.1-flash-image`
- `IMAGE_TOKEN_SECRET` (at least 32 random characters)
- `IMAGE_PROMPT_VERSION=dish_reference_v1`
- `GENERATED_IMAGE_BUCKET` for production GCS caching; leave empty for the local cache

Run backend:

```powershell
cd backend
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

API docs:

- `http://127.0.0.1:8000/docs`

## Android Setup

Requirements:

- JDK `17`
- Android SDK
- Emulator or device

Install debug build:

```powershell
cd android
.\gradlew installDebug
```

Backend base URL is configured in `android/app/build.gradle.kts`.

Use:

- Emulator: `http://10.0.2.2:8000/`
- Physical device: `http://<your-pc-lan-ip>:8000/`
- Cloud Run deployment: `https://menulens-api-gpw37tchwq-an.a.run.app/`

## Deployment Notes

The backend was set up for Cloud Run in `asia-northeast1`.

Operational notes:

- Cloud Run can scale to zero when idle
- Main variable costs come from Vision, Gemini parsing, and cache-miss image generations
- SQLite quota storage is acceptable for local or single-instance use, but not a true shared production store
- Pro billing is not connected

## Documents

- [LLOps Planning.md](LLOps%20Planning.md)
- [docs/WORKLOG_2026-05-06.md](docs/WORKLOG_2026-05-06.md)
- [docs/WORKLOG_2026-04-11.md](docs/WORKLOG_2026-04-11.md)

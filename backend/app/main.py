import base64
import json
import logging
import os
import re
import time
from typing import Any
from uuid import uuid4

import httpx
from fastapi import FastAPI, File, Form, Header, HTTPException, UploadFile
from fastapi.responses import Response
from pydantic import BaseModel
from dotenv import load_dotenv
from app.image_generation import (
    ImageGenerationError,
    ImageGenerationTimeout,
    ImageTokenError,
    create_image_generation_token,
    get_or_generate_dish_image,
    image_generation_enabled,
    is_generatable_dish,
    verify_image_generation_token,
)
from app.prompts.registry import get_active_prompt_version, render_prompt
from app.usage import UsageStore

try:
    import firebase_admin
    from firebase_admin import auth as firebase_auth
except ImportError:
    firebase_admin = None
    firebase_auth = None


load_dotenv()


class DetectedType(BaseModel):
    type: str
    confidence: float


class ImagePreview(BaseModel):
    url: str
    score: float


class Preview(BaseModel):
    en_title: str
    en_description: str
    tags: list[str]
    images: list[ImagePreview]


class ScanItem(BaseModel):
    item_id: str
    jp_text: str
    price_text: str | None = None
    confidence: float
    ocr_diagnostics: dict[str, Any] | None = None
    image_generation_token: str | None = None
    preview: Preview


class ScanMenuResponse(BaseModel):
    scan_id: str
    detected_type: DetectedType
    items: list[ScanItem]
    pipeline_diagnostics: dict[str, Any] | None = None


class LlmItem(BaseModel):
    jp_text: str
    price_text: str | None = None
    en_title: str
    en_description: str
    tags: list[str] = []
    confidence: float = 0.7


class LlmOutput(BaseModel):
    detected_type: str = "dish"
    items: list[LlmItem]


class GenerateDishImageRequest(BaseModel):
    image_generation_token: str


app = FastAPI(title="MenuLens API", version="0.2.0")
logger = logging.getLogger("menulens")
_generated_image_count = 0
_ENABLE_FIREBASE_AUTH = os.getenv("ENABLE_FIREBASE_AUTH", "false").strip().lower() == "true"
_FIREBASE_PROJECT_ID = os.getenv("FIREBASE_PROJECT_ID", "").strip()
_DEV_BYPASS_QUOTA_UIDS = {
    value.strip()
    for value in os.getenv("DEV_BYPASS_QUOTA_UIDS", "").split(",")
    if value.strip()
}


def _env_int(name: str, default: int) -> int:
    raw = os.getenv(name, str(default)).strip()
    try:
        value = int(raw)
    except ValueError as exc:
        raise RuntimeError(f"Invalid integer for {name}: {raw}") from exc
    if value <= 0:
        raise RuntimeError(f"{name} must be > 0")
    return value


_FREE_SCAN_LIMIT_PER_MONTH = _env_int("FREE_SCAN_LIMIT_PER_MONTH", 10)
_PRO_SCAN_LIMIT_PER_MONTH = _env_int("PRO_SCAN_LIMIT_PER_MONTH", 250)
_SCAN_USAGE_DB_PATH = os.getenv("SCAN_USAGE_DB_PATH", "scan_usage.db").strip() or "scan_usage.db"
_usage_store = UsageStore(
    db_path=_SCAN_USAGE_DB_PATH,
    free_quota=_FREE_SCAN_LIMIT_PER_MONTH,
    pro_quota=_PRO_SCAN_LIMIT_PER_MONTH,
)


def _ensure_firebase_admin_initialized() -> None:
    if firebase_admin is None:
        raise HTTPException(status_code=500, detail="firebase-admin is not installed")

    try:
        firebase_admin.get_app()
        return
    except ValueError:
        pass

    options = {"projectId": _FIREBASE_PROJECT_ID} if _FIREBASE_PROJECT_ID else None
    firebase_admin.initialize_app(options=options)


def _extract_bearer_token(authorization: str | None) -> str | None:
    if not authorization:
        return None
    parts = authorization.strip().split(" ", 1)
    if len(parts) != 2 or parts[0].lower() != "bearer" or not parts[1].strip():
        return None
    return parts[1].strip()


def _resolve_authenticated_uid(authorization: str | None) -> str | None:
    token = _extract_bearer_token(authorization)
    if not token:
        if _ENABLE_FIREBASE_AUTH:
            raise HTTPException(status_code=401, detail="Missing Firebase bearer token")
        return None

    if firebase_auth is None:
        if _ENABLE_FIREBASE_AUTH:
            raise HTTPException(status_code=500, detail="firebase-admin import failed")
        logger.warning("firebase-admin unavailable; continuing without Firebase identity")
        return None

    try:
        _ensure_firebase_admin_initialized()
        decoded = firebase_auth.verify_id_token(token, check_revoked=False)
    except Exception as exc:
        if _ENABLE_FIREBASE_AUTH:
            raise HTTPException(status_code=401, detail="Invalid Firebase ID token") from exc
        logger.warning("Firebase token verification failed in optional mode: %s", exc)
        return None

    uid = decoded.get("uid")
    if not uid:
        if _ENABLE_FIREBASE_AUTH:
            raise HTTPException(status_code=401, detail="Firebase token did not include uid")
        return None
    return str(uid)


def _require_env(name: str) -> str:
    value = os.getenv(name, "").strip()
    if not value:
        raise HTTPException(status_code=500, detail=f"Missing required env var: {name}")
    return value


async def _vision_ocr(image_bytes: bytes, api_key: str) -> str:
    encoded = base64.b64encode(image_bytes).decode("utf-8")
    payload = {
        "requests": [
            {
                "image": {"content": encoded},
                "features": [{"type": "TEXT_DETECTION"}],
            }
        ]
    }
    url = f"https://vision.googleapis.com/v1/images:annotate?key={api_key}"
    async with httpx.AsyncClient(timeout=30.0) as client:
        response = await client.post(url, json=payload)
        response.raise_for_status()
        body = response.json()

    ocr_text = (
        body.get("responses", [{}])[0]
        .get("fullTextAnnotation", {})
        .get("text", "")
        .strip()
    )
    if not ocr_text:
        annotations = body.get("responses", [{}])[0].get("textAnnotations", [])
        if annotations:
            ocr_text = annotations[0].get("description", "").strip()
    return ocr_text


async def _gemini_normalize_ocr_text(ocr_text: str, api_key: str) -> str:
    model = os.getenv("GEMINI_MODEL", "gemini-1.5-flash")
    _, prompt = render_prompt("ocr_normalize", ocr_text=ocr_text)

    payload = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {
            "temperature": 0.0,
            "responseMimeType": "application/json",
        },
    }
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={api_key}"
    async with httpx.AsyncClient(timeout=40.0) as client:
        response = await client.post(url, json=payload)
        response.raise_for_status()
        body = response.json()

    try:
        text = body["candidates"][0]["content"]["parts"][0]["text"]
    except (KeyError, IndexError, TypeError) as exc:
        raise ValueError("Gemini normalization response format unexpected") from exc

    parsed = _extract_json_payload(text)
    normalized = str(parsed.get("normalized_text", "")).strip()
    if not normalized:
        raise ValueError("Gemini normalization returned empty text")
    return normalized


def _extract_json_payload(text: str) -> dict[str, Any]:
    cleaned = text.strip()
    if cleaned.startswith("```"):
        cleaned = cleaned.strip("`")
        if cleaned.startswith("json"):
            cleaned = cleaned[4:].strip()
    start = cleaned.find("{")
    end = cleaned.rfind("}")
    if start == -1 or end == -1 or end <= start:
        raise ValueError("Gemini response did not contain a JSON object")
    return json.loads(cleaned[start : end + 1])


async def _gemini_parse_menu(ocr_text: str, target_lang: str, api_key: str) -> LlmOutput:
    model = os.getenv("GEMINI_MODEL", "gemini-1.5-flash")
    max_items = _resolve_max_menu_items()
    _, prompt = render_prompt(
        "menu_parse",
        ocr_text=ocr_text,
        target_lang=target_lang,
        max_items=max_items,
    )

    payload = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {
            "temperature": 0.2,
            "responseMimeType": "application/json",
        },
    }
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={api_key}"
    async with httpx.AsyncClient(timeout=40.0) as client:
        response = await client.post(url, json=payload)
        response.raise_for_status()
        body = response.json()

    try:
        text = body["candidates"][0]["content"]["parts"][0]["text"]
    except (KeyError, IndexError, TypeError) as exc:
        raise ValueError("Gemini response format unexpected") from exc

    parsed = _extract_json_payload(text)
    return LlmOutput.model_validate(parsed)


def _normalize_for_match(value: str) -> str:
    return re.sub(r"\s+", "", value.strip())


def _jp_chars(value: str) -> list[str]:
    chars: list[str] = []
    for ch in value:
        code = ord(ch)
        is_jp = (
            0x3040 <= code <= 0x309F  # Hiragana
            or 0x30A0 <= code <= 0x30FF  # Katakana
            or 0x4E00 <= code <= 0x9FFF  # CJK Unified Ideographs
        )
        if is_jp:
            chars.append(ch)
    return chars


def _calc_item_match_score(item_jp_text: str, source_text: str) -> float:
    item_clean = _normalize_for_match(item_jp_text)
    source_clean = _normalize_for_match(source_text)
    if not item_clean or not source_clean:
        return 0.0

    if item_clean in source_clean:
        return 1.0

    item_jp = _jp_chars(item_clean)
    if item_jp:
        unique = set(item_jp)
        hits = sum(1 for ch in unique if ch in source_clean)
        return hits / max(1, len(unique))

    tokens = re.findall(r"[a-z0-9]+", item_clean.lower())
    if not tokens:
        return 0.0
    hits = sum(1 for token in tokens if token in source_clean.lower())
    return hits / max(1, len(tokens))


def _build_ocr_diagnostics(
    *,
    item_jp_text: str,
    en_title: str,
    source_text_for_matching: str,
    ocr_pipeline: str,
    normalization_changed: bool,
    vision_text_len: int,
    normalized_text_len: int | None,
    llm_confidence: float,
) -> tuple[dict[str, Any], float]:
    match_score = _calc_item_match_score(item_jp_text=item_jp_text, source_text=source_text_for_matching)
    source_quality = max(0.0, min(1.0, vision_text_len / 180.0))
    if normalization_changed:
        source_quality = min(1.0, source_quality + 0.05)

    weak_reasons: list[str] = []
    inferred_style_terms = ("nigiri", "gunkan", "gunkanmaki", "maki", "roll", "sashimi")
    jp_style_hints = (
        "\u63e1\u308a",
        "\u306b\u304e\u308a",
        "\u8ecd\u8266",
        "\u5dfb",
        "\u5dfb\u304d",
        "\u4e3c",
        "\u523a\u8eab",
    )
    title_lower = en_title.lower()
    style_inference_risk = any(term in title_lower for term in inferred_style_terms) and not any(
        hint in item_jp_text for hint in jp_style_hints
    )

    if llm_confidence < 0.5:
        weak_reasons.append("low_llm_confidence")
    if match_score < 0.35:
        weak_reasons.append("weak_ocr_text_match")
    if vision_text_len < 40:
        weak_reasons.append("very_short_ocr_text")
    if style_inference_risk:
        weak_reasons.append("possible_style_inference")

    penalty = 0.0
    if style_inference_risk:
        penalty += 0.12
    if match_score < 0.35:
        penalty += 0.08
    if llm_confidence < 0.5:
        penalty += 0.06

    final_confidence = 0.55 * llm_confidence + 0.25 * match_score + 0.20 * source_quality - penalty
    final_confidence = max(0.0, min(1.0, final_confidence))
    if llm_confidence > 0.95 and match_score > 0.95 and source_quality < 0.75:
        final_confidence = min(final_confidence, 0.93)

    diagnostics: dict[str, Any] = {
        "ocr_pipeline": ocr_pipeline,
        "vision_text_length": vision_text_len,
        "normalized_text_length": normalized_text_len,
        "normalization_changed": normalization_changed,
        "match_score": round(match_score, 3),
        "source_quality": round(source_quality, 3),
        "llm_confidence": round(llm_confidence, 3),
        "weak_reasons": weak_reasons,
    }
    return diagnostics, round(final_confidence, 3)


def _resolve_ocr_pipeline_mode() -> str:
    mode = os.getenv("OCR_PIPELINE_MODE", "hybrid").strip().lower()
    if mode not in {"vision_only", "hybrid"}:
        raise HTTPException(
            status_code=500,
            detail="Invalid OCR_PIPELINE_MODE. Use one of: vision_only, hybrid.",
        )
    return mode


def _resolve_max_menu_items() -> int:
    raw = os.getenv("MAX_MENU_ITEMS", "10").strip()
    try:
        value = int(raw)
    except ValueError as exc:
        raise HTTPException(status_code=500, detail="Invalid MAX_MENU_ITEMS. Use an integer between 1 and 20.") from exc
    if value < 1 or value > 20:
        raise HTTPException(status_code=500, detail="Invalid MAX_MENU_ITEMS. Use an integer between 1 and 20.")
    return value


def _estimate_ocr_candidate_count(ocr_text: str) -> int:
    jp_chunks = re.findall(r"[\u3041-\u3093\u30A1-\u30F3\u4E00-\u9FAF\u30FC]{2,}", ocr_text)
    unique_chunks = {chunk.strip() for chunk in jp_chunks if chunk.strip()}
    return len(unique_chunks)


def _fallback_response() -> ScanMenuResponse:
    return ScanMenuResponse(
        scan_id=str(uuid4()),
        detected_type=DetectedType(type="dish", confidence=0.55),
        items=[
            ScanItem(
                item_id=str(uuid4()),
                jp_text="料理名を読み取れませんでした",
                price_text=None,
                confidence=0.45,
                preview=Preview(
                    en_title="Could not read menu item",
                    en_description="Please retake the photo with sharper focus and better lighting.",
                    tags=["retry"],
                    images=[],
                ),
            )
        ],
    )


@app.post("/v1/scan_menu", response_model=ScanMenuResponse)
async def scan_menu(
    image: UploadFile = File(...),
    target_lang: str = Form(...),
    device_id: str = Form(...),
    app_version: str = Form(...),
    timezone: str = Form(...),
    request_id: str | None = Form(default=None),
    authorization: str | None = Header(default=None),
) -> ScanMenuResponse:
    authenticated_uid = _resolve_authenticated_uid(authorization)
    subject_key = f"uid:{authenticated_uid}" if authenticated_uid else f"device:{device_id}"

    if authenticated_uid and authenticated_uid in _DEV_BYPASS_QUOTA_UIDS:
        logger.info("Developer quota bypass applied. uid=%s", authenticated_uid)
        usage = _usage_store.developer_bypass_decision(subject_key=subject_key)
    else:
        usage = _usage_store.consume_scan(subject_key=subject_key, request_id=request_id)
    if not usage.allowed:
        raise HTTPException(
            status_code=402,
            detail={
                "code": "scan_quota_exceeded",
                "message": "Monthly scan limit reached.",
                "plan": usage.plan,
                "period_ym": usage.period_ym,
                "used_scans": usage.used_scans,
                "quota_scans": usage.quota_scans,
                "remaining_scans": usage.remaining_scans,
            },
        )
    _ = (device_id, app_version, timezone, authenticated_uid, request_id)
    image_bytes = await image.read()
    if not image_bytes:
        raise HTTPException(status_code=400, detail="Uploaded image is empty")

    vision_key = _require_env("GOOGLE_CLOUD_VISION_API_KEY")
    gemini_key = _require_env("GEMINI_API_KEY")
    ocr_pipeline_mode = _resolve_ocr_pipeline_mode()
    max_menu_items = _resolve_max_menu_items()

    try:
        scan_start = time.perf_counter()
        prompt_versions = {
            "menu_parse_prompt_version": get_active_prompt_version("menu_parse"),
            "ocr_normalize_prompt_version": get_active_prompt_version("ocr_normalize"),
        }
        stage_latency_ms: dict[str, int] = {}
        vision_start = time.perf_counter()
        vision_ocr_text = await _vision_ocr(image_bytes=image_bytes, api_key=vision_key)
        stage_latency_ms["vision_ocr"] = int((time.perf_counter() - vision_start) * 1000)
        if not vision_ocr_text:
            return _fallback_response()

        normalized_ocr_text: str | None = None
        normalization_fallback_used = False
        if ocr_pipeline_mode == "hybrid":
            normalize_start = time.perf_counter()
            try:
                normalized_ocr_text = await _gemini_normalize_ocr_text(ocr_text=vision_ocr_text, api_key=gemini_key)
            except Exception:
                normalization_fallback_used = True
                logger.exception("OCR normalization failed. Falling back to raw Vision OCR text.")
            stage_latency_ms["ocr_normalize"] = int((time.perf_counter() - normalize_start) * 1000)
        else:
            stage_latency_ms["ocr_normalize"] = 0

        parse_source_text = normalized_ocr_text or vision_ocr_text
        normalization_changed = (
            normalized_ocr_text is not None
            and _normalize_for_match(normalized_ocr_text) != _normalize_for_match(vision_ocr_text)
        )

        parse_start = time.perf_counter()
        llm = await _gemini_parse_menu(ocr_text=parse_source_text, target_lang=target_lang, api_key=gemini_key)
        stage_latency_ms["menu_parse"] = int((time.perf_counter() - parse_start) * 1000)
        if not llm.items:
            return _fallback_response()

        estimated_candidates = _estimate_ocr_candidate_count(parse_source_text)
        items: list[ScanItem] = []
        for raw_item in llm.items[:max_menu_items]:
            generation_token = None
            if image_generation_enabled() and is_generatable_dish(raw_item.jp_text, raw_item.en_title):
                try:
                    generation_token = create_image_generation_token(
                        jp_text=raw_item.jp_text,
                        en_title=raw_item.en_title,
                        subject_key=subject_key,
                    )
                except Exception:
                    logger.exception(
                        "Image token issuance failed; scan will continue without generated imagery."
                    )
            items.append(
                ScanItem(
                    item_id=str(uuid4()),
                    jp_text=raw_item.jp_text,
                    price_text=raw_item.price_text,
                    confidence=0.0,
                    ocr_diagnostics={},
                    image_generation_token=generation_token,
                    preview=Preview(
                        en_title=raw_item.en_title,
                        en_description=raw_item.en_description,
                        tags=raw_item.tags[:5],
                        images=[],
                    ),
                )
            )

            llm_confidence = max(0.0, min(1.0, raw_item.confidence))
            diagnostics, final_confidence = _build_ocr_diagnostics(
                item_jp_text=raw_item.jp_text,
                en_title=raw_item.en_title,
                source_text_for_matching=parse_source_text,
                ocr_pipeline=ocr_pipeline_mode,
                normalization_changed=normalization_changed,
                vision_text_len=len(vision_ocr_text),
                normalized_text_len=len(normalized_ocr_text) if normalized_ocr_text else None,
                llm_confidence=llm_confidence,
            )
            items[-1].confidence = final_confidence
            items[-1].ocr_diagnostics = diagnostics

        total_latency_ms = int((time.perf_counter() - scan_start) * 1000)
        logger.info(
            "scan_menu completed. mode=%s items=%s elapsed_ms=%s stage_latency_ms=%s",
            ocr_pipeline_mode,
            len(items),
            total_latency_ms,
            stage_latency_ms,
        )
        coverage_ratio = min(1.0, len(items) / estimated_candidates) if estimated_candidates > 0 else None
        return ScanMenuResponse(
            scan_id=str(uuid4()),
            detected_type=DetectedType(type=llm.detected_type, confidence=0.8),
            items=items,
            pipeline_diagnostics={
                "ocr_pipeline_mode": ocr_pipeline_mode,
                "max_menu_items": max_menu_items,
                "estimated_ocr_candidate_count": estimated_candidates,
                "returned_item_count": len(items),
                "coverage_ratio": round(coverage_ratio, 3) if coverage_ratio is not None else None,
                "model": os.getenv("GEMINI_MODEL", "gemini-1.5-flash"),
                "image_generation_enabled": image_generation_enabled(),
                "normalization_fallback_used": normalization_fallback_used,
                "stage_latency_ms": stage_latency_ms,
                "total_latency_ms": total_latency_ms,
                "auth_subject_type": "firebase" if authenticated_uid else "device",
                "usage_period_ym": usage.period_ym,
                "usage_plan": usage.plan,
                "usage_scans_used": usage.used_scans,
                "usage_scans_quota": usage.quota_scans,
                "usage_scans_remaining": usage.remaining_scans,
                "usage_duplicate_request": usage.duplicate_request,
                **prompt_versions,
            },
        )
    except HTTPException:
        raise
    except httpx.HTTPError as exc:
        raise HTTPException(status_code=502, detail=f"Upstream API error: {exc}") from exc
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Scan pipeline failed: {exc}") from exc


@app.post("/v1/generate_dish_image")
async def generate_dish_image(
    request: GenerateDishImageRequest,
    authorization: str | None = Header(default=None),
) -> Response:
    global _generated_image_count

    if not image_generation_enabled():
        raise HTTPException(status_code=503, detail="Dish image generation is currently unavailable")

    try:
        payload = verify_image_generation_token(request.image_generation_token)
    except ImageTokenError as exc:
        message = str(exc)
        status_code = 422 if "eligible" in message else 401
        logger.warning("Dish image token rejected. reason=%s", message)
        raise HTTPException(status_code=status_code, detail=message) from exc

    authenticated_uid = _resolve_authenticated_uid(authorization)
    if payload.subject_key.startswith("uid:"):
        expected_subject = f"uid:{authenticated_uid}" if authenticated_uid else None
        if expected_subject != payload.subject_key:
            raise HTTPException(status_code=403, detail="Image generation token belongs to another user")

    try:
        result = await get_or_generate_dish_image(
            payload,
            api_key=_require_env("GEMINI_API_KEY"),
        )
    except ImageGenerationTimeout as exc:
        logger.error(
            "Dish image generation timed out. model=%s cache_hit=false failure=timeout",
            os.getenv("GEMINI_IMAGE_MODEL", "gemini-3.1-flash-image"),
        )
        raise HTTPException(status_code=502, detail="Dish image generation timed out") from exc
    except ImageGenerationError as exc:
        logger.error(
            "Dish image generation failed. model=%s cache_hit=false failure=%s",
            os.getenv("GEMINI_IMAGE_MODEL", "gemini-3.1-flash-image"),
            type(exc).__name__,
        )
        raise HTTPException(status_code=502, detail="Dish image generation failed") from exc
    except Exception as exc:
        logger.exception(
            "Dish image generation failed unexpectedly. model=%s cache_hit=unknown",
            os.getenv("GEMINI_IMAGE_MODEL", "gemini-3.1-flash-image"),
        )
        raise HTTPException(status_code=502, detail="Dish image generation failed") from exc

    if not result.cache_hit:
        _generated_image_count += 1
    logger.info(
        "Dish image ready. model=%s latency_ms=%s cache_hit=%s generated_image_count=%s",
        result.model,
        result.latency_ms,
        result.cache_hit,
        _generated_image_count,
    )
    return Response(
        content=result.image_bytes,
        media_type="image/webp",
        headers={
            "Cache-Control": "private, max-age=86400",
            "X-MenuLens-Image-Cache": "hit" if result.cache_hit else "miss",
            "X-MenuLens-Image-Model": result.model,
        },
    )

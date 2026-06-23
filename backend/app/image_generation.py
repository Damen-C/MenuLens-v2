from __future__ import annotations

import asyncio
import base64
import hashlib
import hmac
import io
import json
import logging
import os
import re
import time
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Protocol

from google import genai
from google.genai import types
from PIL import Image

logger = logging.getLogger("menulens.image_generation")

DEFAULT_IMAGE_MODEL = "gemini-3.1-flash-image"
DEFAULT_PROMPT_VERSION = "dish_reference_v1"
DEFAULT_TOKEN_TTL_SECONDS = 15 * 60
DEFAULT_GENERATION_TIMEOUT_SECONDS = 75
FALLBACK_JP_TEXT = "料理名を読み取れませんでした"


class ImageTokenError(ValueError):
    pass


class ImageGenerationError(RuntimeError):
    pass


class ImageGenerationTimeout(ImageGenerationError):
    pass


@dataclass(frozen=True)
class ImageTokenPayload:
    jp_text: str
    en_title: str
    subject_key: str
    expires_at: int
    prompt_version: str


@dataclass(frozen=True)
class GeneratedImageResult:
    image_bytes: bytes
    cache_hit: bool
    cache_key: str
    model: str
    latency_ms: int


class ImageCache(Protocol):
    def get(self, cache_key: str) -> bytes | None: ...

    def put(self, cache_key: str, image_bytes: bytes) -> None: ...


class LocalImageCache:
    def __init__(self, directory: str | Path):
        self.directory = Path(directory)

    def _path(self, cache_key: str) -> Path:
        return self.directory / f"{cache_key}.webp"

    def get(self, cache_key: str) -> bytes | None:
        path = self._path(cache_key)
        return path.read_bytes() if path.is_file() else None

    def put(self, cache_key: str, image_bytes: bytes) -> None:
        self.directory.mkdir(parents=True, exist_ok=True)
        destination = self._path(cache_key)
        temporary = destination.with_suffix(".tmp")
        temporary.write_bytes(image_bytes)
        temporary.replace(destination)


class GcsImageCache:
    def __init__(self, bucket_name: str):
        from google.cloud import storage

        self.bucket = storage.Client().bucket(bucket_name)

    @staticmethod
    def _object_name(cache_key: str) -> str:
        return f"dish-images/{cache_key}.webp"

    def get(self, cache_key: str) -> bytes | None:
        blob = self.bucket.blob(self._object_name(cache_key))
        if not blob.exists():
            return None
        return blob.download_as_bytes()

    def put(self, cache_key: str, image_bytes: bytes) -> None:
        from google.api_core.exceptions import PreconditionFailed

        blob = self.bucket.blob(self._object_name(cache_key))
        try:
            blob.upload_from_string(
                image_bytes,
                content_type="image/webp",
                if_generation_match=0,
            )
        except PreconditionFailed:
            # A concurrent request populated the same deterministic cache key.
            return


def image_generation_enabled() -> bool:
    return os.getenv("IMAGE_GENERATION_ENABLED", "false").strip().lower() == "true"


def image_model() -> str:
    return os.getenv("GEMINI_IMAGE_MODEL", DEFAULT_IMAGE_MODEL).strip() or DEFAULT_IMAGE_MODEL


def image_prompt_version() -> str:
    return os.getenv("IMAGE_PROMPT_VERSION", DEFAULT_PROMPT_VERSION).strip() or DEFAULT_PROMPT_VERSION


def _token_secret() -> bytes:
    value = os.getenv("IMAGE_TOKEN_SECRET", "").strip()
    if not value:
        raise RuntimeError("Missing required env var: IMAGE_TOKEN_SECRET")
    if len(value) < 32:
        raise RuntimeError("IMAGE_TOKEN_SECRET must contain at least 32 characters")
    return value.encode("utf-8")


def _b64url_encode(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).decode("ascii").rstrip("=")


def _b64url_decode(value: str) -> bytes:
    padding = "=" * (-len(value) % 4)
    try:
        return base64.urlsafe_b64decode(value + padding)
    except Exception as exc:
        raise ImageTokenError("Malformed image generation token") from exc


def create_image_generation_token(
    *,
    jp_text: str,
    en_title: str,
    subject_key: str,
    now: int | None = None,
    ttl_seconds: int = DEFAULT_TOKEN_TTL_SECONDS,
) -> str:
    issued_at = int(time.time() if now is None else now)
    payload = {
        "v": 1,
        "jp": jp_text.strip(),
        "en": en_title.strip(),
        "sub": subject_key,
        "exp": issued_at + ttl_seconds,
        "pv": image_prompt_version(),
    }
    encoded_payload = _b64url_encode(
        json.dumps(payload, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode("utf-8")
    )
    signature = hmac.new(_token_secret(), encoded_payload.encode("ascii"), hashlib.sha256).digest()
    return f"{encoded_payload}.{_b64url_encode(signature)}"


def verify_image_generation_token(token: str, *, now: int | None = None) -> ImageTokenPayload:
    try:
        encoded_payload, encoded_signature = token.split(".", 1)
    except ValueError as exc:
        raise ImageTokenError("Malformed image generation token") from exc

    expected = hmac.new(_token_secret(), encoded_payload.encode("ascii"), hashlib.sha256).digest()
    supplied = _b64url_decode(encoded_signature)
    if not hmac.compare_digest(expected, supplied):
        raise ImageTokenError("Invalid image generation token signature")

    try:
        payload = json.loads(_b64url_decode(encoded_payload))
        expires_at = int(payload["exp"])
        parsed = ImageTokenPayload(
            jp_text=str(payload["jp"]).strip(),
            en_title=str(payload["en"]).strip(),
            subject_key=str(payload["sub"]),
            expires_at=expires_at,
            prompt_version=str(payload["pv"]),
        )
    except (KeyError, TypeError, ValueError, json.JSONDecodeError) as exc:
        raise ImageTokenError("Malformed image generation token payload") from exc

    current_time = int(time.time() if now is None else now)
    if expires_at < current_time:
        raise ImageTokenError("Image generation token expired")
    if not is_generatable_dish(parsed.jp_text, parsed.en_title):
        raise ImageTokenError("Dish is not eligible for image generation")
    return parsed


def is_generatable_dish(jp_text: str, en_title: str) -> bool:
    jp = jp_text.strip()
    en = en_title.strip().lower()
    blocked_titles = {
        "",
        "could not read menu item",
        "unreadable menu item",
        "unknown dish",
    }
    return bool(jp and jp != FALLBACK_JP_TEXT and en not in blocked_titles)


def build_dish_image_prompt(*, jp_text: str, en_title: str) -> str:
    return (
        "Create one realistic reference photograph of the Japanese dish described below. "
        "Show a typical presentation in a Japanese restaurant, centered as the clear subject, "
        "with natural window lighting, believable ingredients, restrained styling, and a neutral "
        "table setting. The image is an illustrative reference, not a promise of the restaurant's "
        "actual presentation. No people, hands, text, letters, logos, menus, watermarks, branded "
        "packaging, collages, or multiple dishes. Do not add ingredients that conflict with the dish. "
        f"Dish in Japanese: {jp_text.strip()}. English name: {en_title.strip()}."
    )


def normalize_dish_key(jp_text: str, en_title: str) -> str:
    value = unicodedata.normalize("NFKC", f"{jp_text}|{en_title}").casefold()
    return re.sub(r"\s+", " ", value).strip()


def build_cache_key(payload: ImageTokenPayload, model: str) -> str:
    material = "|".join(
        [
            normalize_dish_key(payload.jp_text, payload.en_title),
            payload.prompt_version,
            model,
            "4:3",
            "1K",
        ]
    )
    return hashlib.sha256(material.encode("utf-8")).hexdigest()


def compress_to_webp(image_bytes: bytes) -> bytes:
    try:
        with Image.open(io.BytesIO(image_bytes)) as image:
            converted = image.convert("RGB")
            output = io.BytesIO()
            converted.save(output, format="WEBP", quality=82, method=6)
    except Exception as exc:
        raise ImageGenerationError("Generated response did not contain a readable image") from exc
    result = output.getvalue()
    if not result:
        raise ImageGenerationError("WebP compression produced an empty image")
    return result


def _extract_generated_image_bytes(response: Any) -> bytes:
    final_candidates: list[bytes] = []
    thought_candidates: list[bytes] = []
    for part in getattr(response, "parts", None) or []:
        inline_data = getattr(part, "inline_data", None)
        data = getattr(inline_data, "data", None) if inline_data is not None else None
        if data:
            target = thought_candidates if getattr(part, "thought", False) else final_candidates
            target.append(bytes(data))
    if final_candidates:
        return final_candidates[-1]
    if thought_candidates:
        return thought_candidates[-1]
    raise ImageGenerationError("Gemini returned no generated image")


def generate_with_gemini(
    payload: ImageTokenPayload,
    *,
    api_key: str,
    model: str,
    client: Any | None = None,
) -> bytes:
    active_client = client or genai.Client(api_key=api_key)
    response = active_client.models.generate_content(
        model=model,
        contents=[build_dish_image_prompt(jp_text=payload.jp_text, en_title=payload.en_title)],
        config=types.GenerateContentConfig(
            response_modalities=["TEXT", "IMAGE"],
            image_config=types.ImageConfig(
                aspect_ratio="4:3",
                image_size="1K",
            ),
        ),
    )
    return compress_to_webp(_extract_generated_image_bytes(response))


def default_image_cache() -> ImageCache:
    bucket = os.getenv("GENERATED_IMAGE_BUCKET", "").strip()
    if bucket:
        return GcsImageCache(bucket)
    directory = os.getenv("GENERATED_IMAGE_CACHE_DIR", ".generated_images").strip()
    return LocalImageCache(directory or ".generated_images")


async def get_or_generate_dish_image(
    payload: ImageTokenPayload,
    *,
    api_key: str,
    cache: ImageCache | None = None,
    client: Any | None = None,
    timeout_seconds: int = DEFAULT_GENERATION_TIMEOUT_SECONDS,
) -> GeneratedImageResult:
    active_cache = cache or default_image_cache()
    model = image_model()
    cache_key = build_cache_key(payload, model)
    start = time.perf_counter()

    cached = await asyncio.to_thread(active_cache.get, cache_key)
    if cached:
        return GeneratedImageResult(
            image_bytes=cached,
            cache_hit=True,
            cache_key=cache_key,
            model=model,
            latency_ms=int((time.perf_counter() - start) * 1000),
        )

    try:
        image_bytes = await asyncio.wait_for(
            asyncio.to_thread(
                generate_with_gemini,
                payload,
                api_key=api_key,
                model=model,
                client=client,
            ),
            timeout=timeout_seconds,
        )
    except asyncio.TimeoutError as exc:
        raise ImageGenerationTimeout("Gemini image generation timed out") from exc

    await asyncio.to_thread(active_cache.put, cache_key, image_bytes)
    return GeneratedImageResult(
        image_bytes=image_bytes,
        cache_hit=False,
        cache_key=cache_key,
        model=model,
        latency_ms=int((time.perf_counter() - start) * 1000),
    )

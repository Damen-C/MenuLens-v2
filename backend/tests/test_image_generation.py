import asyncio
import io
from types import SimpleNamespace

import pytest
from PIL import Image

from app.image_generation import (
    ImageGenerationError,
    ImageGenerationTimeout,
    ImageTokenError,
    ImageTokenPayload,
    build_dish_image_prompt,
    create_image_generation_token,
    generate_with_gemini,
    get_or_generate_dish_image,
    verify_image_generation_token,
)


class MemoryCache:
    def __init__(self):
        self.values: dict[str, bytes] = {}

    def get(self, cache_key: str) -> bytes | None:
        return self.values.get(cache_key)

    def put(self, cache_key: str, image_bytes: bytes) -> None:
        self.values[cache_key] = image_bytes


class FakeModels:
    def __init__(self, response=None, error: Exception | None = None):
        self.response = response
        self.error = error
        self.calls = 0
        self.last_model = None
        self.last_contents = None
        self.last_config = None

    def generate_content(self, *, model, contents, config):
        self.calls += 1
        self.last_model = model
        self.last_contents = contents
        self.last_config = config
        if self.error:
            raise self.error
        return self.response


class FakeClient:
    def __init__(self, models: FakeModels):
        self.models = models


def png_bytes() -> bytes:
    output = io.BytesIO()
    Image.new("RGB", (40, 30), color=(180, 60, 40)).save(output, format="PNG")
    return output.getvalue()


def image_response(image_bytes: bytes):
    return SimpleNamespace(
        parts=[
            SimpleNamespace(
                text=None,
                inline_data=SimpleNamespace(data=image_bytes),
            )
        ]
    )


def multi_image_response(first: bytes, final: bytes):
    return SimpleNamespace(
        parts=[
            SimpleNamespace(
                text=None,
                thought=True,
                inline_data=SimpleNamespace(data=first),
            ),
            SimpleNamespace(
                text=None,
                thought=False,
                inline_data=SimpleNamespace(data=final),
            ),
        ]
    )


@pytest.fixture(autouse=True)
def image_env(monkeypatch):
    monkeypatch.setenv("IMAGE_TOKEN_SECRET", "test-secret-that-is-definitely-32-characters")
    monkeypatch.setenv("IMAGE_PROMPT_VERSION", "dish_reference_test")
    monkeypatch.setenv("GEMINI_IMAGE_MODEL", "gemini-3.1-flash-image")


def test_signed_token_round_trip_expiry_and_tamper_rejection():
    token = create_image_generation_token(
        jp_text="醤油ラーメン",
        en_title="Shoyu ramen",
        subject_key="uid:user-1",
        now=1_000,
        ttl_seconds=60,
    )

    payload = verify_image_generation_token(token, now=1_059)
    assert payload.jp_text == "醤油ラーメン"
    assert payload.en_title == "Shoyu ramen"
    assert payload.subject_key == "uid:user-1"

    with pytest.raises(ImageTokenError, match="expired"):
        verify_image_generation_token(token, now=1_061)

    replacement = "a" if token[-1] != "a" else "b"
    with pytest.raises(ImageTokenError, match="signature"):
        verify_image_generation_token(token[:-1] + replacement, now=1_010)


def test_fallback_dish_is_blocked():
    token = create_image_generation_token(
        jp_text="料理名を読み取れませんでした",
        en_title="Could not read menu item",
        subject_key="device:test",
        now=1_000,
    )
    with pytest.raises(ImageTokenError, match="eligible"):
        verify_image_generation_token(token, now=1_001)


def test_prompt_and_model_configuration():
    models = FakeModels(response=image_response(png_bytes()))
    payload = ImageTokenPayload(
        jp_text="天ぷらうどん",
        en_title="Tempura udon",
        subject_key="device:test",
        expires_at=2_000,
        prompt_version="dish_reference_test",
    )

    result = generate_with_gemini(
        payload,
        api_key="unused",
        model="gemini-3.1-flash-image",
        client=FakeClient(models),
    )

    assert result[:4] == b"RIFF"
    assert models.last_model == "gemini-3.1-flash-image"
    prompt = models.last_contents[0]
    assert prompt == build_dish_image_prompt(jp_text="天ぷらうどん", en_title="Tempura udon")
    assert "No people" in prompt
    assert "logos" in prompt
    assert models.last_config.response_modalities == ["TEXT", "IMAGE"]
    assert models.last_config.image_config.aspect_ratio == "4:3"
    assert models.last_config.image_config.image_size == "1K"
    assert models.last_config.image_config.output_mime_type is None


def test_cache_hit_avoids_gemini_call_and_valid_generation_returns_webp():
    payload = ImageTokenPayload(
        jp_text="親子丼",
        en_title="Oyakodon",
        subject_key="device:test",
        expires_at=2_000,
        prompt_version="dish_reference_test",
    )
    cache = MemoryCache()
    models = FakeModels(response=image_response(png_bytes()))

    first = asyncio.run(
        get_or_generate_dish_image(
            payload,
            api_key="unused",
            cache=cache,
            client=FakeClient(models),
        )
    )
    second = asyncio.run(
        get_or_generate_dish_image(
            payload,
            api_key="unused",
            cache=cache,
            client=FakeClient(models),
        )
    )

    assert first.image_bytes[:4] == b"RIFF"
    assert first.cache_hit is False
    assert second.cache_hit is True
    assert second.image_bytes == first.image_bytes
    assert models.calls == 1


def test_final_non_thought_image_is_selected():
    first = io.BytesIO()
    Image.new("RGB", (40, 30), color=(255, 0, 0)).save(first, format="PNG")
    final = io.BytesIO()
    Image.new("RGB", (40, 30), color=(0, 255, 0)).save(final, format="PNG")
    payload = ImageTokenPayload("親子丼", "Oyakodon", "device:test", 2_000, "dish_reference_test")

    webp = generate_with_gemini(
        payload,
        api_key="unused",
        model="gemini-3.1-flash-image",
        client=FakeClient(FakeModels(response=multi_image_response(first.getvalue(), final.getvalue()))),
    )

    with Image.open(io.BytesIO(webp)) as image:
        red, green, _ = image.getpixel((10, 10))
    assert green > red


@pytest.mark.parametrize(
    "response",
    [
        SimpleNamespace(parts=[]),
        SimpleNamespace(parts=[SimpleNamespace(text="blocked", inline_data=None)]),
        SimpleNamespace(parts=[SimpleNamespace(text=None, inline_data=SimpleNamespace(data=b"bad"))]),
    ],
)
def test_empty_blocked_and_malformed_responses_fail_gracefully(response):
    payload = ImageTokenPayload("寿司", "Sushi", "device:test", 2_000, "dish_reference_test")
    with pytest.raises(ImageGenerationError):
        generate_with_gemini(
            payload,
            api_key="unused",
            model="gemini-3.1-flash-image",
            client=FakeClient(FakeModels(response=response)),
        )


def test_timeout_fails_gracefully(monkeypatch):
    payload = ImageTokenPayload("寿司", "Sushi", "device:test", 2_000, "dish_reference_test")

    def slow_generation(*args, **kwargs):
        import time

        time.sleep(0.05)
        return b"unused"

    monkeypatch.setattr("app.image_generation.generate_with_gemini", slow_generation)
    with pytest.raises(ImageGenerationTimeout):
        asyncio.run(
            get_or_generate_dish_image(
                payload,
                api_key="unused",
                cache=MemoryCache(),
                timeout_seconds=0.001,
            )
        )


def test_scan_module_has_no_internet_image_search_path(monkeypatch, tmp_path):
    monkeypatch.setenv("SCAN_USAGE_DB_PATH", str(tmp_path / "usage.db"))
    from app import main

    assert not hasattr(main, "_image_search")
    assert not hasattr(main, "_vertex_search")
    assert not hasattr(main, "_image_search_by_provider")

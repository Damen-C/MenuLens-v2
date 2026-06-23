import os
from pathlib import Path


_PROMPTS_DIR = Path(__file__).resolve().parent
_PROMPT_ENV_VARS = {
    "ocr_normalize": "OCR_NORMALIZE_PROMPT_VERSION",
    "menu_parse": "MENU_PARSE_PROMPT_VERSION",
}
_DEFAULT_VERSIONS = {
    "ocr_normalize": "ocr_normalize_v1",
    "menu_parse": "menu_parse_v2",
}


def get_active_prompt_version(prompt_name: str) -> str:
    env_var = _PROMPT_ENV_VARS[prompt_name]
    default_version = _DEFAULT_VERSIONS[prompt_name]
    version = os.getenv(env_var, default_version).strip() or default_version
    prompt_path = _PROMPTS_DIR / f"{version}.txt"
    if not prompt_path.exists():
        raise RuntimeError(f"Prompt file not found for {prompt_name}: {prompt_path.name}")
    return version


def render_prompt(prompt_name: str, **values: object) -> tuple[str, str]:
    version = get_active_prompt_version(prompt_name)
    prompt_path = _PROMPTS_DIR / f"{version}.txt"
    prompt = prompt_path.read_text(encoding="utf-8").strip()
    for key, value in values.items():
        prompt = prompt.replace(f"{{{key}}}", str(value))
    return version, prompt

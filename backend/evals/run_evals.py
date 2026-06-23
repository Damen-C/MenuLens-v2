import asyncio
import csv
import json
import os
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from app.main import (
    _build_ocr_diagnostics,
    _estimate_ocr_candidate_count,
    _gemini_normalize_ocr_text,
    _gemini_parse_menu,
    _normalize_for_match,
    _require_env,
    _resolve_max_menu_items,
    _resolve_ocr_pipeline_mode,
    _vision_ocr,
)
from app.prompts.registry import get_active_prompt_version
from evals.scoring import score_example, summarize_results


DATASET_PATH = Path(os.getenv("EVAL_DATASET_PATH", Path(__file__).resolve().parent / "dataset" / "examples.json"))
RESULTS_DIR = Path(os.getenv("EVAL_RESULTS_DIR", Path(__file__).resolve().parent / "results"))
TARGET_LANG = os.getenv("EVAL_TARGET_LANG", "en").strip() or "en"
INCLUDE_DISABLED = os.getenv("EVAL_INCLUDE_DISABLED", "false").strip().lower() == "true"
RUN_LEDGER_PATH = Path(os.getenv("EVAL_RUN_LEDGER_PATH", RESULTS_DIR / "eval_runs.csv"))
RUN_LEDGER_FIELDS = [
    "run_at_utc",
    "report_path",
    "dataset_path",
    "target_lang",
    "model",
    "ocr_pipeline_mode",
    "menu_parse_prompt_version",
    "ocr_normalize_prompt_version",
    "example_count",
    "failure_count",
    "parse_success_rate",
    "item_recall",
    "item_precision_proxy",
    "hallucinated_item_rate",
    "coverage_ratio",
    "mean_latency_ms",
    "p95_latency_ms",
]


def _load_dataset() -> list[dict[str, Any]]:
    dataset = json.loads(DATASET_PATH.read_text(encoding="utf-8"))
    if not isinstance(dataset, list):
        raise RuntimeError("Eval dataset must be a JSON array")
    if INCLUDE_DISABLED:
        return dataset
    return [example for example in dataset if example.get("enabled", True)]


def _resolve_image_path(raw_path: str) -> Path:
    candidate = Path(raw_path)
    if candidate.is_absolute():
        return candidate
    return Path(__file__).resolve().parent.parent / raw_path


async def _run_example(example: dict[str, Any]) -> dict[str, Any]:
    fixture_id = str(example["fixture_id"])
    ocr_pipeline_mode = _resolve_ocr_pipeline_mode()
    max_menu_items = _resolve_max_menu_items()
    gemini_key = _require_env("GEMINI_API_KEY")
    vision_key = os.getenv("GOOGLE_CLOUD_VISION_API_KEY", "").strip()

    stage_latency_ms: dict[str, int] = {}
    start = time.perf_counter()

    if example.get("image_path"):
        image_path = _resolve_image_path(str(example["image_path"]))
        if not image_path.exists():
            raise FileNotFoundError(f"Image fixture not found: {image_path}")
        if not vision_key:
            raise RuntimeError("GOOGLE_CLOUD_VISION_API_KEY is required for image-based eval examples")
        image_bytes = image_path.read_bytes()
        stage_start = time.perf_counter()
        vision_ocr_text = await _vision_ocr(image_bytes=image_bytes, api_key=vision_key)
        stage_latency_ms["vision_ocr"] = int((time.perf_counter() - stage_start) * 1000)
    else:
        vision_ocr_text = str(example.get("ocr_text", "")).strip()
        stage_latency_ms["vision_ocr"] = 0

    if not vision_ocr_text:
        raise RuntimeError(f"Example {fixture_id} produced empty OCR text")

    normalized_ocr_text: str | None = None
    normalization_changed = False
    normalization_fallback_used = False
    if ocr_pipeline_mode == "hybrid":
        stage_start = time.perf_counter()
        try:
            normalized_ocr_text = await _gemini_normalize_ocr_text(ocr_text=vision_ocr_text, api_key=gemini_key)
        except Exception:
            normalization_fallback_used = True
            normalized_ocr_text = None
        stage_latency_ms["ocr_normalize"] = int((time.perf_counter() - stage_start) * 1000)
        normalization_changed = (
            normalized_ocr_text is not None
            and _normalize_for_match(normalized_ocr_text) != _normalize_for_match(vision_ocr_text)
        )
    else:
        stage_latency_ms["ocr_normalize"] = 0

    parse_source_text = normalized_ocr_text or vision_ocr_text
    stage_start = time.perf_counter()
    llm_output = await _gemini_parse_menu(
        ocr_text=parse_source_text,
        target_lang=TARGET_LANG,
        api_key=gemini_key,
    )
    stage_latency_ms["menu_parse"] = int((time.perf_counter() - stage_start) * 1000)
    total_latency_ms = (time.perf_counter() - start) * 1000

    estimated_candidates = _estimate_ocr_candidate_count(parse_source_text)
    predictions: list[dict[str, Any]] = []
    for item in llm_output.items[:max_menu_items]:
        diagnostics, final_confidence = _build_ocr_diagnostics(
            item_jp_text=item.jp_text,
            en_title=item.en_title,
            source_text_for_matching=parse_source_text,
            ocr_pipeline=ocr_pipeline_mode,
            normalization_changed=normalization_changed,
            vision_text_len=len(vision_ocr_text),
            normalized_text_len=len(normalized_ocr_text) if normalized_ocr_text else None,
            llm_confidence=max(0.0, min(1.0, item.confidence)),
        )
        predictions.append(
            {
                "jp_text": item.jp_text,
                "price_text": item.price_text,
                "en_title": item.en_title,
                "en_description": item.en_description,
                "tags": item.tags,
                "confidence": final_confidence,
                "ocr_diagnostics": diagnostics,
            }
        )

    metrics = score_example(
        expected_jp_names=list(example.get("expected_jp_names", [])),
        predicted_jp_names=[item["jp_text"] for item in predictions],
        latency_ms=total_latency_ms,
    )

    return {
        "fixture_id": fixture_id,
        "difficulty_tags": list(example.get("difficulty_tags", [])),
        "expected": {
            "expected_item_count": int(example.get("expected_item_count", 0)),
            "expected_jp_names": list(example.get("expected_jp_names", [])),
            "expected_en_titles": list(example.get("expected_en_titles", [])),
        },
        "predicted": {
            "detected_type": llm_output.detected_type,
            "items": predictions,
        },
        "metrics": metrics,
        "pipeline": {
            "ocr_pipeline_mode": ocr_pipeline_mode,
            "estimated_ocr_candidate_count": estimated_candidates,
            "returned_item_count": len(predictions),
            "coverage_ratio": round(min(1.0, len(predictions) / estimated_candidates), 4)
            if estimated_candidates > 0
            else None,
            "model": os.getenv("GEMINI_MODEL", "gemini-1.5-flash"),
            "menu_parse_prompt_version": get_active_prompt_version("menu_parse"),
            "ocr_normalize_prompt_version": get_active_prompt_version("ocr_normalize"),
            "stage_latency_ms": stage_latency_ms,
            "normalization_fallback_used": normalization_fallback_used,
        },
    }


async def _run() -> dict[str, Any]:
    dataset = _load_dataset()
    if not dataset:
        raise RuntimeError("No eval examples found")

    example_results: list[dict[str, Any]] = []
    failures: list[dict[str, str]] = []
    for example in dataset:
        fixture_id = str(example.get("fixture_id", "unknown"))
        try:
            example_results.append(await _run_example(example))
        except Exception as exc:
            failures.append({"fixture_id": fixture_id, "error": str(exc)})

    summary = summarize_results(example_results)
    return {
        "run_at_utc": datetime.now(timezone.utc).isoformat(),
        "dataset_path": str(DATASET_PATH),
        "target_lang": TARGET_LANG,
        "summary": summary,
        "failures": failures,
        "examples": example_results,
    }


def _append_run_ledger(result: dict[str, Any], report_path: Path) -> None:
    summary = result["summary"]
    first_pipeline = result["examples"][0]["pipeline"] if result["examples"] else {}
    row = {
        "run_at_utc": result["run_at_utc"],
        "report_path": str(report_path),
        "dataset_path": result["dataset_path"],
        "target_lang": result["target_lang"],
        "model": first_pipeline.get("model", os.getenv("GEMINI_MODEL", "gemini-1.5-flash")),
        "ocr_pipeline_mode": first_pipeline.get("ocr_pipeline_mode", _resolve_ocr_pipeline_mode()),
        "menu_parse_prompt_version": first_pipeline.get(
            "menu_parse_prompt_version",
            get_active_prompt_version("menu_parse"),
        ),
        "ocr_normalize_prompt_version": first_pipeline.get(
            "ocr_normalize_prompt_version",
            get_active_prompt_version("ocr_normalize"),
        ),
        "example_count": summary["example_count"],
        "failure_count": len(result["failures"]),
        "parse_success_rate": summary["parse_success_rate"],
        "item_recall": summary["item_recall"],
        "item_precision_proxy": summary["item_precision_proxy"],
        "hallucinated_item_rate": summary["hallucinated_item_rate"],
        "coverage_ratio": summary["coverage_ratio"],
        "mean_latency_ms": summary["mean_latency_ms"],
        "p95_latency_ms": summary["p95_latency_ms"],
    }

    RUN_LEDGER_PATH.parent.mkdir(parents=True, exist_ok=True)
    write_header = not RUN_LEDGER_PATH.exists() or RUN_LEDGER_PATH.stat().st_size == 0
    with RUN_LEDGER_PATH.open("a", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=RUN_LEDGER_FIELDS)
        if write_header:
            writer.writeheader()
        writer.writerow(row)


def main() -> None:
    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    result = asyncio.run(_run())
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    output_path = RESULTS_DIR / f"eval_report_{timestamp}.json"
    output_path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    _append_run_ledger(result, output_path)

    summary = result["summary"]
    print(f"Saved eval report to {output_path}")
    print(f"Appended eval run ledger to {RUN_LEDGER_PATH}")
    print(
        "Summary:"
        f" examples={int(summary['example_count'])}"
        f" parse_success={summary['parse_success_rate']:.3f}"
        f" recall={summary['item_recall']:.3f}"
        f" hallucinated={summary['hallucinated_item_rate']:.3f}"
        f" coverage={summary['coverage_ratio']:.3f}"
        f" mean_latency_ms={summary['mean_latency_ms']:.1f}"
        f" p95_latency_ms={summary['p95_latency_ms']:.1f}"
    )
    if result["failures"]:
        print(f"Failures: {len(result['failures'])}")
        for failure in result["failures"]:
            print(f" - {failure['fixture_id']}: {failure['error']}")


if __name__ == "__main__":
    main()

import re
import statistics
from difflib import SequenceMatcher
from typing import Any


_PUNCTUATION_RE = re.compile(r"[\W_]+", re.UNICODE)
_CANONICAL_REPLACEMENTS = {
    "\u3055\u3057\u307f": "\u523a\u8eab",
    "\u304a\u523a\u3057\u8eab": "\u523a\u8eab",
    "\u523a\u3057\u8eab": "\u523a\u8eab",
    "\u5929\u3077\u3089": "\u5929\u5a66\u7f85",
    "\u3066\u3093\u3077\u3089": "\u5929\u5a66\u7f85",
}


def _normalize(value: str) -> str:
    normalized = _PUNCTUATION_RE.sub("", value).strip().lower()
    for source, replacement in _CANONICAL_REPLACEMENTS.items():
        normalized = normalized.replace(source, replacement)
    return normalized


def _safe_mean(values: list[float]) -> float:
    if not values:
        return 0.0
    return float(statistics.mean(values))


def _safe_p95(values: list[float]) -> float:
    if not values:
        return 0.0
    if len(values) == 1:
        return float(values[0])
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, round((len(ordered) - 1) * 0.95)))
    return float(ordered[index])


def _name_match_score(expected: str, predicted: str) -> tuple[float, str]:
    expected_norm = _normalize(expected)
    predicted_norm = _normalize(predicted)
    if not expected_norm or not predicted_norm:
        return 0.0, "empty"

    if expected_norm == predicted_norm:
        return 1.0, "exact"

    shorter = min(len(expected_norm), len(predicted_norm))
    longer = max(len(expected_norm), len(predicted_norm))
    containment_ratio = shorter / longer if longer else 0.0
    if expected_norm in predicted_norm and containment_ratio >= 0.5 and len(expected_norm) >= 5:
        return 0.92, "expected_contained_in_prediction"
    if predicted_norm in expected_norm and containment_ratio >= 0.9:
        return 0.88, "prediction_contained_in_expected"
    if expected_norm in predicted_norm or predicted_norm in expected_norm:
        return containment_ratio, "contained_below_threshold"

    similarity = SequenceMatcher(None, expected_norm, predicted_norm).ratio()
    if similarity >= 0.86:
        return similarity, "fuzzy"

    return similarity, "no_match"


def _match_items(expected_names: list[str], predicted_names: list[str]) -> tuple[list[dict[str, Any]], list[str], list[str]]:
    candidates: list[dict[str, Any]] = []
    for expected_index, expected_name in enumerate(expected_names):
        for predicted_index, predicted_name in enumerate(predicted_names):
            score, reason = _name_match_score(expected_name, predicted_name)
            if score >= 0.86:
                candidates.append(
                    {
                        "expected_index": expected_index,
                        "predicted_index": predicted_index,
                        "expected": expected_name,
                        "predicted": predicted_name,
                        "score": round(score, 4),
                        "reason": reason,
                    }
                )

    matches: list[dict[str, Any]] = []
    matched_expected: set[int] = set()
    matched_predicted: set[int] = set()
    for candidate in sorted(candidates, key=lambda item: item["score"], reverse=True):
        expected_index = int(candidate["expected_index"])
        predicted_index = int(candidate["predicted_index"])
        if expected_index in matched_expected or predicted_index in matched_predicted:
            continue
        matched_expected.add(expected_index)
        matched_predicted.add(predicted_index)
        matches.append(candidate)

    missed_expected = [name for index, name in enumerate(expected_names) if index not in matched_expected]
    extra_predicted = [name for index, name in enumerate(predicted_names) if index not in matched_predicted]
    return matches, missed_expected, extra_predicted


def score_example(*, expected_jp_names: list[str], predicted_jp_names: list[str], latency_ms: float) -> dict[str, Any]:
    expected = [value.strip() for value in expected_jp_names if value.strip()]
    predicted = [value.strip() for value in predicted_jp_names if value.strip()]
    matches, missed_expected, extra_predicted = _match_items(expected, predicted)

    true_positives = len(matches)
    false_positives = len(extra_predicted)
    recall = true_positives / len(expected) if expected else 0.0
    precision_proxy = true_positives / len(predicted) if predicted else 0.0
    hallucinated_item_rate = false_positives / len(predicted) if predicted else 0.0
    coverage_ratio = min(1.0, len(predicted) / len(expected)) if expected else 0.0

    return {
        "expected_item_count": len(expected),
        "predicted_item_count": len(predicted),
        "matched_item_count": true_positives,
        "parse_success": 1.0 if predicted else 0.0,
        "item_recall": round(recall, 4),
        "item_precision_proxy": round(precision_proxy, 4),
        "hallucinated_item_rate": round(hallucinated_item_rate, 4),
        "coverage_ratio": round(coverage_ratio, 4),
        "latency_ms": round(latency_ms, 1),
        "matches": matches,
        "missed_expected_jp_names": missed_expected,
        "extra_predicted_jp_names": extra_predicted,
    }


def summarize_results(example_results: list[dict[str, Any]]) -> dict[str, float]:
    latencies = [float(result["metrics"]["latency_ms"]) for result in example_results]
    parse_successes = [float(result["metrics"]["parse_success"]) for result in example_results]
    recalls = [float(result["metrics"]["item_recall"]) for result in example_results]
    precision_values = [float(result["metrics"]["item_precision_proxy"]) for result in example_results]
    hallucination_values = [float(result["metrics"]["hallucinated_item_rate"]) for result in example_results]
    coverage_values = [float(result["metrics"]["coverage_ratio"]) for result in example_results]

    return {
        "example_count": float(len(example_results)),
        "parse_success_rate": round(_safe_mean(parse_successes), 4),
        "item_recall": round(_safe_mean(recalls), 4),
        "item_precision_proxy": round(_safe_mean(precision_values), 4),
        "hallucinated_item_rate": round(_safe_mean(hallucination_values), 4),
        "coverage_ratio": round(_safe_mean(coverage_values), 4),
        "mean_latency_ms": round(_safe_mean(latencies), 1),
        "p95_latency_ms": round(_safe_p95(latencies), 1),
    }

# MenuLens Eval Workflow

This package turns the current menu-scanning pipeline into a repeatable evaluation loop.

## Goal

Run a fixed benchmark set through the existing OCR and parsing logic, then emit:

- per-example predictions
- quality metrics
- latency summary
- machine-readable JSON results

## Run

From `backend/`:

```powershell
python -m evals.run_evals
```

Optional environment variables:

- `EVAL_DATASET_PATH` overrides the dataset JSON path.
- `EVAL_RESULTS_DIR` overrides the output directory.
- `EVAL_TARGET_LANG` defaults to `en`.
- `EVAL_INCLUDE_DISABLED=true` includes examples marked `enabled: false`.
- `EVAL_RUN_LEDGER_PATH` overrides the append-only CSV ledger path.

The bootstrap dataset supports either:

- `image_path`: run Vision OCR plus the LLM pipeline
- `ocr_text`: run parse evaluation without an image fixture

The `ocr_text` mode exists so the first eval command can run before the image benchmark is fully curated. Replace those cases with real menu fixtures as you build out the benchmark.

## Dataset Shape

See `dataset/schema.json` for the canonical schema.

Each example should include:

- `fixture_id`
- one of `image_path` or `ocr_text`
- `expected_item_count`
- `expected_jp_names`
- optional `expected_en_titles`
- optional `difficulty_tags`

## Output

Each run writes a timestamped JSON artifact into `results/` and appends a summary row to `results/eval_runs.csv`.

The CSV ledger is Excel-compatible and includes the dataset path, model, prompt versions, OCR pipeline mode, failure count, and summary metrics.

The summary includes:

- parse success rate
- item recall
- hallucinated item rate
- coverage ratio
- mean latency
- p95 latency

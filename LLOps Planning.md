# LLMOps Planning

## Purpose

This document turns MenuLens into a concrete LLMOps case-study plan for the next major project phase.

The goal is not to make the project look like a generic "AI app that calls Gemini." The goal is to make it look like an operated LLM system with measurable quality, versioned prompts, observable inference behavior, and a controlled iteration loop.

## Current Baseline

MenuLens already has a solid production-style inference pipeline:

- Android client sends menu images to the FastAPI backend.
- Backend runs Google Cloud Vision OCR.
- Backend optionally runs Gemini OCR normalization.
- Backend runs Gemini structured menu parsing.
- Backend optionally runs Vertex AI Search for image retrieval.
- Backend enforces auth-aware quota and idempotent request handling.
- Backend is deployable on Cloud Run.

Current strengths:

- Multi-stage multimodal pipeline exists.
- Some runtime diagnostics already exist.
- Configurable model and OCR pipeline mode already exist.
- Deployment and cost-awareness are already documented.

Current gaps relative to LLMOps:

- No benchmark dataset.
- No repeatable offline evaluation job.
- No explicit prompt version registry.
- No persistent inference trace store.
- No review queue for bad or low-confidence cases.
- No CI regression gate for prompt/model changes.
- No operational dashboard for latency, quality, and spend trends.

## Target Outcome

After the planned work, MenuLens should support the following narrative:

"MenuLens is a multimodal LLM application operated with basic LLMOps discipline: prompt versioning, benchmark evaluation, structured tracing, low-confidence review workflows, and regression checks before rollout."

## Scope Boundaries

This plan focuses on the backend and operational layer first.

In scope:

- Eval dataset and tooling
- Prompt versioning
- Inference tracing
- Review workflow for low-confidence outputs
- CI-based evaluation gate
- Operational metrics and release controls

Out of scope for the first pass:

- Full web admin UI
- Fine-tuning
- Advanced experimentation platform
- Multi-provider orchestration beyond the current stack
- Full production billing integration

## Architecture Additions

The following additions will be made on top of the current backend:

### 1. Eval Layer

Add a dedicated evaluation package under `backend/evals/`:

- `backend/evals/dataset/`
- `backend/evals/results/`
- `backend/evals/run_evals.py`
- `backend/evals/scoring.py`
- `backend/evals/README.md`

Responsibilities:

- Store benchmark examples
- Run the existing pipeline against the benchmark set
- Compute quality metrics
- Emit machine-readable and human-readable reports

### 2. Prompt Registry

Move inline prompts into versioned files under `backend/app/prompts/`:

- `backend/app/prompts/ocr_normalize_v1.txt`
- `backend/app/prompts/menu_parse_v1.txt`
- `backend/app/prompts/registry.py`

Responsibilities:

- Load prompts by stable version id
- Make active versions configurable by env var
- Attach prompt version ids to logs and traces

### 3. Trace Store

Add persistent request tracing for each scan request.

Initial shape:

- request id
- timestamp
- auth subject type
- active model
- prompt versions
- OCR pipeline mode
- stage latencies
- fallback flags
- quality diagnostics
- usage/quota outcome
- final status

Implementation options:

- Phase 1 local/demo: SQLite
- Portfolio-grade follow-up: Firestore or Cloud SQL

### 4. Review Queue

Add a mechanism to persist low-confidence or suspicious outputs for manual review.

Trigger conditions:

- Low `coverage_ratio`
- Weak OCR diagnostics
- Empty or near-empty parse result
- Fallback path activated
- Retrieval failure when retrieval is enabled

Stored review metadata:

- request id
- image reference or fixture id
- OCR text snapshot
- parsed response snapshot
- failure reason labels
- reviewer status

### 5. CI Regression Gate

Extend GitHub Actions so prompt/model changes can be evaluated before merge.

Responsibilities:

- Run offline evals on a benchmark subset
- Compare results against baseline thresholds
- Fail CI if key metrics regress

## Phase Plan

## Phase 1: Eval Foundation

Objective:

Establish a reproducible benchmark and scoring workflow so prompt/model changes can be evaluated offline.

Deliverables:

- `backend/evals/` package scaffold
- dataset schema definition
- starter benchmark dataset
- eval runner script
- scoring functions
- result artifact output
- documentation for how to run evals locally

Success criteria:

- A developer can run one command and get a metrics report.
- The report includes at least recall, hallucination proxy, latency, and item-count coverage.
- Eval outputs are stored in a predictable location.

Suggested dataset schema:

- fixture id
- image path or fixture reference
- expected item count
- expected Japanese dish names
- expected English titles if available
- tags for difficulty such as `blurry`, `vertical_text`, `partial_menu`, `good_lighting`

Suggested initial metrics:

- item recall
- item precision proxy
- hallucinated item rate
- coverage ratio
- parse success rate
- mean and p95 latency

Implementation notes:

- Start with a small manually curated set, around 20 to 30 examples.
- Do not block on perfect ground truth.
- Prefer a simple JSON dataset format first.

## Phase 2: Prompt Versioning

Objective:

Make prompts explicit, versioned, configurable, and traceable.

Deliverables:

- prompt files moved out of code
- prompt registry loader
- env vars for active prompt versions
- request logs include prompt version ids

Success criteria:

- A prompt can be changed without editing business logic.
- Every inference can be tied to an exact prompt version.

Suggested env vars:

- `MENU_PARSE_PROMPT_VERSION`
- `OCR_NORMALIZE_PROMPT_VERSION`

## Phase 3: Structured Tracing

Objective:

Persist request-level operational data so the pipeline can be monitored and debugged over time.

Deliverables:

- trace storage schema
- write path from `POST /v1/scan_menu`
- trace persistence for success and failure cases
- local query script or admin endpoint for recent traces

Success criteria:

- You can inspect recent failures and compare them by model, prompt version, and pipeline mode.
- Latency and failure path data are available without parsing raw logs only.

Suggested initial storage:

- SQLite table in local/dev
- abstraction that can later move to Firestore or Cloud SQL

## Phase 4: Review Queue

Objective:

Create a closed loop for inspecting and labeling bad outputs.

Deliverables:

- review-queue persistence
- automatic enqueue rules
- simple script or endpoint to list pending cases
- reviewer labels such as `correct`, `partial`, `hallucinated`, `ocr_failed`, `parse_failed`

Success criteria:

- Low-confidence scans are collected systematically.
- Reviewed cases can be promoted back into the eval dataset.

## Phase 5: CI Regression Gate

Objective:

Prevent accidental quality regressions when prompts, models, or parsing logic change.

Deliverables:

- GitHub Actions job for offline evals
- baseline metric file
- threshold comparison logic
- failure conditions for regression

Success criteria:

- CI fails when defined key metrics drop below threshold.
- Prompt/model changes require an explicit quality tradeoff decision.

Suggested gate metrics:

- parse success rate
- recall
- hallucination proxy
- p95 latency ceiling

## Phase 6: Operational Metrics and Release Controls

Objective:

Make the system operable in a more production-like way.

Deliverables:

- structured metric export
- cost and usage estimates per request
- release flags for active prompt/model versions
- simple rollback instructions

Success criteria:

- You can answer whether a release improved quality, latency, or cost.
- Prompt/model rollout can be changed without a risky code edit.

## Recommended File Additions

Planned files and directories:

- `LLOps Planning.md`
- `backend/evals/README.md`
- `backend/evals/dataset/schema.json`
- `backend/evals/dataset/examples.json`
- `backend/evals/results/.gitkeep`
- `backend/evals/run_evals.py`
- `backend/evals/scoring.py`
- `backend/app/prompts/ocr_normalize_v1.txt`
- `backend/app/prompts/menu_parse_v1.txt`
- `backend/app/prompts/registry.py`
- `backend/app/tracing.py`
- `backend/app/review_queue.py`

## Data Model Sketches

### Eval Example

```json
{
  "fixture_id": "menu_001",
  "image_path": "backend/evals/fixtures/menu_001.jpg",
  "expected_item_count": 4,
  "expected_jp_names": ["親子丼", "味噌汁", "焼き魚", "唐揚げ"],
  "expected_en_titles": ["oyakodon", "miso soup", "grilled fish", "fried chicken"],
  "difficulty_tags": ["good_lighting"]
}
```

### Trace Record

```json
{
  "request_id": "abc-123",
  "status": "success",
  "model": "gemini-1.5-flash",
  "menu_parse_prompt_version": "menu_parse_v1",
  "ocr_normalize_prompt_version": "ocr_normalize_v1",
  "ocr_pipeline_mode": "hybrid",
  "latency_ms": {
    "vision_ocr": 420,
    "ocr_normalize": 310,
    "menu_parse": 540,
    "image_retrieval": 180
  },
  "fallbacks": {
    "used_raw_vision_text": false
  },
  "quality": {
    "coverage_ratio": 0.8,
    "returned_item_count": 4
  }
}
```

## Recruiter-Facing Narrative

Once phases 1 through 3 are complete, the project can be described like this:

"Built and operated a multimodal LLM pipeline for Japanese menu understanding using Vision OCR, Gemini, and Vertex AI Search. Added benchmark evaluation workflows, prompt versioning, structured inference tracing, quota controls, and Cloud Run deployment."

Once phases 4 through 6 are complete, the stronger version is:

"Designed LLMOps workflows for a production-style multimodal application, including offline eval datasets, prompt registry/versioning, structured request traces, confidence-based review queues, and CI regression checks for prompt/model changes."

## Execution Order

Recommended implementation order:

1. Phase 1: Eval Foundation
2. Phase 2: Prompt Versioning
3. Phase 3: Structured Tracing
4. Phase 4: Review Queue
5. Phase 5: CI Regression Gate
6. Phase 6: Operational Metrics and Release Controls

This order maximizes recruiter value early while minimizing architecture churn.

## Phase 1 Start Criteria

Before phase 1 begins, align on these decisions:

- Eval dataset format: JSON
- Initial fixture count target: 20 to 30
- Local result artifacts committed or ignored: results ignored, sample dataset committed
- First scoring mode: heuristic matching, not full semantic grading

## Definition of Done

MenuLens can be presented as a credible LLMOps portfolio project when all of the following are true:

- Prompt versions are explicit and traceable.
- The pipeline can be evaluated on a fixed benchmark set.
- Inference traces are persisted and queryable.
- Bad outputs are collected for review.
- Quality regressions are checked before merge.
- Latency, quality, and cost trends can be discussed with evidence.

## Immediate Next Step

The immediate next step for MenuLens is Phase 1: Eval Foundation.

That phase will establish the minimum LLMOps baseline for the project by adding:

- a benchmark dataset
- a repeatable eval runner
- quality scoring outputs
- documented local evaluation workflow

Once that is in place, later prompt, model, and pipeline changes can be measured instead of guessed.


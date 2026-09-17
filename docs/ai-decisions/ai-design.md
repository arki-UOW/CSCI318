# AI extraction and planning design

## Outline extraction

`DocumentTextExtractor` converts each supported upload to normalised text before any provider call: PDFBox handles PDFs, Apache POI handles DOCX paragraphs/tables, and Tesseract OCR handles JPG/JPEG. `OutlineExtraction` then sends only this bounded cleaned text to Gemini in JSON mode. The prompt instructs the model to use null rather than invent facts and to reject policy, SLO and table-heading text as assessments. Provider calls use a bounded 120-second timeout; after a successful call, the response normaliser accepts common Gemini variants such as `"5%"`, `"Week 6"` and `"Weeks 10 & 12"` before domain validation. Provider transport failures and invalid response shapes are reported separately.

`AI_PROVIDER=auto` prefers Gemini, then OpenAI. An explicitly selected provider uses its matching key/model variables. Without a key, conservative labelled-field/table extraction creates reviewable candidates and a visible warning. When a configured provider fails, extraction stops with an actionable key, model, quota, or connectivity message instead of silently replacing the AI result with low-confidence deterministic rows. The UI also reads a status endpoint so it can distinguish a missing key in a running container from a provider failure.

The confirmation boundary re-validates subject code, names, weighting bounds, due weeks and duplicate titles. Subject preparation commits before Assessment Service performs its required REST verification, avoiding the former uncommitted-row callback failure. The import is marked `CONFIRMING` until the idempotent assessment import succeeds, so a transient service failure can be retried safely. No extraction is silently promoted to confirmed domain state.

## Planning agent

The plan generator is a deterministic domain scheduler orchestrated by StudyPlanningAgent. PlanningTools is an application port implemented by RestPlanningData, used for authoritative assessment verification and assistant context. Completed linked-block minutes come from local stream projections. A separate LLM availability assistant converts conversational times into validated, non-overlapping slots; only daily minute limits reach the scheduler.

Validation rejects missing/completed assessments, mismatched subjects, dates outside the deadline horizon, non-positive minutes and daily availability overruns. Weekly availability repeats through each due date; estimated workload is distributed across spaced reviews without double-counting linked completed work. Only valid output reaches StudyPlanRepository. Regeneration re-runs factual inputs and stores a new version with a concise difference summary. Availability/chat provider failures are reported; the deterministic minute allocator itself does not require an API key or claim LLM-generated dates.

## Prompt safety

Prompts request JSON only, constrain available IDs and period, and identify source context. Raw model prose is never returned as a plan. Tests and demonstrations do not require a paid model.

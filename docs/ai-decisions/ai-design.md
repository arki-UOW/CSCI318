# AI extraction and planning design

## Outline extraction

`DocumentTextExtractor` converts each supported upload to normalised text before any provider call: PDFBox handles PDFs, Apache POI handles DOCX paragraphs/tables, and Tesseract OCR handles JPG/JPEG. `OutlineExtraction` then sends only this bounded cleaned text to Gemini in JSON mode. The prompt instructs the model to use null rather than invent facts and to reject policy, SLO and table-heading text as assessments. Provider calls use a bounded 120-second timeout; after a successful call, the response normaliser accepts common Gemini variants such as `"5%"`, `"Week 6"` and `"Weeks 10 & 12"` before domain validation. Provider transport failures and invalid response shapes are reported separately.

`AI_PROVIDER=auto` prefers Gemini, then OpenAI. An explicitly selected provider uses its matching key/model variables. Without a key, conservative labelled-field/table extraction creates reviewable candidates and a visible warning. When a configured provider fails, extraction stops with an actionable key, model, quota, or connectivity message instead of silently replacing the AI result with low-confidence deterministic rows. The UI also reads a status endpoint so it can distinguish a missing key in a running container from a provider failure.

The confirmation boundary re-validates subject code, names, weighting bounds, due weeks and duplicate titles. Subject preparation commits before Assessment Service performs its required REST verification, avoiding the former uncommitted-row callback failure. The import is marked `CONFIRMING` until the idempotent assessment import succeeds, so a transient service failure can be retried safely. No extraction is silently promoted to confirmed domain state.

## Planning agent

The plan generator remains a bounded goal-oriented component. `PlanningTools` reads approved live subject, assessment and activity REST state without requiring a Kafka Streams projection to start. A separate availability assistant converts conversational times into validated, non-overlapping slots; only the derived daily minute limits reach the plan generator.

Validation rejects missing/completed assessments, mismatched subjects, dates outside the seven-day period, non-positive minutes and daily availability overruns. Only valid output reaches `StudyPlanRepository`. Regeneration re-runs tools and stores a new version with a concise difference summary. If an API key is configured but the provider call fails, Planning Service returns a meaningful error rather than silently presenting a deterministic plan as AI output.

## Prompt safety

Prompts request JSON only, constrain available IDs and period, and identify source context. Raw model prose is never returned as a plan. Tests and demonstrations do not require a paid model.

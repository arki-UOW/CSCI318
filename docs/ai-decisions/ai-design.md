# AI extraction and planning design

## Outline extraction

PDFBox extracts sorted, normalised local text for the deterministic path. `OutlineExtraction` is the application port. With `GEMINI_API_KEY`, the LangChain4j Google AI adapter sends the original PDF to Gemini in JSON mode. This preserves table layout and also supports image-based pages that contain little or no extractable text. The prompt instructs the model to use null rather than invent facts.

`AI_PROVIDER=auto` prefers Gemini, then OpenAI. An explicitly selected provider uses its matching key/model variables. Without a key, conservative labelled-field/table extraction creates reviewable candidates and a visible warning. If Gemini fails but local PDF text exists, the adapter falls back with a visible warning; an image-only PDF returns a configuration/error message instead of pretending that extraction succeeded.

The confirmation boundary re-validates subject code, names, weighting bounds, due weeks and duplicate titles. Subject preparation commits before Assessment Service performs its required REST verification, avoiding the former uncommitted-row callback failure. The import is marked `CONFIRMING` until the idempotent assessment import succeeds, so a transient service failure can be retried safely. No extraction is silently promoted to confirmed domain state.

## Planning agent

The agent is a bounded goal-oriented component, not chat. `PlanningTools` exposes approved live-state operations for incomplete/upcoming/due-this-week assessments and subjects. The agent reads those tools, produces structured `PlanItem` objects, and submits them to deterministic validation.

Validation rejects missing/completed assessments, mismatched subjects, dates outside the seven-day period, non-positive minutes and daily availability overruns. Only valid output reaches `StudyPlanRepository`. Regeneration re-runs tools and stores a new version with a concise difference summary. If an API key is configured but the provider call fails, Planning Service returns a meaningful error rather than silently presenting a deterministic plan as AI output.

## Prompt safety

Prompts request JSON only, constrain available IDs and period, and identify source context. Raw model prose is never returned as a plan. Tests and demonstrations do not require a paid model.

# AI extraction and planning design

## Outline extraction

PDFBox extracts local text. `OutlineExtraction` is the application port. With `OPENAI_API_KEY`, the LangChain4j adapter requests strict JSON and instructs the model to use null rather than invent facts. Without a key or after an adapter failure, conservative regex extraction creates reviewable candidates and warnings.

The confirmation boundary re-validates subject code, names, weighting bounds and duplicate titles. No extraction is silently promoted to permanent domain state.

## Planning agent

The agent is a bounded goal-oriented component, not chat. `PlanningTools` exposes approved live-state operations for incomplete/upcoming/due-this-week assessments and subjects. The agent reads those tools, produces structured `PlanItem` objects, and submits them to deterministic validation.

Validation rejects missing/completed assessments, mismatched subjects, dates outside the seven-day period, non-positive minutes and daily availability overruns. Only valid output reaches `StudyPlanRepository`. Regeneration re-runs tools and stores a new version with a concise difference summary.

## Prompt safety

Prompts request JSON only, constrain available IDs and period, and identify source context. Raw model prose is never returned as a plan. Tests and demonstrations do not require a paid model.

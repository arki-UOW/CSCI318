# AI extraction and planning design

## Outline extraction

`DocumentTextExtractor` converts each supported upload to normalised text before any provider call: PDFBox handles PDFs, Apache POI handles DOCX paragraphs/tables, and Tesseract OCR handles JPG/JPEG. `OutlineExtraction` then sends only this bounded cleaned text to Gemini in JSON mode. The prompt instructs the model to use null rather than invent facts and to reject policy, SLO and table-heading text as assessments. Provider calls use a bounded 120-second timeout; after a successful call, the response normaliser accepts common Gemini variants such as `"5%"`, `"Week 6"` and `"Weeks 10 & 12"` before domain validation. Provider transport failures and invalid response shapes are reported separately.

`AI_PROVIDER=auto` prefers Gemini, then OpenAI. An explicitly selected provider uses its matching key/model variables. Without a key, conservative labelled-field/table extraction creates reviewable candidates and a visible warning. When a configured provider fails, extraction stops with an actionable key, model, quota, or connectivity message instead of silently replacing the AI result with low-confidence deterministic rows. The UI also reads a status endpoint so it can distinguish a missing key in a running container from a provider failure.

The confirmation boundary re-validates subject code, names, weighting bounds, due weeks and duplicate titles. Subject preparation commits before Assessment Service performs its required REST verification, avoiding the former uncommitted-row callback failure. The import is marked `CONFIRMING` until the idempotent assessment import succeeds, so a transient service failure can be retried safely. No extraction is silently promoted to confirmed domain state.

## Planning agent

`AgenticPlanningAdvisor` implements a bounded LangChain4j tool loop for generation and regeneration. The model must retrieve `getIncompleteAssessments`, `getCurrentWorkload` and `getStudyProgress`; regeneration also requires `getExistingStudyPlan`. `getUpcomingAssessments` is an optional read tool. The model then calls `saveStudyPlan` with its action, summary and complete structured items array. Unknown tools, missing factual reads and invalid schemas are rejected; there is an eight-turn limit.

`StudyPlanningAgent` obtains authoritative assessments and linked completed work and asks the pure `DeadlineScheduler` for a feasible seven-day candidate. The candidate is supplied to the model as context. The model may adapt it, and its submitted items—not a silently recomputed replacement—are validated and persisted. It must retain at least the feasible candidate's allocated minutes. Missing estimates default to 120; explicit zero means no work. Existing stored plans remain readable, while newly generated plans always span start through start+6.

`PlanningApplicationService` rereads incomplete assessments and completed work before saving. It rejects null/invalid fields, unknown/completed/foreign assessments, mismatched subjects, dates outside the week or after a deadline, nonpositive/oversized durations, excessive daily capacity and allocation exceeding each assessment's remaining estimate. The `saveStudyPlan` tool requests persistence; it cannot bypass these application/domain checks. Plan storage and calendar replacement share a transaction, so failures retain previous state.

`PlanningTools` is implemented by `RestPlanningData` for authoritative facts. Workload and progress tools query local Kafka-derived projections. Completed linked-block minutes also come from these projections. Ordinary study sessions without assessment IDs contribute to subject progress but cannot be attributed to a specific assessment. The availability assistant produces validated non-overlapping time slots; generated calendar blocks split around existing commitments within those slots. Insufficient room rejects replacement safely.

Provider SDK exceptions are translated at the model-call boundary into safe, actionable HTTP 503 messages for quota, credentials, model and connectivity failures. Planning calls have a 60-second timeout and one retry. No key is returned in AI status or error responses. `scripts/verify-readiness.py` exercises the actual OpenAI SDK HTTP protocol with a local fixture; it is not a paid/live-provider quality test.

Plan explanations use a CLOB so the permitted 500-character agent summary plus tool names, schedule totals and regeneration details can be persisted. Hibernate's configured schema update widens the old H2 column; a migration regression test verifies existing rows survive. A JPA integration test executes the real tool loop/application/scheduler with scripted model responses, flushes generated and regenerated plans, and reads the stored explanations and calendar entries back. This tests persistence without requiring credentials; it does not replace a live-provider demonstration.

## Prompt safety

Prompts constrain the allowed workflow and identify source context. Tool schemas restrict actions and arguments. Raw model prose is never trusted as a plan, and the model cannot call arbitrary Java code. Unit tests use a mocked `ChatModel` to verify the tool protocol without consuming provider quota; the Postman agentic requests are the documented live-provider verification path.

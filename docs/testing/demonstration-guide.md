# Repeatable demonstration and failure evidence

1. Configure a real key only in the private `.env` if demonstrating live AI; start the system with the checked launcher. Kafka and all five services must be available.
2. Import `postman/Study-Leftovers.postman_collection.json`. Use the Collection Runner in its supplied order. It creates a unique account and captures IDs/token. Dates and capacities are regenerated dynamically; repeated runs do not reuse previous accounts.
3. Requests demonstrate subject creation/retrieval/list/update, assessment creation/list/incomplete/deadline/workload/completion, session recording/filter/summary, total/weekly projected progress, RT-01/02 convergence, AI-01/02, history and regeneration after completing all work. Projection tests poll with bounded retries.
4. Optional file upload is skipped by default. To demonstrate it separately, set `runOutlineUpload=true`, select a local file, and run that optional request. Review and confirm the extracted data through the browser. It runs after core projection assertions, so it cannot change the deterministic expected counts.
5. Model-invalid FR-18 cases cannot be reliably forced through a real LLM prompt. After building, run `python scripts/verify-readiness.py`: the actual SDK talks to a localhost protocol fixture that deliberately submits fractional/string/missing/extra/out-of-week values and quota failures. Assertions verify rejection, safe messages and unchanged plan/calendar state. No public test endpoint or real credential is introduced.
6. Run `mvn clean verify`, `node --test frontend/tests/*.test.cjs` and `pwsh -File scripts/test-launcher.ps1` for repeatable domain/persistence/UI/launcher checks. CI also runs the actual Kafka verification script in disposable Compose volumes.

The file `results.json` from the HTTP fixture and CI run links are machine-verifiable evidence. Screenshots, final presentation and report discussion belong to the student team. Do not label fixture results as successful live Gemini/OpenAI calls. The exact DTO payloads and outputs for the fourteen consolidated final requirements are in [the use cases](../use-cases/functional-use-cases.md).

## Error examples

| Stimulus | Expected result |
|---|---|
| `assessments: [null]` during subject create/import/confirmation | 400 field validation; no partial mutation |
| Display name 81 chars / goal 801 chars | 400; existing profile unchanged |
| Foreign subject/assessment ID | 400 invalid reference or 404 owned-resource lookup |
| Provider quota/timeout | 503 safe message; no private SDK response leakage |
| Model `allocatedMinutes: 15.5` or string `"15"` | 400; no silent conversion or saved version |
| REST integer minute field `15.5` or `-0.5` | 400 before mutation; saved integer value unchanged |
| Import `estimatedHours: 0` | Accepted and persisted as zero minutes, matching direct entry |
| All remaining estimates zero, no availability supplied | Empty seven-day version; regeneration removes obsolete planned AI blocks |
| Minutes-only plan with an existing evening commitment | New blocks avoid that commitment and retain total allocated minutes |
| Minutes-only generation/regeneration with a fully occupied scheduled day | 400 explaining insufficient availability; previous calendar and plan history unchanged |
| Eight concurrent valid plan requests | Distinct versions and one coherent latest active calendar; bounded contention may be rejected explicitly |
| Complete final assessment then regenerate | 201 empty new version; historical plan retained; obsolete calendar removed |
| Overnight event then next-day query | Entry included until its exclusive end time |
| Docker exit 1 / startup never becomes ready | Launcher fails, does not announce ready or open the site |

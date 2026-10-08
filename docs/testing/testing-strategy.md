# Testing strategy

Run `mvn clean verify` and `node --test frontend/tests/*.test.cjs`. Unit tests require neither Kafka nor LLM keys.

- Pure domain tests cover invariants, deadline/review allocation, real capacity shortfalls, completed-minute subtraction and explicit horizon limits.
- Application tests exercise subject confirmation ordering and local dashboard queries with timezone boundaries.
- TopologyTestDriver executes the real Kafka Streams DSL with persisted state stores: duplicates, stale revisions, corrections, deletes, target changes, completed blocks, malformed facts and account isolation.
- JPA slice tests verify outbox rollback/retry/migration and persistent projection revision guards.
- Node tests cover authenticated fetch SSE, fragmented UTF-8 frames, multiline data, keepalives and cancellation.
- Controller contract tests cover authenticated subject, assessment, activity and planning commands, including the browser timezone header.
- Agent tests prove that generation cannot be approved until the LLM has called the required application tools and submitted a structured decision.
- PlanningPersistenceIntegrationTest runs the real agent loop with a scripted model, generates and regenerates after changed deadline/workload/progress, and flushes/reads actual H2 plans and calendar entries. StudyPlanSchemaUpgradeTest verifies old explanation columns are widened without losing data.
- Regression tests cover mixed dated/undated tasks, legitimate assessment titles, invalid candidate plans, provider failures before persistence, matching slot/capacity inputs, repeated slots and conflicts with existing calendar entries. Frontend tests execute the request handler to verify exact slots reach the API.

## Real broker integration

The kafka-integration CI job starts a disposable Docker Compose stack with a real Kafka broker. `python3 scripts/verify-streaming.py --skip-ai-planning` registers two demo accounts, creates a subject/assessment, observes live SSE workload and progress changes, corrects study durations/weeks, completes a linked calendar block and checks account isolation. CI additionally stops Assessment and Activity, then verifies Planning can still return dashboard/progress from local projections. CI deliberately skips the provider-dependent HTTP planning call because repository secrets are not required for this job; deterministic deadline scheduling and the bounded agent tool loop are covered by unit tests. Running the script without `--skip-ai-planning` adds the live remaining-work deadline-plan check and requires a configured provider key.

Only the explicit study-leftovers-ci project may use the script's upstream-stop option. Do not run disposable cleanup against user containers or delete data directories. To demonstrate locally, start the normal system and run the script without that option; it creates demo accounts/history. LLM extraction is not exercised by this integration test.

Broker-down delivery is tested by mocking publication failure while checking the database row remains pending. Kafka Streams exactly-once configuration and restoration are integration concerns; a passing topology test alone does not prove binder startup or networking. Use the actual CI result before claiming end-to-end success.

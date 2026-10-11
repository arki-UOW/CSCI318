# Testing strategy

Run `mvn clean verify` and `node --test frontend/tests/*.test.cjs`. Unit tests require neither Kafka nor LLM keys.

- Pure domain tests cover invariants, deadline/review allocation, real capacity shortfalls, completed-minute subtraction and strict seven-day horizons and explicit zero workload.
- Application tests exercise subject confirmation ordering and local dashboard queries with timezone boundaries.
- TopologyTestDriver executes the real Kafka Streams DSL with persisted state stores: duplicates, stale revisions, corrections, deletes, target changes, completed blocks, malformed facts and account isolation.
- JPA slice tests verify outbox rollback/retry/migration and persistent projection revision guards.
- Node tests cover authenticated fetch SSE, fragmented UTF-8 frames, multiline data, keepalives and cancellation.
- Controller contract tests cover authenticated subject, assessment, activity and planning commands, including the browser timezone header.
- Agent tests prove that generation cannot be approved until the LLM has called the required application tools and submitted a structured decision.
- PlanningPersistenceIntegrationTest runs the real agent loop with a scripted model, generates and regenerates after changed deadline/workload/progress, and flushes/reads actual H2 plans and calendar entries. StudyPlanSchemaUpgradeTest verifies old explanation columns are widened without losing data.
- Regression tests cover mixed dated/undated tasks, legitimate assessment titles, invalid candidate plans, provider failures before persistence, matching slot/capacity inputs, repeated slots and conflicts with existing calendar entries. Frontend tests execute the request handler to verify exact slots reach the API.

## Real broker integration

The kafka-integration CI job starts a disposable Docker Compose stack with a real Kafka broker. `python3 scripts/verify-streaming.py --skip-ai-planning` registers two demo accounts, creates a subject/assessment, observes live SSE workload and progress changes, corrects study durations/weeks, completes a linked calendar block and checks account isolation. CI additionally stops Assessment and Activity, then verifies Planning can still return dashboard/progress from local projections. CI deliberately skips the provider-dependent HTTP planning call because repository secrets are not required for this job; seven-day candidate scheduling and the bounded agent tool loop are covered by unit tests. Running the script without `--skip-ai-planning` adds the live remaining-work weekly-plan check and requires a configured provider key.

Only the explicit study-leftovers-ci project may use the script's upstream-stop option. Do not run disposable cleanup against user containers or delete data directories. To demonstrate locally, start the normal system and run the script without that option; it creates demo accounts/history. LLM extraction is not exercised by this integration test.

Broker-down delivery is tested by mocking publication failure while checking the database row remains pending. Kafka Streams exactly-once configuration and restoration are integration concerns; a passing topology test alone does not prove binder startup or networking. Use the actual CI result before claiming end-to-end success.


## Isolated acceptance

Run `python scripts/verify-readiness.py` after the Maven build. This starts the real five JARs with independent H2 memory databases and Kafka disabled, plus a local OpenAI-protocol fixture. Assertions cover 2,000-character descriptions, invalid input/error shapes, reference ownership, maximum review titles, actual SDK tool calls, seven-day generated/regenerated plans, history isolation, invalid-output rollback, quota errors and PDF review/confirmation. The fixture uses an explicitly fake credential and never connects to a provider. CI runs this independently from real-Kafka integration.

DescriptionPersistenceTest in Assessment and Activity recreates the legacy VARCHAR(255) mapping, upgrades it using the production entity, preserves existing rows and round-trips a 2,000-character value. ProviderFailureApiTest runs real advisor error handling through MockMvc. CalendarReferencesTest checks missing/foreign/mismatched references and dependency outages. Frontend tests execute generation, previous-version regeneration and history selection handlers with DOM/network boundaries replaced; they are not a complete visual browser certification.

## Fresh-audit regression coverage

The current local suite has 87 Java tests, 20 frontend tests and 96 isolated HTTP checks. The HTTP suite drives eight simultaneous generation requests followed by eight simultaneous regeneration requests and checks unique versions plus one coherent active calendar. It also tests final-assessment completion, all-deleted and zero-capacity empty plans, strict fractional/missing/string/extra model fields, nested null lists, profile length boundaries, outline provider outages and overnight query boundaries. PlanWriteLockTest checks existing duplicate-version repair, uniqueness and restart idempotence.

`node --test frontend/tests/*.test.cjs` exercises stale headers/bodies/401 responses, calendar navigation races, midnight/month boundaries and synchronous sign-out cleanup before logout resolves. `pwsh -File scripts/test-launcher.ps1` runs the actual launcher with controlled native-command/readiness boundaries, including Docker failure, timeout and success. CI executes both suites.

Manual browser smoke checks were performed on 11 October 2026 with disposable accounts: an October 31 23:00 to November 1 01:00 event appears on both calendar dates; the second account sees no first-account event or chat; sign-out clears forms, chat content and feedback immediately. The browser used actual services with an isolated fixture, not a private LLM account.

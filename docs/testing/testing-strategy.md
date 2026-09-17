# Testing strategy

Run `mvn clean verify` and `node --test frontend/tests/*.test.cjs`. Unit tests require neither Kafka nor LLM keys.

- Pure domain tests cover invariants, deadline/review allocation, real capacity shortfalls, completed-minute subtraction and explicit horizon limits.
- Application tests exercise subject confirmation ordering and local dashboard queries with timezone boundaries.
- TopologyTestDriver executes the real Kafka Streams DSL with persisted state stores: duplicates, stale revisions, corrections, deletes, target changes, completed blocks, malformed facts and account isolation.
- JPA slice tests verify outbox rollback/retry/migration and persistent projection revision guards.
- Node tests cover authenticated fetch SSE, fragmented UTF-8 frames, multiline data, keepalives and cancellation.

## Real broker integration

The kafka-integration CI job starts a disposable Docker Compose stack with a real Kafka broker. `python3 scripts/verify-streaming.py` registers two demo accounts, creates a subject/assessment, observes live SSE workload and progress changes, corrects study durations/weeks, completes a linked calendar block, checks remaining-work deadline plans and account isolation. CI additionally stops Assessment and Activity, then verifies Planning can still return dashboard/progress from local projections.

Only the explicit study-leftovers-ci project may use the script's upstream-stop option. Do not run disposable cleanup against user containers or delete data directories. To demonstrate locally, start the normal system and run the script without that option; it creates demo accounts/history. LLM extraction is not exercised by this integration test.

Broker-down delivery is tested by mocking publication failure while checking the database row remains pending. Kafka Streams exactly-once configuration and restoration are integration concerns; a passing topology test alone does not prove binder startup or networking. Use the actual CI result before claiming end-to-end success.

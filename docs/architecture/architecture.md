# Architecture and context map

Five independently deployable bounded contexts own separate databases. No cross-context JPA relationships or shared academic aggregates exist.

```text
Browser --commands--> Account / Subject / Assessment / Activity / Planning
Subject --confirm via REST--> Assessment
Subject / Assessment / Activity / completed Planning blocks
  --transactional outbox--> Kafka
  --> workloadStream / studyProgressStream (Kafka Streams DSL)
  --> workload-projections / progress-projections
  --> Planning's local JPA read models --> authenticated dashboard SSE --> Browser
```

## Layers and inward dependencies

Controllers handle HTTP/authentication. Application services orchestrate use cases and transactions through ports. Domain entities and immutable stream values enforce rules; DeadlineScheduler and WeeklyAvailability contain planning rules without Spring, HTTP or LLM dependencies. Infrastructure implements JPA, REST, document/LLM extraction, outbox delivery, Kafka Streams and SSE.

Subject orchestration now lives in application, with SubjectStore, OutlineImportStore, SubjectDocumentReader, SubjectAssessmentGateway, OutlineExtraction and SubjectAiConfiguration ports. Extraction adapters are separate classes rather than unrelated types hidden in OutlineExtraction.java. PlanningTools is an application port implemented by RestPlanningData; ProjectionStore is implemented by JpaProjectionStore. Existing entity JPA annotations remain a deliberate persistence coupling, not a claim of completely framework-free domain entities.

The messaging-support module shares only a technical event contract and outbox machinery. Each service has its own event_outbox and event_migrations tables in its own database. It does not share domain data.

## Two genuine stateful streaming features

RT01: workloadStream consumes assessment-events, validates version-2 facts, keys by account, and aggregates the latest assessment revisions and deletion tombstones in account-workload-v2-store. Counts, estimated outstanding minutes and high-priority counts change when facts arrive. Deadline-relative views are derived locally using the requested timezone and current date; no upstream REST lookup occurs.

RT02: studyProgressStream merges study-activity-events, planning-events and subject-events, keys by account/subject, and aggregates sessions, completed study blocks and weekly targets in subject-weekly-progress-v2-store. Corrections replace the old duration/week, deletions subtract it, and target changes update cached subject metadata. Monday-based weekly totals and total minutes are maintained as streams arrive.

These are Kafka Streams groupByKey/aggregate materialized state stores, not ordinary listeners that fetch upstream REST. Output topics feed revision-checked persistent query documents in Planning. DashboardQueryService reads only these local documents and stored plans. Projection commits notify DashboardPushHub; an authenticated fetch-based SSE client updates dashboard panels without reloading forms. Heartbeats keep the connection alive and refresh date-dependent views at local midnight. Identity validation still uses Account Service.

## Delivery, recovery and isolation

Events contain account ownership, aggregate identity and a monotonically increasing aggregateRevision. Producers store events in the same database transaction as the academic mutation. OutboxDelivery acknowledges synchronous Kafka publication before deleting the pending row; broker failure leaves it retryable. A crash after publish can duplicate a fact, so domain reducers reject duplicate/stale revisions. This is at-least-once outbox delivery with idempotent projection semantics, not distributed exactly-once database delivery. Kafka Streams uses exactly_once_v2 for its own state/output transaction.

One-time startup snapshot migrations queue existing owned rows as v2 facts. Legacy owner-less records are not assigned to a guessed user. Malformed/unsupported facts produce metadata-only planning-rejected-events records; payloads, tokens and documents are not copied there. Projection sink failures use separate dead-letter topics. Account-qualified keys and repository filters isolate users.

This is CQRS-style materialized read models and event-driven integration, not full event sourcing: domain state is still persisted in service databases.

## Planning to deadlines

Weekly availability repeats through the latest supported due date. DeadlineScheduler allocates remaining estimated minutes across spaced offsets 0, 1, 3, 7, 14, 30 days, subsequent monthly reviews and a final pre-deadline date. Linked completed calendar-block minutes are subtracted on regeneration. Individual blocks are at most 90 minutes and daily capacity is enforced; residual capacity is filled before reporting a shortfall. Review intervals are a scheduling policy, not a scientifically personalized memory model.

Missing/nonpositive estimates default to 120 minutes; missing deadlines use a seven-day window. Deadlines more than one year from the start are explicitly rejected. The LLM captures availability and explains study work; it does not invent dates or allocate unvalidated minutes.

Monthly and weekly views share calendar_entries. Completed history is immutable (delete/correct explicitly instead). Manual spaced-repetition chains remain separate from a generated deadline plan: generated plans already contain their reviews and do not create a second automatic chain.

## Limits

Projections are eventually consistent, usually within seconds; the UI shows connection state. Regeneration immediately after completing a block can race the asynchronous progress projection; wait for the live total to update first. Account authentication remains a synchronous dependency. Development uses one Planning instance; multi-instance SSE fan-out is not claimed. Large academic histories would require bounded retention/compaction or finer-grained state keys. Docker data persistence across container recreation still depends on configured storage; do not remove user data for a demonstration.

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

## Seven-day planning

The pure DeadlineScheduler builds a feasible candidate for start through start+6, prioritising deadlines and using spaced review dates. It subtracts assessment-linked completed minutes, defaults only unknown estimates to 120 minutes and respects explicit zero. It never allocates after a deadline or beyond the requested week; unscheduled remaining work is reported.

The LLM reads required assessment, workload and progress tools (plus the previous plan for regeneration), then submits structured items through saveStudyPlan. The application validates ownership, dates, fields, remaining workload and daily capacity before atomic plan/calendar persistence. Exact time slots additionally constrain calendar placement around commitments. Saved versions are available through owner-scoped list/detail endpoints and the frontend history/regeneration controls.

Monthly and weekly views share calendar_entries. Completed history is immutable (delete/correct explicitly instead). Manual spaced-repetition chains remain separate from generated plans. Supplied academic references are verified through the CalendarReferences port and REST adapter before create/update; full-length valid titles can complete safely because generated review titles respect the domain limit.

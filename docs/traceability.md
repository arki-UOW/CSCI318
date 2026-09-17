# Traceability matrix

| Requirement | Implementation | Verification |
|---|---|---|
| Publish and receive meaningful domain facts | EventPublisher + transactional outbox; version-2 owner-scoped envelopes; explicit binder destinations | OutboxTest rollback, retry and contract checks |
| RT01 live workload | PlanningStreamTopology.workload; WorkloadState; account-workload-v2-store | PlanningStreamTopologyTest duplicate/stale updates, completion, deletion, isolation |
| RT02 weekly subject progress | PlanningStreamTopology.progress; ProgressState; subject-weekly-progress-v2-store | topology tests correction/week movement, deletion, target changes, completed blocks |
| CQRS/local dashboard | ProjectionIngestion, JpaProjectionStore, DashboardQueryService | JpaProjectionStoreTest persistence/revision guards; DashboardQueryServiceTest timezone/read-model queries |
| Dynamic dashboard | DashboardPushHub + dashboard-stream.js; bearer-authenticated SSE | Node fragmented-frame/auth/cancellation tests; real-Kafka integration observes SSE without reload |
| Plan through due date and estimated workload | pure DeadlineScheduler + WeeklyAvailability; StudyPlanningAgent application orchestrator | DeadlineSchedulerTest capacity, shortfall, horizon, completed-minute subtraction; integration calendar/plan checks |
| Layering and bounded contexts | Subject application ports/adapters; PlanningTools port/RestPlanningData; separate service DBs | source structure and application tests |
| Existing-row migration and safe rejection | SnapshotMigration; EventDecoder | OutboxTest migration transaction; topology metadata-only rejection checks |
| End-to-end actual broker evidence | scripts/verify-streaming.py and kafka-integration CI job | real producer/binder/topology/sink/SSE chain, account isolation, upstream-offline query check |

Lecture alignment: L3 layering, L4 aggregate/domain-service responsibilities, L5 asynchronously maintained CQRS read models, L6 stateful Kafka Streams operations and L8 repeatable reliability checks. This does not claim every lecture technology (event sourcing, Spring AI, formal verification) is implemented.

The team must capture its own demonstration screenshots, final report and presentation. Tests and CI results are evidence only when actually run; this matrix is not a substitute for results.

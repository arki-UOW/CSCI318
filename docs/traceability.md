# Traceability matrix

| Requirement | Implementation | Verification |
|---|---|---|
| FR-01–FR-04 subjects | Subject aggregate/application service; owned POST/GET/list/PATCH endpoints; manual and outline workflows | SubjectTest, SubjectControllerApiTest, frontend structure tests |
| FR-05–FR-10 assessments | Owned create/import/list/filter/edit/complete/delete endpoints and lifecycle events | Assessment domain/application tests and AssessmentControllerApiTest |
| FR-11–FR-13 study activity | Record/edit/delete/list with optional subject filter; weekly summaries and stream-derived totals | StudySessionTest, StudyActivityControllerApiTest, topology and dashboard tests |
| Publish and receive meaningful domain facts | EventPublisher + transactional outbox; version-2 owner-scoped envelopes; explicit binder destinations | OutboxTest rollback, retry and contract checks |
| RT01 live workload | PlanningStreamTopology.workload; WorkloadState; account-workload-v2-store | PlanningStreamTopologyTest duplicate/stale updates, completion, deletion, isolation |
| RT02 weekly subject progress | PlanningStreamTopology.progress; ProgressState; subject-weekly-progress-v2-store | topology tests correction/week movement, deletion, target changes, completed blocks |
| CQRS/local dashboard | ProjectionIngestion, JpaProjectionStore, DashboardQueryService | JpaProjectionStoreTest persistence/revision guards; DashboardQueryServiceTest timezone/read-model queries |
| Dynamic dashboard | DashboardPushHub + dashboard-stream.js; bearer-authenticated SSE | Node fragmented-frame/auth/cancellation tests; real-Kafka integration observes SSE without reload |
| Seven-day plan with deadline and workload constraints | pure DeadlineScheduler + WeeklyAvailability; StudyPlanningAgent application orchestrator | DeadlineSchedulerTest capacity, shortfall, horizon, completed-minute subtraction; integration calendar/plan checks |
| AI-01/FR-16 generate plan | AgenticPlanningAdvisor LangChain4j tool loop requires incomplete-assessment, workload and progress tools; model submits structured items via saveStudyPlan; pre-save validation | AgenticPlanningAdvisorTest, PlanningControllerApiTest, scheduler/validation tests |
| AI-02/FR-17 regenerate plan | tool loop additionally requires existing-plan state; new saved version, change explanation, owned history/detail APIs and frontend controls | AgenticPlanningAdvisorTest plus planning application/integration scenarios |
| FR-18 reject invalid AI output | allowlisted tools, structured item schema, required-tool gate, field/assessment/date/duration/daily-capacity/remaining-work validation before save | agent unit test and DeadlineScheduler/application validation tests |
| REST/SDK acceptance | scripts/verify-readiness.py starts actual five-service stack with a localhost model fixture | descriptions, errors, calendar links, generate/regenerate/history, invalid output rollback and provider quota response; no real key |
| Layering and bounded contexts | Subject application ports/adapters; PlanningTools port/RestPlanningData; separate service DBs | source structure and application tests |
| Existing-row migration and safe rejection | SnapshotMigration; EventDecoder | OutboxTest migration transaction; topology metadata-only rejection checks |
| End-to-end actual broker evidence | scripts/verify-streaming.py and kafka-integration CI job | real producer/binder/topology/sink/SSE chain, account isolation, upstream-offline query check |
| NFR-01/NFR-06 reproducibility | Docker Compose, `.env.example`, one-command launcher, authenticated Postman collection and demo-data script | clean CI build plus real-Kafka job; ordered Postman assertions |
| NFR-02 secrets | environment-only provider keys; ignored `.env`; no credentials in events | repository secret scan and event contract tests |
| NFR-03/NFR-04 consistency | shared error shape per service; controller/application/domain/infrastructure packages | handler/controller tests and package audit |

Lecture alignment: L3 layering, L4 aggregate/domain-service responsibilities, L5 asynchronously maintained CQRS read models, L6 stateful Kafka Streams operations and L8 repeatable reliability checks. This does not claim every lecture technology (event sourcing, Spring AI, formal verification) is implemented.

The team must capture its own demonstration screenshots, final report and presentation. Tests and CI results are evidence only when actually run; this matrix is not a substitute for results.

Fresh-audit evidence: FR-17 additionally covers empty-work regeneration and concurrent version/calendar commits; FR-18 covers malformed numeric and missing model fields before conversion. NFR-03 covers nested-null/profile bounds and outline-provider 503s; NFR-01 covers launcher failures. Account privacy and overnight display have frontend and HTTP regressions. See [completion audit](compliance/software-completion-audit.md), [detailed use cases](use-cases/functional-use-cases.md), [domain UML](domain-model/domain-model.md), [demo guide](testing/demonstration-guide.md), [decision record](ai-decisions/ADR-002-completion-decisions.md) and [proposal reconciliation](architecture/proposal-reconciliation.md).

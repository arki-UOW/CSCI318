# Traceability matrix — final report FR-01–FR-14 / UC-01–UC-14

The authoritative final user-goal grouping is Chapter 2 of the team report. This table replaces the original proposal's finer-grained FR-01–FR-18 operational numbering; **the old numbering is preserved only in historical documents**. Every active final requirement maps one-to-one to a consolidated UC, with two explicit Kafka Streams stories (RT-01/02) and two agentic stories (AI-01/02).

| FR / UC | Business objective | Implementation / contract | Verification evidence |
|---|---|---|---|
| FR-01 / UC-01 | OBJ-01 | Subject Service, Assessment import; POST /api/subject-outlines; GET /api/subject-outlines/{id}; POST /api/subject-outlines/{id}/confirm | Subject outline controller, import confirmation and frontend workflow tests |
| FR-02 / UC-02 | OBJ-01, OBJ-02 | Subject Service; POST/GET /api/subjects; GET/PATCH /api/subjects/{id} | SubjectTest; SubjectControllerApiTest |
| FR-03 / UC-03 | OBJ-01, OBJ-02, OBJ-03 | Assessment Service, Subject verification; POST/GET /api/assessments; PATCH /api/assessments/{id}; POST /api/assessments/{id}/complete | AssessmentControllerApiTest; assessment lifecycle/outbox tests |
| FR-04 / UC-04 | OBJ-02, OBJ-04 | Study Activity Service; POST /api/study-sessions | StudySessionTest; StudyActivityControllerApiTest |
| FR-05 / UC-05 | OBJ-02 | Study Activity and Planning Services; GET /api/study-sessions?subjectId=...; GET /api/study-sessions/summary; GET /api/planning/progress/{subjectId} | Activity summary and planning progress API tests |
| FR-06 / UC-06 | OBJ-05, OBJ-06 | Account Service; POST /api/auth/register; POST /api/auth/login; GET /api/auth/session; PATCH /api/profile | Account API/session/profile tests |
| FR-07 / UC-07 | OBJ-04, OBJ-05 | Planning Service; GET/POST /api/calendar; PATCH /api/calendar/{id}; planning availability input | Calendar and availability application/API tests |
| FR-08 / UC-08 | OBJ-04 | Planning calendar application; POST /api/calendar/{id}/complete | Calendar completion/spaced review tests |
| FR-09 / UC-09 / RT-01 | OBJ-02, OBJ-06 | RT-01, PlanningStreamTopology.workload; assessment-events; Planning workload topology; GET /api/planning/workload | PlanningStreamTopologyTest workload, dedupe, owner isolation |
| FR-10 / UC-10 / RT-02 | OBJ-02, OBJ-06 | RT-02, PlanningStreamTopology.progress; study-activity, subject-target and planning events; Planning progress queries | PlanningStreamTopologyTest progress, correction/target updates |
| FR-11 / UC-11 / AI-01 | OBJ-03, OBJ-04 | AI-01, StudyPlanningAgent; POST /api/planning/plans; LangChain4j tool calls; plan/version persistence | AgenticPlanningAdvisorTest; scheduler and planning integration cases |
| FR-12 / UC-12 / AI-02 | OBJ-03, OBJ-04, OBJ-06 | AI-02, planning persistence; POST /api/planning/plans/{id}/regenerate; GET /api/planning/plans | AgenticPlanningAdvisorTest; version/history/calendar integration cases |
| FR-13 / UC-13 | OBJ-03, OBJ-06 | Planning domain validation; AgenticPlanningAdvisor; DeadlineScheduler; validation/transaction tests | Agent validation, missing/fractional fields and rollback tests |
| FR-14 / UC-14 | OBJ-02, OBJ-05 | Planning projections, DashboardPushHub; Planning dashboard REST/SSE, GET /api/planning/dashboard/stream | DashboardQueryServiceTest; SSE/frontend tests |

## Cross-cutting engineering controls

| Concern | Implementation | Evidence |
|---|---|---|
| Domain-driven design and bounded contexts | Five Spring Boot services; independently owned H2 databases; named aggregate and value concepts | Domain model and service tests |
| Transactional event publication | Outbox persistence and publisher; version-2 owner-scoped event envelopes | Outbox rollback/retry/contract tests |
| Eventual consistency and CQRS | Kafka Streams stateful read models and Planning query projections | Real Kafka integration and topology tests |
| Secure account separation | Bearer token validation and owner-scoped data and events | Cross-account HTTP and controller checks |
| NFR-01–NFR-06 | README, Compose, env example, shared errors, tests, Postman demonstrations | [completion audit](compliance/software-completion-audit.md) and [demo guide](testing/demonstration-guide.md) |

**Evidence caution:** Recorded local fixture checks do not establish live Gemini success. The final report's manual live-Gemini claims must remain distinct from automated fixture CI results. Presentation screenshots and final presentation are group-owned deliverables.

Current student workflow verification uses the same final 14 requirement/use-case IDs. See [fresh workflow evidence](testing/student-workflow-evidence.md) for actual inputs, outputs, automated coverage and limits. Historical operation examples and past test results are unchanged.

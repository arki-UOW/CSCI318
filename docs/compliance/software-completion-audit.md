# CSCI318 software readiness verification

Verification date: 11 October 2026. Baseline: `0fda7273a58f657d4ef18ff93ee4583dd706cd2c`. Changes are on `agent/specification-readiness`.

This is an engineering artifact for the repository. It is not the student-authored assessed report or presentation. Requirements were taken from the professor's **CSCI318 Project Specification Spring 2026 (4).pdf**, pages 3–7, and the team's **CSCI318_Study_Leftovers_Project_Proposal_Updated (4).pdf**, especially FR-01–FR-18, NFR-01–NFR-06, RT-01/02 and AI-01/02. Earlier repository claims of unconditional completion are superseded by this verification.

## Result and limits

The six reproduced defects and three identified proposal gaps are fixed in source and covered by passing local regression/acceptance checks. All 18 functional requirements have corresponding code paths and test evidence. New planning follows the proposal's exact seven-day contract; the model submits structured items and the server validates them before persistence.

Local evidence: **86 Java tests, 13 frontend tests and 51 isolated five-service HTTP checks passed**. No real API key was requested, read, committed or used. The HTTP suite used the actual LangChain4j OpenAI SDK against an explicitly local protocol fixture. It proves integration and failure handling, not the quality/availability of a live Gemini/OpenAI model.

The PR's `test` and `kafka-integration` checks must be green on the reviewed commit. The former rebuilds and runs Java, frontend and isolated HTTP acceptance; the latter starts real Kafka and all services in fresh Docker volumes, tests event projections/SSE and proves queries work with upstream academic services stopped. The final delivery checklist records the exact GitHub run/revision when completed.

No finite test suite establishes that software is absolutely perfect. The team's privately configured live-provider demonstration and academic deliverables are distinct from these code fixes. Credentials, quota, model access, coordinator approvals and member contributions cannot be certified from code.

## Completed changes

| Audit item | Completed implementation | Regression evidence |
|---|---|---|
| F1: valid descriptions failed persistence | Assessment and Activity descriptions use CLOB; DTO/domain/UI limits agree at 2,000 characters. Subject/assessment text fields are bounded before storage. | Two DescriptionPersistenceTest suites migrate legacy VARCHAR(255), preserve existing rows and read back 2,000 characters; HTTP boundary checks |
| F2: SDK failures escaped safe mapping | Model-call boundary translates SDK exceptions to safe actionable 503 responses; bounded planning timeout/retry; availability/study assistants share safe provider errors. | ProviderFailureApiTest quota/auth/model/timeout cases; actual SDK HTTP 429 fixture, no raw detail leakage |
| F3: zero became 120 minutes | Only null defaults to 120; explicit zero schedules no work. | DeadlineSchedulerTest and HTTP API-to-plan zero-work check |
| F4: missing dates returned 500; error shapes differed | Validated required fields, duration and text; missing subject is invalid input; all five handlers return consistent fields for validation/malformed requests and safe errors. Nullable subject-target input is validated. | HTTP missing date/ID, zero duration, oversized notes, malformed JSON on five services and null weekly-target cases |
| F5: long titles broke review completion | Generated review titles respect 160 characters; full source title remains in description. | CalendarApplicationServiceTest and HTTP completion/readback |
| F6: invalid calendar references accepted | CalendarReferences port and REST adapter verify owned subject/assessment and matching relation on create/update; null/null personal entries remain permitted. | CalendarReferencesTest missing/mismatch/outage cases; real HTTP nonexistent/foreign/mismatched references |
| D1: deadline horizon differed from proposal | Candidate and stored API plans span exactly start through start+6. Future deadlines are handled inside that week; unmet remaining work is reported. | Domain/application tests and HTTP horizon assertion |
| D2: model submitted only a decision | saveStudyPlan accepts structured items; required assessment/workload/progress tools, plus previous plan on regeneration. Server validates actual model items and remaining-work budgets before atomic plan/calendar persistence. | AgenticPlanningAdvisorTest, PlanningValidationTest, JPA integration and actual SDK fixture; invalid output creates no version and preserves calendar |
| D3: history and UI regeneration absent | Owned list/detail APIs, history selector, explicit previous-plan regeneration; version increases from newest saved version. Async plan responses are discarded if the signed-in account changes. | JPA history/isolation tests, HTTP history/detail checks and frontend handler tests |
| Reproducibility/documentation | Acceptance script runs from source without private keys; CI includes it. README, API contracts, architecture, AI design, tests, traceability and Postman weekly assertions reconciled. | Clean build, JSON parsing, whitespace/secret checks and CI |

## Professor's technical criteria

| Criterion | Code assessment and evidence |
|---|---|
| At least four bounded contexts | Five independent runnable services: Account, Subject, Assessment, Study Activity, Planning. |
| Required framework and JDK | JDK 21; Spring Boot/Web/Data JPA/H2/Cloud Stream, Kafka and LangChain4j used in actual production flows; clean build. |
| REST and event-driven integration | Authenticated REST clients plus transactional outbox/event publication and explicit Kafka consumers. |
| At least two stream-processing stories | Workload and progress topologies perform keyed, revision-aware aggregation; persisted local query projections and SSE. Topology tests plus real-broker CI. |
| At least two agentic stories | Generation and regeneration perform bounded model-selected tool loops, required factual retrieval, structured submission, validation and persistence. Real SDK transport tested using local fixture. |
| Layering | Controller/application/domain/infrastructure packages across services; PlanningTools and CalendarReferences ports/adapters. Some repository dependencies remain direct application-to-infrastructure, consistent with the four-layer prototype. |
| Meaningful DDD patterns | Identity-bearing aggregates, invariant-preserving methods, immutable weekly availability/time-slot value objects, scheduling/workload domain services, versioned business facts. |
| Separate persistence ownership | Independent H2 databases/volumes; cross-service references are IDs rather than shared JPA entities. Persistence/migration tests pass. |
| At least 12 functional requirements | All 18 below implemented. |
| Reproducible API demonstration | README/Compose/launchers/Postman plus executable HTTP and real-Kafka verification scripts. A live-key Postman run is not claimed by fixture tests. |

## Proposal traceability

| ID | Required capability | Code and executable evidence |
|---|---|---|
| FR-01 | Create subject | Manual and outline confirmation paths; controller/domain tests; HTTP manual and PDF confirmation |
| FR-02 | Retrieve subject | Owned lookup; controller tests and cross-account isolation |
| FR-03 | List subjects | Owner-scoped repository/application/controller; UI loading |
| FR-04 | Update details/weekly target | Validated PATCH paths and domain invariants; HTTP target/null-input checks; target-event topology coverage |
| FR-05 | Create assessment for subject | REST subject verification, bounded input and import; HTTP manual and confirmed outline assessment |
| FR-06 | Display all assessments | Owned list endpoint and frontend list; acceptance reads imported records |
| FR-07 | Incomplete assessments | Status filtering of authoritative assessment records; no title-based exclusion |
| FR-08 | Change deadline | Validated PATCH; changed-state regeneration test |
| FR-09 | Change estimated workload | Nonnegative estimate; null/zero differentiated; HTTP update-to-regeneration and zero-work checks |
| FR-10 | Complete assessment | Domain transition, owned endpoint, event publication; topology and real-Kafka completion tests |
| FR-11 | Record completed study | Validated owned subject/date/minutes/description; HTTP long notes and invalid-input cases |
| FR-12 | Sessions by subject | Owned subject-filtered listing; controller/application tests |
| FR-13 | Total/weekly duration | Activity weekly summary and Planning weekly/lifetime projections; correction/week movement tests |
| FR-14 / RT-01 | Workload events | Kafka Streams workload aggregation, completion/deletion/correction/stale-event guards; topology and broker CI |
| FR-15 / RT-02 | Progress events | Activity/subject/calendar facts aggregate progress, targets and totals; topology and broker CI |
| FR-16 / AI-01 | Seven-day plan | Required facts/progress tools, structured model items, exact horizon and capacity validation; unit/JPA/HTTP checks |
| FR-17 / AI-02 | Regenerate after changes | Required previous-plan retrieval, changed facts/completed-work subtraction, new saved version; JPA/HTTP/frontend checks |
| FR-18 | Reject invalid AI plans | Required-tool/schema gates, IDs/status/subject/date/minute/capacity/remaining-work validation; no persistence on failure; parameterized and HTTP rollback checks |

| ID | Non-functional requirement | Assessment |
|---|---|---|
| NFR-01 | Reproducible local system | Clean JDK 21 build, documented Compose/configuration and isolated acceptance script; real-Kafka CI required |
| NFR-02 | No committed secrets | Private environment injection retained; fixture credential is explicitly fake; status/errors do not return keys |
| NFR-03 | Consistent errors | All five advice handlers use timestamp/status/error/message/path/validationErrors; malformed/validation paths tested through HTTP |
| NFR-04 | Consistent layers/names | Five bounded contexts with four layers; technical shared messaging module |
| NFR-05 | Repeatable tests | 86 Java, 13 frontend, 51 isolated HTTP checks plus real-Kafka CI |
| NFR-06 | Repeatable Postman demo | Ordered account/ID capture, bounded projection polling, seven-day generation and changed-state regeneration assertions; real-key execution remains an external verification prerequisite |

## Operational boundaries

- Keep genuine provider keys in private environment configuration. Missing keys are an intentional setup prerequisite, not missing code. A valid key does not guarantee quota/network/model availability; those errors now fail safely.
- Stream projections are eventually consistent. Tests poll for convergence; completed linked work can lag immediately after a command.
- A plan replaces the account's currently planned AI calendar entries, while saved plan versions and completed history remain available. If exact slots conflict with commitments, validation rejects the request and retains the previous plan.
- Confirmed assessments with no date use the current week; explicit zero work yields an empty plan. Overdue work is excluded from new future schedules and is not silently assigned after its deadline.
- Professor administrative requirements (report/presentation authorship, meeting minutes, signed contributions, private-repository submission and approval of extra dependencies) require team evidence; they are not inferred from source code. No repository visibility or submission settings were changed.

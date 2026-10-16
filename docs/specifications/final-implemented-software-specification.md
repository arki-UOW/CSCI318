# Study Leftovers — Final Implemented Software Specification

**CSCI318 Software Engineering Practices & Principles · Spring 2026**  
**Project:** Study Leftovers — AI-Powered, Event-Driven Study and Assessment Planning System  
**Team:** Better Engineering Geeks (Group 13)  
**Status:** Final implemented software specification — reviewed against the referenced evidence baseline  
**Evidence baseline:** [`arki-UOW/CSCI318` at commit `21465e1385e08d74a006666ef416eb3b376e8379`](https://github.com/arki-UOW/CSCI318/commit/21465e1385e08d74a006666ef416eb3b376e8379) (`main`, inspected 16 October 2026). This is the reviewed baseline, **not** an immutable statement about subsequent commits or local uncommitted work.

> **Document purpose.** This specification records the **implemented software** and its verifiable acceptance criteria. It supplements, but does not replace or retrospectively rewrite, the original *Study Leftovers Group Project Proposal* (Spring 2026). Historical plans and final behaviour are deliberately distinguished. Source paths below refer to the project repository.

## Current workflow addendum — 16 October 2026

The historical evidence baseline below remains unchanged. The current student workflow adds TXT import, forward-only account-local planning, visible overdue work, source-linked calendar deadlines/activity, assessment-linked study records, and interactive history under Profile. No final FR/UC or original operation was removed. Detailed contracts and newly executed evidence are in [contracts](../architecture/contracts.md#student-workflow-contract-additions--final-fruc-numbering) and [student workflow evidence](../testing/student-workflow-evidence.md).

## 1. Business context and objectives

Students often manage subject outlines, assessments, due dates, work estimates, study sessions and calendar commitments in disconnected places. Study Leftovers provides a local academic workspace in which a student can capture subject information, monitor workload and progress, record study activity, and generate or regenerate a practical seven-day study plan grounded in current application data. A browser client makes the workflows accessible; REST APIs remain the authoritative service interfaces.

The system is an academic prototype, not a hosted university information system or a guarantee of optimised study outcomes. It demonstrates domain-driven design, independently persisted microservices, REST and asynchronous integration, two stateful Kafka Streams queries, and LangChain4j-mediated tool-using planning. It also includes account isolation, outline extraction, calendar operations and supplementary study-assistant capabilities introduced during implementation.

**Primary stakeholders:** student end users; the project team maintaining and demonstrating the prototype; and CSCI318 teaching staff evaluating design, implementation, reproducibility and evidence.

**Functional objectives:** manage subjects and assessments; monitor outstanding academic workload and weekly progress; record completed study; capture availability; generate and revise schedules without exceeding verified constraints; preserve account-scoped history; and present results in a usable interface.

## 2. Implemented scope and boundaries

### 2.1 Delivered capabilities

- Account registration, sign-in, remembered sessions, profile and password management, and account-scoped academic data.
- Manual subject creation/editing, plus outline import from PDF/DOCX/TXT/JPG/JPEG with extraction and a human confirmation step; the frontend queues up to 10 documents per batch.
- Assessment creation, import, editing, completion, deletion, workload estimates and due dates, with Subject Service verification of references.
- Study-session creation, correction, deletion and subject-based summaries.
- Two independent Kafka Streams materialised computations: assessment workload and weekly subject progress.
- Streaming-derived dashboard values pushed to the browser using authenticated server-sent events (SSE).
- Seven-day planning through a bounded LangChain4j LLM tool workflow plus deterministic scheduling and application-side constraints; generation, regeneration, version history and calendar integration.
- Availability capture by structured minutes/slots and conversational natural-language interaction.
- Editable month/week calendar; manual work items, completed study blocks and spaced review scheduling.
- A context-grounded study-assistant chat, theme/navigation preferences and responsive frontend.
- Docker-based local startup, Maven build, Postman demonstration, automated tests and repository evidence.

### 2.2 Explicit operational exclusions and limits

- No claim of production-grade cloud deployment, Kubernetes support, university/LMS synchronisation, external email/SMS integration or mobile-native application.
- No claim that local fixture tests prove live model reliability, model quality, quota, or provider availability.
- No claim of mathematical global optimum for schedules. Study blocks are constrained feasible recommendations within the specified seven-day period.
- No claim that Kafka Streams `exactly_once_v2` extends to cross-service H2 transactions or outbox publication; projections are eventually consistent.
- Private LLM credentials must be supplied externally for live AI workflows. Core academic CRUD and streaming functions can operate without a provider key.

## 3. Five bounded contexts and data ownership

| Bounded context/service | Port | Owned academic or account state | Main responsibility |
|---|---:|---|---|
| Account Service | 8085 | Accounts, password hashes, session hashes, profile and UI settings | Identity, authentication and profile ownership |
| Subject Service | 8081 | Subjects, study targets, outline-import extraction and confirmation state | Subject lifecycle and trusted source of subject data |
| Assessment Service | 8082 | Assessments, deadlines, estimated workloads, priority and completion | Assessment lifecycle and associated domain events |
| Study Activity Service | 8083 | Study sessions and recorded durations | Record, update and summarise actual study activity |
| Planning Service | 8084 | Workload/progress query projections, availability, plans/versions, calendar entries | Read models, scheduling, agent integration and calendar |

Each service owns an independent file-backed H2 database. Cross-service academic relationships are identifiers and service contracts, not shared JPA aggregates. The browser client runs on port 3000 and does not own domain persistence. A `messaging-support` Maven module provides technical event/outbox infrastructure without becoming an additional business bounded context.

**Integration topology:** Assessment Service checks the subject reference through Subject REST before assessment creation. Academic changes are written with outbox records, published to Kafka, reduced by Planning's stateful streams and projected into locally owned read models. Planning's tool adapters read authenticated current data and pass validated plans to its own persistence and calendar services. For exact routes and events, see `docs/architecture/contracts.md`.

## 4. Core requirements and acceptance contracts

The initial proposal recorded **18 elementary functional requirements (FR-01–FR-18)** and NFR-01–NFR-06. During final report preparation, related actions were **consolidated into 14 final functional requirements (FR-01–FR-14) and 14 corresponding use cases (UC-01–UC-14)**. This is a renumbered final requirement model, not deletion of four working features. See the [initial specification](initial-software-specification.md) for the untouched historical list and [requirements mapping](../requirements/implemented-requirements.md) for current acceptance evidence.

| Final ID / use case | Consolidated implemented requirement | Primary acceptance condition |
|---|---|---|
| FR-01 / UC-01 | Import and Confirm Subject Outline | A supported outline is uploaded, extraction reviewed and corrected if needed, and only confirmed academic records are persisted. |
| FR-02 / UC-02 | Manage Subjects | Account-owned subject records can be created, retrieved, listed and updated. |
| FR-03 / UC-03 | Manage Assessments | Owned assessments can be created/imported, filtered, edited and completed; invalid subject IDs are rejected. |
| FR-04 / UC-04 | Record Completed Study Session | A valid subject-owned activity is stored and a study-session event is queued. |
| FR-05 / UC-05 | Retrieve Study History and Duration Summary | Account-scoped historical sessions and weekly/total duration are returned. |
| FR-06 / UC-06 | Manage Account, Authentication and Profile | Registration and login establish sessions; profile requests enforce ownership. |
| FR-07 / UC-07 | Manage Calendar Entries and Availability | Account-owned calendar entries are maintained and validated availability is provided to planning. |
| FR-08 / UC-08 | Schedule Spaced-Repetition Reviews | Completing an eligible calendar review adds the next applicable interval from 1/3/7/14/30 days. |
| FR-09 / UC-09 / RT-01 | Update Real-Time Workload Projection | Committed assessment changes update keyed workload state without duplicate/stale inflation. |
| FR-10 / UC-10 / RT-02 | Update Real-Time Study-Progress Projection | Accepted study activity, target and calendar-completion facts update subject/week progress. |
| FR-11 / UC-11 / AI-01 | Generate Seven-Day Study Plan | Current academic facts and availability inform a validated seven-day plan; accepted plan is saved. |
| FR-12 / UC-12 / AI-02 | Regenerate Study Plan | Existing plan plus current facts yield a newly validated version without overwriting prior history. |
| FR-13 / UC-13 | Validate and Reject Invalid AI-Generated Plan | Malformed or constraint-violating provider output must not change plan history or calendar. |
| FR-14 / UC-14 | View Academic Dashboard | The owner sees projected workload, progress and upcoming tasks, with eventually consistent updates. |

**Executable and detailed evidence:** [consolidated functional use cases](../use-cases/functional-use-cases.md), [REST and event catalogue](../architecture/contracts.md), [Postman collection](../../postman/) and the final report's Chapter 2 UML activity diagrams.

### 4.1 Supplementary implemented user capabilities

The 18-item original numbering is retained **only in the original proposal**. The final report uses FR-01–FR-14/UC-01–UC-14. The `EXT` identifiers below record supplementary implemented details and should not be mistaken for additional separate final use cases. Each acceptance statement describes an observable condition rather than guaranteeing all failure modes have been exhausted.

| ID | Supplementary delivered capability | Principal acceptance condition and current evidence |
|---|---|---|
| EXT-01 | Registration, authentication and remembered sessions | Account registration/login returns an authenticated session; protected service requests require an account-scoped bearer token. `account-service/`, `docs/architecture/contracts.md`. |
| EXT-02 | Profile, password and appearance settings | Owned profile/password updates and saved theme/navigation choices are available; password changes revoke other sessions. Account API tests, `frontend/tests/`. |
| EXT-03 | Subject-outline ingestion and review | Subject Service accepts PDF, DOCX, TXT, JPG/JPEG outline input, exposes review state, and requires user confirmation before assessment import. `POST /api/subject-outlines`, `GET /api/subject-outlines/{id}`, `POST /api/subject-outlines/{id}/confirm`; Subject tests. |
| EXT-04 | Multi-file outline queue in browser | Frontend can queue up to 10 supported outline files for sequential submission; server upload endpoint handles one file per call. `README.md`, `frontend/`. |
| EXT-05 | Calendar operations and version history | Owned calendar entries can be viewed, created, edited and completed; saved plan versions can be listed and retrieved. `GET/POST /api/calendar`, `PATCH /api/calendar/{id}`, `GET /api/planning/plans`. |
| EXT-06 | Conversational availability | Natural-language weekday/time expressions are translated into a reviewable availability schedule; invalid or ambiguous slots require clarification or rejection. `POST /api/planning/availability/chat`, availability tests. |
| EXT-07 | Live dashboard projection updates | Authenticated browser stream delivers updated workload/progress state after eventual Kafka projection convergence. `GET /api/planning/dashboard/stream`, Kafka verification script and frontend dashboard tests. |
| EXT-08 | Spaced review scheduling | Completion of an applicable calendar study item can schedule subsequent review entries using the documented 1/3/7/14/30-day sequence. `POST /api/calendar/{id}/complete`, calendar application tests. |
| EXT-09 | Context-grounded study assistant | An authenticated student may request study help using profile-scoped subject, assessment and schedule context when a provider is configured. `POST /api/planning/assistant/chat`; this does not guarantee the correctness of all model answers. |

### 4.2 Requirement ownership and acceptance boundaries

All account and academic data is scoped to the authenticated owner; missing and foreign-owned resources are not returned as other users' records. REST and frontend features require the corresponding service and runtime configuration; AI-backed features additionally require provider credentials, quota and network availability. The complete implementation contract, rather than this overview, is recorded in [`docs/architecture/contracts.md`](https://github.com/arki-UOW/CSCI318/blob/21465e1385e08d74a006666ef416eb3b376e8379/docs/architecture/contracts.md). Detailed final consolidated use cases are recorded in [`docs/use-cases/functional-use-cases.md`](https://github.com/arki-UOW/CSCI318/blob/21465e1385e08d74a006666ef416eb3b376e8379/docs/use-cases/functional-use-cases.md).

## 5. Non-functional requirements

| ID | Implemented acceptance target | Evidence and limit |
|---|---|---|
| NFR-01 | Reproducible local system | JDK 21/Maven build, Docker Compose, verified launch scripts, README; separate clean-machine check remains desirable |
| NFR-02 | Protect credentials | Real keys only in private runtime configuration; `.env.example` contains placeholders; no credentials in source, events or frontend |
| NFR-03 | Consistent error responses | `timestamp`, `status`, `error`, `message`, `path`, `validationErrors`; standard 400/401/404/409/503 cases |
| NFR-04 | Layered service responsibilities | Presentation, application, domain and infrastructure packages; deliberate JPA coupling is documented |
| NFR-05 | Repeatable software verification | Java, frontend, isolated HTTP and real-Kafka CI workflows with documented constraints |
| NFR-06 | Repeatable demonstration | Ordered authenticated Postman collection, fixture-based provider tests and documented live-key setup |

## 6. REST, event-driven integration and streaming

### 6.1 REST contracts

The current API uses authenticated account-scoped requests, with the Account Service providing bearer-session verification. Subject/Assessment/Activity/Planning controllers enforce ownership and business validation. Example endpoints include `POST /api/subjects`, `POST /api/assessments`, `POST /api/study-sessions`, `GET /api/planning/workload`, `GET /api/planning/progress/{subjectId}`, `POST /api/planning/plans`, `POST /api/planning/plans/{id}/regenerate` and `GET /api/calendar`.

The original proposal's example routes such as `POST /api/planning/generate` are **historical proposals**, not the implemented API. The authoritative complete endpoint catalogue and request formats are in `docs/architecture/contracts.md`.

### 6.2 Kafka topics and envelope

Owned application changes are written with a transactional outbox. Current business topics are `subject-events`, `assessment-events`, `study-activity-events` and `planning-events`. Kafka Streams produces `workload-projections` and `progress-projections`; rejected/sink-failure traffic is separated in metadata-only rejection and dead-letter topics.

Current version-2 event envelopes include UUID event and aggregate identifiers, a UUID owner identifier, an integer version and aggregate revision, a UTC timestamp, source service and structured payload. Account-qualified keys and increasing revisions allow the reducers to ignore duplicate or stale facts. Events do not contain passwords, tokens or raw outline text. Details: `docs/architecture/contracts.md`.

### 6.3 RT-01 — Assessment workload

The `workloadStream` aggregates the latest valid per-assessment facts by account in the `account-workload-v2-store` materialised Kafka Streams state store. It accounts for create/update/completion/deletion and stale revisions. Current outstanding workload, upcoming work and priority summaries are served from Planning's local projected records; deadline-relative output uses the requested timezone and current date.

### 6.4 RT-02 — Weekly study progress

The `studyProgressStream` combines activity events, subject-target changes and completed planning-block events by account and subject. `subject-weekly-progress-v2-store` maintains totals and weekly progress, including correction/removal and changes in weekly targets. **Change from proposal:** target propagation is event-based, not the original proposed REST lookup-and-cache workflow. The resulting read models remain queryable if an upstream academic service is temporarily unavailable, subject to projection convergence.

## 7. Agentic AI and scheduling

- **Provider boundary:** LangChain4j connects to Gemini and can be configured for the supported OpenAI-compatible path; valid private credentials and provider availability are operational prerequisites.
- **Structured tool loop:** The model retrieves required incomplete assessments, workload and progress facts (and the previous plan during regeneration), then submits structured study blocks via `saveStudyPlan`. Java validates fields and referenced account-owned data before writing.
- **Seven-day horizon:** Both candidate and saved plans span exactly `startDate` through `startDate + 6 days`.
- **Feasibility:** Deterministic scheduling and application checks enforce available minutes/slots, outstanding work, relevant deadlines, valid subject/assessment references and existing calendar commitments. Unscheduled work is reported rather than silently placed outside constraints.
- **Regeneration:** A new account-unique plan version is saved. Superseded AI-planned calendar entries are replaced; previous versions and non-AI commitments are preserved. Owner-specific locking and a database uniqueness constraint protect version allocation under concurrent calls.
- **Zero work:** An empty seven-day version is valid when remaining work is zero. Availability need not be fabricated and obsolete AI-planned blocks can be cleared.
- **Failure handling:** Schema-invalid model submissions, ownership mismatches, capacity violations and unavailable providers fail without committing an invalid plan. Local protocol fixtures verify integration cases; actual Gemini connectivity must be validated separately.
- **Adjacent AI features:** Provider-backed outline interpretation, conversational availability and study-assistant chat are documented in the README and API contracts. They are separate from the two required planning user stories.

## 8. Domain design and data rules

Domain entities and consistency boundaries follow service-owned aggregates (including Subject, Assessment and StudySession). The project also uses invariant-bearing values such as `WeeklyAvailability` and `WeeklyTimeSlots` and pure domain logic such as `DeadlineScheduler`. The original proposal identified `SubjectCode`, `Deadline` and `EstimatedWorkload` as potential value-object classes. The implementation instead represents some corresponding constraints through aggregate behaviour and validation, while explicitly providing invariant-bearing types such as `WeeklyAvailability` and `WeeklyTimeSlots`.

Four architectural layers are described in `docs/architecture/architecture.md`. Persistence uses JPA with independent H2 stores. The technical event infrastructure is shared; business records are not.

## 9. Quality assurance and evidence provenance

At the reviewed repository snapshot (`21465e1`), `docs/compliance/software-completion-audit.md` reports **94 Java tests, 23 frontend tests and 142 isolated five-service HTTP checks passing** in a documented local execution, alongside a separate real-Kafka CI workflow. These figures are an **audit record**, not a claim that every environment, live Gemini session or later revision is automatically verified.

The isolated HTTP suite uses a local protocol fixture through the real LangChain4j SDK; it must not be described as live provider verification. The team separately reports manually testing live Gemini plan generation and regeneration, persistence, version advancement and corresponding calendar changes. Keep that firsthand observation distinct from scripted/CI evidence and record the final submitted commit and successful CI workflow links when the delivery archive is frozen. **A passing CI run for this exact snapshot has not been independently established by this document.**

Evidence entry points: `docs/compliance/software-completion-audit.md`, `docs/testing/testing-strategy.md`, `docs/testing/demonstration-guide.md`, `scripts/verify-readiness.py`, `scripts/verify-streaming.py`, `.github/workflows/`, `frontend/tests/`, and each service's `src/test/`.

## 10. Original proposal versus final implementation

| Original Spring 2026 proposal | Final implementation |
|---|---|
| Four academic bounded contexts | Four academic contexts plus Account Service (five total) |
| No authentication or production browser interface in scope | Account-scoped authentication/profile services and a local browser frontend |
| Manual subject/assessment management | Manual management plus human-reviewed PDF/DOCX/image outline imports |
| Four service ports, 8081–8084 | Five service ports, 8081–8085; frontend at 3000 |
| Planning reads weekly study target from Subject REST | Subject target events join the Kafka Streams progress topology |
| Long aggregate IDs and version-1 event examples | Owner-scoped UUID aggregate identifiers and revisioned version-2 facts |
| Proposed individual update endpoints | Implemented consolidated PATCH/query-filter REST contracts; see catalogue |
| Seven-day generate/regenerate concept | Validated agentic tool submission, version history, atomic plan/calendar persistence and valid empty plans |
| Postman as principal client | Postman plus a responsive local browser UI, SSE dashboard, calendars and study assistant |
| No formal account/session domain | Account-scoped session, password and profile controls |

The original proposal should be retained unchanged as a historical artefact. This final document is the current implementation specification and does **not** imply that the original proposal predicted these additions or that all changes received coordinator approval.

## 11. Repository structure and reproducibility

Principal top-level paths: `account-service/`, `subject-service/`, `assessment-service/`, `study-activity-service/`, `planning-service/`, `messaging-support/`, `frontend/`, `docs/`, `postman/`, `scripts/`, `.github/workflows/`, `pom.xml`, `docker-compose.yml`, `.env.example`, `README.md`, and `Start-Study-Leftovers.cmd` / `Stop-Study-Leftovers.cmd`.

Typical quick start: install Docker Desktop; keep actual provider secrets in private `.env` (never commit them); run `Start-Study-Leftovers.cmd` on Windows or `.\scripts\run-all.ps1` from PowerShell as documented; open the frontend on `http://localhost:3000`. The root README is authoritative for supported setup commands, dependencies, model selection, troubleshooting and reset operations. A fully working provider-backed demonstration additionally requires an accessible valid account-specific API key and provider quota.

## 12. Outstanding verification and approval items before submission

1. **Freeze commit and CI:** The specification was reviewed at commit `21465e1385e08d74a006666ef416eb3b376e8379`; record the actual submission commit and successful CI workflow links after final changes, and avoid applying prior test figures to an untested newer revision.
2. **Clean archive:** Download/extract the final repository ZIP and check that Docker and local-start instructions actually work without untracked files or private environment data.
3. **Academic presentation/report:** Ensure the assessed report includes each implemented user story's input and output, final accountability/signatures and the separately provided presentation instructions.
4. **Dependency permission:** The CSCI318 professor specification requires explicit permission for dependencies outside its named baseline. The repository lists additional libraries (e.g. authentication, file/OCR, validation and frontend utilities); the available evidence does not prove the coordinator approved them. The team should retain approval evidence or ask for clarification.
5. **Specification transport difference:** The initial proposal described Planning retrieving study targets through REST; current implementation distributes target facts by Kafka. Preserve this disclosure and check whether any teaching-staff requirement demands the original transport, rather than assuming equivalence is formally approved.
6. **Provider evidence:** Keep live-Gemini manual observations separate from automated SDK fixture and Kafka checks.

## 13. Linked repository evidence and source precedence

- `README.md` — feature overview, configuration, startup, model prerequisites and demonstration.
- `docs/requirements/implemented-requirements.md` — FR/NFR acceptance coverage.
- `docs/use-cases/functional-use-cases.md` — 14 consolidated use cases and links to final-report activity diagrams.
- `docs/architecture/architecture.md` and `docs/architecture/diagrams.md` — contexts, layering, workflows and diagrams.
- `docs/architecture/contracts.md` — complete REST and Kafka contracts.
- `docs/architecture/proposal-reconciliation.md` — explicit design changes.
- `docs/domain-model/domain-model.md` — documented domain structures and DDD responsibilities.
- `docs/traceability.md` — objective-to-test/evidence mapping.
- `docs/ai-decisions/ADR-001-prototype-architecture.md` and `ADR-002-completion-decisions.md` — engineering decisions, with the latter's AI-assisted provenance acknowledged.
- `docs/compliance/software-completion-audit.md` — audited tests, corrections and verification limits.
- `postman/Study-Leftovers.postman_collection.json` — authenticated demonstration requests.

**Version-control note:** These paths were checked against commit [`21465e1`](https://github.com/arki-UOW/CSCI318/commit/21465e1385e08d74a006666ef416eb3b376e8379). On the final submission commit, reviewers should confirm any renamed, added or removed files and update this document accordingly. The root README and `docs/architecture/contracts.md` provide the most specific run/API instructions; source code and executable tests govern demonstrated behaviour; original-proposal text is historical.

**Sources:** original 14-page *CSCI318 Study Leftovers Group Project Proposal* supplied by the team; current repository documentation and source-tree evidence. Where source documents differ, implemented contracts and verified code behaviour control this final specification, and differences are recorded rather than concealed.
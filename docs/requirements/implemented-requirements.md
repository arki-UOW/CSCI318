# Implemented requirements and acceptance evidence — final 14-requirement model

**Authority:** Study Leftovers Final Project Report, Chapter 2, Table 2.1. The original proposal's FR-01–FR-18 remains preserved only in the [initial specification](../specifications/initial-software-specification.md). Final requirements FR-01–FR-14 and use cases UC-01–UC-14 consolidate related operations; they do not imply that four capabilities were removed. The detailed cases are in [functional-use-cases.md](../use-cases/functional-use-cases.md).

| Final requirement | Implemented behaviour | Acceptance evidence / trace |
|---|---|---|
| FR-01 | AI-assisted outline extraction and user confirmation | A supported outline is uploaded, extraction reviewed and corrected if needed, and only confirmed academic records are persisted. UC-01; Subject Service, Assessment import. |
| FR-02 | Create, view and update subjects including weekly study targets | Account-owned subject records can be created, retrieved, listed and updated. UC-02; Subject Service. |
| FR-03 | Create, view and manage assessments, deadlines, workload and completion | Owned assessments can be created/imported, filtered, edited and completed; invalid subject IDs are rejected. UC-03; Assessment Service, Subject verification. |
| FR-04 | Record a completed study session | A valid subject-owned activity is stored and a study-session event is queued. UC-04; Study Activity Service. |
| FR-05 | Retrieve study history and study-duration summaries | Account-scoped historical sessions and weekly/total duration are returned. UC-05; Study Activity and Planning Services. |
| FR-06 | Manage accounts, authentication and profile | Registration and login establish sessions; profile requests enforce ownership. UC-06; Account Service. |
| FR-07 | Manage calendar entries and planning availability | Account-owned calendar entries are maintained and validated availability is provided to planning. UC-07; Planning Service. |
| FR-08 | Schedule spaced-repetition reviews | Completing an eligible calendar review adds the next applicable interval from 1/3/7/14/30 days. UC-08; Planning calendar application. |
| FR-09 | Maintain real-time assessment workload via Kafka Streams | Committed assessment changes update keyed workload state without duplicate/stale inflation. UC-09; RT-01, PlanningStreamTopology.workload. |
| FR-10 | Maintain real-time study-progress via Kafka Streams | Accepted study activity, target and calendar-completion facts update subject/week progress. UC-10; RT-02, PlanningStreamTopology.progress. |
| FR-11 | Generate a personalised seven-day plan via agentic AI | Current academic facts and availability inform a validated seven-day plan; accepted plan is saved. UC-11; AI-01, StudyPlanningAgent. |
| FR-12 | Regenerate a plan with preserved version history | Existing plan plus current facts yield a newly validated version without overwriting prior history. UC-12; AI-02, planning persistence. |
| FR-13 | Reject invalid AI-generated plans before persistence | Malformed or constraint-violating provider output must not change plan history or calendar. UC-13; Planning domain validation. |
| FR-14 | Display an account-specific academic dashboard | The owner sees projected workload, progress and upcoming tasks, with eventually consistent updates. UC-14; Planning projections, DashboardPushHub. |

## Non-functional requirements (historical NFR-01–NFR-06 identifiers retained)

| Requirement | Acceptance |
|---|---|
| NFR-01 | Clean JDK 21 build, Compose configuration and launcher that checks native failure/readiness |
| NFR-02 | Keys injected privately at runtime; no genuine secrets in repository/frontend/errors/events |
| NFR-03 | Structured error body; invalid input 400, unauthorized 401, absent owned resource 404, conflict 409, unavailable dependency/provider 503 |
| NFR-04 | Controller/application/domain/infrastructure responsibilities and independent database ownership |
| NFR-05 | Repeatable Java, frontend, launcher, SDK/HTTP and real-Kafka integration verification |
| NFR-06 | Ordered Postman examples with captured IDs and projection polling; malformed model cases through the controllable local fixture |

These acceptance criteria do not imply that every failure mode has been exhaustively tested. Automated fixture evidence and personally observed live provider behaviour must be identified separately; see [software completion audit](../compliance/software-completion-audit.md).

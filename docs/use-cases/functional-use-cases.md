# Functional use cases — final report alignment (14 cases)

**Authority:** Final Project Report, Chapter 2, Table 2.1 and Table 2.2 (CSCI318 Spring 2026). These are the team's *consolidated final user-goal use cases*, not 18 separate elementary API operations. Original 18-operation scenarios have been archived in [legacy examples](legacy-18-operation-examples.md) for historical testing reference. The [initial software specification](../specifications/initial-software-specification.md) also deliberately preserves the original FR-01–FR-18 proposal numbering. Do not apply those original IDs to the revised final requirement numbering.

The authenticated owner initiates user-facing cases, while system-driven Kafka and validation cases execute as consequences of user actions. Calls shown below are representative evidence locations, not complete HTTP request/response fixtures. The [Postman collection](../../postman/) and [REST catalogue](../architecture/contracts.md) contain executable and detailed contracts; the final report contains full use-case preconditions, alternate flows and UML activity diagrams.

| Use case | Consolidated goal | Final requirement | Related story |
|---|---|---|---|
| UC-01 | Import and Confirm Subject Outline | FR-01 | — |
| UC-02 | Manage Subjects | FR-02 | — |
| UC-03 | Manage Assessments | FR-03 | — |
| UC-04 | Record Completed Study Session | FR-04 | — |
| UC-05 | Retrieve Study History and Duration Summary | FR-05 | — |
| UC-06 | Manage Account, Authentication and Profile | FR-06 | — |
| UC-07 | Manage Calendar Entries and Availability | FR-07 | — |
| UC-08 | Schedule Spaced-Repetition Reviews | FR-08 | — |
| UC-09 | Update Real-Time Workload Projection | FR-09 | RT-01 |
| UC-10 | Update Real-Time Study-Progress Projection | FR-10 | RT-02 |
| UC-11 | Generate Seven-Day Study Plan | FR-11 | AI-01 |
| UC-12 | Regenerate Study Plan | FR-12 | AI-02 |
| UC-13 | Validate and Reject Invalid AI-Generated Plan | FR-13 | — |
| UC-14 | View Academic Dashboard | FR-14 | — |

## UC-01 — Import and Confirm Subject Outline

**User goal / requirement:** FR-01 — AI-assisted outline extraction and user confirmation. **Objectives:** OBJ-01.

**Preconditions:** Valid authenticated account and the relevant account-owned input/resources; configured dependencies where required.

**Main success flow:** A supported outline is uploaded, extraction reviewed and corrected if needed, and only confirmed academic records are persisted. The service returns the resulting record, state or query result to the originating workflow. **Acceptance:** A supported outline is uploaded, extraction reviewed and corrected if needed, and only confirmed academic records are persisted.

**Alternative / exception flow:** Reject invalid data or foreign-owned references without mutating unrelated accounts' records. Extraction errors and assessment-import failures are reported; confirmation remains reviewable or retryable where supported.

**Implementation and evidence:** Subject Service, Assessment import. **Representative contracts:** `POST /api/subject-outlines; GET /api/subject-outlines/{id}; POST /api/subject-outlines/{id}/confirm`. Consult the final report Chapter 2 (UC-01) for the authoritative detailed narrative and activity diagram.

## UC-02 — Manage Subjects

**User goal / requirement:** FR-02 — Create, view and update subjects including weekly study targets. **Objectives:** OBJ-01, OBJ-02.

**Preconditions:** Valid authenticated account and the relevant account-owned input/resources; configured dependencies where required.

**Main success flow:** Account-owned subject records can be created, retrieved, listed and updated. The service returns the resulting record, state or query result to the originating workflow. **Acceptance:** Account-owned subject records can be created, retrieved, listed and updated.

**Alternative / exception flow:** Reject invalid data or foreign-owned references without mutating unrelated accounts' records. Missing owned data returns an appropriate absent/empty result; unavailable dependencies are reported rather than silently succeeding.

**Implementation and evidence:** Subject Service. **Representative contracts:** `POST/GET /api/subjects; GET/PATCH /api/subjects/{id}`. Consult the final report Chapter 2 (UC-02) for the authoritative detailed narrative and activity diagram.

## UC-03 — Manage Assessments

**User goal / requirement:** FR-03 — Create, view and manage assessments, deadlines, workload and completion. **Objectives:** OBJ-01, OBJ-02, OBJ-03.

**Preconditions:** Valid authenticated account and the relevant account-owned input/resources; configured dependencies where required.

**Main success flow:** Owned assessments can be created/imported, filtered, edited and completed; invalid subject IDs are rejected. The service returns the resulting record, state or query result to the originating workflow. **Acceptance:** Owned assessments can be created/imported, filtered, edited and completed; invalid subject IDs are rejected.

**Alternative / exception flow:** Reject invalid data or foreign-owned references without mutating unrelated accounts' records. Missing owned data returns an appropriate absent/empty result; unavailable dependencies are reported rather than silently succeeding.

**Implementation and evidence:** Assessment Service, Subject verification. **Representative contracts:** `POST/GET /api/assessments; PATCH /api/assessments/{id}; POST /api/assessments/{id}/complete`. Consult the final report Chapter 2 (UC-03) for the authoritative detailed narrative and activity diagram.

## UC-04 — Record Completed Study Session

**User goal / requirement:** FR-04 — Record a completed study session. **Objectives:** OBJ-02, OBJ-04.

**Preconditions:** Valid authenticated account and the relevant account-owned input/resources; configured dependencies where required.

**Main success flow:** A valid subject-owned activity is stored and a study-session event is queued. The service returns the resulting record, state or query result to the originating workflow. **Acceptance:** A valid subject-owned activity is stored and a study-session event is queued.

**Alternative / exception flow:** Reject invalid data or foreign-owned references without mutating unrelated accounts' records. Missing owned data returns an appropriate absent/empty result; unavailable dependencies are reported rather than silently succeeding.

**Implementation and evidence:** Study Activity Service. **Representative contracts:** `POST /api/study-sessions`. Consult the final report Chapter 2 (UC-04) for the authoritative detailed narrative and activity diagram.

## UC-05 — Retrieve Study History and Duration Summary

**User goal / requirement:** FR-05 — Retrieve study history and study-duration summaries. **Objectives:** OBJ-02.

**Preconditions:** Valid authenticated account and the relevant account-owned input/resources; configured dependencies where required.

**Main success flow:** Account-scoped historical sessions and weekly/total duration are returned. The service returns the resulting record, state or query result to the originating workflow. **Acceptance:** Account-scoped historical sessions and weekly/total duration are returned.

**Alternative / exception flow:** Reject invalid data or foreign-owned references without mutating unrelated accounts' records. Missing owned data returns an appropriate absent/empty result; unavailable dependencies are reported rather than silently succeeding.

**Implementation and evidence:** Study Activity and Planning Services. **Representative contracts:** `GET /api/study-sessions?subjectId=...; GET /api/study-sessions/summary; GET /api/planning/progress/{subjectId}`. Consult the final report Chapter 2 (UC-05) for the authoritative detailed narrative and activity diagram.

## UC-06 — Manage Account, Authentication and Profile

**User goal / requirement:** FR-06 — Manage accounts, authentication and profile. **Objectives:** OBJ-05, OBJ-06.

**Preconditions:** For registration, valid new account details; for protected operations, a valid account session.

**Main success flow:** Registration and login establish sessions; profile requests enforce ownership. The service returns the resulting record, state or query result to the originating workflow. **Acceptance:** Registration and login establish sessions; profile requests enforce ownership.

**Alternative / exception flow:** Reject invalid data or foreign-owned references without mutating unrelated accounts' records. Missing owned data returns an appropriate absent/empty result; unavailable dependencies are reported rather than silently succeeding.

**Implementation and evidence:** Account Service. **Representative contracts:** `POST /api/auth/register; POST /api/auth/login; GET /api/auth/session; PATCH /api/profile`. Consult the final report Chapter 2 (UC-06) for the authoritative detailed narrative and activity diagram.

## UC-07 — Manage Calendar Entries and Availability

**User goal / requirement:** FR-07 — Manage calendar entries and planning availability. **Objectives:** OBJ-04, OBJ-05.

**Preconditions:** Valid authenticated account and the relevant account-owned input/resources; configured dependencies where required.

**Main success flow:** Account-owned calendar entries are maintained and validated availability is provided to planning. The service returns the resulting record, state or query result to the originating workflow. **Acceptance:** Account-owned calendar entries are maintained and validated availability is provided to planning.

**Alternative / exception flow:** Reject invalid data or foreign-owned references without mutating unrelated accounts' records. Missing owned data returns an appropriate absent/empty result; unavailable dependencies are reported rather than silently succeeding.

**Implementation and evidence:** Planning Service. **Representative contracts:** `GET/POST /api/calendar; PATCH /api/calendar/{id}; planning availability input`. Consult the final report Chapter 2 (UC-07) for the authoritative detailed narrative and activity diagram.

## UC-08 — Schedule Spaced-Repetition Reviews

**User goal / requirement:** FR-08 — Schedule spaced-repetition reviews. **Objectives:** OBJ-04.

**Preconditions:** Valid authenticated account and the relevant account-owned input/resources; configured dependencies where required.

**Main success flow:** Completing an eligible calendar review adds the next applicable interval from 1/3/7/14/30 days. The service returns the resulting record, state or query result to the originating workflow. **Acceptance:** Completing an eligible calendar review adds the next applicable interval from 1/3/7/14/30 days.

**Alternative / exception flow:** Reject invalid data or foreign-owned references without mutating unrelated accounts' records. Missing owned data returns an appropriate absent/empty result; unavailable dependencies are reported rather than silently succeeding.

**Implementation and evidence:** Planning calendar application. **Representative contracts:** `POST /api/calendar/{id}/complete`. Consult the final report Chapter 2 (UC-08) for the authoritative detailed narrative and activity diagram.

## UC-09 — Update Real-Time Workload Projection

**User goal / requirement:** FR-09 — Maintain real-time assessment workload via Kafka Streams. **Objectives:** OBJ-02, OBJ-06.

**Preconditions:** An account-owned source change has been committed and the corresponding versioned event delivered to Kafka.

**Main success flow:** Committed assessment changes update keyed workload state without duplicate/stale inflation. The service returns the resulting record, state or query result to the originating workflow. **Acceptance:** Committed assessment changes update keyed workload state without duplicate/stale inflation.

**Alternative / exception flow:** Reject invalid data or foreign-owned references without mutating unrelated accounts' records. Events may be processed asynchronously; stale/duplicate revisions are ignored.

**Implementation and evidence:** RT-01, PlanningStreamTopology.workload. **Representative contracts:** `assessment-events; Planning workload topology; GET /api/planning/workload`. Consult the final report Chapter 2 (UC-09) for the authoritative detailed narrative and activity diagram.

## UC-10 — Update Real-Time Study-Progress Projection

**User goal / requirement:** FR-10 — Maintain real-time study-progress via Kafka Streams. **Objectives:** OBJ-02, OBJ-06.

**Preconditions:** An account-owned source change has been committed and the corresponding versioned event delivered to Kafka.

**Main success flow:** Accepted study activity, target and calendar-completion facts update subject/week progress. The service returns the resulting record, state or query result to the originating workflow. **Acceptance:** Accepted study activity, target and calendar-completion facts update subject/week progress.

**Alternative / exception flow:** Reject invalid data or foreign-owned references without mutating unrelated accounts' records. Events may be processed asynchronously; stale/duplicate revisions are ignored.

**Implementation and evidence:** RT-02, PlanningStreamTopology.progress. **Representative contracts:** `study-activity, subject-target and planning events; Planning progress queries`. Consult the final report Chapter 2 (UC-10) for the authoritative detailed narrative and activity diagram.

## UC-11 — Generate Seven-Day Study Plan

**User goal / requirement:** FR-11 — Generate a personalised seven-day plan via agentic AI. **Objectives:** OBJ-03, OBJ-04.

**Preconditions:** Valid authenticated account and the relevant account-owned input/resources; configured dependencies where required.

**Main success flow:** Current academic facts and availability inform a validated seven-day plan; accepted plan is saved. The service returns the resulting record, state or query result to the originating workflow. **Acceptance:** Current academic facts and availability inform a validated seven-day plan; accepted plan is saved.

**Alternative / exception flow:** Reject invalid data or foreign-owned references without mutating unrelated accounts' records. Unavailable providers or invalid proposal output must not produce an invalid persisted plan.

**Implementation and evidence:** AI-01, StudyPlanningAgent. **Representative contracts:** `POST /api/planning/plans; LangChain4j tool calls; plan/version persistence`. Consult the final report Chapter 2 (UC-11) for the authoritative detailed narrative and activity diagram.

## UC-12 — Regenerate Study Plan

**User goal / requirement:** FR-12 — Regenerate a plan with preserved version history. **Objectives:** OBJ-03, OBJ-04, OBJ-06.

**Preconditions:** Valid authenticated account and the relevant account-owned input/resources; configured dependencies where required.

**Main success flow:** Existing plan plus current facts yield a newly validated version without overwriting prior history. The service returns the resulting record, state or query result to the originating workflow. **Acceptance:** Existing plan plus current facts yield a newly validated version without overwriting prior history.

**Alternative / exception flow:** Reject invalid data or foreign-owned references without mutating unrelated accounts' records. Unavailable providers or invalid proposal output must not produce an invalid persisted plan.

**Implementation and evidence:** AI-02, planning persistence. **Representative contracts:** `POST /api/planning/plans/{id}/regenerate; GET /api/planning/plans`. Consult the final report Chapter 2 (UC-12) for the authoritative detailed narrative and activity diagram.

## UC-13 — Validate and Reject Invalid AI-Generated Plan

**User goal / requirement:** FR-13 — Reject invalid AI-generated plans before persistence. **Objectives:** OBJ-03, OBJ-06.

**Preconditions:** An AI plan proposal has been received for validation.

**Main success flow:** Malformed or constraint-violating provider output must not change plan history or calendar. The service returns the resulting record, state or query result to the originating workflow. **Acceptance:** Malformed or constraint-violating provider output must not change plan history or calendar.

**Alternative / exception flow:** Reject invalid data or foreign-owned references without mutating unrelated accounts' records. Unavailable providers or invalid proposal output must not produce an invalid persisted plan.

**Implementation and evidence:** Planning domain validation. **Representative contracts:** `AgenticPlanningAdvisor; DeadlineScheduler; validation/transaction tests`. Consult the final report Chapter 2 (UC-13) for the authoritative detailed narrative and activity diagram.

## UC-14 — View Academic Dashboard

**User goal / requirement:** FR-14 — Display an account-specific academic dashboard. **Objectives:** OBJ-02, OBJ-05.

**Preconditions:** Valid authenticated account and the relevant account-owned input/resources; configured dependencies where required.

**Main success flow:** The owner sees projected workload, progress and upcoming tasks, with eventually consistent updates. The service returns the resulting record, state or query result to the originating workflow. **Acceptance:** The owner sees projected workload, progress and upcoming tasks, with eventually consistent updates.

**Alternative / exception flow:** Reject invalid data or foreign-owned references without mutating unrelated accounts' records. Missing owned data returns an appropriate absent/empty result; unavailable dependencies are reported rather than silently succeeding.

**Implementation and evidence:** Planning projections, DashboardPushHub. **Representative contracts:** `Planning dashboard REST/SSE, GET /api/planning/dashboard/stream`. Consult the final report Chapter 2 (UC-14) for the authoritative detailed narrative and activity diagram.

Current student workflow verification uses the same final 14 requirement/use-case IDs. See [fresh workflow evidence](../testing/student-workflow-evidence.md) for actual inputs, outputs, automated coverage and limits. Historical operation examples and past test results are unchanged.

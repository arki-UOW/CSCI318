# Study Leftovers — Initial Software Specification

**Original project proposal · CSCI318 Spring 2026**

> Historical baseline transcribed from the team's original 14-page PDF. This document preserves the original proposed scope and terminology; for implemented functionality see [Final Implemented Software Specification](final-implemented-software-specification.md).

CSCI318 Software Engineering Practices & Principles
GROUP PROJECT PROPOSAL

Study Leftovers
An AI-Powered, Event-Driven Study and Assessment Planning System

University of Wollongong
School of Computing and Information Technology
Spring 2026
Prepared for Group Project Planning and Implementation

## 1. Executive Summary
Study Leftovers is a cloud-native prototype designed to help university students manage subjects,
assessments, deadlines, study activity, and weekly study planning. The system combines four bounded
contexts implemented as Spring Boot microservices with REST APIs, Apache Kafka event communication,
stream processing, and a LangChain4j-based planning agent.
The product scope is intentionally small. The aim is not to create a commercial student platform, but to
demonstrate the CSCI318 software-engineering concepts exceptionally clearly, reliably, and with strong
traceability between business objectives, requirements, domain models, implementation, tests, and
demonstration evidence.
The implementation will be completed by the end of Week 8. Weeks 9 and 10 are reserved for the report,
presentation, video, README, and supporting evidence. Week 11 is a full submission rehearsal and technical
audit. Week 12 is reserved for final submission and presentation.

## 2. Project Goal
The goal is to design and implement an AI-powered cloud-native application within a realistic university-study
domain using AI-assisted software engineering practices. The application will demonstrate:

- Domain-Driven Design and bounded contexts.
- Microservices and layered architecture.
- Independent service data ownership.
- REST-based and asynchronous communication.
- Apache Kafka messaging and event-driven architecture.
- Real-time analysis using stream-processing techniques.
- LLM-based agentic AI through LangChain4j.
- Consistent validation, exception handling, testing, and documentation.
- Human-AI collaborative development with accepted, modified, and rejected proposals recorded.
Specification basis: The CSCI318 Spring 2026 Group Project Specification requires at least four bounded
contexts implemented as microservices, event-driven architecture, real-time stream processing, agentic AI,
Spring Boot, Spring Web, Spring Data JPA, H2, Spring Cloud Stream, Apache Kafka, LangChain4j, and JDK 21.

## 3. Business Problem and Value Proposition
University students commonly manage assessments and study activity across separate subject pages,
calendars, notes, and task lists. This fragmentation makes it difficult to understand combined workload,
monitor actual progress, and adapt a plan when deadlines or availability change.
Study Leftovers provides one prototype through which a student can register subjects, manage assessments,
record study sessions, observe continuously updated workload and progress summaries, and ask an AI agent
to produce or revise a weekly plan using current application data.

### 3.1 Primary Stakeholders

| Stakeholder | Needs and Expectations |
|---|---|
| Student | Simple subject and assessment management; accurate workload visibility; study-progress tracking; practical weekly plans; clear feedback when information changes. |
| Project Team | A manageable implementation; strong rubric coverage; repeatable integration; clear task ownership; reusable evidence for the final report and presentation. |
| Teaching Team | A coherent architecture; inspectable source code; reproducible setup; clear evidence of DDD, microservices, Kafka, stream processing, agentic AI, testing, and AI-assisted development. |

## 4. Project Objectives
1. Provide subject management and weekly study-target information.
2. Provide assessment creation, updating, deadline changes, workload estimates, priority, and completion.
3. Record study sessions and calculate study duration by subject.
4. Update workload information when assessment events occur.
5. Update study-progress information when study-session events occur.
6. Generate a seven-day study plan using current application data.
7. Regenerate a plan after deadlines, status, progress, or availability change.
8. Demonstrate four justified bounded contexts with independent data ownership.
9. Apply DDD patterns intentionally rather than as labels only.
10. Demonstrate meaningful event-driven communication and stateful stream processing.
11. Demonstrate genuine tool-using agentic AI rather than a generic chatbot prompt.
12. Maintain requirement traceability and strong automated and manual testing evidence.

## 5. Scope
### 5.1 Included Scope
- Subject creation, retrieval, listing, and study-target updates.
- Assessment creation, retrieval, listing, deadline changes, workload updates, priority updates, and
completion.
- Study-session recording and subject-based summaries.
- Real-time workload and study-progress projections.
- AI-assisted plan generation and regeneration.
- Four Spring Boot microservices with H2 persistence.
- REST APIs, Kafka events, Spring Cloud Stream, and LangChain4j.
- Postman demonstration collection, automated tests, validation, and error handling.
- Running instructions, seed data, traceability, and AI decision records.

### 5.2 Explicitly Excluded Scope
- Authentication and role management.
- Production frontend or mobile application.
- Moodle, university, email, SMS, or calendar integration.
- Cloud deployment, Kubernetes, and production monitoring.
- Vector databases, model training, and multiple autonomous agents.
- Complex mathematical optimisation or commercial-scale scheduling.
- Shared databases between microservices.
- Features that do not directly strengthen a required architectural, testing, or reporting outcome.

## 6. Proposed Architecture
Study Leftovers will contain four bounded contexts. Each context will be implemented as an independently
running Spring Boot microservice with its own H2 database. No service will read another service’s database
directly. Assessment Service will synchronously verify subject existence through the Subject Service REST API
before creating an assessment. Planning Service will retrieve a subject’s weekly study target through the
Subject Service REST API when updating the subject progress projection.
**Proposed integration topology (historical design):**

```text
Subject Service
   | REST subject verification
   v
Assessment Service
   | assessment-events (Kafka)
   v
Planning Service <---- study-activity-events ---- Study Activity Service
   | LangChain4j tool-calling integration
   v
LLM Provider
```

| Microservice | Primary responsibility | Owned data |
|---|---|---|
| Subject Service | Manage subjects and weekly study targets. | Subjects, subject codes, names, credit points, weekly targets. |
| Assessment Service | Manage assessed tasks and publish assessment events. | Assessments, deadlines, estimated workload, priority, status. |
| Study Activity Service | Record study sessions and publish activity events. | Study sessions, dates, durations, descriptions. |
| Planning Service | Consume events, maintain projections, and provide AI planning. | Workload projections, progress projections, study plans and plan versions. |

Subject verification behaviour: if the referenced subject exists, Assessment Service continues creation; if it
does not exist, the request is rejected with a clear client error; if Subject Service is unavailable, Assessment
Service returns a clear service-unavailable response rather than creating an unverified assessment.

## 7. Bounded Contexts and Microservices
### 7.1 Subject Service

Responsibilities
- Create, retrieve, list, and update subjects.
- Update weekly target study hours.
- Validate subject code, credit points, and study target.

Principal Domain Objects
- Subject
- SubjectId
- SubjectCode

- CreditPoints
- WeeklyStudyTarget

Proposed REST Endpoints
    POST /api/subjects
    GET /api/subjects
    GET /api/subjects/{subjectId}
    PUT /api/subjects/{subjectId}
    PATCH /api/subjects/{subjectId}/study-target

### 7.2 Assessment Service

Responsibilities
- Create, retrieve, list, and update assessments.
- Change deadline, estimated workload, and priority.
- Mark an assessment complete.
- Publish assessment domain events.

Principal Domain Objects
- Assessment
- AssessmentId
- Deadline
- EstimatedWorkload
- AssessmentPriority
- AssessmentStatus

Proposed REST Endpoints
    POST /api/assessments
    GET /api/assessments
    GET /api/assessments/{assessmentId}
    GET /api/assessments/incomplete
    PUT /api/assessments/{assessmentId}
    PATCH /api/assessments/{assessmentId}/deadline
    PATCH /api/assessments/{assessmentId}/workload
    PATCH /api/assessments/{assessmentId}/priority
    PATCH /api/assessments/{assessmentId}/complete

### 7.3 Study Activity Service

Responsibilities
- Record study sessions.
- Retrieve all sessions or sessions by subject.
- Calculate total and weekly study duration.
- Publish StudySessionRecorded events.

Principal Domain Objects
- StudySession
- StudySessionId
- StudyDuration
- StudyDate
- ActivityDescription

Proposed REST Endpoints
    POST /api/study-sessions
    GET /api/study-sessions
    GET /api/study-sessions/{sessionId}
    GET /api/study-sessions/subject/{subjectId}
    GET /api/study-sessions/summary/{subjectId}

### 7.4 Planning Service

Responsibilities
- Consume assessment and study-session events.
- Maintain workload and progress projections.
- Expose real-time query endpoints.
- Generate and regenerate study plans through a LangChain4j agent.
- Validate and store AI output.

Principal Domain Objects
- WorkloadSummary
- StudyProgressSummary
- StudyPlan
- StudyPlanItem
- PlanningPeriod
- PlanningRecommendation

Proposed REST Endpoints
    GET /api/planning/workload
    GET /api/planning/progress
    GET /api/planning/progress/{subjectId}
    POST /api/planning/generate
    POST /api/planning/regenerate
    GET /api/planning/plans
    GET /api/planning/plans/{planId}

## 8. Layered Architecture

| Layer | Responsibilities | Typical packages |
|---|---|---|
| Presentation | REST controllers, request/response DTOs, HTTP codes, request validation, exception mapping. | controller, dto, exception |
| Application | Coordinate use cases, load and save aggregates, invoke domain behaviour, define transactions, call ports, publish events. | application |
| Domain | Entities, value objects, aggregates, domain events, domain services, invariants, domain exceptions. | domain |
| Infrastructure | JPA adapters, Kafka bindings, REST clients, LLM adapters, persistence mapping, configuration. | infrastructure, config |

## 9. Functional Requirements

| ID | Requirement |
|---|---|
| FR-01 | Create a subject. |
| FR-02 | Retrieve a subject. |
| FR-03 | List all registered subjects. |
| FR-04 | Update subject information and weekly study targets. |
| FR-05 | Create an assessment associated with a subject. |
| FR-06 | Display all assessments. |
| FR-07 | Display incomplete assessments. |
| FR-08 | Change an assessment deadline. |
| FR-09 | Update estimated assessment workload. |
| FR-10 | Mark an assessment as completed. |
| FR-11 | Record a completed study session. |
| FR-12 | Display study sessions for a selected subject. |
| FR-13 | Calculate total and weekly study duration for each subject. |
| FR-14 | Update assessment workload information from assessment events. |
| FR-15 | Update study-progress information from study-session events. |
| FR-16 | Generate a seven-day study plan using current data. |
| FR-17 | Regenerate an existing study plan after relevant changes. |
| FR-18 | Reject an AI-generated plan that references invalid or completed assessments, exceeds daily availability, contains invalid dates, or violates required plan constraints. |

### 9.1 Non-Functional Requirements

| ID | Non-functional requirement |
|---|---|
| NFR-01 | The complete local system shall be reproducible by following the README and supplied configuration files. |
| NFR-02 | No LLM API key, password, or other secret shall be committed to the repository. |
| NFR-03 | All microservices shall use a consistent structured error-response format. |
| NFR-04 | The codebase shall use a consistent layered package structure and shared naming conventions. |
| NFR-05 | Core domain behaviour, REST endpoints, event flows, stream projections, and AI-plan validation shall be supported by repeatable tests. |
| NFR-06 | The complete Postman demonstration shall be repeatable using deterministic sample data and documented startup steps. |

## 10. Real-Time Stream-Processing Requirements
The real-time analysis will be implemented using Spring Cloud Stream with the Kafka Streams binder. This
provides explicit stream-processing topologies, keyed state, aggregation, and materialised queryable
projections rather than treating ordinary Kafka consumers as stream processing.

### 10.1 RT-01: Assessment Workload
When an assessment is created, updated, rescheduled, or completed, the Planning Service will update a
current workload projection. The projection will include total incomplete assessments, assessments due
within seven days, estimated workload hours within seven days, high-priority count, and an overall workload
classification.
Assessment event stream
→ validate event
→ key by assessment ID
→ update current assessment projection
→ filter incomplete assessments
→ aggregate counts and estimated workload
→ materialise current workload summary
→ expose through GET /api/planning/workload

### 10.2 RT-02: Study Progress
When a study session is recorded, the Planning Service will update study progress by subject. The projection
will include total and weekly study minutes, weekly target, remaining target, and a progress status such as
NO_ACTIVITY, BEHIND_TARGET, ON_TRACK, or TARGET_REACHED. The Planning Service will obtain the
weekly study target from Subject Service through REST and cache the value with the progress projection.
StudySessionRecorded stream
→ validate event
→ key by subject ID
→ aggregate study duration
→ calculate weekly total
→ compare with subject target
→ materialise current progress summary
→ expose through planning query endpoints

## 11. Event-Driven Architecture
Kafka will be used for asynchronous reactions to completed business events. The system will avoid
unnecessary topics and production-grade messaging infrastructure that is not required for the prototype.
              Topic                                                     Events
assessment-events               AssessmentCreated, AssessmentUpdated, AssessmentDeadlineChanged,
AssessmentCompleted
study-activity-events           StudySessionRecorded
planning-events                 Optional planning-domain events only where they provide clear value.

### 11.1 Event Envelope
    {
"eventId": "550e8400-e29b-41d4-a716-446655440000",
"eventType": "AssessmentCreated",
"eventVersion": 1,
"eventTimestamp": "2026-08-20T10:30:00Z",
"sourceService": "assessment-service",
"payload": {
"assessmentId": 10,

"subjectId": 2,
"dueDate": "2026-08-27",
"estimatedHours": 5,
"priority": "HIGH",
"status": "INCOMPLETE"
}
}

Assessment events will normally be keyed by assessment ID. Study-session events may be keyed by subject
ID for subject-level aggregation. Events will describe something that has already occurred and will include
event ID, type, version, timestamp, source, and required business payload.

## 12. Agentic AI Design
One LangChain4j planning agent will support two user stories. The component will be genuinely agentic
because it will receive a goal, select approved application tools, retrieve current data, produce a structured
action, submit the result for validation, and save a valid study plan.

### 12.1 Approved Agent Tools
getIncompleteAssessments()
getUpcomingAssessments()
getCurrentWorkload()
getStudyProgress(subjectId)
getExistingStudyPlan(planId)
saveStudyPlan(plan)

### 12.2 AI-01: Generate Study Plan
- Retrieve incomplete assessments, deadlines, estimated workload, workload status, and progress.
- Respect the requested planning period and available daily minutes.
- Avoid completed assessments.
- Produce a structured seven-day plan.
- Validate and store the plan.

### 12.3 AI-02: Regenerate Study Plan
- Retrieve the existing plan and updated application data.
- Identify obsolete, completed, or rescheduled tasks.
- Produce a revised plan and a concise explanation of material changes.
- Save a new plan version after validation.

### 12.4 Plan Validation and Failure Handling
- All referenced assessments must exist.
- Completed assessments cannot be scheduled.
- Dates must fall inside the requested planning period.
- Daily allocated time cannot exceed availability.
- Durations must be positive.
- Missing API keys, provider failures, timeouts, invalid structured output, and constraint violations must
return clear errors without storing an invalid plan.

## 13. Domain-Driven Design

| DDD pattern | Proposed examples | Purpose |
|---|---|---|
| Entities | Subject, Assessment, StudySession, StudyPlan | Objects with continuing identity and lifecycle. |
| Value Objects | SubjectCode, Deadline, EstimatedWorkload, StudyDuration, PlanningPeriod | Immutable concepts defined by value and responsible for their invariants. |
| Aggregate Roots | Subject, Assessment, StudySession, StudyPlan | Consistency boundaries modified through business methods. |
| Domain Events | AssessmentCreated, AssessmentCompleted, StudySessionRecorded | Records of meaningful completed business changes. |
| Domain Services | WorkloadClassificationService, where required | Behaviour spanning values that does not naturally belong to one entity. |

Business state changes will be expressed through domain methods such as `assessment.changeDeadline(...)`, `assessment.changeEstimatedWorkload(...)`, and `assessment.markCompleted()`, rather than unrestricted setters. DDD terminology will only be used where the implementation provides the corresponding responsibility.

## 14. Technical and Repository Conventions
                Item                                                      Decision
Java                              JDK 21
Mandatory stack                   Spring Boot, Spring Web, Spring Data JPA, H2, Spring Cloud Stream, Apache Kafka,
LangChain4j
Stream processing                 Spring Cloud Stream with the Kafka Streams binder
Subject target access             Planning Service retrieves weekly study targets from Subject Service through REST and
caches them in progress projections
Database ownership                One H2 database per microservice
Database IDs                      Long
Event IDs                         UUID
Dates and timestamps              ISO dates; ISO-8601 timestamps; UTC for event timestamps where practical
Study duration                    Integer minutes
Ports                             Subject 8081; Assessment 8082; Study Activity 8083; Planning 8084
Secrets                           Environment variables; .env.example committed; real keys excluded
Demonstration client              Postman collection and environment

### 14.1 Repository Structure
study-leftovers/
├── subject-service/
├── assessment-service/
├── study-activity-service/
├── planning-service/
├── docs/
    │   ├── requirements/
    │   ├── use-cases/
    │   ├── domain-model/
    │   ├── architecture/
    │   ├── decisions/

    │   ├── ai-prompts/
    │   ├── ai-decisions/
    │   ├── testing/
    │   ├── meeting-minutes/
    │   └── evidence/
├── postman/
├── scripts/
├── docker-compose.yml
├── .env.example
├── .gitignore
└── README.md

## 15. AI-Assisted Development Process
Generative AI will be used extensively for requirements proposals, domain modelling, architectural
alternatives, API and event design, code generation, test generation, debugging, refactoring, and repository
documentation. The project report and presentation will remain student-authored and will reflect the team’s
own analysis, decisions, justifications, reflections, and lessons learned.

### 15.1 AI Decision Record Template
Date:
Team member:
Development task:
Prompt or instruction:
AI proposal:
Team decision: Accepted / Modified / Rejected
Reason:
Related files and commit:
Observed result:

The repository will preserve representative examples of accepted, modified, and rejected AI proposals.
Examples may include rejecting authentication or a fifth service, modifying an overcomplicated Kafka
topology, accepting a value-object design, or rejecting an AI-generated plan that violates daily availability.

## 16. Testing Strategy

| Test level | Representative tests |
|---|---|
| Domain unit tests | Reject invalid subject codes, negative workload, zero-duration sessions, duplicate completion, invalid planning periods. |
| Application tests | Create assessment, save aggregate, publish event, record session, store validated plan. |
| Controller/API tests | Correct status codes and DTOs; 400 for invalid input; 404 for missing resources. |
| Event tests | Correct event type and fields; valid consumption; malformed events handled without service crash. |
| Stream tests | No duplicate assessment count after updates; completion reduces workload; sessions aggregate separately by subject. |
| Agentic AI tests | Completed work excluded; availability respected; dates valid; invalid LLM output rejected; regeneration changes outdated plan. |
| End-to-end tests | Normal plan generation; deadline change and regeneration; assessment completion and workload reduction. |

## 17. Requirement Traceability
A traceability matrix will be maintained throughout development. Each important capability will be connected
through the following chain:
Business objective
→ Functional requirement
→ User story or use case
→ Bounded context
→ Domain object
→ REST endpoint or event
→ Source-code component
→ Test
→ Demonstration evidence

       Traceability Element                                               Example
Objective                         Help students identify concentrated workload.
Requirement                       FR-14: update workload from assessment events.
User Story                        RT-01: real-time assessment workload.
Bounded Context                   Planning.
Input                             Assessment events.
Domain Object                     WorkloadSummary.
Endpoint                          GET /api/planning/workload.
Test                              Completing an assessment reduces outstanding workload.
Evidence                          Postman output, test result, and event-consumer log.

## 18. Development Methodology and Team Roles
The team will use a lightweight Agile process with one-week iterations, a formal weekly meeting, assigned
owners, small pull requests, peer review, integrated demonstrations, updated action items, and continuing
traceability. Unnecessary Scrum overhead will be avoided.
              Role                                              Primary Responsibilities
Architecture and Integration   Repository conventions, service contracts, architecture, Kafka contracts, Planning Service
Lead                           integration, system testing, technical consistency.
Subject and Assessment Lead    Subject Service, Assessment Service, domain modelling, assessment events, related tests
and evidence.
Study Activity and AI Lead     Study Activity Service, LangChain4j workflows, AI validation, related tests and evidence.
All Members                    Weekly meetings, pull-request review, full-system operation, AI records, student-authored
report sections, demonstration, contribution sign-off.

## 19. Milestones and Hard Deadlines
 Week            Milestone                                 Required Deliverables                                     Hard Gate
Week 2      Proposal, Scope and      Final proposal; business problem and stakeholders; scope and             No major service
            Architecture             exclusions; 18 functional requirements and 6 non-functional              implementation begins
                                     requirements; four bounded contexts; architecture and context            until boundaries and
                                     map; data ownership; API contracts; event catalogue; stream              contracts are agreed.
definitions; agent tools; private repository; team allocation; initial
traceability.
Week 3      Service Foundations      Four Spring Boot projects; JDK 21; mandatory dependencies;               All four services start
            and Domain Model         layered packages; H2; error format; health endpoints; complete           and Subject Service is
                                     Subject Service; core Assessment aggregate; initial domain               fully demonstrable.
tests; local Kafka environment.
Week 4      Core REST                Complete Assessment and Study Activity services; Planning                The application works
            Application              foundations; validation; error handling; unit and API tests;             through REST without
                                     Postman collection; updated domain and architecture diagrams.            Kafka or AI.
Week 5      Event-Driven             Kafka and Spring Cloud Stream configuration; assessment and              The team
            Integration              study-session producers; Planning consumers; versioned                   demonstrates events
                                     schemas; event IDs and timestamps; producer/consumer tests;              moving between
                                     event demonstration.                                                     services.
Week 6      Stream Processing        Workload and study-progress topologies; stateful projections;            Both real-time stories
                                     query endpoints; update/completion/aggregation tests; saved              work from incoming
                                     evidence; updated diagrams.                                              events without manual
summary changes.
Week 7      Agentic AI               LLM configuration; LangChain4j; tools; generation and                    Changing assessment
                                     regeneration; structured output; validation; failure handling; AI        data produces a
                                     tests; decision records.                                                 meaningfully revised
validated plan.
Week 8      Full Integration and     Three end-to-end scenarios; final Postman collection; cleanup;           No new features after
            Development Freeze       secret audit; .env.example; repeatable startup; final diagrams;          Week 8.
complete technical evidence and traceability.
Week 9      Report Evidence and      Final requirements, use cases, UML activity diagrams, domain             Every required report
            Complete Draft           model, architecture, layers, ownership, code evidence, testing           section contains
                                     evidence, streaming evidence, AI evidence, complete first report         substantive content.
draft.
Week 10     Final Report and         Final report, references, README, sample inputs/outputs, slides,         Only proofreading and
            Presentation             video, speaking roles, demo script, contribution descriptions.           minor corrections
            Artefacts                                                                                         remain.
Week 11     Final Audit and          Clean-environment startup, README test, full Postman demo,               All artefacts are ready
            Submission               test execution, compliance audit, signatures, ZIP/PDF/video              for immediate
            Rehearsal                checks, backups and rehearsal.                                           submission.
Week 12     Submission and           Upload and verify report PDF, repository ZIP, and required media;        No non-essential last-
            Presentation             preserve backups; deliver presentation.                                  minute development.

## 20. Key Risks and Mitigation
                 Risk                                                              Mitigation
AI generates inconsistent services         Freeze shared contracts, package conventions, versions, IDs, dates, ports, and event
fields before generation.
Kafka configuration consumes too           Test Kafka in Week 3; keep topics minimal; avoid advanced production messaging
much time                                  features.
Services work separately but not           Integrate every week and maintain end-to-end demonstration scenarios.
together

                 Risk                                                       Mitigation
Assessment updates are double-       Key projection state by assessment ID and test create, update, deadline change, and
counted                              completion.
AI produces invalid plans            Use structured DTO output and validate all plan constraints before persistence.
LLM is unavailable during demo       Provide clear failure handling and preserve backup screenshots/video and
deterministic sample evidence.
Scope expands                        Apply the rule: no feature unless it directly strengthens a required architecture, test,
or report item.
Report conflicts with code           Update diagrams and traceability after implementation changes; final audit in Week
11.
Secrets are committed                Use environment variables, .gitignore, .env.example, and a Week 8/11 secret audit.

## 21. Definition of Completion and Quality Target
The project is complete only when all four services run, each service has independent persistence, REST
endpoints work, Kafka events are published and consumed, both real-time projections update automatically,
both AI workflows work through application tools, invalid AI output is rejected, core tests pass, end-to-end
scenarios pass, Postman and README are reproducible, no secrets are exposed, and the report evidence
accurately reflects the implementation.
The quality target is not a large feature set. It is the smallest system that demonstrates every mandatory
concept exceptionally clearly. Requirements, diagrams, domain models, APIs, events, code, tests, and
presentation evidence must describe the same coherent system.

## 22. Source Note
This proposal was developed from the CSCI318 Software Engineering Practices & Principles Spring 2026
Group Project Specification provided by the user. Exact submission deadlines remain subject to Moodle
announcements and any later presentation guidelines issued by the subject teaching team.


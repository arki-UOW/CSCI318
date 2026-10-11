# Implemented domain model

This repository artefact describes the implemented prototype. It supports, but does not replace, the team's own assessed analysis. UUID links across contexts are logical references, not cross-database JPA relationships.

```mermaid
classDiagram
namespace SubjectContext {
  class Subject {
    UUID id
    UUID ownerId
    String code
    String name
    Integer creditPoints
    int weeklyStudyTargetMinutes
    changeDetails()
    changeWeeklyStudyTarget()
  }
  class SubjectOutlineImport {
    UUID id
    UUID subjectId
    String candidateJson
    Status status
    beginConfirmation()
    confirm()
  }
}
namespace AssessmentContext {
  class Assessment {
    UUID id
    UUID subjectId
    UUID ownerId
    LocalDate dueDate
    Integer estimatedMinutes
    Priority priority
    Status status
    changeDeadline()
    changeEstimatedWorkload()
    markCompleted()
  }
}
namespace StudyActivityContext {
  class StudySession {
    UUID id
    UUID subjectId
    UUID ownerId
    LocalDate studyDate
    int durationMinutes
    edit()
  }
}
namespace PlanningContext {
  class StudyPlan {
    UUID id
    UUID ownerId
    int version
    LocalDate startDate
    LocalDate endDate
    String itemsJson
  }
  class PlanItem {
    LocalDate date
    UUID subjectId
    UUID assessmentId
    int allocatedMinutes
    int repetitionStage
  }
  class CalendarEntry {
    UUID id
    UUID planId
    UUID assessmentId
    LocalDateTime startAt
    LocalDateTime endAt
    EntryStatus status
    edit()
    complete()
  }
  class WeeklyAvailability {
    Map minutes
  }
  class WeeklyTimeSlots {
    Map days
    free()
  }
  class DeadlineScheduler {
    schedule()
  }
  class WorkloadState {
    UUID ownerId
    Map assessments
    long revision
    apply()
  }
  class ProgressState {
    UUID ownerId
    UUID subjectId
    int totalMinutes
    Map weeklyMinutes
    apply()
  }
}
namespace AccountContext {
  class Account {
    UUID id
    String username
    String passwordHash
    updateProfile()
    changePasswordHash()
  }
  class AccountSession {
    UUID accountId
    String tokenHash
    Instant expiresAt
  }
}
Account "1" -- "0..*" AccountSession : local ownership
SubjectOutlineImport "0..*" --> "0..1" Subject : confirmed local reference
Subject "1" <.. "0..*" Assessment : subjectId via REST
Subject "1" <.. "0..*" StudySession : subjectId via REST
StudyPlan "1" *-- "0..*" PlanItem : stored JSON values
StudyPlan "0..1" <-- "0..*" CalendarEntry : planId
Assessment "1" <.. "0..*" PlanItem : validated ID
DeadlineScheduler ..> WeeklyAvailability
DeadlineScheduler ..> PlanItem
CalendarEntry ..> WeeklyTimeSlots : placement constraints
Assessment ..> WorkloadState : versioned facts
StudySession ..> ProgressState : versioned facts
Subject ..> ProgressState : target facts
CalendarEntry ..> ProgressState : completion facts
```

## Pattern responsibilities and ownership

| Pattern | Concrete implementation | Responsibility |
|---|---|---|
| Entity / aggregate root | Subject, Assessment, StudySession, StudyPlan, CalendarEntry, Account | Stable UUID identity; own persistence/lifecycle boundary; domain methods guard state changes |
| Value object | WeeklyAvailability, WeeklyTimeSlots.Window/Interval, PlanItem | Values describe availability and allocations; availability constructors validate invariants; submitted PlanItem values are validated at the application boundary |
| Domain service | DeadlineScheduler, WorkloadClassification | Scheduling and progress/workload policies separated from HTTP/LLM transport |
| Domain/integration event | SubjectCreated/TargetChanged, AssessmentCreated/Updated/Completed, StudySessionRecorded, StudyBlockCompleted | Committed facts with owner, aggregate ID and revision; dispatched through a transactional outbox |
| Read model | WorkloadState, ProgressState, ProjectionDocument | Derived state owned by Planning; not the authoritative academic aggregates |
| Infrastructure coordination | planning_owner_locks, unique owner/version index | Serialize plan/calendar commits and protect version uniqueness; not a business bounded context |

Each microservice owns its H2 database. `messaging-support` supplies technical outbox tables in each producer's database. It does not share a database or academic entities. PlanItem is stored as JSON within StudyPlan, while CalendarEntry has its own editable lifecycle. Owner IDs refer to Account through authentication; domain services never read the Account database.

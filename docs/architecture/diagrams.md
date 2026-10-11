# Architecture and layer diagrams

```mermaid
flowchart LR
  UI[Browser or Postman] --> Account[Account Service]
  UI --> Subject[Subject Service]
  UI --> Assessment[Assessment Service]
  UI --> Activity[Study Activity Service]
  UI --> Planning[Planning Service]
  Subject -->|confirmation REST| Assessment
  Assessment -->|subject verification REST| Subject
  Activity -->|subject verification REST| Subject
  Planning -->|authoritative assessment and subject REST| Assessment
  Planning -->|subject reference REST| Subject
  Subject -->|outbox subject facts| Kafka[(Kafka)]
  Assessment -->|outbox assessment facts| Kafka
  Activity -->|outbox activity facts| Kafka
  Planning -->|outbox completed blocks| Kafka
  Kafka --> Streams[Planning Kafka Streams topologies]
  Streams --> ProjectionTopics[(Projection topics)]
  ProjectionTopics --> ReadModels[Planning local read models]
  ReadModels -->|REST and SSE| UI
  Planning <-->|approved tool loop| LLM[Privately configured LLM provider]
  Subject <-->|reviewable outline extraction| LLM
  Account --- AccountDB[(Account H2)]
  Subject --- SubjectDB[(Subject H2)]
  Assessment --- AssessmentDB[(Assessment H2)]
  Activity --- ActivityDB[(Activity H2)]
  Planning --- PlanningDB[(Planning H2)]
```

All protected services validate the bearer token through Account REST (omitted from the drawing to keep business flows readable). No real key is sent to the browser or stored in a source file. Read-model queries use local Planning data; authentication still depends on Account.

```mermaid
flowchart TB
  C[Presentation: controller / dto / exception] --> A[Application: use-case orchestration and transactions]
  A --> D[Domain: aggregates, immutable values and policies]
  A --> P[Application ports]
  I[Infrastructure adapters: JPA, REST, Kafka, SDK] -. implements .-> P
  I --> D
  A --> R[Infrastructure JPA repositories where used directly]
```

The same four responsibilities are present in all five services. Subject uses explicit stores/gateways/reader/extraction ports; Planning uses PlanningTools, ProjectionStore and CalendarReferences. Some application-to-repository dependencies are direct and domain entities carry JPA annotations. This is a deliberate four-layer prototype, not a claim of perfectly inverted clean architecture.

```mermaid
sequenceDiagram
  actor Student
  participant API as Planning API
  participant Facts as Academic REST / local progress
  participant Agent as LangChain4j agent
  participant DB as Planning H2
  Student->>API: Generate or regenerate seven-day plan
  API->>Facts: Get incomplete assessments and completed work
  API->>Agent: Goal, availability, candidate
  loop At most eight turns
    Agent->>API: Approved fact tool calls
    API->>Facts: Owner-scoped facts / previous plan
    Facts-->>Agent: Tool results
  end
  Agent->>API: saveStudyPlan with structured items
  API->>API: Strict schema validation
  API->>DB: Lock owner row in transaction
  API->>Facts: Revalidate current assessment state and budgets
  API->>DB: Allocate next version and replace planned AI calendar
  alt All work is completed or deleted
    API->>DB: Save empty version and clear obsolete planned blocks
  end
  DB-->>API: Commit plan and calendar together
  API-->>Student: 201 saved plan
```

An invalid model plan does not mutate plan/calendar state. The model cannot directly write the database. Provider failures return safe 503 errors; invalid input/output constraints return 400. Plan lock contention may return a retryable conflict rather than committing inconsistent versions.

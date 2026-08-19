# Architecture and context map

The four bounded contexts are intentionally small and independently deployable.

```text
Browser ──REST──> Subject Service ──REST confirm──> Assessment Service
   │                    ▲                                │
   ├──REST──────────────┼──── Study Activity Service    │ assessment-events
   │                    │             │                  ▼
   └──REST────────> Planning Service <┴── study-activity-events
                          │
                          └── Kafka Streams materialised projections
```

Subject verification is synchronous because the caller needs an immediate accept/reject answer. Events are facts emitted after successful persistence. The Planning Service is the dashboard read-model owner and keeps frontend aggregation logic small.

## Layering

Controllers validate transport concerns. Application services coordinate use cases, transactions, external ports and event publication. Domain entities protect invariants through named methods. Infrastructure implements JPA, REST, PDF, LLM and stream adapters.

## Data ownership

| Context | Database tables | Cross-context access |
|---|---|---|
| Subject | `subjects`, `subject_outline_imports` | Assessment creation through REST |
| Assessment | `assessments` | Subject existence through REST |
| Activity | `study_sessions` | Subject existence through REST |
| Planning | `study_plans`, Kafka state stores | Assessment/subject REST plus events |

H2 file names are unique. There are no cross-service JPA relationships.

## Stream processing

`workloadStream` filters valid assessment envelopes, deduplicates event IDs, keys by assessment ID and reduces updates into `assessment-latest-store`. This prevents updates from being counted as new assessments.

`studyProgressStream` filters `StudySessionRecorded`, keys by subject ID and reduces minutes into `weekly-study-minutes-store`. The query model combines current minutes with the Subject Service weekly target to produce `NO_ACTIVITY`, `BEHIND_TARGET`, `ON_TRACK`, or `TARGET_REACHED`.

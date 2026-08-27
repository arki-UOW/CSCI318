# Architecture and context map

The five bounded contexts are intentionally small and independently deployable.

```text
Browser ──authenticate──> Account Service <── token validation ── all services
   │
   ├──REST──> Subject Service ──REST confirm──> Assessment Service
   ├──REST──> Study Activity Service
   └──REST──> Planning Service ── owns plans, calendar and assistant context
                    ▲
                    └── subject, assessment and activity REST reads
```

Subject verification is synchronous because the caller needs an immediate accept/reject answer. Events are facts emitted after successful persistence. The Planning Service is the dashboard read-model owner and keeps frontend aggregation logic small.

## Layering

Controllers validate transport concerns. Application services coordinate use cases, transactions, external ports and event publication. Domain entities protect invariants through named methods. Infrastructure implements JPA, REST, PDF, LLM and stream adapters.

## Data ownership

| Context | Database tables | Cross-context access |
|---|---|---|
| Account | `accounts`, `account_sessions` | Identity validation only; no academic-domain access |
| Subject | `subjects`, `subject_outline_imports` | Assessment creation through REST |
| Assessment | `assessments` | Subject existence through REST |
| Activity | `study_sessions` | Subject existence through REST |
| Planning | `study_plans`, `calendar_entries`, Kafka state stores | Assessment/subject/activity REST plus events |

H2 file names are unique. There are no cross-service JPA relationships. Academic rows carry the validated account ID so two users can never retrieve or mutate one another's subjects, assessments, sessions, plans or calendar items. Profile images, password hashes, navigation order and theme settings live only in Account Service.

## Calendar and review scheduling

Generated plan items are materialised as editable calendar study blocks. Manual tasks and sessions use the same model, so the monthly overview and detailed weekly schedule cannot disagree. When a spaced-repetition item is completed, Planning Service creates the next review after 1, 3, 7, 14 and 30 days. Each review records its source and stage, making the progression inspectable rather than hidden inside the LLM.

## Stream processing

`workloadStream` filters valid assessment envelopes, deduplicates event IDs, keys by assessment ID and reduces updates into `assessment-latest-store`. This prevents updates from being counted as new assessments.

`studyProgressStream` filters `StudySessionRecorded`, keys by subject ID and reduces minutes into `weekly-study-minutes-store`. The query model combines current minutes with the Subject Service weekly target to produce `NO_ACTIVITY`, `BEHIND_TARGET`, `ON_TRACK`, or `TARGET_REACHED`.

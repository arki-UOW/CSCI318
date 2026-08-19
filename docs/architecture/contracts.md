# REST and event catalogue

## Key REST endpoints

| Service | Method and path | Purpose |
|---|---|---|
| Subject | `POST /api/subject-outlines` | Upload and extract a PDF |
| Subject | `GET /api/subject-outlines/{id}` | Retrieve review state |
| Subject | `POST /api/subject-outlines/{id}/confirm` | Confirm corrected extraction |
| Subject | `GET /api/subjects` | Subject overview |
| Assessment | `POST /api/assessments/import` | Confirmed import contract |
| Assessment | `GET /api/assessments?status=&subjectId=` | Ordered assessment overview |
| Assessment | `PATCH /api/assessments/{id}` | Deadline/workload/priority changes |
| Assessment | `POST /api/assessments/{id}/complete` | Mark complete |
| Activity | `POST /api/study-sessions` | Record activity |
| Activity | `GET /api/study-sessions/summary` | Weekly subject summary |
| Planning | `GET /api/planning/workload` | Workload projection |
| Planning | `GET /api/planning/this-week` | Coherent dashboard read model |
| Planning | `POST /api/planning/plans` | Generate and validate a plan |
| Planning | `POST /api/planning/plans/{id}/regenerate` | Re-read state and version a plan |

Errors contain `timestamp`, `status`, `error`, `message`, `path`, and optional `validationErrors`.

## Events

All envelopes contain UUID `eventId`, `eventType`, integer `eventVersion`, UTC `eventTimestamp`, `sourceService`, and structured `payload`.

| Topic | Events |
|---|---|
| `assessment-events` | `AssessmentCreated`, `AssessmentUpdated`, `AssessmentDeadlineChanged`, `AssessmentWorkloadChanged`, `AssessmentPriorityChanged`, `AssessmentCompleted` |
| `study-activity-events` | `StudySessionRecorded` |

Malformed envelopes are ignored by projection adapters and never enter query state.

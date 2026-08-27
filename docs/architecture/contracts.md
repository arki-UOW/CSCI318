# REST and event catalogue

## Key REST endpoints

| Service | Method and path | Purpose |
|---|---|---|
| Account | `POST /api/auth/register` | Create an account and a 30-day remembered session |
| Account | `POST /api/auth/login` | Verify the password and start a remembered session |
| Account | `GET /api/auth/session` | Restore the signed-in profile from a bearer token |
| Account | `PATCH /api/profile` | Save personal academic information and an optional validated profile image |
| Account | `PATCH /api/profile/password` | Verify the current password, replace it and revoke other sessions |
| Account | `PATCH /api/settings/theme` | Save the profile's theme and navigation-state colours |
| Account | `PATCH /api/settings/navigation` | Save a complete, validated order for the draggable main tabs |
| Subject | `POST /api/subject-outlines` | Upload and extract one PDF/DOCX/JPG/JPEG; the web client queues up to 10 and submits them sequentially |
| Subject | `GET /api/subject-outlines/{id}` | Retrieve review state |
| Subject | `POST /api/subject-outlines/{id}/confirm` | Confirm corrected extraction |
| Subject | `GET /api/subjects` | Subject overview |
| Subject | `POST /api/subjects` | Create a subject and optional assessments manually |
| Subject | `GET /api/ai/status` | Report the AI configuration visible to the running service |
| Assessment | `POST /api/assessments/import` | Confirmed import contract |
| Assessment | `GET /api/assessments?status=&subjectId=` | Ordered assessment overview |
| Assessment | `POST /api/assessments` | Manually create an assessment for an owned subject |
| Assessment | `PATCH /api/assessments/{id}` | Edit title, type, deadline, workload, description and priority |
| Assessment | `POST /api/assessments/{id}/complete` | Mark complete |
| Assessment | `DELETE /api/assessments/{id}` | Remove an incorrect assessment |
| Activity | `POST /api/study-sessions` | Record activity |
| Activity | `PATCH /api/study-sessions/{id}` | Correct an owned study session |
| Activity | `DELETE /api/study-sessions/{id}` | Remove an owned study session |
| Activity | `GET /api/study-sessions/summary` | Weekly subject summary |
| Planning | `GET /api/planning/workload` | Workload projection |
| Planning | `GET /api/planning/this-week` | Coherent dashboard read model |
| Planning | `POST /api/planning/plans` | Generate and validate a plan |
| Planning | `POST /api/planning/plans/{id}/regenerate` | Re-read state and version a plan |
| Planning | `POST /api/planning/availability/chat` | Convert natural-language availability into time slots |
| Planning | `GET /api/planning/ai/status` | Report planning AI configuration |
| Planning | `GET /api/calendar?from=&to=` | Profile-scoped monthly or weekly calendar range |
| Planning | `POST /api/calendar` | Create a manual task, session or review |
| Planning | `PATCH /api/calendar/{id}` | Edit a calendar item |
| Planning | `POST /api/calendar/{id}/complete` | Complete an item and schedule its next spaced review |
| Planning | `POST /api/planning/assistant/chat` | Ask the LLM study assistant using profile-scoped context |

Errors contain `timestamp`, `status`, `error`, `message`, `path`, and optional `validationErrors`.

All subject, assessment, activity, planning and calendar calls require the opaque bearer token returned by Account Service. Services validate the token with Account Service and scope every query and mutation to its account ID. Raw tokens and passwords are never stored: session tokens are SHA-256 hashed and passwords use BCrypt.

Subject confirmation uses a committed `CONFIRMING` import state before calling Assessment Service. Assessment Service can therefore verify the subject through REST, and repeated imports return existing same-title assessments instead of duplicating them. Subject Service marks the import `CONFIRMED` only after that call succeeds.

## Events

All envelopes contain UUID `eventId`, `eventType`, integer `eventVersion`, UTC `eventTimestamp`, `sourceService`, and structured `payload`.

| Topic | Events |
|---|---|
| `assessment-events` | `AssessmentCreated`, `AssessmentUpdated`, `AssessmentDeadlineChanged`, `AssessmentWorkloadChanged`, `AssessmentPriorityChanged`, `AssessmentCompleted` |
| `study-activity-events` | `StudySessionRecorded`, `StudySessionUpdated`, `StudySessionDeleted` |

Malformed envelopes are ignored by projection adapters and never enter query state.

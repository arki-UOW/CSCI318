# Specification readiness progress

Baseline GitHub main: `0fda7273a58f657d4ef18ff93ee4583dd706cd2c`. Implementation branch: `agent/specification-readiness`.

| Task | Status | Evidence |
|---|---|---|
| Inspect baseline against both PDFs | Done | Architecture/FR/NFR audit; six defects reproduced before edits |
| F1 descriptions/storage/migration | Done | Domain/DTO bounds, legacy upgrade tests and HTTP persistence |
| F2 safe provider errors | Done | Four API failure cases and real SDK 429 fixture |
| F3 explicit zero estimate | Done | Scheduler and HTTP end-to-end zero-plan tests |
| F4 input validation/error contract | Done | All five services' malformed JSON; study and subject target validation |
| F5 long spaced-review titles | Done | Unit and HTTP completion checks |
| F6 calendar ownership/references | Done | REST adapter and unit/HTTP missing/foreign/mismatch tests |
| D1 seven-day horizon | Done | Candidate and API horizon bounded to start+6 |
| D2 model-submitted structured plan | Done | saveStudyPlan, mandatory progress, workload/identity/date validation, rollback tests |
| D3 history and UI regeneration | Done | Owned queries, version selector, regeneration button and tests |
| Local clean build | Done | 86 Java tests, no failures/errors/skips |
| Frontend verification | Done | 13 tests |
| Isolated five-service acceptance | Done | 51 assertions, actual SDK protocol fixture, no real key |
| Specification/contract/documentation reconciliation | Done | Completion audit, traceability, README, architecture, API/AI design and Postman |
| GitHub real-Kafka validation | CI gate | Current PR checks publish the exact revision/result; final delivery includes the run link |

Full task list, evidence and limitations: [completion verification](compliance/software-completion-audit.md). Live provider credentials/quota are external prerequisites and were not tested using a private key. This checklist does not assert absolute bug freedom or completion of student-authored academic deliverables.

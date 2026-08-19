# Traceability matrix

| Objective / requirement | Use case | Context and domain | Contract | Source | Test / demonstration |
|---|---|---|---|---|---|
| Import outlines safely | Upload → review → confirm | Subject; `SubjectOutlineImport`, `Subject` | `/api/subject-outlines/**` | `SubjectApplicationService`, `SafeOutlineExtractor` | Scenario 1; `SubjectTest` |
| Own confirmed assessment state | Correct, update, complete | Assessment; `Assessment` | `/api/assessments/**`, `assessment-events` | `AssessmentApplicationService` | `AssessmentTest`; Scenario 1/3 |
| Track study work | Record session | Activity; `StudySession` | `/api/study-sessions`, `StudySessionRecorded` | `StudyActivityApplicationService` | `StudySessionTest`; Scenario 2 |
| Show current workload | View dashboard | Planning; `WorkloadSummary` | `/api/planning/workload`, `/this-week` | `ProjectionService`, `PlanningApplicationService` | Scenario 1/2 |
| Demonstrate stateful streams | Update projections | Planning state stores | both Kafka topics | `workloadStream`, `studyProgressStream` | topology test seam; Scenario 2 |
| Produce safe plans | Generate / regenerate | Planning; `StudyPlan` | `/api/planning/plans/**` | `StudyPlanningAgent`, validation in application service | `StudyPlanTest`; Scenario 3 |
| Keep secrets safe | Configure optional AI | Infrastructure | environment variables | `.env.example`, YAML placeholders | secret audit |

Demonstration evidence should be captured by the team as screenshots or recordings during assessment preparation; this repository does not fabricate evidence.

# Student workflow completion

Baseline: merged PR #19 (`21465e1`), plus the team's requirements reconciliation through `6e3e82d`, incorporated without overwriting the working changes. Started 16 October 2026 (Australia/Sydney).

Agreed decisions: reliable local Docker deployment; overdue assessments remain visible, while normal planning schedules only eligible work. No automatic deadline changes or catch-up scheduling after deadlines. Private provider keys remain outside source control.

| Goal | Implementation | Verification |
|---|---|---|
| Forward-only seven-day planning in account timezone (UC-07/11/12) | Implemented; current date, elapsed-slot rejection and final placement clock check | Java clock/placement tests, full-document journey and HTTP pass |
| Honest overdue, zero-work and blocked-plan outcomes (UC-03/11/12/13) | Implemented; overdue stays visible, eligible work proceeds, blocked updates preserve state | Mixed/overdue-only/zero-work HTTP regressions pass |
| Current plan plus interactive history in Profile; saved availability (UC-12) | Implemented; immutable previews, explicit weekday reuse, no silent 120-minute fallback | Full-document controls, race tests and persistence round-trip pass |
| Calendar includes deadlines, planned work and completed activity (UC-03/05/07) | Implemented; source-linked views and date-only recorded sessions | Full-document source edit/navigation, overnight and owner-isolation tests pass |
| Visible, editable activity history and consistent progress (UC-04/05/10) | Implemented; optional assessment link, edit/delete recorded sessions, completed-block view without duplicate insertion | HTTP correction/summary/ownership tests pass; real Kafka extension in CI |
| PDF, DOCX, TXT, JPG and JPEG import (UC-01) | Implemented; TXT restored, review warns about past deadlines | TXT/PDF HTTP and DOCX Java pass; real Docker OCR formats step added to CI |
| Actionable dashboard without implementation diagnostics (UC-09/14) | Implemented; overdue, weekly labels, recent sessions, source-linked next items, concise AI errors | Full-document tests and desktop/mobile fixture rendering checked |
| Original proposal/rubric and final report requirement mapping | Final FR/UC-01–14 retained; historical 18 operations untouched | Current API references corrected; fresh 14-case evidence matrix supplied |
| Student journeys, failure recovery, account isolation and clean Docker CI | 124 Java tests, 40 frontend tests, 176 HTTP checks and launcher/diagnostic checks passed locally | Exact-commit Docker CI outcome tracked in the PR |

The supplied final report consolidates the earlier 18 functional requirements into 14. Preserve a crosswalk instead of treating the different numbering as missing functionality. Backend constraints include five independent services/databases, layered DDD, two stateful Kafka Streams workflows, SSE, two LangChain4j tool-using workflows, validation before persistence and preserved history on failure.

Passing component tests alone is not completion. Verify representative student journeys in the interface and across services. Record live-provider evidence separately from protocol fixtures. Do not manufacture team evaluations, signatures or report evidence.

Fresh results and limits: [student workflow evidence](testing/student-workflow-evidence.md). A live private-key Gemini run is not part of the local fixture evidence.

# ADR-002 — Decisions made during the completion correction

Date: 11 October 2026. Author of this technical record: the implementation assistant. Human direction: the user explicitly requested completion of the nine audited defects and repository evidence. Status: implemented engineering decisions. This does not claim a historical team vote, coordinator approval or student authorship of assessed work.

| Disposition | AI proposal / previous approach | Decision and concrete evidence |
|---|---|---|
| Accepted | Represent a valid no-work result as an empty seven-day version | Preserve the factual tool loop and save the empty result, clearing only obsolete planned AI blocks. `DeadlineScheduler`, `PlanningApplicationService`; HTTP all-completed/deleted regressions |
| Accepted | Validate model structure before mapping to Java primitives | Check exact required fields, text/UUID/date representations and integral minute/stage values. `AgenticPlanningAdvisor`; malformed-provider fixture regressions |
| Modified | Existing read-latest-plus-one version allocation inside a transaction | Add a database owner row lock and unique owner/version index; repair legacy duplicates without changing plan IDs/content. `PlanWriteLock`, migration test and eight-request nonempty-plan HTTP check |
| Modified | Guard a few individual frontend plan handlers | Enforce session identity before returning any shared request response, guard calendar navigation order, clear account-bound DOM/drafts and discard stale 401 responses. `app.js`, `session-calendar.test.cjs` |
| Modified | Launcher assumed a native command obeyed PowerShell's error preference | Check native exit status and poll every service plus frontend before announcing readiness. `run-all.ps1`, `test-launcher.ps1` |
| Rejected | Treat passing existing tests as sufficient evidence of complete correctness | Fresh probes reproduced failures despite green baseline tests. Add targeted regressions, report exact checked revision and distinguish fixture/live-provider evidence |
| Rejected | Commit real API keys or claim a fixture proves live model availability | Preserve private environment injection; use an explicitly local protocol fixture for repeatable tests; live provider access remains separate evidence |
| Rejected | Invent missing historical accepted/rejected team decisions | Record only current observable engineering choices. Preserve the fact that ADR-001 originally lacked team acceptance rather than fabricating it |

These examples supply authentic accepted/modified/rejected AI engineering proposals from this work. Team members should use their own words to discuss the decisions they actually reviewed in their assessed report. The technical artefacts may be cited with their AI-assisted origin identified.

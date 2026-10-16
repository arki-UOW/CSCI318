# Student workflow verification — final UC-01–UC-14

Date: 16 October 2026 (Australia/Sydney). Requirement authority: final report Chapter 2 and `docs/specifications/final-implemented-software-specification.md`. This is new evidence for the student workflow changes; it does not replace historical audit results or reinterpret their old FR IDs.

## Executed locally

- `mvn verify`: **124 tests, 0 failures, 0 errors, 0 skipped** across the services and messaging module.
- `node --test frontend/tests/*.test.cjs`: **40 tests passed**, including the complete HTML/application test with bound controls (`student-journey.test.cjs`). Install the pinned test dependency with `pnpm install --dir frontend --frozen-lockfile` first.
- `python scripts/verify-readiness.py`: **176 HTTP/JPA checks passed** against five separate service processes/databases. The real SDK calls a local OpenAI-protocol fixture; Kafka is disabled in this suite.
- Windows launcher tests passed for Docker failure, readiness timeout and successful startup. Gemini diagnostic tests passed for missing key, valid response, upstream 503/429, malformed output and key redaction.
- Browser-rendered **static fixtures** exported from the full-document test were visually inspected at the normal desktop viewport and a 390px viewport override. Dashboard showed overdue work, upcoming blocks/deadlines and recent activity. Calendar overflow stayed inside its scroll area. A hidden profile-picture input causing page overflow was found and fixed; Profile and Plan then matched the viewport width. These are rendering checks, not live-provider screenshots or browser-to-service evidence.

## Observed workflow evidence for Chapter 4

| Final use case | Actual test inputs / action | Observed result and evidence |
|---|---|---|
| UC-01 Import and Confirm Subject Outline | UTF-8 `outline.txt`, labelled CSCI319, through multipart upload and local model fixture; review then confirm | HTTP 201 review; no subject before confirmation; HTTP 201 confirmation creates the subject. PDF confirmation and DOCX reader regressions pass. `TextOutlineTest` rejects invalid UTF-8/binary text. Real Docker OCR coverage is a separate CI step. |
| UC-02 Manage Subjects | Create CSCI318; change weekly target; update owned subject fields | HTTP creation/update and validation regressions pass; source-owned records retained. `SubjectTest`, subject controller and HTTP checks. |
| UC-03 Manage Assessments | Create eligible report and overdue report; change a deadline; complete/delete owned work | Overdue remains retrievable. Full-document UI moves the linked deadline from 20 to 22 October when its source changes and opens the assessment editor from the calendar. Boundary/ownership checks pass. |
| UC-04 Record Completed Study Session | Record 25 minutes linked to the owned report | HTTP 201 retains `assessmentId`; foreign and mismatched subject/assessment links return 400. Subject-only records remain supported. UI offers an optional assessment selector. |
| UC-05 Retrieve Study History and Duration Summary | Correct that session to 35 minutes, query summary, then remove it | Summary returns 35 once; deletion returns 204 and history becomes empty. UI displays recent activity on Dashboard, Activity and Profile with source controls. Completed calendar history is available independently of the visible month. |
| UC-06 Manage Account, Authentication and Profile | Two separate fixture accounts; delayed responses across sign-out; profile length boundaries | Foreign session update returns 404; histories remain isolated. Frontend ignores previous-account responses and clears private plan/history/activity DOM at sign-out. Existing password/profile/session regressions pass. |
| UC-07 Manage Calendar Entries and Availability | Controlled Friday 16 October 2026; reuse Monday/Wednesday 18:00–20:00; manually add a slot and attempt overlap | Upcoming dates are Monday 19 and Wednesday 21; manual addition enables planning, overlap is rejected. Source deadlines and date-only recorded activity appear; overnight entries span days. Invalid/exhausted time leaves the prior calendar unchanged. |
| UC-08 Schedule Spaced-Repetition Reviews | Complete linked calendar block with a 160-character title | HTTP completion succeeds and creates its review with valid title length. Existing domain tests verify review intervals and completed-history protections. Completed blocks are displayed once as calendar activity, not inserted again into Activity Service. |
| UC-09 / RT-01 Workload Projection | Assessment create/edit/complete/delete and stale/duplicate events | Java topology/projection regressions pass. Dashboard query now exposes `overdue` separately. Real broker/SSE verification is the separate Docker CI job, including overdue publication/removal. |
| UC-10 / RT-02 Study-Progress Projection | Activity and completed-block facts, corrected durations, target changes and deletes | Java stateful topology and projection tests pass, including linked assessment deductions. Docker CI additionally checks a linked session raises 75→100 minutes, correction raises 100→110, and deletion restores 75. |
| UC-11 / AI-01 Generate Seven-Day Study Plan | Current/future start, eligible and overdue work, exact 14:00–16:00 slots; real SDK/local model fixture | Accepted plan includes only eligible assessment, exact seven-day horizon and calendar placement. Accepted availability and unscheduled workload are persisted. Past start returns 400. Clock tests prevent elapsed same-day placement. |
| UC-12 / AI-02 Regenerate Study Plan | Change workload/deadline, regenerate; preview prior version; reuse saved availability | New versions preserve originals. Profile preview does not replace current plan/dashboard/calendar. Explicit time reuse maps weekdays forward. Overdue-only regeneration returns 400 and preserves byte-equivalent history/calendar responses. |
| UC-13 Reject Invalid AI Plan | Fractional/missing/string/extra model fields, past or late dates, unavailable provider, conflicting commitments | Invalid plans do not mutate history/calendar. Existing bounded model correction loop remains exercised. All-zero or all-completed work can still save a valid empty version and clear obsolete blocks. |
| UC-14 View Academic Dashboard | Projected overdue assessment and 45-minute study progress plus source session/deadline/plan fixture | UI shows overdue action, labelled weekly period, recent activity and next calendar items; raw tool names/model explanation are absent. Totals remain Kafka projections, rather than a new browser aggregation replacing the streaming requirement. |

## Verification boundaries

The local Java/DOM/HTTP results above were executed during this change. The Docker CI job must separately pass on the submitted commit before its Kafka, container and JPG/JPEG OCR checks are counted as verified. Its run URL and outcome are recorded in the pull request.

No private provider key was read, committed or used. Live Gemini extraction/planning was **not** verified in this environment. Protocol fixtures test request/response handling and workflow constraints, not current Google quota, model availability or answer quality. Do not describe these fixtures as a live Gemini demonstration in the report.

For a final report, use these results with the submitted commit and CI artifact links. Team contributions, accepted/rejected AI proposal decisions and firsthand live-provider demonstration observations must retain their real authors and provenance. Do not fabricate those records from these automated checks.

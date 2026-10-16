# Student usability follow-up — 17 October 2026

Authority: the final FR-01–FR-14 / UC-01–UC-14 specification and Chapter 2 of the supplied `CSCI318_Report_Template (3).docx`. Original 18-operation identifiers and historical evidence are unchanged. Base: merged PR #20, main `eab046a`.

## Corrections and requirement trace

| Area | Implemented behaviour | Final requirement / verification |
|---|---|---|
| Past assessment deadlines | Preserve real historical dates and overdue records. Editing a past date shows a warning to check the year and explains that no new study blocks will be allocated to overdue work. Existing import warnings remain. | FR-01, FR-03, FR-11–13. Existing HTTP tests cover overdue-only rejection, eligible work alongside overdue work, and unchanged calendar/history on failure. This intentionally does not impose a blanket ban on historical deadlines. |
| Profile avatar | Fixed non-shrinking square dimensions prevent the sidebar image becoming an oval beside a long account name. | FR-06. Desktop browser geometry was 36×36; photo was visually circular. |
| Timezone selection | Search supported locations by city, region or identifier; Kolkata resolves to a valid `Asia/Kolkata` option. Show UTC offset, selected ID and local-time preview; offer device timezone. Save applies the selected zone. | FR-06. Full-document test exercises search, selection, preview and more than 100 available zones. Existing backend zone validation remains. |
| Readable study assistant | Parse the reply envelope strictly enough to reject malformed JSON, wrong field types, empty replies and control characters; unwrap nested reply envelopes. One format-repair request is allowed, then a clear error. Never display the raw provider envelope as the answer. Paragraphs, emphasis and local KaTeX render the accepted answer safely. | Study-support UI; FR-06 account context. New Java tests cover malformed backslash escapes, nested JSON, repair success/failure. DOM test renders actual KaTeX and confirms HTML-looking provider text creates no image element. |
| Availability | When conversational availability includes time that has already elapsed, remove elapsed slots and trim today's partially elapsed slot to the next whole minute. Retain usable future slots and show the exact interpreted dates/times for review. No new plan is automatically saved. Manual slots can be removed individually. Daily-minute setup now populates the review list and caps today's minutes to the remaining day; the user explicitly builds/updates the plan afterward. | FR-07, FR-11–13. Clock test: Friday 16 October at 19:30:20 turns 16:00–22:00 into 19:31–22:00 (149 minutes), while Monday's 360 minutes remain. DOM tests cover manual addition, overlap rejection, removal and daily-minute review. Generation still rejects past starts/slots and preserves existing plans on failure. |
| Optional activity notes | Creation and editing allow omitted/blank notes, displayed as “Study session”. Duration/date/ownership validation and the 2,000-character maximum remain. | FR-04/05. Resolves the report UC-04 optional-description mismatch. Four new HTTP checks verify omitted and cleared notes; frontend test verifies neither field is required. |
| Special acknowledgement | Added the requested professional thanks to Ayush Sarkar as an early tester and Grade 4 student at Chatswood Public School in Sydney, NSW, as explicitly supplied by the user. | User-requested content. Browser inspection confirms the rendered section. Only the identification details explicitly supplied for this acknowledgement are included. |

## Executed evidence

- Full Maven verification: **129 Java tests, zero failures/errors/skips**. After the optional-note adjustment, Activity Service and its messaging dependency were rebuilt and their tests rerun successfully.
- Final frontend regression run: **40 tests passed**, including the complete HTML/application workflow. New assertions extend that workflow rather than inflating its test count.
- Final five-process HTTP/JPA acceptance run: **180 checks passed**, including SDK tool calls against the local protocol fixture. Actual result: `All 180 readiness checks passed. This fixture is not live-provider or Kafka evidence.`
- Browser rendering: static full-document test fixtures inspected at desktop and 390px viewport. Profile, circular avatar, assistant equations/paragraphs and acknowledgement were inspected. These are visual fixture checks, not a live Gemini demonstration.
- The submitted commit's separate CI jobs run the clean build, Windows-launcher/diagnostic tests, the HTTP suite, real Kafka workload/progress/SSE isolation checks, and PDF/DOCX/TXT/JPG/JPEG imports through Docker/OCR. See the PR for the actual run URL and final outcome; local fixture tests alone do not prove those jobs passed.

The existing [fourteen-use-case evidence table](student-workflow-evidence.md) identifies the broader regression coverage rerun here. The five-service architecture, independent databases, two Kafka Streams projections, two agentic planning workflows and account isolation remain intact. API keys remain private runtime configuration.

## Report reconciliation and remaining submission work

The supplied report's Chapter 2 uses the correct fourteen final requirements. Its UC-04 optional-note contract is now implemented. Chapter 3's architecture matches the repository at this level of review; no unrelated architecture refactor was made.

Chapter 4 is still labelled “Mathew – needs to be done”, contains empty setup/demo/evidence placeholders and UC-15–UC-18 template examples. Complete it using the final UC-01–UC-14 mapping and actual observed inputs/outputs. Remove obsolete current-use-case placeholders without deleting historical traceability. The report's 94 Java / 23 frontend / 142 HTTP figures are historical and must be dated as such or accompanied by this run's new evidence; do not present old results as newly executed.

The report includes team-authored live-Gemini success claims. Those must retain the team's real provenance and supporting observations. No private key was read or used for this change, and live Gemini answer quality, quota and availability were **not verified** here. The external report was reviewed for requirements and evidence consistency, not edited. This review does not certify an absence of every possible defect or guarantee a grade.

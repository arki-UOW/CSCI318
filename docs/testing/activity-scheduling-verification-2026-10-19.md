# Completed study and future scheduling verification

Executed 19 October 2026 against `agent/activity-scheduling-dates`, based on main `8a282cf9fb760d06f54521e407a24055b3e6af44`.

## Changes and requirement coverage

- UC-04 / UC-05: the activity form explicitly records completed work, with today/past dates. A future completed-session request remains rejected by the backend.
- UC-07: a separate **Schedule a session** action opens the calendar form. It carries the selected subject, optional assessment, description and duration, allows future dates, rejects elapsed start times for new entries, and opens the calendar at the saved date. Scheduling alone does not record completed study.
- UC-07 / UC-11 / UC-12: calendar labels, duration arithmetic and manual availability use account civil times, rather than interpreting them in the browser timezone across daylight-saving transitions. Calendar Today controls use the account date. Historical calendar entries remain editable under the existing backend contract.
- UC-09 / UC-10 / UC-14: completed-study credit is preserved; no future session is sent to the activity endpoint by the new scheduling action. Existing service ownership, event contracts and streaming architecture are unchanged.
- Postman planning dates now use tomorrow in the configured account timezone. The excessive-capacity case checks the specific 1440-minute validation reason, rather than accepting any HTTP 400.

## Executed checks

| Check | Observed result |
|---|---|
| Maven reactor `verify`, JDK 21 | Build succeeded; 129 Java tests passed |
| `TZ=America/New_York node --test tests/*.test.cjs` in frontend | All 44 tests passed |
| Sydney timezone run of activity-scheduling and postman-dates tests | All 4 tests passed |
| `scripts/verify-readiness.py` with isolated services and model fixture | All 182 HTTP checks passed |
| Live browser against local Docker app | Created an isolated test account; opened Record activity > Schedule a session; saved 20 October 2026 18:00–18:45; observed success and the item on October 20 in the calendar |
| `git diff --check` | Passed |

The frontend tests include account/browser dates on opposite sides of midnight, spring/fall daylight-saving arithmetic, future scheduling without completed-study writes, optional assessment association, navigation to the scheduled month, and account-date Today highlighting. HTTP checks include rejecting future completed study and checking the specific excessive-capacity validation response.

## Scope and limits

The HTTP fixture uses controlled model responses and does not establish live Gemini/OpenAI availability or Kafka delivery. The live browser check covers the new scheduling path, not a fresh execution of every use case. No live AI request was needed for this change. Existing historical verification records have not been rewritten. These results support the affected workflows; they are not a guarantee that software or an external provider can never fail.

The submission README documents environment creation, Docker startup, the local browser URL, shutdown without data deletion, and the distinction between completed and scheduled study. Private `.env` files and generated build outputs must remain outside the submission ZIP.

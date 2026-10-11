# Four follow-up corrections

Date: 12 October 2026. Baseline: merged PR #14, `d38e32968e73635f0b5496ff4ac17ccc8ab0cd1d`. Branch: `agent/readiness-edge-cases`.

These corrections address the four explicitly requested follow-up issues. Earlier correction records remain historical evidence; they did not establish that these additional cases worked.

| Issue | Implemented correction | Regression evidence |
|---|---|---|
| Fractional REST integers silently truncated | Disable Jackson floating-point-to-integer coercion in all five services. Integer fields reject decimal JSON numbers before application mutation. | Actual HTTP assessment create/update, study-session create/update, subject credit points/target and daily-capacity map reject fractional values with structured 400 errors. Existing values and row counts remain unchanged. Floating-point percentage and hour fields still accept valid fractions. |
| Zero remaining work incorrectly required availability | Use the same remaining-minute calculation for the availability guard and scheduling. Explicit zero estimates and estimates fully covered by completed study require no availability. | Domain tests cover zero estimates, completed minutes equal to the estimate, and one minute still outstanding. HTTP generation/regeneration with empty availability saves empty seven-day versions; regeneration clears obsolete AI blocks and preserves history. Positive outstanding work still requires availability. |
| Imports rejected zero estimates | Use nonnegative validation for estimated hours in both Subject and Assessment import DTOs. | HTTP direct creation, direct import, nested manual subject creation and outline confirmation all preserve zero. Negative hours fail; 0.5 hours still becomes 30 minutes. |
| Minutes-only plans overlapped existing commitments | Share interval subtraction and placement between explicit-slot and minutes-only paths. Subtract existing manual, review and completed commitments, including overnight overlap. Validate the whole replacement before deleting old planned AI entries. | Unit tests cover blocked evenings, overnight commitments, full-day capacity and impossible placement. HTTP generation avoids an existing evening commitment. Both generation and regeneration with a fully occupied day return 400 and retain previous calendar entries and plan history. |

## Calendar placement contract

Daily minutes specify capacity rather than clock-time restrictions. Automatic placement prefers free time from 18:00 to midnight when that can fit the entire day's allocation. Otherwise it uses the day's free intervals in chronological order, potentially including early morning. Supply `availabilitySlots` to constrain clock times. Items can split across free intervals without changing their total minutes or scheduled date.

If a scheduled date lacks enough free time, the error identifies that date and asks the user to adjust availability or their calendar. The transaction retains the previous saved plan and calendar. This verifies replacement against commitments present during planning; it does not prevent a user from subsequently creating a manual overlap.

## Verification

- `mvn --batch-mode clean verify`: **94 Java tests passed**, no failures/errors/skips.
- `node --test frontend/tests/*.test.cjs`: **21 tests passed**.
- `python scripts/verify-readiness.py`: **132 HTTP checks passed**, including the 36 new assertions for these corrections.
- `scripts/test-launcher.ps1`: Docker failure, readiness timeout and successful startup scenarios passed.
- GitHub's `test` and `kafka-integration` jobs remain the publication gate on the final PR revision. The PR records their run link after completion.

The HTTP checks start all five real service JARs with independent temporary databases and a local OpenAI-protocol fixture. They use a fake credential, not the team's private provider key. Real Kafka is verified separately in CI; live-provider availability and model quality are not inferred from fixture results. No credential-handling changes are required.

These results close the four reproduced cases. They do not establish absolute freedom from every possible software defect or certify student-authored assessment deliverables.

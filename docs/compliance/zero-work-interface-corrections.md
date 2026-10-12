# Zero-work interface corrections

Date: 12 October 2026. Baseline: merged PR #15, `f1ebffe`. Branch: `agent/zero-work-interface-gaps`.

The final rubric review found two gaps after the earlier REST and scheduling corrections. This change closes those specific gaps without changing private API-key configuration.

| Gap | Correction | Regression coverage |
|---|---|---|
| Omitted availability still failed zero-work generation/regeneration | The request DTO normalizes omitted or null `dailyAvailabilityMinutes` to an empty map before validation. The scheduler still requires capacity for positive remaining work. | HTTP checks exercise omitted and null maps through both operations, require empty seven-day versions for zero work, require a clear 400 for positive work, and verify failed requests retain saved history and calendar. |
| Manual subject form blocked zero estimated hours | Both manual and outline-review assessment editors use a minimum of zero hours while retaining fractional-hour entry. | Node tests execute both production renderers and check zero-inclusive constraints and preservation of zero. Separate DOM constraint checks verify zero, negative, fractional and blank values in both forms. Existing HTTP import tests verify persisted zero values. |

The suite now includes 94 Java tests, 23 frontend tests and 142 isolated HTTP assertions. The eight separate DOM checks use jsdom in the audit tooling; no frontend runtime or repository dependency was added. The final PR records actual local results and links the completed GitHub test/Kafka checks on its final revision.

This record supersedes the two unresolved interface findings in the external 12 October rubric audit. It does not replace that audit's other boundaries concerning student-authored deliverables, provider access, proposal reconciliation or submission settings. An empty plan still uses the required agentic workflow and therefore needs configured provider access outside the local test fixture.

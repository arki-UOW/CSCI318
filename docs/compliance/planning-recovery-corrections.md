# Planning recovery and saved-history corrections

Date: 15 October 2026. Baseline: merged PR #18, `843cbf2`.

The reported screenshots show three distinct conditions: an individual Gemini request returned 503, a model-proposed study block failed deadline validation, and no plan had been saved yet. A successful provider diagnostic does not establish that subsequent document or multi-turn planning requests will succeed.

| Finding | Implemented correction | Verification |
|---|---|---|
| Start over depended on Gemini, so clearing could fail or receive an inaccurate confirmation | The button clears local state immediately. The backend also handles the explicit clear commands without selecting or calling a provider. | Node event/state tests, full-document DOM check, Java no-provider test, HTTP check while the provider fixture is unavailable |
| Invalid model allocations were rejected only after the agent finished | Both generation and regeneration supply the application validator to the tool loop. A rejected submission returns its reason to the model for up to two corrections, within the existing eight-turn limit. The final submission is validated again before atomic persistence. | Five-service HTTP tests: corrected date/deadline proposals succeed; repeated violations return 400 without changing versions or calendar |
| Brief provider failures could exhaust the single retry | Gemini requests now use the SDK's bounded backoff with two retries (three attempts total). Persistent failures still produce a truthful error. | Actual configured Gemini SDK against local HTTP: two 503 responses then success, persistent 503 stops after three calls, 400 is not retried |
| History was a blank disabled selector when no plan existed | Explicit empty and failed-load explanations, a Refresh versions button, and a distinction between viewing history and changing the calendar | Node and full-document DOM tests; HTTP saved-version retrieval and ownership checks |
| Late responses could restore cleared availability or the wrong selected version | Availability responses are checked against request order, account and selected week. Saved-version selection discards older responses. History refresh retains a version saved during its request. | Delayed-response Node tests |
| Regeneration could reuse unconfirmed availability, and overlapping frontend generation requests were possible | Regeneration requires confirmation for existing slots; generation controls prevent simultaneous submissions and preserve disabled state after availability is cleared | Node tests; failed generation retains the selected plan and history |
| Signing out left the availability summary rendered and could leave Send disabled | Sign-out renders the empty schedule and restores the relevant controls before waiting for logout | Node sign-out regression and full-document DOM check |
| CI reported failure after the diagnostic harness printed PASS | The harness now explicitly exits zero after all assertions and cleanup succeed, instead of leaking the intentionally failing malformed-response child process exit code | Reproduced with the GitHub PowerShell wrapper convention; verified the same wrapper exits zero after the fix |

## Results and limits

- 117 Java tests passed with no failures, errors or skips. This includes four real Gemini SDK tool-transport scenarios covering generation/regeneration with and without corrective feedback, preserving signed metadata on later turns.
- 35 frontend Node tests passed. These retain the earlier account isolation, calendar navigation, overnight event, exact-slot and zero-estimate coverage.
- 155 isolated HTTP checks passed across all five built services, using separate H2 databases and a local model-protocol fixture. Earlier strict integer validation, zero-work planning, ownership, concurrent versions, import boundaries and calendar-conflict checks remain passing.
- Nine additional full-document jsdom checks exercised the actual HTML and bound event handlers with a local fetch fixture. These are DOM checks, not visual browser testing.

No private API key was read, changed or committed. No live Gemini success or real Kafka result is inferred from these local fixtures; the pull request's CI reports its own test and Kafka integration outcomes. The failing installation is on another computer. This change fixes the reproduced application gaps and adds recovery paths, but cannot promise an external provider will never return an error or that a model will always submit a valid plan. Exhausted recovery preserves existing data and reports the failure.

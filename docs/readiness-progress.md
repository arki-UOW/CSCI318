# Completion progress — fresh audit

Baseline merged main: `d38e32968e73635f0b5496ff4ac17ccc8ab0cd1d` (PR #14). Correction branch: `agent/readiness-edge-cases`. Date: 12 October 2026. The original nine corrections are retained below alongside the four follow-up fixes.

| Task | Status | Evidence |
|---|---|---|
| Reconcile the two supplied documents and fresh audit | Done | FR/NFR matrix and proposal reconciliation |
| 1. Final-assessment/empty-work regeneration | Done | Empty new version, obsolete calendar removed, history preserved; HTTP |
| 2. Concurrent plan generation/regeneration | Done | DB lock and unique index; 8+8 simultaneous requests; legacy repair test |
| 3. Account-switch response races and private UI state | Done | Shared session guards, calendar sequence, immediate DOM cleanup; Node/browser |
| 4. Overnight calendar overlap | Done | Backend range query, month/week continuation, midnight exclusivity; HTTP/Node/browser |
| 5. Null nested assessments | Done | Validated lists and application guards; three HTTP entry paths |
| 6. Oversized profile input | Done | DTO/domain/UI limits; maximum and over-boundary HTTP tests |
| 7. Outline provider status mapping | Done | Safe 503 through actual SDK protocol fixture |
| 8. Fractional/malformed model allocations | Done | Strict schema before mapping; invalid output cannot modify history/calendar |
| 9. Windows startup false success | Done | Native exit checks and readiness polling; three executable launcher scenarios |
| UML/domain/layer/activity diagrams and 18 use cases | Done | docs/domain-model, architecture/diagrams, use-cases |
| Accepted/modified/rejected AI decision record | Done | ADR-002 records current decisions, no invented team approvals |
| Examples for all 18 FRs and six NFRs | Done | Demonstration guide, Postman, requirements and executable checks |
| Design/proposal differences explained | Done | Target events versus REST, authenticated UUID contracts, seven-day plans |
| Follow-up 1. Fractional REST integers | Done | Structured 400 before mutation in all five services; HTTP boundary checks |
| Follow-up 2. Zero remaining work without availability | Done | Shared remaining-work guard; zero estimates and fully studied estimates; domain/HTTP |
| Follow-up 3. Zero estimates through imports | Done | Direct, nested manual and outline-confirmation routes preserve zero; HTTP |
| Follow-up 4. Minutes-only calendar conflicts | Done | Shared interval placement; overnight/full-day checks; failed generation/regeneration preserves calendar/history |
| Java verification | Passed | 94 tests; zero failures/errors/skips |
| Frontend verification | Passed | 21 tests; browser account isolation and overnight checks |
| Five-service API/persistence/model protocol verification | Passed | 132 checks with a local fixture; no private provider key |
| Launcher verification | Passed | Docker failure, timeout, healthy startup |
| Exact-revision GitHub CI | Required publication gate | Final delivery links the branch revision and completed test/Kafka run |

The [completion audit](compliance/software-completion-audit.md) and [four follow-up corrections](compliance/edge-case-corrections.md) contain the evidence and limits. These corrections close the nine earlier listed code defects, four follow-up cases and requested repository artefact gaps. A live provider demonstration still uses the team's private key; academic reports, presentations and actual team approvals cannot be manufactured from source code. No claim of absolute bug freedom is made.

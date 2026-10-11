# Implemented requirements and acceptance evidence

The source of requirements is the supplied Study Leftovers proposal and CSCI318 professor specification. The detailed current contracts, normal flows, postconditions and activities are in [all eighteen use cases](../use-cases/functional-use-cases.md). [Design reconciliation](../architecture/proposal-reconciliation.md) records changes from the proposal rather than silently claiming literal equivalence.

| Requirement | Acceptance |
|---|---|
| FR-01–FR-13 academic commands and queries | Owned REST resources, bounded valid input, structured errors, lifecycle facts and accurate activity summaries |
| FR-14 / RT-01 | Assessment mutations/replay/corrections/completion update a local keyed workload read model |
| FR-15 / RT-02 | Session/target/completed-block facts maintain total and weekly progress, including corrections/deletions |
| FR-16 / AI-01 | Required factual tool use, model-submitted valid items, exact seven-day horizon, transactional plan/calendar save |
| FR-17 / AI-02 | Current facts plus previous-plan retrieval, new unique version, obsolete work removed even when no incomplete work remains |
| FR-18 | Strict model JSON types/fields and business constraints; failures preserve history/calendar |
| NFR-01 | Clean JDK 21 build, Compose configuration and launcher that checks native failure/readiness |
| NFR-02 | Keys injected privately at runtime; no genuine secrets in repository/frontend/errors/events |
| NFR-03 | Structured error body; invalid input 400, unauthorized 401, absent owned resource 404, conflict 409, unavailable dependency/provider 503 |
| NFR-04 | Controller/application/domain/infrastructure responsibilities and independent database ownership |
| NFR-05 | Repeatable Java, frontend, launcher, SDK/HTTP and real-Kafka integration verification |
| NFR-06 | Ordered Postman examples with captured IDs and projection polling; malformed model cases through the controllable local fixture |

The six non-functional requirements are acceptance criteria, not claims that every possible operational or security scenario has been tested. The current completion report records actual commands/results and limits.

# ADR-001 — Complete prototype architecture

- Date: 2026-08-19
- Development task: establish the Study Leftovers end-to-end prototype
- AI proposal: five Spring Boot services, independent H2 stores, REST for immediate commands, Kafka facts, Kafka Streams projections, a LangChain4j tool-calling agent with deterministic plan validation, and a static frontend
- Historical team decision: not recorded; no retrospective vote is asserted
- Status: Superseded as a technical baseline by ADR-002 and the implemented architecture
- Reason: satisfies mandatory CSCI318 concepts while keeping operation and explanation manageable for three students
- Related files: root `pom.xml`, service modules, `docker-compose.yml`, `frontend/`, architecture documentation
- Observed result: source modules and end-to-end contracts implemented; clean-build and real-Kafka verification run in repository CI

This historical record does not claim human acceptance. Current implemented decisions and their evidence are recorded in [ADR-002](ADR-002-completion-decisions.md).

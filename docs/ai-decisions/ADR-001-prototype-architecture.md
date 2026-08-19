# ADR-001 — Complete prototype architecture

- Date: 2026-08-19
- Development task: establish the Study Leftovers end-to-end prototype
- AI proposal: four Spring Boot services, independent H2 stores, REST for immediate commands, Kafka facts, Kafka Streams projections, optional LangChain4j with deterministic fallback, static frontend
- Team decision: **For team review**
- Status: Proposed
- Reason: satisfies mandatory CSCI318 concepts while keeping operation and explanation manageable for three students
- Related files: root `pom.xml`, service modules, `docker-compose.yml`, `frontend/`, architecture documentation
- Observed result: source modules and end-to-end contracts implemented; full Docker verification remains a team-machine activity

This record does not claim human acceptance. The team should change the status to Accepted, Modified or Rejected after review.

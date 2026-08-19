# Testing strategy

The test pyramid prioritises deterministic behaviour:

- domain tests: subject codes/targets, assessment weights/workload/completion, study duration, seven-day planning periods
- application tests: import confirmation, dependency failures, event envelopes, plan validation and regeneration
- stream tests: latest assessment by ID, completion removing outstanding work, per-subject minute aggregation, malformed-event rejection
- API tests: happy paths plus structured 400, 404 and 503 responses
- end-to-end demo: upload/confirm, activity/progress, generate/change/regenerate

Run `mvn clean verify`. Kafka and an LLM are not required for ordinary unit tests; use Kafka Streams test utilities for topology tests and stub the extraction/agent ports.

For a manual clean-start check: remove generated `data/` folders, run `docker compose up --build`, wait for Kafka readiness, open port 3000, then execute the three demo scenarios in the README.

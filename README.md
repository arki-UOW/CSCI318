# Study Leftovers

Study Leftovers is a CSCI318 prototype that turns subject-outline PDFs into reviewable subject and assessment data, then combines deadlines and study activity into a practical seven-day plan.

## What works

- PDF upload, local text extraction, candidate extraction, explicit review and confirmation
- optional LangChain4j/OpenAI extraction and planning; deterministic no-key mode for tests and demos
- four independently persisted Spring Boot services with clear data ownership
- assessment and study-session domain events through Spring Cloud Stream and Kafka
- Kafka Streams materialised assessment and weekly-study state
- workload, study progress, “This Week,” assessment overview, plan generation and regeneration
- deterministic validation before any AI-produced plan is stored
- responsive, dependency-free frontend covering the end-to-end workflow

## Architecture

| Service | Port | Owns |
|---|---:|---|
| Subject Service | 8081 | subjects, outline imports, extraction state, weekly targets |
| Assessment Service | 8082 | confirmed assessments and their lifecycle |
| Study Activity Service | 8083 | study sessions |
| Planning Service | 8084 | workload/progress projections and versioned plans |
| Frontend | 3000 | browser UI; no domain persistence |

Each service has its own file-backed H2 database. REST handles commands and immediate verification. Kafka carries completed business facts. No service reads another service’s database.

## Quick start

Requirements: Docker Desktop with Compose. An OpenAI key is optional.

```powershell
Copy-Item .env.example .env
docker compose up --build
```

Open <http://localhost:3000>. Kafka and all four services start together. On a first start, allow roughly two minutes for images, Maven dependencies and Kafka initialisation.

To enable AI-backed extraction/planning, set `OPENAI_API_KEY` in `.env`. Without a key, the bounded deterministic adapters remain usable and no paid service is required.

## Local development

Requirements: JDK 21, Maven 3.9+, Kafka on port 9092.

```powershell
mvn test
mvn -pl subject-service spring-boot:run
mvn -pl assessment-service spring-boot:run
mvn -pl study-activity-service spring-boot:run
mvn -pl planning-service spring-boot:run
```

Serve `frontend/` with any static server. The UI expects the documented localhost ports.

## Demo path

1. Upload a text-based PDF and review every extracted field.
2. Confirm it; the subject is stored by Subject Service and assessments by Assessment Service.
3. Record a study session and observe the Kafka-backed progress projection.
4. Generate a seven-day plan, complete or reschedule an assessment, then regenerate.

The Postman collection in `postman/` includes query and command examples. IDs returned by earlier calls should be placed into the collection variables.

## Tests and evidence

Run `mvn clean verify`. Domain tests cover core invariants. API/application boundaries are designed for stubbed `RestClient`, `StreamBridge`, and extraction ports so tests never need an LLM key. See [testing strategy](docs/testing/testing-strategy.md) and [traceability matrix](docs/traceability.md).

## Configuration and secrets

Only `.env.example` is committed. `.env`, databases, Maven output, logs and frontend dependencies are ignored. Never place keys in YAML, JavaScript, source code, Postman examples, or commits.

## Documentation

- [Architecture and context map](docs/architecture/architecture.md)
- [API and event catalogue](docs/architecture/contracts.md)
- [AI extraction and planning](docs/ai-decisions/ai-design.md)
- [Testing strategy](docs/testing/testing-strategy.md)
- [Traceability](docs/traceability.md)
- [AI-assisted development decision record](docs/ai-decisions/ADR-001-prototype-architecture.md)

This repository contains technical project documentation, not the team’s final university report or presentation.

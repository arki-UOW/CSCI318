# Study Leftovers

Study Leftovers is a CSCI318 prototype that turns subject-outline documents into reviewable subject and assessment data, then combines deadlines and availability into a practical seven-day plan.

## What works

- Up to 10 PDF, DOCX, JPG or JPEG outlines per batch, local text/OCR extraction, and a review queue before confirmation
- LangChain4j/Gemini analysis of cleaned extracted text and AI-assisted planning, with optional OpenAI compatibility
- manual subject and assessment entry when no document is available
- four independently persisted Spring Boot services with clear data ownership
- assessment and study-session domain events through Spring Cloud Stream and Kafka
- live REST-backed workload, study progress, “This Week,” assessment overview, plan generation and regeneration
- conversational availability capture for time slots such as “Monday 6–8pm”
- deterministic validation before any AI-produced plan is stored
- responsive, dependency-free frontend covering the end-to-end workflow

## Architecture

| Service | Port | Owns |
|---|---:|---|
| Subject Service | 8081 | subjects, outline imports, extraction state, weekly targets |
| Assessment Service | 8082 | confirmed assessments and their lifecycle |
| Study Activity Service | 8083 | study sessions |
| Planning Service | 8084 | workload/progress views, availability chat and versioned plans |
| Frontend | 3000 | browser UI; no domain persistence |

Each service has its own file-backed H2 database. REST handles commands and immediate verification. Kafka carries completed business facts. No service reads another service’s database.

## Quick start

Requirements: Docker Desktop with Compose. A Gemini key is recommended but optional.

```powershell
Copy-Item .env.example .env
docker compose up -d --build --force-recreate
```

Open <http://localhost:3000>. Kafka and all four services start together. On a first start, allow roughly two minutes for images, Maven dependencies and Kafka initialisation.

To enable Gemini-backed extraction and planning, set `GEMINI_API_KEY` in the private `.env` file—not in `.env.example`. The backend first extracts and normalises PDF/DOCX text or runs OCR for JPG/JPEG, then Gemini receives that cleaned text. This keeps provider input bounded and makes incorrect policy/SLO rows easier to reject.

## Gemini API key setup

1. Create or copy an API key in [Google AI Studio](https://aistudio.google.com/app/apikey).
2. From the repository root, create your private environment file:

   ```powershell
   Copy-Item .env.example .env
   ```

3. Open `.env` and set these values:

   ```dotenv
   AI_PROVIDER=gemini
   GEMINI_API_KEY=your_key_here
   GEMINI_MODEL=gemini-3.6-flash
   ```

4. Rebuild and recreate the containers after changing the key. A browser refresh alone does not reload environment variables:

   ```powershell
   docker compose down
   docker compose up -d --build --force-recreate
   ```

For local `mvn spring-boot:run` processes, set the same variables in each terminal before starting Subject Service or Planning Service:

```powershell
$env:AI_PROVIDER = "gemini"
$env:GEMINI_API_KEY = "your_key_here"
$env:GEMINI_MODEL = "gemini-3.6-flash"
```

`AI_PROVIDER=auto` prefers Gemini when both provider keys are present. `AI_PROVIDER=openai` preserves the existing OpenAI path. Never put a real key in `application.yml`, frontend JavaScript, `.env.example`, screenshots, commits, or chat messages.

## Local development

Requirements: JDK 21, Maven 3.9+, Kafka on port 9092 for domain-event services.

```powershell
mvn test
mvn -pl subject-service spring-boot:run
mvn -pl assessment-service spring-boot:run
mvn -pl study-activity-service spring-boot:run
mvn -pl planning-service spring-boot:run
```

Serve `frontend/` with any static server. The UI expects the documented localhost ports.

## Demo path

1. Upload or drop up to 10 PDF, DOCX, JPG or JPEG files together, or choose **Enter manually**.
2. Review and confirm each queued subject; subjects are stored by Subject Service and assessments by Assessment Service.
3. Record a study session and refresh the live progress view.
4. Tell the planning assistant your time slots and generate a seven-day plan.
5. Complete or remove incorrect assessments, then regenerate.

The Postman collection in `postman/` includes query and command examples. IDs returned by earlier calls should be placed into the collection variables.

## Tests and evidence

Run `mvn clean verify`. Domain tests cover core invariants. API/application boundaries are designed for stubbed `RestClient`, `StreamBridge`, and extraction ports so tests never need an LLM key. See [testing strategy](docs/testing/testing-strategy.md) and [traceability matrix](docs/traceability.md).

## Configuration and secrets

Only `.env.example` is committed. `.env`, databases, Maven output, logs and frontend dependencies are ignored. The example contains variable names only. Never place real keys in YAML, JavaScript, source code, Postman examples, screenshots, commits, or messages.

## Documentation

- [Architecture and context map](docs/architecture/architecture.md)
- [API and event catalogue](docs/architecture/contracts.md)
- [AI extraction and planning](docs/ai-decisions/ai-design.md)
- [Testing strategy](docs/testing/testing-strategy.md)
- [Traceability](docs/traceability.md)
- [AI-assisted development decision record](docs/ai-decisions/ADR-001-prototype-architecture.md)

This repository contains technical project documentation, not the team’s final university report or presentation.

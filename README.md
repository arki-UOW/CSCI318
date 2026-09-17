# Study Leftovers

Study Leftovers is a personal academic workspace that turns subject-outline documents into reviewable data, combines deadlines and availability into a practical plan, and carries that plan into editable weekly and monthly calendars.

## What works

- Up to 10 PDF, DOCX, JPG or JPEG outlines per batch, local text/OCR extraction, and a review queue before confirmation
- LangChain4j/Gemini analysis of cleaned extracted text and AI-assisted planning, with optional OpenAI compatibility
- manual subject and assessment entry when no document is available
- five independently persisted Spring Boot services with clear data ownership
- assessment and study-session domain events through Spring Cloud Stream and Kafka
- two stateful Kafka Streams features: account workload and weekly subject progress, served from local read models and pushed live to the dashboard
- conversational availability capture for time slots such as “Monday 6–8pm”
- deterministic deadline planning that repeats weekly availability, uses estimated minutes and spaces reviews through each due date
- editable monthly overview and detailed weekly calendar, including manual tasks and study sessions
- spaced repetition that creates reviews 1, 3, 7, 14 and 30 days after completed study blocks
- Gemini/OpenAI study-assistant chat grounded in the signed-in student's subjects, assessments and schedule
- remembered username/password accounts with profile pictures, password management, profile-scoped academic data and session history
- profile-saved theme colours (including active/hover navigation colour) and draggable navigation order
- readable LaTeX equations in study-assistant answers through KaTeX
- responsive, dependency-free frontend covering the end-to-end workflow

## Architecture

| Service | Port | Owns |
|---|---:|---|
| Subject Service | 8081 | subjects, outline imports, extraction state, weekly targets |
| Assessment Service | 8082 | confirmed assessments and their lifecycle |
| Study Activity Service | 8083 | study sessions |
| Planning Service | 8084 | workload/progress views, availability chat and versioned plans |
| Account Service | 8085 | accounts, password hashes, remembered sessions, profiles and theme settings |
| Frontend | 3000 | browser UI; no domain persistence |

Each service has its own file-backed H2 database. REST handles commands and immediate verification. Kafka carries completed business facts. No service reads another service’s database.

## Quick start

Requirements: Docker Desktop. A Gemini key is recommended but optional.

On Windows, double-click **`Start-Study-Leftovers.cmd`**. The launcher creates the private `.env` file when needed, starts the entire application, and opens the website. Double-click **`Stop-Study-Leftovers.cmd`** when you want to stop it; account and study data remain saved.

The command-line equivalent is:

```powershell
.\scripts\run-all.ps1
```

Open <http://localhost:3000>. Create an account on the welcome screen; its login remains valid for 30 days unless you sign out. Kafka and all five services start together. On a first start, allow roughly two minutes for images, Maven dependencies and Kafka initialisation.

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

4. Back up existing container databases before an upgrade or recreation: the development Compose file does not mount persistent service database volumes. Ordinary Stop now stops containers without deleting them. A rebuild/configuration change can still recreate them. A browser refresh alone does not reload environment variables.

   ```powershell
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
mvn clean install
mvn -pl account-service spring-boot:run
mvn -pl subject-service spring-boot:run
mvn -pl assessment-service spring-boot:run
mvn -pl study-activity-service spring-boot:run
mvn -pl planning-service spring-boot:run
```

Serve `frontend/` with any static server. The UI expects the documented localhost ports.

## Demo path

1. Create an account or sign back in to restore your profile, data and theme.
2. Upload or drop up to 10 PDF, DOCX, JPG or JPEG files together, or choose **Enter manually**.
3. Review and confirm each queued subject; subjects are stored by Subject Service and assessments by Assessment Service.
4. Tell the planning assistant your weekly time slots and generate a spaced plan through every assessment due date.
5. Open Schedule and switch between the monthly overview and weekly detail to edit generated blocks or add your own tasks and sessions.
6. Mark a spaced-repetition block complete and check the next review in the monthly overview.
7. Ask the Study Assistant for explanations or help breaking down the nearest assessment.

The Postman collection in `postman/` includes query and command examples. IDs returned by earlier calls should be placed into the collection variables.

## Tests and evidence

Run `mvn clean verify` and `node --test frontend/tests/*.test.cjs`. Tests cover domain rules, outbox rollback/retry, Kafka Streams replay/corrections/account isolation, persistent projections and authenticated dashboard push parsing. No LLM key is required.

CI starts a real Kafka broker, executes `python3 scripts/verify-streaming.py`, checks both live calculations and SSE, then stops Assessment/Activity to verify dashboard query independence from upstream REST. Run the script against a demo system; it creates two demo accounts. See [testing strategy](docs/testing/testing-strategy.md) and [traceability matrix](docs/traceability.md).

Existing academic rows are republished once as owner-scoped version-2 snapshots at startup. The dashboard may briefly show “Waiting for events” while Kafka catches up. Do not delete databases or Kafka state for an ordinary upgrade.

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

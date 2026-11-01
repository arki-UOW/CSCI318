# Study Leftovers

## Group 13 submission: how to run

Follow these steps after extracting the Group 13 source-code ZIP.

**Recommended: run locally with Docker Desktop.** This source-code submission contains no prebuilt JARs and no private API keys. Docker builds the five backend services from source and starts Kafka and the frontend. You do not need to install Java, Maven or Kafka separately for this route.

### 1. Extract the source and start Docker

- Extract `Group_13_source_code.zip` completely. Run the application from the extracted `Group_13_source_code` folder, not from inside the ZIP.
- Install and start Docker Desktop with Linux containers enabled. Wait until Docker is running.
- Internet access is required for the first build to download images and dependencies, and for live AI requests. Allow several minutes for the initial build.
- Ports `3000`, `8081`–`8085` and `9092` must be available. Stop another copy of this application before starting this one.

### 2. Configure AI privately (required for AI features)

Open PowerShell in the extracted folder. If `.env` does not already exist, create it:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
notepad .env
```

In `.env`, set `AI_PROVIDER=gemini`, enter your own key after `GEMINI_API_KEY=`, and set `GEMINI_MODEL` to a model available to that key/project. Save the file. Alternatively, configure the documented OpenAI provider.

**`.env.example` is the safe, included template; its API-key fields are blank. `.env` is your private local configuration and must not be submitted, committed or shared. Keep `.env.example` in the source package.** Do not overwrite an existing `.env` when upgrading.

The template currently names `gemini-3.6-flash`. That default is not a guarantee of provider availability; it previously returned HTTP 503 during team testing. Use a model your project can access, with sufficient quota. To check a configured Gemini key/model without uploading a document:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-gemini.ps1
```

This diagnostic checks one small request only. Confirm a real outline import and plan generation separately. Without a key, the app still supports accounts, manual subjects/assessments, study activity, calendar and streaming projections; AI outline analysis, conversational availability, agentic planning and the study assistant require a configured provider.

### 3. Start and use the application

On Windows, double-click **`Start-Study-Leftovers.cmd`**. It builds and starts the application, checks service readiness and opens the browser. If you skipped configuration, it creates a blank-key `.env` from the template.

Alternatively, from the extracted folder run:

```powershell
docker compose up -d --build
```

On macOS/Linux, use the same Docker Compose command after copying `.env.example` to `.env` and configuring it as above. The `.cmd` launcher is Windows-only.

Open **http://localhost:3000**, create an account and sign in. There are no preloaded personal accounts or databases. For a quick demonstration, enter a subject and future assessment manually, record study activity, and check the calendar/dashboard. With AI configured, import an outline, review and confirm its extracted details, add future availability, and generate a plan.

### 4. Stop, restart or troubleshoot

- Stop on Windows with `Stop-Study-Leftovers.cmd`, or run `docker compose down`. Docker volumes retain saved data.
- After changing `.env`, run `docker compose up -d --build --force-recreate`; refreshing the browser does not apply backend configuration changes.
- If startup fails, check `docker compose ps` and `docker compose logs --tail=100`. Avoid sharing private configuration or unredacted logs.
- Do not use `docker compose down -v` for an ordinary stop: it deletes this deployment's saved data.

### 5. Record completed work or schedule future work

- **Record activity > Record or schedule study:** select any study date. Today/past dates record completed work. Selecting a future date changes the submit button to **Schedule session** and opens the calendar form with that date, subject, assessment and duration ready to confirm. Future study does not count as completed progress.
- **Record activity > Schedule a session:** opens the calendar form for today/future times. Choose the subject and optional assessment, check the start/end, and save. The calendar opens at that date. Mark the session complete after doing the work to count it toward progress; do not record it again as separate activity.
- Planning starts today or later and covers seven days. Overdue assessments stay visible for correction or completion; normal planning respects assessment deadlines.

**Postman demonstration:** import `postman/Study-Leftovers.postman_collection.json` and run requests in the supplied order. The collection `timezone` must match the test account (default `Australia/Sydney`); it need not match the computer timezone. Plans start tomorrow in that timezone. The 1441-minute negative test checks both HTTP 400 and the capacity validation reason. AI requests require a working provider.

**Verification evidence:** see `docs/testing/activity-scheduling-verification-2026-10-19.md` for the checks performed and their limits. A successful startup or small provider diagnostic alone does not verify every AI workflow.

Running without Docker is also supported, but requires JDK 21, Maven 3.9+, Kafka, Tesseract for image OCR, a static frontend server and five separately started services. See **Local development** below. Docker is the recommended marking setup.

---

Study Leftovers is a personal academic workspace that turns subject-outline documents into reviewable data, combines deadlines and availability into a practical plan, and carries that plan into editable weekly and monthly calendars.

## What works

- Up to 10 PDF, DOCX, TXT, JPG or JPEG outlines per batch, local text/OCR extraction, and a review queue before confirmation
- LangChain4j/Gemini analysis of cleaned extracted text and tool-using agentic planning, with optional OpenAI compatibility
- manual subject and assessment entry, plus later editing, when no document is available
- five independently persisted Spring Boot services with clear data ownership
- assessment and study-session domain events through Spring Cloud Stream and Kafka
- two stateful Kafka Streams features: account workload and weekly subject progress, served from local read models and pushed live to the dashboard
- conversational availability capture for time slots such as “Monday 6–8pm”
- seven-day agentic planning: the model submits structured study blocks using deadlines, remaining workload and required progress tools; application validation guards persistence
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

Requirements: Docker Desktop. A Gemini or OpenAI key is required for outline analysis, conversational availability, agentic plan generation and the study assistant; account, subject, assessment, activity, calendar and streaming features remain usable without one.

On Windows, double-click **`Start-Study-Leftovers.cmd`**. The launcher creates the private `.env` file when needed, starts the entire application, and opens the website. Double-click **`Stop-Study-Leftovers.cmd`** when you want to stop it. Named Docker volumes retain account, academic, planning and Kafka state across container recreation.

The command-line equivalent is:

```powershell
.\scripts\run-all.ps1
```

Open <http://localhost:3000>. Create an account on the welcome screen; its login remains valid for 30 days unless you sign out. Kafka and all five services start together. On a first start, allow roughly two minutes for images, Maven dependencies and Kafka initialisation.

To enable Gemini-backed extraction and planning, set `GEMINI_API_KEY` in the private `.env` file—not in `.env.example`. The backend first extracts and normalises PDF/DOCX/TXT text or runs OCR for JPG/JPEG, then Gemini receives that cleaned text. This keeps provider input bounded and makes incorrect policy/SLO rows easier to reject.

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

4. Rebuild the services after changing `.env`. A browser refresh alone does not reload backend environment variables. Docker named volumes preserve the databases; `docker compose down -v` deliberately deletes them and should only be used when you want a clean reset.

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

## Diagnose a live Gemini failure

An outline-import HTTP 503 is the application's dependency-error status; it does **not** by itself mean Google returned 503 or the key is invalid. Updated error messages include the upstream HTTP status when the SDK supplies it, distinguish temporary provider failures from authentication, quota and request-format failures, and never echo raw provider responses.

On the computer running the app, from the repository root, run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-gemini.ps1
```

The diagnostic reads the private `.env` (process environment takes precedence), sends one small JSON-generation request directly to Google, and reports the upstream error safely. It does not upload documents or print the key. Exit codes: `0` minimal live request passed; `1` request/response failed; `2` missing/invalid local configuration. Share only its output, never `.env`. A passing probe still requires a successful real outline import and planning demonstration. If the probe passes but the app fails, recreate the containers to apply the same configuration and retry the document. The existing SDK retries transient failures; persistent Google unavailability cannot be repaired by changing application error messages. See [Google's troubleshooting guidance](https://ai.google.dev/gemini-api/docs/troubleshooting).

## Local development

Gemini planning uses multiple tool turns. With the current LangChain4j integration, both returnThinking(true) and sendThinking(true) preserve the signed metadata required by Gemini 3. These settings do not display model thinking in the UI. A successful one-message diagnostic does not verify this tool workflow; generation and regeneration also have HTTP transport regression tests.

The availability assistant returns weekday names and time slots. Java resolves dates within the selected seven-day period and produces the confirmation from the same schedule shown in the UI. Model-generated dates are ignored, invalid weekday/slot output is rejected, and generation stays disabled while clarification is needed. The AI badge reports configuration, not a successful live-provider health check.

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
2. Upload or drop up to 10 PDF, DOCX, TXT, JPG or JPEG files together, or choose **Enter manually**.
3. Review and confirm each queued subject; subjects are stored by Subject Service and assessments by Assessment Service.
4. Tell the planning assistant your weekly time slots and generate a spaced plan for exactly the requested seven days.
5. Open Schedule and switch between the monthly overview and weekly detail to edit generated blocks or add your own tasks and sessions.
6. Mark a spaced-repetition block complete and check the next review in the monthly overview.
7. Ask the Study Assistant for explanations or help breaking down the nearest assessment.

The Postman collection in `postman/` is an ordered authenticated demonstration. It creates and captures its own account and IDs, exercises subject editing, produces the two Kafka-derived projections, and runs both agentic planning stories. The outline-upload request remains manual because Postman must be given a local file.

Conversational availability preserves exact time slots in the generated calendar. Blocks stay in the requested seven-day period and split around existing commitments. If a day cannot fit its planned work, the request explains the conflict and retains the previous plan. The simple minutes-per-day form remains available for flexible schedules. In the Postman Collection Runner, projection checks retry until the expected state appears (with a bounded timeout), and regeneration follows a changed deadline and workload.

Minutes-only planning also avoids existing commitments. It prefers free evening time after 18:00 when the day's allocation fits; otherwise it uses free time across that day, potentially including early morning. Use explicit time slots to restrict clock times. If no placement fits, generation and regeneration preserve the previous plan and calendar.

## Tests and evidence

Run `mvn clean verify`, then `pnpm install --dir frontend --frozen-lockfile` and `node --test frontend/tests/*.test.cjs` (Node 22 and pnpm 11.25.0). Tests cover domain rules, outbox rollback/retry, Kafka Streams replay/corrections/account isolation, persistent projections and authenticated dashboard push parsing. No LLM key is required.

CI starts a real Kafka broker, executes `python3 scripts/verify-streaming.py --skip-ai-planning`, checks both live calculations and SSE, then stops Assessment/Activity to verify dashboard query independence from upstream REST. The flag keeps CI secret-free; running the script without it also verifies live agentic plan generation and therefore requires a configured provider key. The script creates two demo accounts. See [testing strategy](docs/testing/testing-strategy.md) and [traceability matrix](docs/traceability.md).

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


### Readiness acceptance and plan history

`GET /api/planning/plans` lists this account's saved versions; `GET /api/planning/plans/{id}` retrieves one. The Study plan page shows the current plan. **Profile → Plan history** has interactive version cards and read-only snapshots. Viewing a version does not replace the current plan or calendar. **Use these study times for a new plan** explicitly reloads saved availability onto matching weekdays in the next seven days; elapsed slots are excluded. Legacy versions without saved availability ask for new times. **Update my plan** requires confirmed availability and creates a new version through the agent workflow; it never silently substitutes a daily amount. Explicit zero workload is respected; missing estimates default to 120 minutes.

After `mvn --batch-mode clean verify`, run `python scripts/verify-readiness.py`. It starts five isolated JARs on ports 18081–18085 with in-memory databases and a local OpenAI-protocol fixture. It tests actual SDK tool calls, HTTP validation, persistence, ownership, seven-day generation/regeneration and safe failures without using a real key. Supply `--java /path/to/java` if needed. Its logs/results are written to a temporary directory (or `--output DIR`). Kafka is tested separately by the Docker CI job.

Real API keys stay in your private `.env` or process environment and are injected when starting/recreating the services. They are not compiled into the application or sent to the browser. `study.ai.openai.base-url` is an optional trusted operator setting for an OpenAI-compatible endpoint; it defaults to OpenAI and is overridden only to localhost by the acceptance fixture. Never point a service holding a real key at an untrusted endpoint.

See [readiness progress](docs/readiness-progress.md), [requirements traceability](docs/traceability.md) and [verification scope](docs/compliance/software-completion-audit.md). Automated fixture results do not establish that a privately configured live provider has available credentials, quota or model access.

## Completion evidence

See [the correction checklist](docs/readiness-progress.md), [the fourteen final use cases and activity-diagram references](docs/use-cases/functional-use-cases.md), [UML domain model](docs/domain-model/domain-model.md), [architecture/layer diagrams](docs/architecture/diagrams.md), [proposal reconciliation](docs/architecture/proposal-reconciliation.md), [implemented AI decisions](docs/ai-decisions/ADR-002-completion-decisions.md), and [demonstration guide](docs/testing/demonstration-guide.md).

Generation with no remaining work saves an empty seven-day version without requiring availability and clears obsolete planned AI blocks. This includes incomplete assessments whose estimates are zero or fully covered by completed study. Zero estimates are accepted through import and direct entry. Integer REST fields reject decimal JSON values with 400 before mutation. See the [four follow-up corrections and regression evidence](docs/compliance/edge-case-corrections.md).

Plan writes are serialized per account in the database, with a unique owner/version index. On startup, only owners with legacy duplicate plan versions are renumbered chronologically; IDs, plan content, dates and calendar links remain intact. Normal unique histories retain their numbers.

The Windows launcher checks Docker's exit code and waits for service endpoints before opening the site. The optional outline request is skipped in the ordered Postman run unless explicitly enabled. Private provider keys remain runtime configuration.

## Student workflow corrections (16 October 2026)

New planning starts today in the account timezone and rejects past dates or elapsed time. Overdue unfinished assessments stay visible for correction/completion; they do not turn a blocked plan into an empty successful version. Eligible work can still be planned. Calendar views combine editable assessment deadlines, scheduled blocks and date-only activity from their source records. Recent activity appears on Dashboard, Activity and Profile. An optional assessment link on a recorded session contributes to that assessment’s remaining workload after Kafka processing. General study contributes to subject progress only. Completed calendar blocks already count; do not record the same work again.

See the [progress checklist](docs/student-workflow-progress.md) and [fresh UC-01–UC-14 evidence](docs/testing/student-workflow-evidence.md). Historical audit counts remain historical.

## Usability follow-up (17 October 2026)

Profile now offers searchable timezone locations, UTC offsets, a local-time preview and device detection. Study-assistant answers use locally bundled equation rendering and readable paragraphs; malformed provider JSON gets one format-repair attempt instead of appearing as raw text. Conversational availability keeps usable future time when today's proposed start has elapsed. Manual slots can be removed, and quick daily-minute setup shows the proposed amounts for review before an explicit build/update action.

Real past deadlines remain valid for accurate imports and overdue tracking, with warnings when edited. They do not permit backdated planning. Session notes are optional under final UC-04; omitted notes display as “Study session”. See the [follow-up fixes, executed tests and report reconciliation](docs/testing/usability-followup-evidence.md).

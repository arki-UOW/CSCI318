# Study Leftovers

**Turn scattered deadlines into a study plan you can actually follow.**

Study Leftovers is a self-hosted academic planning workspace for students. Bring your subjects, assessments, study sessions and availability together in one place, then use an AI assistant to help turn upcoming work into a practical seven-day plan.

It combines a personal dashboard, editable calendar, progress tracking, spaced repetition and optional AI-assisted subject-outline imports. You can also enter everything manually, without an AI key.

> **Current status:** A locally hosted project, not a public cloud service. You run it on your own computer using Docker. AI features depend on a supported provider, valid credentials and available quota.

## What you can do

- **Organise subjects and deadlines.** Import up to 10 PDF, DOCX, TXT, JPG or JPEG subject outlines at once, review extracted information before saving it, or add subjects and assessments manually.
- **Build a weekly study plan.** Tell the planning assistant when you're available. It considers upcoming deadlines, remaining work and existing commitments, then proposes a seven-day schedule.
- **Use an editable calendar.** See monthly and weekly views, schedule your own sessions and tasks, and adjust generated study blocks.
- **Track actual progress.** Record completed study, monitor subject progress and workload, and see updates on your dashboard.
- **Review what you've learned.** Completed study can create spaced-repetition reviews after 1, 3, 7, 14 and 30 days.
- **Ask for study help.** Use the optional AI study assistant to discuss subjects, assessments and your schedule.
- **Keep your workspace personal.** Sign in to your account and customise your profile, theme and navigation.

AI suggestions are checked by the application before they are saved. You can always inspect and edit your plans.

## Get started

### What you'll need

- **Docker Desktop** running with Linux containers, or Docker Engine with the Compose plugin on macOS/Linux.
- An internet connection for the initial build.
- Free local ports **3000**, **8081–8085** and **9092**.
- **Optional:** your own Gemini or OpenAI API key for AI-powered features.

You do **not** need to install Java, Maven or Kafka separately when using Docker.

### 1. Get the code

Clone the repository:

```bash
git clone https://github.com/arki-UOW/Study-Leftovers.git
cd Study-Leftovers
```

Alternatively, use GitHub's **Code → Download ZIP**, then extract the ZIP and open a terminal in the extracted folder.

### 2. Configure AI (optional)

Create a private environment file from the example.

**Windows PowerShell:**

```powershell
Copy-Item .env.example .env
notepad .env
```

**macOS / Linux:**

```bash
cp .env.example .env
```

For Gemini, set the following values in `.env`:

```dotenv
AI_PROVIDER=gemini
GEMINI_API_KEY=your_api_key_here
GEMINI_MODEL=your_available_model
```

You can get a Gemini key from [Google AI Studio](https://aistudio.google.com/app/apikey). Choose a model available to your API key and account; the model named in the example configuration may not be available to everyone.

To use OpenAI instead, select `AI_PROVIDER=openai` and configure `OPENAI_API_KEY` and `OPENAI_MODEL`.

**Keep `.env` private.** Never commit it, upload it, or share your API key. A key is not required for manual subjects, assessments, study activity, calendar features and dashboard projections.

### 3. Start Study Leftovers

**Windows:** double-click `Start-Study-Leftovers.cmd`. It starts the application and opens your browser after checking startup.

**Any platform:** from the repository directory, run:

```bash
docker compose up -d --build
```

The first build can take several minutes because Docker downloads and builds the services.

Open **[http://localhost:3000](http://localhost:3000)**, create an account and sign in. Your workspace starts without preloaded accounts or academic data.

## Your first study plan

1. **Add a subject.** Import a subject outline and check the extracted details before confirming, or select the manual-entry option.
2. **Add your assessments.** Check the due dates and estimated work. Past deadlines can be tracked, but plans are scheduled for today or later.
3. **Set your availability.** Give the planning assistant your available days and times, or use the daily-minutes option.
4. **Generate your plan.** Review the proposed seven-day schedule before using it.
5. **Open the calendar.** Move between the monthly overview and weekly detail, and add or edit study sessions.
6. **Complete sessions.** Mark scheduled work as complete to count it toward progress. Avoid logging the same session a second time as separate activity.
7. **Keep planning.** Check the dashboard, use the study assistant if enabled, and generate a new plan as your deadlines change.

**Tip:** Future sessions are scheduled work, not completed progress. Once you've done them, mark them complete.

### Plan history

Your saved plans are versioned. Go to **Profile → Plan history** to inspect earlier versions. You can reuse saved availability when creating a new plan, without replacing your current plan just by viewing an older one.

## Running without AI

Study Leftovers still supports account creation, manually managed subjects and assessments, study records, calendar use and event-driven progress summaries without an API key.

The following features require a working Gemini or OpenAI configuration:

- AI analysis of imported outlines
- Conversational availability assistance
- Agentic plan generation and regeneration
- AI study-assistant chat

Provider availability, quotas and model support can change. If AI features fail, verify your provider settings before changing your academic data.

## Stop, restart and manage your data

| Task | Command |
|---|---|
| Start / rebuild | `docker compose up -d --build` |
| Check services | `docker compose ps` |
| View recent logs | `docker compose logs --tail=100` |
| Stop while preserving data | `docker compose stop` |
| Restart after changing `.env` | `docker compose up -d --build --force-recreate` |

Windows users can also double-click `Stop-Study-Leftovers.cmd`.

Study Leftovers uses named Docker volumes for its databases and Kafka state. **Do not run `docker compose down -v` unless you intentionally want to delete saved application data.**

### Troubleshooting

**The page doesn't open:** Confirm Docker is running, wait for startup to finish, then check `docker compose ps` and the service logs. Make sure ports 3000, 8081–8085 and 9092 aren't used by another application.

**AI says it's unavailable:** Confirm the provider, API key, selected model and quota. After editing `.env`, recreate the containers using the command above. The repository also includes `scripts/check-gemini.ps1` for a small Gemini connectivity check on Windows.

**The dashboard is initially empty:** Give the Kafka-backed projections time to process events after startup. Add a subject, assessment or completed session and refresh if necessary.

**My plan won't fit:** Adjust your availability, commitments or remaining workload. The planner rejects schedules that cannot be placed safely instead of silently overwriting an existing plan.

**A document import doesn't look right:** Review the extracted subject and assessment fields before confirming them. You can always add or correct the information manually.

## For developers

Study Leftovers has a browser frontend and five Spring Boot services, each with its own H2 database:

| Component | Local port | Responsibility |
|---|---:|---|
| Frontend | 3000 | Student workspace |
| Subject Service | 8081 | Subjects and outline imports |
| Assessment Service | 8082 | Assessments and deadlines |
| Study Activity Service | 8083 | Study sessions |
| Planning Service | 8084 | Study plans, availability and progress views |
| Account Service | 8085 | Accounts, profiles and sessions |

Apache Kafka carries domain events. Kafka Streams maintains workload and progress projections for the dashboard. The AI workflows use LangChain4j and configurable model providers; application validation protects saved plans.

For architecture, APIs, contribution-oriented testing and technical detail, see:

- [Architecture overview](docs/architecture/architecture.md)
- [API and event contracts](docs/architecture/contracts.md)
- [AI design](docs/ai-decisions/ai-design.md)
- [Testing strategy](docs/testing/testing-strategy.md)

**Local development:** See the technical documentation if you want to run services outside Docker. That route requires JDK 21, Maven 3.9+, Kafka and a frontend server.

---

*Study Leftovers helps organise and plan academic work. Always check imported deadlines and review AI-generated suggestions before relying on them.*

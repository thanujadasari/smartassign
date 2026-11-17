# SmartAssign — Explainable Claim Routing for Guidewire ClaimCenter

Constraint-aware claim routing with explainable recommendations (college minor project).
Three portals in one application — **Customer**, **Supervisor**, **Handler** — and every record lives in PostgreSQL.
Not connected to a live ClaimCenter.

```
smartassign/
  backend/            Java 21 · Spring Boot 3.3 · Spring Security + JWT · JPA · Flyway
  ml-service/         Python FastAPI suitability service + reproducible training
  frontend/           React + TypeScript + Tailwind (customer, supervisor and handler portals)
  dataset/            supplied dataset (training only), prepare_dataset.py, derived tables
  model-artifacts/    trained pipeline (preprocessing + classifier) + metadata
  documentation/      findings, architecture, API reference, triage rules, ClaimCenter notes
  docker-compose.yml
```

## How the application works

| Portal | Who | What they do |
|---|---|---|
| Customer | anyone (free sign-up) | Registers a claim (type, policy, incident, date, city, estimated loss, injury, third party, description) and follows its progress; sees the assigned handler's name and contact. Never sees internal triage values. |
| Supervisor | staff (sign-up needs the staff code) | **Triage**: the rules recommend a **priority** (HIGH / MEDIUM / LOW), severity, complexity and fraud risk with reasons; the supervisor decides (a note is required when the decision differs) and can **change the priority later** with a reason. **Routing**: hard eligibility checks → ranking of eligible handlers only → approve / override (reason required) / manual review; no eligible handler → automatic manual review. Queues are ordered most urgent first. Manages handler capacity, expertise and activation. Fairness monitoring, audit, settings. |
| Handler | staff (sign-up needs the staff code) | Sees the claims assigned to them with full details and **the supervisor who assigned each one**. Cannot accept or reject. Only two choices: **Available** or **Unavailable** (reason required). Marks a claim **Resolved** with a note, which frees capacity and builds their real success rate and average resolution time. |

Claim lifecycle: `SUBMITTED` (customer) → `UNASSIGNED` (triaged) → `ASSIGNED` (supervisor) → `RESOLVED` (handler), with `MANUAL_REVIEW` as the safe fallback. Every change is written to `claim_status_history`; availability changes to `handler_availability_log`; every action to the append-only `audit_logs`.

### What the dataset is (and isn't) used for
The supplied 7,000-row dataset is used **only** to train the suitability model, to derive the city-distance table used for proximity, and to calibrate the severity/complexity triage thresholds (see `documentation/05-triage-rules.md`). **No claim, handler or user from the dataset is loaded into the application.** Migration `V2` deletes everything that the earlier version seeded.

New handlers have no history, so for *ranking only* their success rate, resolution time and similar-claims count use neutral values (the training-data medians). These are never displayed — the UI shows "No resolved claims yet" until the handler resolves claims.

## Status (honest)
| Item | Status |
|---|---|
| Flyway V1 + V2 migrations and reference seed | **Run on PostgreSQL 16**: on the old seeded database (7,000 claims removed, 49 city distances kept) and on an empty database |
| Framework-free core (eligibility, scoring, explanations, fairness, triage + priority advisor) | **Compiled with javac**; triage advisor checked by hand-run cases |
| Spring Boot layer (auth with three roles, customer / handler portal / triage endpoints, routing, assignments, dashboard fix …) | Written and reviewed; **not compiled with Maven here** — Maven Central is not reachable from the build workspace. Compile and run the tests locally (below) |
| Unit tests + end-to-end integration test (customer → triage → routing → handler → resolve) | Written; **not run here** for the same reason |
| ML service | Unchanged; 32 core pytest tests pass |
| Frontend (three portals) | Bundled and exercised in a browser against a mock of the backend contract: customer sign-up → report claim → staff sign-up → supervisor triage → routing → approve → handler sees claim + supervisor → unavailable (reason) → available → resolve → customer sees handler and resolution. No console errors; no horizontal overflow at 390 px. **`npm run build`, typecheck and Vitest not run here** (npm registry not reachable) |

## Run locally

### Option A — Docker Compose
```bash
cp .env.example .env          # set every value, including SMARTASSIGN_STAFF_REGISTRATION_CODE
docker compose up --build     # postgres + ml-service + backend :8080 + web app http://localhost:3000
```

### Option B — local Java + PostgreSQL
```bash
export SMARTASSIGN_DB_URL=jdbc:postgresql://localhost:5432/smartassign
export SMARTASSIGN_DB_USER=postgres SMARTASSIGN_DB_PASSWORD=<your password>
export SMARTASSIGN_JWT_SECRET=$(openssl rand -base64 48)
export SMARTASSIGN_STAFF_REGISTRATION_CODE=<a code you give to staff>
export SMARTASSIGN_ML_URL=http://localhost:8000
cd ml-service && pip install -r requirements.txt && uvicorn app.main:app --port 8000 &
cd backend && mvn spring-boot:run
cd frontend && npm install && npm run dev                   # http://localhost:5173
```
**Upgrading an existing database:** just start the backend. Flyway applies `V2` (removes all synthetic claims, handlers, demo users, runs and audit rows; adds the portal tables/columns) and `V3` (adds claim priority, drops the benchmarking tables). Claims triaged before V3 get a priority derived from their severity, marked as such. (Alternatively drop and recreate the database.)

### First use
1. Open `/staff-signup`, create a **Supervisor** with the staff code.
2. Create one or more **Handlers** the same way (expertise, experience, city). A supervisor can then set each handler's capacity.
3. Open `/signup`, create a **Customer**, sign in and **Report a claim**.
4. Sign in as the supervisor → **Triage Queue** → open the claim → confirm triage → **Route this claim** → approve.
5. Sign in as the handler → the claim is listed with the supervisor who assigned it → **Mark resolved**.

API docs: http://localhost:8080/swagger-ui.html · health: http://localhost:8080/actuator/health

## Tests
```bash
cd ml-service && pip install -r requirements-dev.txt && python -m pytest
cd frontend && npm test
cd backend
mvn test                     # unit tests: eligibility, scoring, fairness, triage + priority rules, ML client, CSV
mvn verify -Pintegration     # end-to-end on a real PostgreSQL container (needs Docker)
```
The integration test registers a customer, a supervisor and a handler through the API, checks portal security (403 across portals, wrong staff code rejected, customers cannot see each other's claims), routing refused before triage, triage note required when departing from the recommendation, approval, the handler's view including the assigning supervisor, the customer's view (no fraud score), unavailability requires a reason and causes the manual-review fallback, resolution frees workload and records statistics, and the dashboard loads.

## Key design decisions
* Hard eligibility is applied first; only eligible handlers are ever scored or sent to the ML service. Core rules cannot be disabled.
* Every approval/override re-checks eligibility on live, row-locked data inside one transaction, and records the assigning supervisor.
* Triage recommendations are transparent rules; the supervisor's decision is stored next to the recommendation.
* The suitability score (0–100) is a weighted, explainable heuristic. The ML output is one factor ("predicted claim fit"); the UI never shows model names or metrics.
* Priority (HIGH / MEDIUM / LOW) is suggested from the customer's answers (injury, fire, major property damage, ongoing risk, loss size, investigation flags) and always decided by the supervisor - see `documentation/05-triage-rules.md`.
* Strategy Benchmarking was removed at the user's request (code, API, UI and database tables).
* Fixed: the dashboard failed ("We couldn't load this information") because a model-registration write ran inside the dashboard's read-only transaction. It now runs in its own transaction.

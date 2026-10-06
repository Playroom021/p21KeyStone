# KEYSTONE

Field-service work order management for a service business. Dispatchers raise and assign work orders,
technicians work them from a phone-friendly UI (parts, time, status), customers raise requests and follow
progress, and managers watch SLA health, stock and workload.

> **Verification status:** the frontend build and unit tests were run for real. The backend (`mvn`, tests,
> Flyway on PostgreSQL) and the Docker setup have **not** been executed by the author of this README
> (no Maven Central / Docker access in the build sandbox). See [FINAL_HANDOFF.md](FINAL_HANDOFF.md) for exactly
> what was and was not verified, and run the [Testing](#testing) commands first.

## Features

- **Authentication**: email/password login, stateless JWT, logout with token blacklist, BCrypt hashing.
- **Four roles**: MANAGER, DISPATCHER, TECHNICIAN, CUSTOMER, enforced in `SecurityConfig` (role gate) and in services (row-level scope).
- **Customers and sites**: CRUD with search/paging; customers manage their own sites through the portal.
- **Work orders**: create, assign, edit, cancel, close; lifecycle `NEW → ASSIGNED → IN_PROGRESS ⇄ ON_HOLD → COMPLETED → CLOSED` (and `CANCELLED`), with a status history that records who/what/when and an optional note.
- **Technician operations**: Start / Hold / Resume / Complete, parts used, time logs.
- **Parts and inventory**: stock-tracked parts, transactional consumption, negative-stock protection, low-stock filter.
- **SLA**: due time from priority, `ON_TRACK` / `AT_RISK` / `BREACHED`, scheduled breach detection, dashboards.
- **Dashboards/reports**: summary, SLA report, technician workload (MANAGER/DISPATCHER).
- **React UI** for all four roles (mobile-first for technicians and customers).
- **OpenAPI/Swagger UI** at `/swagger-ui.html`.

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.3.4, Spring Web, Spring Security, Spring Data JPA, Bean Validation |
| Auth | JWT (jjwt 0.12.6), BCrypt |
| Database | PostgreSQL 16, Flyway 10 migrations (`ddl-auto=validate`) |
| API docs | springdoc-openapi 2.6.0 (Swagger UI) |
| Frontend | React 19, TypeScript, Vite 7, React Router 7, Axios |
| Tests | JUnit 5 + Spring Boot Test + MockMvc on H2 (backend); `tsx --test` (frontend) |
| Containers | Docker, Docker Compose, nginx |

## Architecture

```
Browser ──► React SPA (Vite dev :5173 / nginx :3000) ──/api──► Spring Boot (:8080) ──► PostgreSQL (:5432)
                                                                 │
                                                                 ├─ SecurityConfig + JwtAuthFilter  (authentication, role gate)
                                                                 ├─ controller/  (REST, DTOs only)
                                                                 ├─ service/     (business rules, row-level scoping, SLA, stock)
                                                                 ├─ repository/  (Spring Data JPA)
                                                                 └─ SlaScheduler (periodic SLA sweep)
```

```
src/main/java/com/keyStone/Playroom021/
  config/      SecurityConfig, SlaConfig, OpenApiConfig
  controller/  Auth, Customer, CustomerPortal, Site, WorkOrder, WorkOrderResource, Part, Dashboard, DashboardReport
  dto/         request/response objects
  entity/      User, Role, Customer, Site, WorkOrder (+status/history), Part, PartUsage, TimeLog, Priority, SlaStatus
  exception/   GlobalExceptionHandler (ApiError), ConflictException, InvalidRequestException
  repository/  Spring Data repositories
  security/    JwtUtil, JwtAuthFilter, CallerScope (customer/technician scoping), TokenBlacklistService
  service/     WorkOrderService, TechnicianJobService, PartUsageService, TimeLogService, SlaService, SlaMonitorService, ...
src/main/resources/db/migration/   V1..V5 Flyway migrations
frontend/                          React app (pages/, components/, api/, auth/, lib/, hooks/, types/)
docs/HANDOFF_STEP9.md              history of the UI step
```

Design rules worth knowing: the **backend is the authority** (the UI only hides impossible actions); customer scope
comes from the authenticated principal, never from request input; another customer's or another technician's
record answers **404**, not 403; stock is decremented with a single conditional `UPDATE ... WHERE quantity_on_hand >= :qty`
inside one transaction, plus a DB CHECK constraint.

## Setup (local, without Docker)

Prerequisites: JDK 21, Maven 3.9+, Node 20.19+ (22 recommended), PostgreSQL 14+.

```bash
# 1. database
createdb authapp_db            # or: CREATE DATABASE authapp_db;

# 2. configuration (Spring does not read .env itself; export the variables)
cp .env.example .env           # edit DB_PASSWORD and JWT_SECRET
set -a; source .env; set +a

# 3. backend  -> http://localhost:8080 (Flyway migrates on startup)
mvn spring-boot:run

# 4. frontend -> http://localhost:5173 (proxies /api to :8080)
cd frontend && npm install && npm run dev
```

### Create one user per role

`POST /api/auth/signup` (a `CUSTOMER` also needs `companyName`):

```bash
for r in MANAGER DISPATCHER TECHNICIAN; do
  curl -s -X POST localhost:8080/api/auth/signup -H 'Content-Type: application/json' \
    -d "{\"fullName\":\"Demo $r\",\"email\":\"$(echo $r | tr A-Z a-z)@example.com\",\"password\":\"Passw0rd!\",\"role\":\"$r\"}"
done
curl -s -X POST localhost:8080/api/auth/signup -H 'Content-Type: application/json' \
  -d '{"fullName":"Demo Customer","email":"customer@example.com","password":"Passw0rd!","role":"CUSTOMER","companyName":"Acme Ltd"}'
```

> **Security note:** signup currently lets the caller pick any role (including MANAGER). Restrict or remove it before any public deployment.

## Environment variables

Defined in `.env.example`; every one has a local default in `application.properties`.

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8080` | backend port |
| `DB_URL` | `jdbc:postgresql://localhost:5432/authapp_db` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `postgres` | DB credentials |
| `JPA_DDL_AUTO` | `validate` | never `update`/`create` on a real DB |
| `JPA_SHOW_SQL` | `false` | SQL logging |
| `FLYWAY_ENABLED` / `FLYWAY_BASELINE_ON_MIGRATE` | `true` / `false` | migrations |
| `JWT_SECRET` | dev placeholder | **must** be overridden outside local dev (>= 32 bytes) |
| `JWT_EXPIRATION_MS` | `86400000` | token lifetime (24 h) |
| `SLA_CRITICAL_HOURS` / `HIGH` / `MEDIUM` / `LOW` | `4 / 24 / 48 / 72` | SLA target hours by priority |
| `SLA_AT_RISK_PERCENT` | `25` | AT_RISK when remaining time <= this % of the window |
| `SLA_SCHEDULER_ENABLED` | `true` | background SLA sweep |
| `SLA_CHECK_INTERVAL_MS` / `SLA_CHECK_INITIAL_DELAY_MS` | `60000` / `30000` | sweep timing |
| `POSTGRES_DB`, `DB_HOST_PORT`, `BACKEND_PORT`, `FRONTEND_PORT` | see file | Docker Compose only |

Frontend (`frontend/.env.example`, dev only): `VITE_PROXY_TARGET` (default `http://localhost:8080`),
`VITE_API_BASE_URL` (leave empty to use same-origin `/api`).

## Database and Flyway

Flyway owns the schema (`src/main/resources/db/migration`): `V1` core schema, `V2` role rename, `V3` RESOLVED→COMPLETED,
`V4` parts / part usage / time logs, `V5` SLA tracking. Migrations run automatically at startup; Hibernate only validates.
Never edit an applied migration: add `V6__description.sql`.
Tests use in-memory H2 with Hibernate `ddl-auto=update` and **do not execute the Flyway SQL**, so a migration/entity mismatch
shows up only when the app starts against PostgreSQL (see FINAL_HANDOFF.md).

## Authentication and roles

`POST /api/auth/login` → `{ token, ... }`; send `Authorization: Bearer <token>`. `POST /api/auth/logout` blacklists the token. `GET /api/me` returns the caller.

| Capability | MANAGER | DISPATCHER | TECHNICIAN | CUSTOMER |
|---|:-:|:-:|:-:|:-:|
| Customers / sites create + edit | ✔ | ✔ | – | own sites only (portal) |
| Customers / sites delete | ✔ | – | – | – |
| Create / edit work orders, assign | ✔ | ✔ | – | – |
| Delete work order (NEW only) | ✔ | – | – | – |
| See work orders | all | all | **assigned only** | **own customer only** |
| Start / Hold / Resume / Complete | – | – | ✔ (assigned) | – |
| Record / remove parts, log time | ✔ / ✔ | ✔ / ✔ | ✔ (assigned, active states) | – |
| Parts catalogue create / edit / delete | ✔ / ✔ / ✔ | ✔ / ✔ / – | read | – |
| Dashboards (summary, SLA, workload) | ✔ | ✔ | – | – |
| Raise requests (`/api/customer/**`) | – | – | – | ✔ |

## Commands

```bash
# backend
mvn clean package            # compile + run tests + build jar (target/authapp-1.0.0.jar)
mvn test                     # tests only (H2, no PostgreSQL needed)
mvn spring-boot:run

# frontend (cd frontend)
npm install
npm run dev                  # http://localhost:5173
npm run build                # tsc -b && vite build -> dist/
npm test                     # 29 unit tests
npm run preview
```

## Docker (local development)

```bash
cp .env.example .env         # set DB_PASSWORD and JWT_SECRET
docker compose up --build    # db + backend + frontend
docker compose logs -f backend
docker compose down          # stop (data kept)
docker compose down -v       # stop and delete the database volume
```

| Service | URL |
|---|---|
| Frontend (nginx) | http://localhost:3000 |
| Backend | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html (also via http://localhost:3000/swagger-ui.html) |
| PostgreSQL | localhost:5433 (container 5432) |

The frontend container proxies `/api` to `backend:8080`, so no CORS or API-URL setup is needed. This stack is for local
development only: no TLS, default credentials, permissive CORS, signup open to any role.

## Swagger / OpenAPI

With the backend running: Swagger UI `http://localhost:8080/swagger-ui.html`, JSON `http://localhost:8080/v3/api-docs`.
Log in via `POST /api/auth/login`, click **Authorize**, paste the token (without `Bearer `).

## Testing

- **Backend**: `mvn test`. Integration tests (MockMvc + H2) cover auth, customers/sites, work order lifecycle, technician
  authorization (404 on someone else's job), customer isolation, part usage (stock decrement, insufficient stock → 409,
  never negative, batch rollback, concurrent requests), time logs, SLA calculation and the scheduler, dashboards.
- **Frontend**: `npm test` (validation, lifecycle rules, job ordering, formatting, role navigation); `npm run build` type-checks.
- **Manual checklist**: see `FINAL_HANDOFF.md` §3 and `docs/HANDOFF_STEP9.md` §5.

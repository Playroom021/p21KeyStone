# KEYSTONE — Final Handoff (Step 10: final QA, documentation, Docker)

Starting point: `KEYSTONE_CHECKPOINT_09.zip`. Scope: QA, docs, Docker. No new features; only code changes are listed in §4.

## ⚠️ Read this first: what was and was NOT verified

The build sandbox had working npm access but **no Maven Central** (HTTP 403 `host_not_allowed` on `repo.maven.apache.org`), **no Docker**,
and no PostgreSQL. Result:

| Requested task | Status |
|---|---|
| 1. Backend tests + Maven build | **NOT RUN.** `mvn package` fails at the first step (cannot download `spring-boot-starter-parent:3.3.4`). Backend code has still never been compiled or tested by an assistant in any step (Steps 3–7). A JDK-only `javac` pass over `src/main` found **0 syntax errors**; its ~1,700 other errors are all missing-dependency noise (Spring/Lombok jars absent) and prove nothing about correctness |
| 2. Frontend build | **DONE, PASS.** `npm install` (101 packages), `npm run build` (`tsc -b && vite build`) succeeded against the real registry, 149 modules, `dist/` ≈ 381 kB JS. This resolves the Step 9 caveat about type shims |
| `npm test` | **DONE, 29/29 pass** |
| 3. Login + all 4 roles | **NOT RUN** (needs backend). Static review only: `SecurityConfig` role matrix matches the README table |
| 4. Work Order lifecycle | **NOT RUN.** Covered by existing, never-executed integration tests (`WorkOrderIntegrationTest`, `TechnicianOperationsIntegrationTest`) |
| 5. Customer data isolation | **NOT RUN.** Code review: `CallerScope.customerIdOrNull` derives scope from the principal, fails closed if a CUSTOMER has no linked customer; tests exist in `CustomerSiteIntegrationTest`/`WorkOrderIntegrationTest` |
| 6. Technician authorization | **NOT RUN.** Code review: `/start,/hold,/resume,/complete` are TECHNICIAN-only in `SecurityConfig`; assigned-only enforced via `WorkOrderService.findAccessible` (404 otherwise); test `jobActions_onWorkOrderAssignedToSomeoneElse_returns404` exists |
| 7. Parts stock / negative stock | **NOT RUN.** Code review of `PartUsageService.record`: one `@Transactional` unit, conditional `decrementStock` (`quantity_on_hand >= :qty`) → 409 on failure, rollback of earlier items; tests for zero-then-reject, batch rollback and concurrency exist |
| 8. SLA calc / breach detection | **NOT RUN.** Code review of `SlaService` (due = created + target by priority; BREACHED if now strictly after due; AT_RISK if ≤ 25 % of window remains; completed jobs judged by completion time; cancelled excluded). `SlaServiceTest` (pure unit test) and `SlaSchedulerIntegrationTest` exist |
| 9. Fix confirmed bugs | **No bug was confirmed** (nothing could be executed against the backend). No existing code was changed to "fix" anything |
| 10. Swagger/OpenAPI | **Was missing** (no springdoc in `pom.xml`). Added, but **unverified**: see §4 |
| 11. Docker | **Written, NOT run** (no Docker here). Compose YAML parses; nothing else checked |
| 12. README | Done (statuses above are stated in it) |

**First action next time (≈15 min):** `mvn clean package` → fix whatever it reports → `docker compose up --build` → run §3.
Compile errors on the first Maven run are plausible (code from Steps 3–7 has never been compiled).

## 1. Completed features (as implemented in the code; backend behavior unverified at runtime)

- JWT login/logout, BCrypt, 4 roles (MANAGER, DISPATCHER, TECHNICIAN, CUSTOMER), `/api/me`
- Customers, sites (staff CRUD, customer portal for own sites)
- Work orders: create/assign/edit/cancel/close/delete(NEW), status graph with history; customer portal requests
- Technician actions: start/hold/resume/complete; part usage with transactional stock; time logs
- Parts catalogue/inventory with low-stock filter
- SLA: due dates by priority, AT_RISK/BREACHED evaluation, scheduled sweep, SLA/summary/workload dashboards
- Flyway migrations V1–V5
- React UI for all four roles (Step 9) — builds and unit tests pass
- Swagger/OpenAPI (added now, unverified), Docker Compose (added now, unverified)

## 2. Tests performed

| Test | Result |
|---|---|
| `npm install`, `npm run build` (real toolchain) | pass |
| `npm test` | 29/29 pass |
| `mvn` build / tests (≈ 11 backend test classes in the repo) | not run (Maven Central blocked) |
| `javac` syntax-level pass on `src/main/java` | no syntax errors (semantic errors expected without dependencies) |
| Static review of `SecurityConfig`, `CallerScope`, `PartUsageService`, `SlaService` | consistent with the documented rules; no defect found (a review is not a test) |
| Browser/e2e, Docker, PostgreSQL/Flyway | not run |

## 3. Commands to run

```bash
# backend
mvn clean package && mvn spring-boot:run          # needs PostgreSQL + exported env vars (see README)
# frontend
cd frontend && npm install && npm run build && npm test && npm run dev
# everything in Docker
cp .env.example .env && docker compose up --build
```

Manual checklist after the stack is up: (1) sign up one user per role (README `curl`s) and log in with each; (2) MANAGER: create customer → site → part;
(3) DISPATCHER: create + assign a work order; (4) TECHNICIAN: Start → record part (stock drops) → log time → Hold → Resume → Complete;
(5) try to record more than the stock → 409 and stock unchanged; (6) second CUSTOMER cannot read the first one's work order (404);
a second TECHNICIAN gets 404 on the first one's job; (7) SLA: create a CRITICAL order and set `SLA_CRITICAL_HOURS` tiny (or edit `sla_due_at` in the DB), wait for the sweep, expect BREACHED on the dashboard;
(8) open `/swagger-ui.html`, authorize with a token, call `GET /api/me`. Step 9's §5 lists more.

## 4. Changes made in this step

| File | Change |
|---|---|
| `pom.xml` | + `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0` (version chosen for Spring Boot 3.3.x; **not resolved here**) |
| `config/SecurityConfig.java` | `permitAll` for `/v3/api-docs/**`, `/swagger-ui.html`, `/swagger-ui/**` |
| `config/OpenApiConfig.java` (new) | API title + JWT bearer scheme for the Authorize button |
| `Dockerfile`, `frontend/Dockerfile`, `frontend/nginx.conf`, `docker-compose.yml`, `.dockerignore`, `frontend/.dockerignore` | new, local-dev Docker |
| `.env.example`, `.gitignore` | Compose variables; ignore `.env*`, `secrets/`, keys |
| `README.md` | rewritten (old one described the previous AuthApp and static pages) |
| `HANDOFF.md` → `docs/HANDOFF_STEP9.md` | moved |
| `frontend/package-lock.json` | new (generated by the real `npm install`; Dockerfile uses `npm ci`) |

Not changed: all other backend/frontend source, migrations, tests.

## 5. Known issues

1. **Backend never compiled/tested/run by an assistant** (Steps 3–10). Highest risk item.
2. **Tests use H2 + `ddl-auto=update` and skip Flyway.** A mismatch between `V1–V5` SQL and the JPA entities (column names, types, enums, CHECK constraints) would only fail at startup on PostgreSQL (`ddl-auto=validate`). Add a Testcontainers/PostgreSQL test.
3. **Signup lets the caller choose any role** (including MANAGER) — must be locked down before any public deployment.
4. **CORS** allows any origin with credentials (`SecurityConfig`); tighten for production. nginx in Docker avoids CORS entirely.
5. JWT stored in browser `localStorage`; logout blacklist is in memory (lost on restart, not shared between instances).
6. Default/placeholder secrets (`JWT_SECRET`, DB password) in `application.properties`/Compose — override everywhere beyond local dev.
7. Swagger UI is public (documentation only; endpoints remain protected).
8. Docker: no backend healthcheck (no Actuator), no TLS, `docker compose` file is dev-only. The frontend image assumes the Compose service name `backend`.
9. Legacy static HTML pages (`src/main/resources/static/*.html`) still ship with the backend alongside the React app.
10. Frontend items from Step 9 still apply: no ESLint, no component/e2e tests, pickers capped at 100 rows, staff cannot record parts from the UI, no signup UI.
11. Potential dependency vulnerabilities not audited (`npm audit` / `mvn dependency-check` not run).

## 6. Deployment status

**Not deployed.** Nothing has been hosted. Local-dev Docker config exists but is untested. Before any real deployment: items 1–4 and 6 above,
HTTPS termination, a managed PostgreSQL with backups, externalized secrets, and restricted signup.

## 7. Archive contents

`KEYSTONE_FINAL.zip` excludes `node_modules/`, `target/`, `.git/`, `.env`, `secrets/` and also `frontend/dist/` (build output).

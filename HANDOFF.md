# KEYSTONE — Step 9 Handoff (React frontend UI for all four roles)

Starting point: `KEYSTONE_CHECKPOINT_08.zip`. Scope: real pages for MANAGER, DISPATCHER, TECHNICIAN and CUSTOMER on top
of the **existing** backend APIs and JWT login. **No backend file, migration, security rule or backend test was touched**
(verified with a recursive diff against Checkpoint 08: only `frontend/` and this file changed). No new backend features,
no deployment, no Docker.

## ⚠️ Read this first: what was and was NOT verified

The sandbox again has no npm registry access (`npm view react` → **403**), no Maven and no PostgreSQL, so:

| Requested check | Status |
|---|---|
| `npm install` / **`npm run build`** (`tsc -b && vite build`) | **NOT RUN** (vite, react-router-dom, axios, @types/* cannot be installed) |
| Real backend (`mvn`, PostgreSQL, Spring Boot) | **NOT RUN**; Steps 3–7 remain unverified, as in earlier handoffs |
| Unit tests (`npm test`) | **Run, 29/29 pass** (`tsx --test`): 7 from Step 8 + 22 new (validation, lifecycle rules, job ordering, formatting, navigation/role areas) |
| Type-check of all 56 non-test `.ts/.tsx` source files | **Run with substitutes**: `tsc --strict --noUnusedLocals --noUnusedParameters`, 0 errors, but against **hand-written type shims** for react / react-router-dom / axios, not the real `@types`. It proves my own types, imports and property names are consistent; it does not prove the real library typings accept every call |
| Login with each role, role-specific pages, CRUD flows, work-order lifecycle **in a browser** | **Run only against a stand-in, not the real stack**: the app was bundled with esbuild, with minimal sandbox replacements for `react-router-dom` and `axios`, and driven in headless Chromium (Playwright) against an in-page **fake backend** that re-implements the contract read from `SecurityConfig` and the services (role matrix, status graph, stock rules, time-log rules, 404-for-other-customer). Result: **83/83 checks pass, twice, no browser console errors** |

What the browser run covers: login (wrong password, per role, redirect, logout, 401 mid-session → login), role isolation
(`/unauthorized`, nav per role), customers/sites/parts create-edit-delete incl. backend 409 messages, form validation,
dispatcher create → assign, the full technician lifecycle (Start → parts → time → Hold → Resume → Complete) at a
390 px phone viewport (no horizontal scroll, ≥44 px action buttons), staff Close, customer add-site / raise request /
detail / status history / empty states, and an API-failure error state with "Try again".

What it does **not** prove: that the real backend behaves exactly like the fake (the fake was written from the Java source,
not from a running server), that the real `react-router-dom`/`axios`/Vite build behaves like the stand-ins, visual quality
in real mobile browsers, or anything about the SLA scheduler, Flyway or PostgreSQL. The fake backend and the esbuild harness
live in the sandbox only and are **not** in this ZIP.

**First action next time:** `cd frontend && npm install && npm run build && npm test`, fix whatever the real compiler reports
(most likely spots: strict typing of `onChange` helpers in `PartFormModal`, the `Field` render-prop spread, Axios header typing
in `api/client.ts` from Step 8). Then start the backend, create one user per role (see Step 8 §4 for the signup `curl`) and
run the manual checklist in §5 below. Also run `mvn clean package` (Steps 3–7 still never compiled).

## 1. Pages completed

All routes sit under the role's home path; `RoleRoute` is a UX guard only, the backend remains the authority.

| Role | Page | Route | Notes |
|---|---|---|---|
| MANAGER | Dashboard | `/manager` | summary tiles, counts by status, overdue + at-risk lists, technician workload |
| MANAGER | Customers | `/manager/customers` | search, paging, create / edit / **delete** |
| MANAGER | Sites | `/manager/sites` | search, customer filter, paging, create / edit / **delete** |
| MANAGER | Work Orders | `/manager/work-orders`, `/manager/work-orders/:id` | filters, assign, edit, cancel, close, **delete (NEW only)** |
| MANAGER | Parts / Inventory | `/manager/parts` | search, low-stock filter, create / edit / delete |
| MANAGER | (extra) New work order | `/manager/new-work-order` | same form as dispatcher; backend allows it, link from the list |
| DISPATCHER | Work Orders | `/dispatcher/work-orders`, `/:id` | `/dispatcher` redirects here; quick **Assign** on NEW rows |
| DISPATCHER | Create Work Order | `/dispatcher/new-work-order` | |
| DISPATCHER | Assign Technician | modal on list + detail | technician picker (see §3) |
| DISPATCHER | Customers, Sites | `/dispatcher/customers`, `/dispatcher/sites` | create/edit only (no delete: backend is MANAGER-only) |
| TECHNICIAN | My Jobs | `/technician/jobs` | tabs Active (assigned+in progress+on hold, soonest SLA first) / Completed / All |
| TECHNICIAN | Job Details, Start, Hold, Resume, Complete | `/technician/jobs/:id` | sticky bottom action bar; buttons shown only for valid next actions, optional note |
| TECHNICIAN | Parts Used, Time Logs, Status History | tabs on the job page | record/remove parts, log/delete own time, history timeline |
| CUSTOMER | My Sites | `/customer/sites` | list + add site |
| CUSTOMER | Raise Request | `/customer/requests/new` | |
| CUSTOMER | My Work Orders / Details / Status History | `/customer/work-orders`, `/:id` | status filter, read-only detail + history |

Cross-cutting: loading, error (with "Try again") and empty states on every data view (`LoadingState` / `ErrorState` /
`EmptyState` in `components/ui.tsx`); client-side validation (limits mirror the DTO annotations) before every submit, with the
backend's message shown if it still rejects; confirm dialogs for destructive/state-changing actions; tables become stacked cards
below 720 px; technician and customer screens are card/tab based with 44 px+ touch targets and a safe-area-aware action bar.
The Step 8 placeholder dashboards and `RoleDashboardPlaceholder` were removed (replaced by the real pages); `AppLayout` gained
role navigation.

## 2. API integrations (all existing endpoints; JWT via the Step 8 Axios interceptor)

| Module | Endpoints used |
|---|---|
| `api/customers.ts` | `GET/POST /api/customers`, `PUT/DELETE /api/customers/{id}` |
| `api/sites.ts` | `GET /api/sites`, `POST /api/customers/{id}/sites`, `PUT/DELETE /api/sites/{id}`; portal: `GET/POST /api/customer/sites` |
| `api/workOrders.ts` | `GET/POST /api/work-orders`, `GET/PUT/DELETE /api/work-orders/{id}`, `POST …/{id}/status` (assign, cancel, close), `POST …/{id}/start|hold|resume|complete`, `GET/POST/DELETE …/part-usage`, `GET/POST/DELETE …/time-logs` |
| `api/customerPortal.ts` | `GET/POST /api/customer/work-orders`, `GET /api/customer/work-orders/{id}` |
| `api/parts.ts` | `GET/POST /api/parts`, `PUT/DELETE /api/parts/{id}` |
| `api/dashboard.ts` | `GET /api/dashboard/summary`, `/sla`, `/technician-workload` (the Step 8 role stub fetch was removed with the placeholders) |
| Step 8, unchanged | `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/me` |

`src/types/domain.ts` mirrors the backend DTOs. Dates are sent as ISO instants (`datetime-local` → UTC); money is shown with 2 decimals.

## 3. Design decisions worth knowing

- **Technician picker for "Assign"** uses `GET /api/dashboard/technician-workload` because there is no "list users" endpoint
  and adding one was out of scope. It lists *every* technician with open/overdue counts (MANAGER/DISPATCHER only).
- **"Active" jobs for a technician** = three `GET /api/work-orders?status=…` calls merged client-side (the API takes one status).
- **Customers use the portal endpoints** (`/api/customer/**`), since staff endpoints for customers are 404/403 by design.
- **Lifecycle rules in the UI** (`lib/lifecycle.ts`) mirror the backend graph only to hide impossible buttons; the backend still
  validates and its 409/403/404 messages are displayed. Technicians can add/remove parts only on IN_PROGRESS/ON_HOLD and log time on
  IN_PROGRESS/ON_HOLD/COMPLETED, exactly as the services enforce.
- Staff see parts/time logs read-only; staff cannot add part usage from the UI even though the API permits MANAGER/DISPATCHER
  (requirement lists Parts Used under TECHNICIAN only).
- `useAsync` (hooks) is a tiny loader hook; no data-fetching library was added. **No new npm dependencies.**

## 4. Tests

- `npm test` (`tsx --test src/auth/*.test.ts src/lib/*.test.ts`): **29/29 pass**. New files: `lib/validation.test.ts`,
  `lib/lifecycle.test.ts`, `lib/jobs.test.ts`, `lib/format.test.ts`, `auth/nav.test.ts`.
- Strict type-check against shims and the 83-check headless-browser run against a fake backend: see the table at the top.
- Not run: `npm run build`, real-backend login/CRUD/lifecycle, `mvn` anything. No component-level or end-to-end tests are in the repo.

## 5. Manual checklist to run against the real stack (not yet done)

1. Create four users (`POST /api/auth/signup`; the CUSTOMER needs `companyName`) and at least one more TECHNICIAN to see the picker.
2. MANAGER: dashboard loads (summary/SLA/workload); create a customer → site; add a part; delete flows show backend 409s when blocked.
3. DISPATCHER: create a work order, assign it, edit it; no Delete buttons on customers/sites; `/manager` → "no access".
4. TECHNICIAN (phone or devtools mobile): My jobs shows only the assigned job → Start → record part (stock drops) → log time → Hold →
   Resume → Complete; Parts become read-only, time still loggable; History lists every step with notes.
5. MANAGER/DISPATCHER: Close the completed order; Cancel works from NEW/ASSIGNED/IN_PROGRESS/ON_HOLD.
6. CUSTOMER: My sites, add site, Raise request, open it, see history; another customer's work-order id shows "Work order not found".
7. Logout → `/login`; reusing the old token against the API returns 401.

## 6. Known issues / limitations

- **Nothing here has run against the real toolchain or backend** (top of file). Treat compile errors on first `npm run build` as likely, small and local.
- No ESLint config; the few `eslint-disable` comments are inert until one is added. No lockfile (carets as in Step 8).
- JWT still in `localStorage` (Step 8 issue); no client-side expiry check beyond the 401 handler.
- The work-order list has no technician/customer/site filters in the UI (API supports them); sorting is fixed (newest first).
- Customer and site lists load at most 100 customers in the pickers (backend max page size); beyond that the picker is truncated.
- Staff cannot record part usage on a technician's behalf from the UI (API allows it); customers cannot cancel/edit requests (no API).
- Time zone: time-log inputs use the browser's local time and are converted to UTC for the API.
- Backend legacy static pages (`/login.html`, …) and README were not touched; `README.md` still does not describe the React app.
- Staff dashboard for DISPATCHER does not exist as a separate page (requirement list had none); `/dispatcher` redirects to Work Orders.
- Signup UI still not built; `SignupRequest` still lets the caller choose any role (review before any public deployment).

## 7. Next step

1. `cd frontend && npm install && npm run build && npm test`; fix compile issues; `mvn clean package`; run §5 against a real backend.
2. Then Step 10 (suggested, not started): fix whatever §5 finds, add component/e2e tests (e.g. Vitest + Testing Library, Playwright
   against the real backend), then decide on deployment/Docker.

---

# Previous handoff (Step 8), kept for reference

# KEYSTONE — Step 8 Handoff (React frontend foundation + authentication UI)

Starting point: `KEYSTONE_CHECKPOINT_07.zip`. Scope: new `frontend/` app (React + TypeScript + Vite), Router, Axios
client, Login, JWT handling, Logout, protected + role-based routes, four placeholder dashboards. **Backend code,
security config and tests were not touched.** No customer/work-order/parts/SLA UI, no deployment.

## ⚠️ Read this first: npm install, build, login and backend connection were NOT run

This sandbox has no network: the npm registry returns **403 (`host_not_allowed`)**, and there is no Maven/PostgreSQL.
A real `npm install` was attempted and failed with `E403` on the first package, so:

| Requested check | Status |
|---|---|
| `npm install` | **Not run** (E403, registry blocked) |
| `npm run build` | **Not run** (needs vite, react-router, axios, @types/*; none installable here) |
| Type-check (`tsc -b`) | **Not run** (no React/Router/Axios type packages available) |
| Login / logout / protected routes / role routing in a browser | **Not run** |
| Real backend connection | **Not run** (backend cannot start here: no Maven, no PostgreSQL) |
| Unit tests of the pure auth logic (`roles.ts`, `storage.ts`) | **Run, 7/7 pass** (`tsx --test`) |
| Syntax parse of all 25 `.ts/.tsx` files | **Run, 0 errors** (esbuild) |

Everything else below is written against the backend source (`AuthController`, `AuthResponse`, `SecurityConfig`,
`JwtAuthEntryPoint`), not against a running server. `package.json` has no lockfile and uses caret ranges
(react 19, react-router-dom 7, axios 1.x, vite 7, @vitejs/plugin-react 5, typescript 5.x), so versions were never
resolved. **First action next time: `cd frontend && npm install && npm run build`** and fix whatever the compiler
reports. Vite 7 needs Node ≥ 20.19 (22.x is fine).

## 1. Frontend setup

- Location: `frontend/` (the Maven backend at the repo root is unchanged).
- Dev: `cd frontend && npm install && npm run dev` → http://localhost:5173
- The dev server **proxies `/api` to `http://localhost:8080`** (override `VITE_PROXY_TARGET`), so the browser sees one
  origin. For a build served elsewhere set `VITE_API_BASE_URL` to the backend URL (backend CORS already allows all
  origins with credentials; unchanged). Template: `frontend/.env.example` (real `.env*` files are git-ignored).
- Scripts: `dev`, `build` (`tsc -b && vite build`), `preview`, `typecheck`, `test` (`tsx --test src/auth/*.test.ts`).
- Root `.gitignore`: added `node_modules/` and `dist/` (only change outside `frontend/` besides this file).

## 2. Files created (`frontend/`)

| Path | Purpose |
|---|---|
| `package.json`, `tsconfig*.json`, `vite.config.ts`, `index.html`, `.env.example`, `.gitignore` | Tooling/config |
| `src/main.tsx`, `src/App.tsx`, `src/index.css` | Entry, route table, minimal styling |
| `src/types/auth.ts` | `Role`, `AuthResponse`, `AuthUser` |
| `src/api/client.ts` | Axios instance, Bearer interceptor, 401 handling, `getErrorMessage` |
| `src/api/auth.ts` | `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/me` |
| `src/api/dashboard.ts` | The existing role stub endpoints (`/api/dashboard/manager\|dispatcher\|worker\|customer`) |
| `src/auth/roles.ts` | Role → home path, `canAccessPath`, `postLoginPath` (pure) |
| `src/auth/storage.ts` | localStorage token/user helpers (pure, validates stored data) |
| `src/auth/AuthContext.tsx` | `AuthProvider` + `useAuth()` (login, logout, session restore) |
| `src/components/ProtectedRoute.tsx`, `RoleRoute.tsx`, `AppLayout.tsx`, `FullPageMessage.tsx`, `RoleDashboardPlaceholder.tsx` | Route guards, header with Log out, placeholder body |
| `src/pages/LoginPage.tsx`, `ManagerDashboardPage.tsx`, `DispatcherDashboardPage.tsx`, `TechnicianDashboardPage.tsx`, `CustomerDashboardPage.tsx`, `UnauthorizedPage.tsx`, `NotFoundPage.tsx`, `RootRedirect.tsx` | Pages |
| `src/auth/roles.test.ts`, `storage.test.ts` | 7 unit tests |

Routes: `/login` (public) · `/` → role home or login · `/manager` · `/dispatcher` · `/technician` · `/customer`
(each only for its role) · `/unauthorized` · `*` → not found.

## 3. Login flow

1. `LoginPage` posts `{email, password}` to `/api/auth/login`. Backend returns `{token, id, fullName, email, role, companyName}`.
2. Token and user are stored in `localStorage` (`keystone_token`, `keystone_user`). The Axios request interceptor adds
   `Authorization: Bearer <token>` to every request.
3. The user is sent to the page they originally asked for if their role may see it, else their role home
   (MANAGER→`/manager`, DISPATCHER→`/dispatcher`, TECHNICIAN→`/technician`, CUSTOMER→`/customer`).
4. `ProtectedRoute` redirects anonymous visitors to `/login` (remembering the target). `RoleRoute` sends a signed-in
   user of the wrong role to `/unauthorized`. **These are UX guards only; the backend remains the authority** (it
   already returns 403 per role).
5. On page load a stored session is checked with `GET /api/me`. A 401 (expired/blacklisted) clears the session and
   returns to login; other failures (e.g. backend down) keep the session.
6. **Logout**: `POST /api/auth/logout` (backend blacklists the token), then local session is cleared and the app goes
   to `/login`, even if the call fails. Logging out in another tab also signs this tab out (`storage` event).
7. Each placeholder dashboard makes one authenticated call to its role's existing stub endpoint and shows the backend's
   greeting ("Backend connection: Welcome, …") or the error.
8. Error messages come from the backend `ApiError.message`; "Cannot reach the server" when there is no response.

## 4. Tests

- Run: `roles.test.ts` (4) and `storage.test.ts` (3), **7/7 pass** — role→path map, role validation, path-prefix
  tricks (`/managerial` ≠ `/manager`), redirect choice, token/user round-trip, clear, corrupt/tampered stored user.
- **Not run:** everything in the table at the top. No component or end-to-end tests exist yet.
- Manual checklist to run once the toolchain is available (backend running on 8080, one user per role; there is no
  seed data, create users with `POST /api/auth/signup` e.g.
  `curl -X POST localhost:8080/api/auth/signup -H 'Content-Type: application/json' -d '{"fullName":"Mia","email":"mia@x.io","password":"secret1","role":"MANAGER"}'`;
  CUSTOMER also needs `companyName`):
  1. Open `/manager` signed out → lands on `/login`.
  2. Wrong password → red error "Invalid email or password"; correct → role dashboard + "Backend connection: Welcome…".
  3. As MANAGER open `/technician` → `/unauthorized`. Repeat per role.
  4. Reload while signed in → stays signed in. Log out → `/login`; Back button / `/manager` → `/login`.
  5. Reusing the old token against the API returns 401 (blacklisted).

## 5. Known issues

- **Nothing was built or run in a browser** (see top). Most likely first problems: version resolution in
  `package.json`, a strict-TS complaint in `src/api/client.ts` (Axios header typing) or the `useEffect` in
  `AuthContext.tsx`.
- JWT is kept in `localStorage` (matches the existing static pages; readable by any XSS). An httpOnly-cookie design would
  need backend changes, which were out of scope.
- No client-side token-expiry check; expiry is discovered by the `/api/me` check or the next 401.
- `/api/me` does not return `companyName`; the frontend keeps the value from login.
- Signup UI not built (not requested). Existing backend `SignupRequest` lets the caller choose any role; unchanged
  and worth reviewing before any public deployment.
- Backend still serves its legacy static pages (`/login.html`, `/dashboard.html`); they use different storage keys and
  do not interact with the React app.
- No ESLint config; no component tests; `README.md` not updated.

## 6. Next step

1. `cd frontend && npm install && npm run build && npm test`; fix any compile errors. Start the backend and run the
   manual checklist in §4. Also finally run `mvn clean package` (Steps 3–7 still unverified).
2. Then Step 9: first real UI slice, e.g. work-order list/detail for staff and technician, using the existing `api`
   client and role routes.

---

# Previous handoff (Step 7), kept for reference

# KEYSTONE — Step 7 Handoff (SLA + Dashboard APIs)

Starting point: Step 6 code (`KEYSTONE_CHECKPOINT_06.zip`; no new ZIP was attached to this request, so the
Step 6 ZIP from the previous turn was used). Scope: SLA calculation/status/scheduled checks and backend
dashboard/report APIs. React, frontend dashboard and deployment were **not** touched.

## ⚠️ Read this first: build and tests were NOT run

Same environment limit as Steps 1–6: no Maven, no Maven cache, no network. `mvn test` /
`mvn clean package` **could not be executed**. What was done instead: balanced braces/parens check on every Java
file, every `com.keyStone...` import resolved, and each new class re-read against the APIs it calls. Compilation,
JPQL/constructor-expression validation, the V5 migration against PostgreSQL, and **all tests (old and new)** are
unverified. Run `mvn clean package` first. Expected total: 118 (Steps 3–6) + 52 (new) = **170**.

## 1. Changes made

- **SlaService** (all SLA business rules; controllers/repositories hold none): due-time calculation, live status
  evaluation, recorded-state maintenance, priority-change handling. Takes an injectable `Clock`.
- **SlaMonitorService + SlaScheduler**: scheduled sweep that re-evaluates open work orders and records the first
  time each became AT_RISK / BREACHED. Scheduler is a thin trigger (no logic), switchable by property.
- **DashboardService + DashboardReportController**: three read-only report endpoints (see §3).
- **Work orders**: `slaStatus` added to `WorkOrderResponse` and `WorkOrderDetailResponse` (staff API and customer
  portal). `slaDueAt` already existed.
- **Consolidation (not new behavior)**: the due-time calculation was duplicated in `WorkOrderService` and
  `CustomerPortalService` (same 4/24/48/72h values). Both now call `SlaService`; the duplicates were removed.
- **Schema**: `V5__sla_tracking.sql` (V1–V4 untouched) adds `completed_at`, `sla_status`, `sla_at_risk_at`,
  `sla_breached_at` to `work_orders`, backfills `completed_at`/final SLA outcome for already-finished work from
  the status history, and indexes `sla_due_at`.
- **Lifecycle hook**: `WorkOrderService.transitionStatus` stamps `completedAt` on the move to COMPLETED and
  re-records SLA state on every transition. `update()` re-times SLA when priority changes (rules below).
- Small supporting edits: `UserRepository.findByRole`, `WorkOrderRepository` aggregation queries, new `Clock` bean.

### Files
| File | Change |
|---|---|
| `db/migration/V5__sla_tracking.sql` | **New** |
| `entity/SlaStatus.java`, `entity/WorkOrder.java` | New enum; 4 new fields on WorkOrder |
| `config/SlaConfig.java` | **New** (`Clock` bean, `@EnableScheduling`) |
| `service/SlaService.java`, `SlaMonitorService.java`, `SlaScheduler.java`, `SlaCheckResult.java`, `DashboardService.java` | **New** |
| `controller/DashboardReportController.java` | **New** |
| `repository/WorkOrderSlaRow.java` | **New** (read model) |
| `dto/SlaSummary, DashboardSummaryResponse, SlaWorkOrderItem, SlaReportResponse, TechnicianWorkload, TechnicianWorkloadResponse.java` | **New** |
| `service/WorkOrderService.java`, `CustomerPortalService.java` | SLA wiring; duplicated calc removed |
| `dto/WorkOrderResponse.java`, `WorkOrderDetailResponse.java` | + `slaStatus` |
| `repository/WorkOrderRepository.java`, `UserRepository.java` | + queries / `findByRole` |
| `config/SecurityConfig.java` | + 3 matchers |
| `application.properties`, `.env.example` | + `app.sla.*` settings |
| `src/test/resources/application.properties` | + `app.sla.scheduler.enabled=false` |
| tests | 5 new classes + `AbstractApiTest` base (see §4) |

## 2. SLA rules

- **Target** (hours from creation): CRITICAL 4, HIGH 24, MEDIUM 48, LOW 72 — configurable via
  `app.sla.critical-hours|high-hours|medium-hours|low-hours`. `slaDueAt = createdAt + target`.
- **Open work** (NEW, ASSIGNED, IN_PROGRESS, ON_HOLD). **The clock keeps running while ON_HOLD.**
  - `BREACHED`: now is strictly after `slaDueAt` (the due instant itself is not late).
  - `AT_RISK`: not breached and time remaining ≤ `app.sla.at-risk-percent` (default **25%**, inclusive) of the
    whole window (`slaDueAt − createdAt`). Integer arithmetic, no floating point.
  - `ON_TRACK`: otherwise.
- **Finished work** (COMPLETED, CLOSED) is judged by `completedAt`: on/before due → `ON_TRACK` (met), after →
  `BREACHED`. Never AT_RISK, and later passage of time never changes it. Unknown `completedAt` → no status.
- **CANCELLED** or no due time → no status (`slaStatus` is null/absent in JSON).
- **Priority change** (`PUT /api/work-orders/{id}`): open work is re-timed from its original `createdAt` with the
  new priority's target, **unless it is already BREACHED** (a breach cannot be edited away by lowering priority).
  Finished work is never re-timed. Raising priority on old work can breach it immediately.
- **API value vs recorded value**: API responses and dashboards use the **live** evaluation (never stale).
  `work_orders.sla_status` / `sla_at_risk_at` / `sla_breached_at` are the *recorded* state, maintained on
  create / priority change / status change and by the scheduler; the `*_at` columns are **detection** times.
- **Overdue** = open and past due now. **SLA breaches** = overdue + finished work completed late.
  **Compliance** = met / (met + breaches) × 100, 2 decimals; open-not-yet-due work is undecided and excluded;
  `null` when nothing is decided yet. **Completed** = COMPLETED + CLOSED (cancelled reported separately).
- **Scheduler**: every `app.sla.check-interval-ms` (default 60 000, initial delay 30 000) it loads open work whose
  recorded status is null or not BREACHED, re-evaluates it, and records AT_RISK/BREACHED transitions (logging a
  WARN per breach). Idempotent. Disable with `app.sla.scheduler.enabled=false` (this is the test default).

## 3. APIs added (all `GET`, `Authorization: Bearer <jwt>`; **MANAGER and DISPATCHER only** — others 403, no token 401)

| Path | Returns |
|---|---|
| `/api/dashboard/summary` | `totalWorkOrders`, `openWorkOrders`, `completedWorkOrders`, `cancelledWorkOrders`, `overdueWorkOrders`, `countsByStatus{}`, `sla{onTrackOpen, atRisk, breachedOpen, breachedCompleted, totalBreaches, metOnTime, compliancePercent}` |
| `/api/dashboard/sla` | `summary` (same block) + `breachedWorkOrders[]` (most overdue first, `minutesOverdue`) + `atRiskWorkOrders[]` (soonest due first, `minutesRemaining`); each capped at `listLimit` = 50 |
| `/api/dashboard/technician-workload` | `technicians[]` (every technician incl. idle; `assigned`, `inProgress`, `onHold`, `openTotal`, `overdue`, `atRisk`, `completed`; busiest first) + `unassignedOpen` |

Existing: `/api/dashboard/manager|dispatcher|worker|customer` stub endpoints are unchanged.
Changed responses: `slaStatus` (`ON_TRACK|AT_RISK|BREACHED`, absent for cancelled) added to work-order list/detail,
status-transition and customer-portal responses.

New settings: `app.sla.critical-hours`, `high-hours`, `medium-hours`, `low-hours`, `at-risk-percent`,
`scheduler.enabled`, `check-interval-ms`, `check-initial-delay-ms` (env vars `SLA_*`, see `.env.example`).

## 4. Tests (52 new — NOT RUN)

- `service/SlaServiceTest` (21, plain JUnit, fixed clock): due calc per priority, configurable/invalid config,
  open-work boundaries (exactly 25% left, due instant, +1 ms), ON_HOLD, finished work, cancelled/untimed,
  recorded-state first-seen times, all priority-change rules.
- `service/DashboardServiceTest` (5, Mockito): status-group counts, overdue/breaches/compliance, compliance
  arithmetic incl. empty case, SLA report ordering/minutes, technician workload incl. idle technicians.
- `SlaIntegrationTest` (16): due by priority (staff + customer portal), live status on detail/list/technician view,
  on-time and late completion, completed work immune to elapsed time, cancelled has no status, priority change
  (retime / cannot hide breach / immediate breach / no retime after completion), **breach detection via the
  sweep** (at-risk, breached, idempotent, escalation keeps first-seen times, ignores cancelled/finished),
  scheduler bean off in test profile.
- `SlaSchedulerIntegrationTest` (3): scheduler enabled with a 200 ms interval — `@Scheduled` present and the
  recorded status flips to BREACHED / AT_RISK **with no API call** (Awaitility, 20 s cap). Uses
  `@DirtiesContext` so the background thread stops afterwards.
- `DashboardReportIntegrationTest` (7): authorization matrix (manager/dispatcher 200, technician/customer 403,
  anonymous 401, on all 3 endpoints), summary deltas for open/completed/cancelled/overdue/breaches/by-status,
  compliance, SLA report lists, technician workload, existing stub endpoints still work.
- `AbstractApiTest`: shared helpers. Since the clock is real, integration tests move the SLA window by writing
  the DB (`setDue` via entity, `setWindow` via native SQL because `created_at` is not updatable).
- The H2 DB is shared across test classes, so dashboard assertions on totals are before/after **deltas**.

## 5. Known issues

- **Build/tests unverified** (top of file). Most likely first failures: JPQL constructor expression with the
  `WorkOrderSlaRow` record, the native `created_at` update in `AbstractApiTest.setWindow`, and the timing in
  `SlaSchedulerIntegrationTest`.
- **V5 not exercised**: tests run on H2 with `ddl-auto=update`, Flyway disabled; V5 SQL has never run against
  PostgreSQL (V1–V4 same situation). The backfill assumes `work_order_status_history.to_status = 'COMPLETED'`.
- **SLA clock does not pause on ON_HOLD** (deliberate, documented rule); no business-hours calendar.
- **Dashboard scale**: open work orders are loaded as lightweight rows and evaluated in memory; counts by status
  and finished-work compliance are SQL. Fine for thousands of open orders, not millions. No date-range filter.
- **Scheduler sweep** loads all candidate entities in one transaction and has no cluster lock: with several app
  instances each would run the sweep (harmless, idempotent, but redundant). A sweep bumps `updatedAt` on rows it
  changes.
- Technician workload is staff-only; a technician cannot call it for themselves.
- `completed_at` is set only by `WorkOrderService.transitionStatus` (the only path to COMPLETED).
- `README.md` still lacks API docs for Steps 4–7. Carried over from Step 6: job-action race, no time-log overlap
  check, no stock-movement history.

## 6. Next step

1. Run `mvn clean package`; fix compile/test failures (170 expected). Apply V1–V5 to a real PostgreSQL once.
2. Next slice (React/frontend dashboard consuming these APIs, or deployment). Candidates before that:
   date-range filters on the dashboard, an admin "run SLA check now" endpoint, SLA breach notifications.

---

# Previous handoff (Step 6), kept for reference

## KEYSTONE — Step 6 Handoff (Technician operations + Parts + Time Logs)

Starting point: `KEYSTONE_CHECKPOINT_05.zip`. Scope: technician job actions, Parts inventory
CRUD, Part Usage on work orders (transactional stock), Time Logs. SLA, Dashboard, React and
deployment were **not** touched.

## ⚠️ Read this first: build and tests were NOT run

Same environment limit as Steps 1–5: this sandbox has no Maven, no Maven cache and no network,
so `mvn test` and `mvn clean package` **could not be executed**. All new Java files were checked
for balanced braces/parens and that every `com.keyStone...` import resolves to an existing class,
and were re-read against the APIs they call — but compilation, JPA/JPQL validation, and every test
(including the 27 new ones) are **unverified**. Run `mvn clean package` first.
Expected total: 91 (Steps 3–5) + 27 (new) = **118** tests.

## 1. Changes made

- **Technician job actions** — `start`, `hold`, `resume`, `complete`. Thin, strictly-checked wrappers
  over `WorkOrderService.transitionStatus`, so the Step 5 status graph, role check and StatusHistory
  row are reused (no parallel lifecycle logic). Each action requires an exact current status (see §3).
- **Parts inventory CRUD** — `Part` entity (`sku` unique/upper-cased, `name`, `description`, `unit`,
  `quantityOnHand`, `reorderLevel`, `unitCost`), paged/searchable list with `lowStock` filter.
- **Part usage** — `PartUsage` entity; usage is recorded in batches and decrements stock in the same
  transaction. Unit cost is snapshotted at time of use. A usage can be removed, which returns the stock.
- **Time logs** — `TimeLog` entity (technician, startedAt, endedAt, computed minutes, note).
- **Schema** — new migration `V4__parts_part_usage_time_logs.sql` (V1–V3 untouched). Includes
  `CHECK (quantity_on_hand >= 0)` as a database-level backstop.
- **Authorization** — technician access limited to assigned work orders by reusing
  `WorkOrderService.findAccessible` (visibility changed from `private` to package-private; no behavior
  change). Not-assigned = `404`, same as Step 5.

### Files
| File | Change |
|---|---|
| `db/migration/V4__parts_part_usage_time_logs.sql` | **New** |
| `entity/Part.java`, `PartUsage.java`, `TimeLog.java` | **New** |
| `repository/PartRepository.java`, `PartUsageRepository.java`, `TimeLogRepository.java` | **New** |
| `dto/PartRequest/PartResponse/PartUsageItem/PartUsageRequest/PartUsageResponse/TimeLogRequest/TimeLogResponse/TimeLogSummaryResponse/JobActionRequest.java` | **New** |
| `service/PartService.java`, `PartUsageService.java`, `TimeLogService.java`, `TechnicianJobService.java` | **New** |
| `controller/PartController.java`, `WorkOrderResourceController.java` | **New** |
| `config/SecurityConfig.java` | Added Step 6 matchers |
| `service/WorkOrderService.java` | `findAccessible` `private` → package-private (one word) |
| `test/.../TechnicianOperationsIntegrationTest.java` | **New** — 27 tests |
| `HANDOFF.md` | This file |

## 2. APIs added

All require `Authorization: Bearer <jwt>`.

**Technician job actions** (`TECHNICIAN` only; body optional: `{"note": "..."}`; returns work order detail + history)

| Method | Path | Requires status | Result |
|---|---|---|---|
| POST | `/api/work-orders/{id}/start` | ASSIGNED | IN_PROGRESS |
| POST | `/api/work-orders/{id}/hold` | IN_PROGRESS | ON_HOLD |
| POST | `/api/work-orders/{id}/resume` | ON_HOLD | IN_PROGRESS |
| POST | `/api/work-orders/{id}/complete` | IN_PROGRESS | COMPLETED |

**Parts** (read: MANAGER/DISPATCHER/TECHNICIAN; create/update: MANAGER/DISPATCHER; delete: MANAGER; CUSTOMER: none)

| Method | Path | Notes |
|---|---|---|
| POST | `/api/parts` | `{sku, name, description?, unit, quantityOnHand, reorderLevel, unitCost}` → 201; duplicate SKU 409 |
| GET | `/api/parts` | `search` (name/sku), `lowStock=true`, `page`, `size`, `sortBy` (`id|sku|name|quantityOnHand|unitCost|createdAt|updatedAt`), `direction` |
| GET | `/api/parts/{id}` | |
| PUT | `/api/parts/{id}` | same body as create (row-locked) |
| DELETE | `/api/parts/{id}` | 409 once the part has any recorded usage |

**Part usage** (MANAGER/DISPATCHER/TECHNICIAN; technician: assigned work orders only)

| Method | Path | Notes |
|---|---|---|
| POST | `/api/work-orders/{id}/part-usage` | `{items:[{partId, quantity}], note?}` → 201 list; each item has `remainingStock`. WO must be IN_PROGRESS/ON_HOLD |
| GET | `/api/work-orders/{id}/part-usage` | |
| DELETE | `/api/work-orders/{id}/part-usage/{usageId}` | returns stock; technician only while IN_PROGRESS/ON_HOLD |

**Time logs**

| Method | Path | Notes |
|---|---|---|
| POST | `/api/work-orders/{id}/time-logs` | `{startedAt, endedAt, note?}` (ISO-8601 instants). **TECHNICIAN only**, assigned WO, status IN_PROGRESS/ON_HOLD/COMPLETED |
| GET | `/api/work-orders/{id}/time-logs` | `{entries[], totalMinutes}`; MANAGER/DISPATCHER/TECHNICIAN |
| DELETE | `/api/work-orders/{id}/time-logs/{logId}` | technician: own entries only (others' = 404); MANAGER/DISPATCHER: any |

Time-log validation (400): `endedAt` after `startedAt`, not in the future (60s skew), ≥ 1 minute, ≤ 24 h.

## 3. Transaction rules

1. `PartUsageService.record` is one `@Transactional` unit for the **whole request**. Every item's stock
   decrement and usage row commit together; any failure (unknown part → 404, insufficient stock → 409)
   throws a `RuntimeException`, rolling back **all earlier items in that request**.
2. Stock decrement is a single conditional SQL update (`PartRepository.decrementStock`:
   `... where id = :id and quantity_on_hand >= :qty`). The check and the write are one atomic statement, so
   concurrent requests cannot both take the last units. `0` rows updated ⇒ `409 Insufficient stock`.
3. Stock can never be negative: (a) the conditional update, (b) request validation (`quantity >= 1`,
   `quantityOnHand >= 0`), (c) the DB `CHECK` in V4.
4. Removing a usage runs `incrementStock` + deletes the usage row in one transaction.
5. `PUT /api/parts/{id}` loads the row with `PESSIMISTIC_WRITE`, so an absolute quantity overwrite cannot
   interleave with a concurrent decrement.
6. Part usage is only allowed on IN_PROGRESS / ON_HOLD work orders (staff included); time logs also on
   COMPLETED (late entry).
7. Job actions run in one transaction with the status change and its StatusHistory row (Step 5 behavior).

## 4. Tests (`TechnicianOperationsIntegrationTest`, 27 tests — NOT RUN)

- **Job actions (5)**: full start→hold→resume→complete with 6 history rows; no-body request; wrong-state
  409s; other technician's WO 404 on all four; MANAGER/DISPATCHER/CUSTOMER 403 + unauthenticated 401.
- **Parts CRUD (8)**: create + SKU normalization; duplicate SKU 409; validation 400s; get/list/search/
  lowStock/paging/bad sort; update (+ duplicate SKU, 404, negative qty); delete (manager-only, blocked once
  used); role matrix (dispatcher/technician/customer/unauthenticated).
- **Part usage (9)**: stock decrease + cost snapshot + listing; insufficient stock 409 with stock unchanged;
  exactly-zero then rejected; **batch rollback** (valid item + failing item ⇒ nothing applied; unknown part ⇒
  rollback; valid batch commits); **8 concurrent requests on stock 5 ⇒ exactly 5 succeed**; invalid
  requests; active-status rule; technician sees only assigned WOs; manager/dispatcher allowed, customer 403;
  remove returns stock.
- **Time logs (5)**: minutes + totals; invalid ranges; status rules; authorization; delete ownership.

Tests run without a surrounding test transaction, so commits/rollbacks are real.

## 5. Known issues

- **Build and tests unverified** (top of file). The concurrency test is the most environment-sensitive
  (H2 row locking); if it flakes, check it before suspecting the service.
- **Job-action race**: two simultaneous `start` calls can both pass the status check (no row lock on the
  work order). Low impact (one technician per job); add `PESSIMISTIC_WRITE` on the work order if needed.
- **No overlap check** on time logs (a technician can log overlapping periods).
- **No stock adjustment/receiving endpoint** other than `PUT /api/parts/{id}` (absolute quantity, no audit
  trail / stock-movement history).
- **Removing a usage by a technician** is allowed while IN_PROGRESS/ON_HOLD; no separate audit record.
- Bare `403` bodies (Spring default) for role-denied calls, as in Steps 4–5.
- `README.md` still lacks API reference for Steps 4–6.
- `GlobalExceptionHandler` has no handler for `DataIntegrityViolationException`; only the Part SKU race is
  caught explicitly.
- Carried over: in-memory `TokenBlacklistService`, placeholder default `app.jwt.secret`, no technician
  reassignment endpoint (Step 5 §5).

## 6. Next step

1. Run `mvn clean package` / `mvn test`; fix any compile/test failures (118 expected).
2. Then the next slice (SLA tracking, Dashboard, React, or deployment), each schema change as `V5__...`.
3. Optionally: stock-movement history, time-log overlap check, technician reassignment.

## 7. How to run

```bash
cp .env.example .env && export $(grep -v '^#' .env | xargs)
mvn clean package     # runs tests (H2, no Postgres needed)
mvn spring-boot:run   # Flyway applies V1–V4 on startup
```

---

# Previous handoff (Step 5), kept for reference

## KEYSTONE — Step 5 Handoff (Work Order management + lifecycle)

Starting point: `KEYSTONE_CHECKPOINT_04.zip`. Scope of this step: Work Order CRUD and
status lifecycle only. Parts, TimeLog, SLA tracking, Dashboard and React were **not**
touched.

## ⚠️ Read this first: build and tests were NOT run

Same environment limit as Steps 1–4: this sandbox has no Maven, no Maven cache and no
network (`apt-get install maven` still fails with `403 Forbidden`), so `mvn test` and
`mvn clean package` **could not be executed**. Every Java file (main + test) was checked
for balanced braces/parens and manually re-read against the classes/methods it calls, but
type/symbol resolution, JPA query derivation and actual test execution could not be
verified. **The next developer must run `mvn clean package` and `mvn test` before
building on this checkpoint**, and fix anything that fails (expected: 28 new Work Order
tests + the 45 Customer/Site tests from Step 4 + the 17 auth tests from Step 3 +
`contextLoads` = 91 total).

## 0. A pre-existing gap this step had to close

The checkpoint already had the `WorkOrder` / `WorkOrderStatusHistory` entities, their
repositories, and their DTOs from an earlier step (used only by the customer-portal
"raise a service request" flow) — but **no way for staff to look up a technician's user
id**, which is required to assign one to a work order. `AuthResponse` (signup/login) and
`GET /api/me` only ever returned `fullName`/`email`/`role`(/`companyName`), never `id`.
Fixed by adding `id` to both — a minimal, necessary enabler for task 2 ("connect Work
Orders to … Technician"), not a new feature. See §1 for the exact diff.

## 1. Changes made

- **Status rename**: `WorkOrderStatus.RESOLVED` → `COMPLETED`, to match this step's
  required status list. New migration `V3__rename_resolved_to_completed.sql` (follows
  the same pattern as Step 3's `V2__rename_roles.sql` — a new migration, not an edit to
  `V1`). Frontend (`customer-portal.html`, `style.css`) and `README.md` updated to match;
  `V1__init_core_schema.sql` itself is untouched (historical).
- **Work Order CRUD** (`WorkOrderController` → `WorkOrderService`): create, paginated
  list/search/filter, get-by-id-with-history, update (title/description/priority only),
  delete. New endpoints under `/api/work-orders`, separate from the existing
  `/api/customer/work-orders` portal endpoints (unchanged — that's still how a CUSTOMER
  raises a service request).
- **Connected to Customer, Site, Technician**: a work order's `customer` is derived from
  its `site` at creation (`site.getCustomer()`), exactly like the existing portal flow;
  `assignedTechnician` is set only via the `ASSIGNED` status transition (see below) —
  there is no separate "assign" endpoint, to keep one place responsible for "who may
  attach a technician to a work order and when."
- **Priority and status**: already modeled (`Priority`, `WorkOrderStatus` enums); nothing
  new needed there beyond the rename above.
- **Statuses**: `NEW, ASSIGNED, IN_PROGRESS, ON_HOLD, COMPLETED, CLOSED, CANCELLED` — all
  seven implemented, as required.
- **Valid status transitions enforced in the service layer** (`WorkOrderService`, not the
  controller or the DB): see §3. Both the from→to shape of the transition *and* which
  role may trigger it are checked server-side; an invalid transition is `409`, a
  disallowed role is `403`.
- **StatusHistory for every successful transition**: written inside the same
  `@Transactional` method, right after the status field is saved; a rejected transition
  (invalid shape, wrong role, or missing/invalid technician) throws before anything is
  written, so there is never a partial or inconsistent history row.
- **Role-based authorization**: path/method gates in `SecurityConfig` (who may call each
  endpoint at all) plus row-level scoping in `WorkOrderService` (whose data a given call
  actually reaches) — same two-layer pattern as Step 4's Customer/Site.
- **Technician scoping**: `CallerScope.technicianIdOrNull()` (new, alongside the existing
  `customerIdOrNull()`) returns the caller's own user id when the role is `TECHNICIAN`.
  `WorkOrderService.findAccessible()` uses it so a technician reaching for a work order
  not assigned to them gets the same `404` as a nonexistent id — never a `403` that would
  confirm the work order exists. This applies uniformly to `GET /{id}`,
  `POST /{id}/status`, and (implicitly) to the list endpoint, which is always pre-filtered
  to their own assigned work orders regardless of any `technicianId` query param they
  pass.
- **Customer isolation**: `CallerScope.customerIdOrNull()` (already existed) applied the
  same way — a `CUSTOMER` only ever lists/reads their own work orders, same `404` rule. A
  `CUSTOMER` cannot reach `POST /api/work-orders`, `PUT`, `DELETE`, or
  `POST /{id}/status` at all (`403` at the `SecurityConfig` level — they never even reach
  the service for those).
- **`id` added to `AuthResponse` and `GET /api/me`** (see §0) — the only change to
  existing Step 3/4 response shapes; purely additive, nothing existing was removed or
  renamed.
- **Shared DTOs populated more fully**: `WorkOrderResponse` / `WorkOrderDetailResponse`
  gained `customerId`, `siteId`, `assignedTechnicianId` (previously only the *name*
  strings were exposed); `StatusHistoryResponse` gained `changedByEmail` /
  `changedByRole`. These are the same DTOs the pre-existing `CustomerPortalService` uses,
  so its two builder call sites were updated to populate the new fields too — otherwise
  the old portal responses would have these fields present-but-always-null while the new
  API's responses have them populated, which would be a confusing inconsistency in a
  shared type.
- **No change** to Parts, TimeLog, SLA (not modeled — out of scope), Dashboard, or any
  React/frontend behavior beyond the two string replacements noted above.

### Files

| File | Change |
|---|---|
| `controller/WorkOrderController.java` | **New** |
| `service/WorkOrderService.java` | **New** |
| `dto/WorkOrderStatusUpdateRequest.java` | **New** |
| `security/CallerScope.java` | Added `technicianIdOrNull(...)` |
| `entity/WorkOrderStatus.java` | `RESOLVED` → `COMPLETED` |
| `db/migration/V3__rename_resolved_to_completed.sql` | **New** |
| `dto/WorkOrderRequest.java` | Added `@Size` limits (200 / 2000) matching the DB columns |
| `dto/WorkOrderResponse.java`, `WorkOrderDetailResponse.java` | Added `customerId`, `siteId`, `assignedTechnicianId` |
| `dto/StatusHistoryResponse.java` | Added `changedByEmail`, `changedByRole` |
| `dto/AuthResponse.java` | Added `id` |
| `service/AuthService.java` | Populate the new `id` field (2 builder call sites) |
| `controller/DashboardController.java` | `GET /api/me` now also returns `id` |
| `service/CustomerPortalService.java` | Populate the new response fields (§1); no behavior change |
| `repository/WorkOrderRepository.java` | Now also `JpaSpecificationExecutor`; added `findByIdAndAssignedTechnicianId`, `@EntityGraph` paged `findAll` |
| `repository/WorkOrderStatusHistoryRepository.java` | Added `deleteByWorkOrderId` (used only when deleting a `NEW` work order) |
| `config/SecurityConfig.java` | Added Step 5 matchers (8 lines) |
| `static/customer-portal.html`, `static/css/style.css`, `README.md` | `RESOLVED` → `COMPLETED` string updates |
| `test/.../WorkOrderIntegrationTest.java` | **New** — 28 tests |
| `HANDOFF.md` | This file |

No entity, repository, or DTO for Parts, TimeLog, or SLA was added. No Dashboard or React
work was done.

## 2. Work Order APIs

All require `Authorization: Bearer <jwt>` (`401` if missing/invalid).

| Method | Path | Purpose | Success |
|---|---|---|---|
| POST | `/api/work-orders` | Create `{siteId, title, description?, priority}` — customer is derived from the site | 201 |
| GET | `/api/work-orders` | List/search/filter work orders | 200 page |
| GET | `/api/work-orders/{id}` | Get one work order, with full status history | 200 |
| PUT | `/api/work-orders/{id}` | Update `{siteId, title, description?, priority}` — `siteId` in the body is ignored, the site/customer can never change | 200 |
| DELETE | `/api/work-orders/{id}` | Delete — only while status is still `NEW` | 204 |
| POST | `/api/work-orders/{id}/status` | Transition status `{status, technicianId?, note?}` — `technicianId` is required (and only used) when `status == ASSIGNED` | 200 + full detail incl. history |

**List query params**: `search` (title or code, case-insensitive contains), `status`,
`priority`, `customerId`, `siteId`, `technicianId` (staff only — see §4), `page` (0-based,
default 0), `size` (1–100, default 20), `sortBy`
(`id|code|title|priority|status|createdAt|updatedAt|slaDueAt`, default `createdAt`),
`direction` (`asc|desc`). Response: `{content[], page, size, totalElements, totalPages}`.

`WorkOrderResponse` fields: `id, code, title, customerId, customerName, siteId, siteName,
priority, status, assignedTechnicianId, assignedTechnician, slaDueAt, createdAt`.
`WorkOrderDetailResponse` adds `description, updatedAt, history[]`, where each history
entry is `{fromStatus, toStatus, note, changedByEmail, changedByRole, changedAt}`
(`fromStatus` is `null` for the creation entry).

**Status codes**: `400` validation / bad transition payload, `401` unauthenticated, `403`
role not allowed to call the endpoint or to set that particular target status, `404` not
found / not yours / not assigned to you, `409` invalid status transition or a blocked
delete.

The existing `CustomerPortalService`/`CustomerPortalController` endpoints
(`/api/customer/sites`, `/api/customer/work-orders`, `/api/customer/work-orders/{id}`)
are unchanged — a `CUSTOMER` still raises a service request there, not through
`POST /api/work-orders`.

## 3. Lifecycle rules

### Status graph (enforced in `WorkOrderService.VALID_TRANSITIONS`)

```
NEW ─────────► ASSIGNED ─────────► IN_PROGRESS ◄──► ON_HOLD
 │                 │                    │
 ▼                 ▼                    ▼
CANCELLED      CANCELLED            COMPLETED ──► CLOSED
                                         │
                                         ▼
                                     CANCELLED
```
Concretely: `NEW → {ASSIGNED, CANCELLED}`; `ASSIGNED → {IN_PROGRESS, ON_HOLD,
CANCELLED}`; `IN_PROGRESS → {ON_HOLD, COMPLETED, CANCELLED}`; `ON_HOLD → {IN_PROGRESS,
CANCELLED}`; `COMPLETED → {CLOSED}`; `CLOSED` and `CANCELLED` are terminal (no further
transitions). Any pair not in this table is rejected with `409`.

### Who may trigger which target status (checked independently of the above)

| Target status | Allowed roles |
|---|---|
| `ASSIGNED` | MANAGER, DISPATCHER (requires `technicianId` in the body; that user must exist and have role `TECHNICIAN`, else `400`/`404`) |
| `IN_PROGRESS`, `ON_HOLD`, `COMPLETED` | MANAGER, DISPATCHER, TECHNICIAN |
| `CLOSED`, `CANCELLED` | MANAGER, DISPATCHER only |

A `TECHNICIAN` can therefore start, pause/resume, and complete work on a job assigned to
them, but cannot self-assign, cancel, or formally close one — those remain dispatch/
management decisions. A disallowed role for an otherwise-valid transition is `403`; an
invalid transition shape (regardless of role) is `409` and is checked first, so e.g. a
`TECHNICIAN` trying `NEW → COMPLETED` gets `409` (bad shape), while a `TECHNICIAN` trying
the valid `ASSIGNED → CANCELLED` gets `403` (role not permitted).

### Other lifecycle/CRUD rules
- `PUT` is blocked (`409`) once a work order is `CLOSED` or `CANCELLED`.
- `DELETE` is only allowed while status is still `NEW` (`409` otherwise) — once anything
  has happened to a work order, deleting it would destroy history nobody asked to delete;
  cancel it instead. Deleting a `NEW` order also deletes its one history row (the
  creation entry) first, to satisfy the FK.
- `slaDueAt` is computed from priority at creation exactly as the existing portal flow
  does (CRITICAL +4h, HIGH +24h, MEDIUM +48h, LOW +72h) — unchanged, just reused.
- There is no "reassign technician" action distinct from the `ASSIGNED` transition. To
  change who's assigned once already `ASSIGNED`/`IN_PROGRESS`/`ON_HOLD`, the only way
  with this step's code is to go through another non-`ASSIGNED` transition and back — in
  practice this means reassignment isn't really supported yet; see §5 Known issues.

## 4. Tests (`WorkOrderIntegrationTest`, 28 tests)

MockMvc through the real security filter chain on the H2 test DB (existing test profile,
unchanged). Each test signs up its own users and tags names with a random suffix, so
there is no ordering dependency and no cleanup needed.

- **CRUD** (9): create by MANAGER / by DISPATCHER, create against an unknown site (404),
  create with a blank title (400), get-by-id includes the creation history entry,
  get unknown id (404), update changes fields but not site/customer, delete a `NEW` work
  order (204, then 404), delete by DISPATCHER (403 — MANAGER only), delete after
  assignment (409 — no longer `NEW`).
- **Valid transitions + history** (2): a full lifecycle
  `NEW→ASSIGNED→IN_PROGRESS→ON_HOLD→IN_PROGRESS→COMPLETED→CLOSED` across a MANAGER and a
  TECHNICIAN, asserting exactly 7 history rows with the right from/to/role on each; cancel
  from `NEW` by a DISPATCHER, then confirm `CANCELLED` is terminal.
- **Invalid transitions** (4): `NEW → IN_PROGRESS` directly (409), any transition out of
  `CLOSED` (409), `ASSIGNED` with no `technicianId` (400), `ASSIGNED` with a user who
  isn't a `TECHNICIAN` (400).
- **Technician authorization** (6): cannot self-assign (403), cannot cancel/close (403),
  `GET` on a work order assigned to someone else (404) vs. their own (200), status
  transition on a work order assigned to someone else (404), list only ever shows their
  own assigned work orders, cannot `PUT` (403, blocked at `SecurityConfig` regardless of
  assignment).
- **Customer isolation** (6): list only shows their own, `GET` on another customer's work
  order (404), cannot create via the staff endpoint (403, portal endpoint unaffected),
  cannot transition status (403), cannot delete (403).
- **Cross-cutting** (1): unauthenticated list request (401).

**Not verified by a real run — see the warning at the top.**

## 5. Known issues

- **Build and tests unverified** (see top). Highest priority for the next developer.
- **No technician reassignment action.** Once a work order is `ASSIGNED` to someone, this
  step has no endpoint to hand it to a different technician without an unrelated status
  bounce. Not required by this step's task list, but likely needed soon — a natural
  Step 6 candidate would be a dedicated `PATCH .../technician` or allowing `technicianId`
  on the existing `PUT` for staff.
- **Role-permission-denied transitions return a bare `403`** with no JSON body (Spring
  Security's default `AccessDeniedHandler`, same as the Step 4 `DELETE` endpoints) rather
  than a `ConflictException`/`ApiError`-style body. Consistent with the existing codebase
  pattern, but worth deciding on a custom `AccessDeniedHandler` if a uniform error body
  across all 403s is wanted later.
- **`V3__rename_resolved_to_completed.sql` assumes Postgres's default constraint names**
  (`work_orders_status_check`, `work_order_status_history_from_status_check`,
  `work_order_status_history_to_status_check`) — correct per Postgres's
  `<table>_<column>_check` convention for the unnamed inline `CHECK`s in `V1`, but would
  need adjusting if those were ever renamed by hand. Same caveat already noted for `V2`'s
  `app_users_role_check` in the Step 3 handoff.
- **Old and new work-order endpoints coexist**, same as Step 4's Site endpoints:
  `/api/customer/work-orders` (portal, `WorkOrderResponse`/`WorkOrderDetailResponse`, read
  + create-only) and `/api/work-orders` (this step, full CRUD + lifecycle). Not merged,
  per "no unrelated changes" — a future step could fold the portal's read endpoints into
  the new one now that the new one already supports `CUSTOMER` scoping.
- **`README.md` was not updated with the Work Order API reference** (only the
  `RESOLVED`→`COMPLETED` status-flow line was fixed) — same gap already noted as a known
  issue in the Step 4 handoff for the Customer/Site APIs; still unaddressed.
- Carried over from Steps 3–4: in-memory `TokenBlacklistService`, placeholder default
  `app.jwt.secret` (override `JWT_SECRET`), `V2__rename_roles.sql`'s own constraint-name
  assumption, and customers/site names are not unique.

## 6. Next step

1. Run `mvn clean package` and `mvn test`; fix any compile/test failures from Steps 3–5
   (91 tests expected to pass: 17 + 45 + 28 + `contextLoads`).
2. Only then start the next slice — Parts, TimeLog, SLA, dashboard, or React — each
   schema change as its own `V4__...` migration.
3. Decide on technician reassignment (§5) and whether to fold the portal's work-order
   endpoints into the new `/api/work-orders` API.

## 7. How to run

```bash
cp .env.example .env && export $(grep -v '^#' .env | xargs)
psql -U postgres -c "CREATE DATABASE authapp_db;"
mvn clean package     # runs tests (H2, no Postgres needed)
mvn spring-boot:run   # Flyway applies V1, V2, V3 on startup
```

Try it (after signing up a MANAGER, a CUSTOMER with a site, and a TECHNICIAN):
```bash
curl -X POST localhost:8080/api/work-orders -H 'Authorization: Bearer <manager-token>' \
  -H 'Content-Type: application/json' \
  -d '{"siteId":1,"title":"AC not cooling","priority":"HIGH"}'

curl -X POST localhost:8080/api/work-orders/1/status -H 'Authorization: Bearer <manager-token>' \
  -H 'Content-Type: application/json' \
  -d '{"status":"ASSIGNED","technicianId":<technician-id-from-signup-response>}'

curl -X POST localhost:8080/api/work-orders/1/status -H 'Authorization: Bearer <technician-token>' \
  -H 'Content-Type: application/json' \
  -d '{"status":"IN_PROGRESS"}'
```

# AuthApp — Multi-Role JWT Authentication (Spring Boot + PostgreSQL)

A minimal, working Spring Boot app where four kinds of users — **Manager**,
**Local Customer**, **Local Worker**, and **Developer** — can sign up, log
in, and log out, with stateless JWT authentication and a PostgreSQL-backed
user store. Includes a basic dark-themed UI (plain HTML/CSS/JS, no build
step) served straight from Spring Boot's static resources.

## Stack
- Java 17, Spring Boot 3.3.4
- Spring Security + JWT (jjwt 0.12.6), BCrypt password hashing
- Spring Data JPA + PostgreSQL
- Vanilla HTML/CSS/JS frontend (`src/main/resources/static`)

## Project layout
```
src/main/java/com/keyStone/Playroom021/
  config/        SecurityConfig, GlobalExceptionHandler
  controller/    AuthController (signup/login/logout), DashboardController
  dto/           SignupRequest, LoginRequest, AuthResponse, ApiError
  entity/        User, Role
  repository/    UserRepository
  security/      JwtUtil, JwtAuthFilter, CustomUserDetailsService,
                 CustomUserDetails, TokenBlacklistService, JwtAuthEntryPoint
  service/       AuthService
src/main/resources/
  application.properties
  static/        index.html, login.html, signup.html, dashboard.html, css/, js/
```

## 1. Prerequisites
- JDK 17+
- Maven 3.9+ (or use an IDE that bundles it)
- PostgreSQL 14+ running locally (or reachable over the network)

## 2. Create the database
```sql
CREATE DATABASE authapp_db;
```
No manual table creation needed — `spring.jpa.hibernate.ddl-auto=update`
creates the `app_users` table automatically on first run.

## 3. Configure
Edit `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/authapp_db
spring.datasource.username=postgres
spring.datasource.password=postgres
app.jwt.secret=CHANGE_THIS_TO_A_LONG_RANDOM_SECRET_AT_LEAST_32_BYTES_LONG_1234567890
```
For anything beyond local testing, move the datasource password and
`app.jwt.secret` into environment variables instead of the file, e.g.:
```properties
spring.datasource.password=${DB_PASSWORD}
app.jwt.secret=${JWT_SECRET}
```

## 4. Run locally
```bash
mvn spring-boot:run
```
Then open **http://localhost:8080** — it redirects to the login page if
you're signed out, or the dashboard if you already have a token.

## Customer Portal

Signing up (or logging in) with role `LOCAL_CUSTOMER` lands on `/customer-portal.html`
instead of the generic dashboard. Every customer account is linked 1:1 to a `Customer`
record (the company — e.g. "ABC Office Pvt Ltd") created automatically at signup from
the `companyName` field. From there, a customer can:

- **Raise a service request** — creates a `WorkOrder` (`WO-xxxx`) with status `NEW`, an
  auto-generated `WO-1001`-style code, and an SLA due time computed from priority
  (Critical: 4h, High: 24h, Medium: 48h, Low: 72h).
- **Select their site** — sites (e.g. "Noida Office") belong to a customer; the portal
  includes a simple "Add site" form since site setup isn't wired to a separate ops/manager
  module yet.
- **See their work orders** — a table of everything they've raised: code, title, site,
  priority, status, SLA due date, assigned technician (or "Unassigned").
- **Track status / view history** — clicking a work order opens its full detail, including
  the append-only status-change history (`WorkOrderStatusHistory`), seeded with a "Service
  request raised by customer" entry at creation.

### How customer isolation is enforced
This is the important part, so it's enforced at more than one layer:
1. `/api/customer/**` is restricted to `hasRole("LOCAL_CUSTOMER")` in `SecurityConfig` —
   no other role can call these endpoints at all.
2. `CustomerPortalService` never takes a `customerId` from the request. It always reads
   the caller's own `Customer` off the authenticated JWT principal
   (`principal.getUser().getCustomer()`), and every repository query is scoped by that id
   from the start — e.g. `findByIdAndCustomerId(id, customerId)` — so a work order or site
   belonging to a different customer simply isn't a match. There's no separate "is this
   mine?" check to forget to add.
3. Fetching another customer's work order by guessing its id returns a plain `404`, the
   same response as an id that doesn't exist — the API never confirms that someone else's
   record exists.

### Work order domain model
- `Customer` — the company/account (1:1 with a `LOCAL_CUSTOMER` user for now).
- `Site` — a physical location, belongs to exactly one `Customer`.
- `WorkOrder` — the central object: code, title, description, customer, site, priority,
  status, assigned technician (nullable — no assignment workflow yet), SLA due time,
  created/updated timestamps.
- `WorkOrderStatusHistory` — append-only audit trail of every status change on a work order.

### New customer endpoints
```
GET  /api/customer/sites
POST /api/customer/sites            { name, addressLine, city }
GET  /api/customer/work-orders      (optional ?status=NEW|ASSIGNED|IN_PROGRESS|...)
GET  /api/customer/work-orders/{id}
POST /api/customer/work-orders      { siteId, title, description, priority }
```

### Not built yet (by design — out of scope for the customer portal)
- Manager/dispatcher UI to assign a technician and move a work order through
  `ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED`.
- Technician-facing portal.
- Parts and time-tracking on a work order.

## API quick reference


```bash
# Sign up
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Asha Rao","email":"asha@example.com","password":"secret123","role":"MANAGER"}'

# Log in (returns a JWT)
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"asha@example.com","password":"secret123"}'

# Call a protected, role-gated endpoint
curl http://localhost:8080/api/dashboard/manager \
  -H "Authorization: Bearer <token from login>"

# Log out (blacklists the token server-side)
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer <token from login>"
```
`role` must be one of `MANAGER`, `LOCAL_CUSTOMER`, `LOCAL_WORKER`,
`DEVELOPER`.

## How auth works
- Passwords are hashed with BCrypt before storage — never stored in plain text.
- `/api/auth/**` is public; everything else requires a valid `Authorization: Bearer <token>` header.
- `JwtAuthFilter` runs once per request, validates the token, and loads the user into Spring Security's context.
- Role-specific endpoints (`/api/dashboard/manager`, `/customer`, `/worker`, `/developer`) are gated with `hasRole(...)` in `SecurityConfig`, so the check happens server-side, not just in the UI.
- JWTs are stateless by nature, so **logout** blacklists the token in an in-memory store (`TokenBlacklistService`) until it would have expired anyway, in addition to the frontend discarding it. For a multi-instance production deployment, swap that in-memory map for a shared store (e.g. Redis) — noted in the class's Javadoc.

## 6. Build a deployable jar
```bash
mvn clean package
java -jar target/authapp-1.0.0.jar
```

## 7. Deploying (step by step)

### Option A — a single VM/server (systemd)
1. Provision a Linux host with JDK 17 and PostgreSQL (or point at a managed PostgreSQL instance).
2. `mvn clean package` locally (or in CI) and copy `target/authapp-1.0.0.jar` to the server.
3. Set real config via environment variables rather than the properties file:
   ```bash
   export DB_PASSWORD=your_real_password
   export JWT_SECRET=$(openssl rand -base64 48)
   ```
4. Create `/etc/systemd/system/authapp.service`:
   ```ini
   [Unit]
   Description=AuthApp
   After=network.target postgresql.service

   [Service]
   Environment=DB_PASSWORD=your_real_password
   Environment=JWT_SECRET=your_generated_secret
   ExecStart=/usr/bin/java -jar /opt/authapp/authapp-1.0.0.jar
   Restart=on-failure
   User=authapp

   [Install]
   WantedBy=multi-user.target
   ```
5. `sudo systemctl daemon-reload && sudo systemctl enable --now authapp`
6. Put nginx (or your LB of choice) in front for TLS termination, proxying to `127.0.0.1:8080`.

### Option B — Docker
1. Add this `Dockerfile` at the project root:
   ```dockerfile
   FROM eclipse-temurin:17-jre
   WORKDIR /app
   COPY target/authapp-1.0.0.jar app.jar
   EXPOSE 8080
   ENTRYPOINT ["java", "-jar", "app.jar"]
   ```
2. Build and run:
   ```bash
   mvn clean package
   docker build -t authapp:1.0.0 .
   docker run -d -p 8080:8080 \
     -e SPRING_DATASOURCE_URL=jdbc:postgresql://<db-host>:5432/authapp_db \
     -e SPRING_DATASOURCE_USERNAME=postgres \
     -e DB_PASSWORD=your_real_password \
     -e JWT_SECRET=your_generated_secret \
     authapp:1.0.0
   ```
3. For local dev, pair it with a `docker-compose.yml` that also runs a `postgres:16` container.

### Option C — a managed platform (Render, Railway, Fly.io, etc.)
1. Push this repo to GitHub.
2. Create a PostgreSQL instance on the platform and note its connection URL.
3. Create a new web service from the repo; most of these platforms auto-detect Maven/Spring Boot and run `mvn clean package` + `java -jar target/*.jar` for you.
4. Set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET` as environment variables in the platform's dashboard.
5. Deploy — the app creates its own `app_users` table on first boot.

## Next steps you may want
- Move `app.jwt.secret` fully to a secrets manager before production use.
- Add email verification / password reset flows.
- Add per-role landing content in `dashboard.html` beyond the placeholder message.
- Add refresh tokens if you want shorter-lived access tokens.

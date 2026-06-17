# grading.hrlab.uz

Secure multi-tenant SaaS platform for conducting grading projects (HR Laboratories).

- **Backend:** Java 21 + Spring Boot 3.x (modular monolith)
- **Frontend:** React + TypeScript + Vite
- **Database:** PostgreSQL + Liquibase
- Production deployment lives in `docker-compose.prod.yml` (pulls immutable
  GHCR images, real OIDC, secrets from `.env.prod`). **Nothing below touches it.**

---

## Run locally

Two ways to bring the whole app up on your machine. Both use the **`local`**
Spring profile, which enables passwordless **dev-auth** (no real OIDC) and runs
the Liquibase **`dev`** seed — so demo tenants (ACME Holdings, Beta University),
demo users and demo projects exist and the screens come up **populated**.

> **DEV-ONLY — never deploy any of this.** Dev-auth is passwordless, the
> credentials are obvious placeholders (`grading_app` / `grading_app_pwd`,
> `grading_super` / `grading_super_pwd`), and the demo data is seeded by the
> `dev` Liquibase context which production deliberately omits. The full-Docker
> path uses `docker-compose.local.yml` + `infra/local/*`, which share **no**
> network/volume/container with the production stack.

### Option A — Full Docker (one command, nothing else installed)

Builds Postgres + backend + frontend **from source**. Best for "clone and click
through the real app".

```bash
docker compose -f docker-compose.local.yml up --build
```

Wait until all three services are healthy (the backend cold-starts the JVM, runs
Liquibase migrations and seeds the demo data — give it ~1–2 min on first run),
then open:

| URL                                            | What                                             |
| ---------------------------------------------- | ------------------------------------------------ |
| <http://localhost:8081>                        | The app (SPA). **Start here.**                   |
| <http://localhost:8080/actuator/health>        | Backend health                                   |
| <http://localhost:8080/swagger-ui.html>        | API docs                                          |
| `localhost:55432`                              | Postgres (`grading_control_db`) — override host port via `POSTGRES_HOST_PORT` |

**Log in:** on the login screen click **"Sign in as super-admin"** (the
passwordless dev-auth button). You land in the demo tenant with full
permissions. Use the tenant selector to switch between **ACME Holdings** and
**Beta University**.

**How the frontend reaches the backend:** the local frontend image
(`infra/local/frontend/Dockerfile.local`) builds the SPA with `VITE_DEV_AUTH=true`
and `VITE_USE_MSW=false`, and nginx (`infra/local/frontend/nginx.local.conf`)
**reverse-proxies `/api/v1/` → the `backend` service**. So the SPA and API are
**same-origin** on port 8081; every API call hits the **real local backend**
(not the in-browser mock), and dev-auth forwards `X-Dev-User` / `X-Dev-Tenant`
headers that the backend's `DevAuthFilter` resolves to real roles/permissions
from the seeded DB.

Stop and **wipe the demo DB**:

```bash
docker compose -f docker-compose.local.yml down -v
```

Drop `-v` to keep the data volume between runs.

### Option B — Hybrid (Postgres in Docker, backend + frontend on the host)

The configs (`application-local.yml`, `vite.config.ts`, `frontend/.env.example`)
are designed for this. Fastest inner loop — hot-reload on both sides. Requires
JDK 21 and Node 22 installed.

**1. Postgres only** (published on host 5432 so the `local` profile's
`localhost:5432` datasource reaches it — if 5432 is taken, pick another and set the
datasource accordingly):

```bash
POSTGRES_HOST_PORT=5432 docker compose -f docker-compose.local.yml up -d postgres
```

This creates `grading_control_db` + the `grading_app` bootstrap user (via
`infra/local/initdb/00-bootstrap.sql`) and publishes `localhost:5432`.

**2. Backend (new terminal, in `backend/`):**

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The `local` profile points at `jdbc:postgresql://localhost:5432/grading_control_db`
as `grading_app`, runs Liquibase (contexts `control-plane,seeds,test-roles,mode-shared,dev`
— the `dev` context seeds the demo data), binds `DevAuthFilter`, and serves on
**:8080**. CORS already allows `http://localhost:5173`.

**3. Frontend (new terminal, in `frontend/`):**

```bash
cd frontend
npm install          # first time only
npm run dev          # http://localhost:5173
```

Open <http://localhost:5173> and click **"Sign in as super-admin"**.

**How the frontend reaches the backend:** Vite's dev server proxies `/api` →
`http://localhost:8080` (see `server.proxy` in `vite.config.ts`; override the
target with `VITE_API_PROXY`). The SPA's default API base is `/api/v1`
(`VITE_API_BASE_URL`), so requests go to `/api/v1/...` and are proxied to the
backend — and the backend's CORS allowlist already includes `localhost:5173`.
Copy `frontend/.env.example` to `frontend/.env.local` if you want to tweak any
`VITE_*` value (it already has `VITE_DEV_AUTH=true`, `VITE_USE_MSW=false`).

> Tip: `frontend/.env.local` ships with `VITE_USE_MSW=true` (standalone demo
> against the in-browser mock, **no backend**). For the hybrid path against the
> real backend keep `VITE_USE_MSW=false` (the value in `.env.example`).

### Troubleshooting

- **Frontend up but API calls 404/blocked:** make sure the backend is healthy
  (`curl localhost:8080/actuator/health`). In Option A the frontend `depends_on`
  the backend being healthy, so the SPA only starts once the API is ready.
- **`port is already allocated`:** something else owns 5432/8080/8081. Stop it
  or remap the left-hand side of the `ports:` entries in `docker-compose.local.yml`.
- **Empty screens / no demo tenants:** the `dev` seed only runs under the
  `local` profile. Confirm `SPRING_PROFILES_ACTIVE=local` (Option A sets it;
  Option B uses `-Dspring-boot.run.profiles=local`). To re-seed, recreate the DB
  volume: `docker compose -f docker-compose.local.yml down -v` then `up` again.
- **Reset everything:** `docker compose -f docker-compose.local.yml down -v`
  removes the `grading-pg-data-local` volume and the next `up` re-migrates and
  re-seeds from scratch.

# grading.hrlab.uz — Shared-VPS Docker Deploy Runbook (MVP 1)

Production deployment of grading.hrlab.uz onto a **single shared VPS** using
**Docker Compose**, with **git-push auto-deploy (CD)**. The VPS already hosts
**other subdomains and their own PostgreSQL databases** — this design coexists
with them and must never touch them.

- Compose stack: [`docker-compose.prod.yml`](../../docker-compose.prod.yml)
- Env template: [`.env.prod.example`](../../.env.prod.example)
- DB role bootstrap: [`infra/db/prod/01-create-prod-roles.sh`](../../infra/db/prod/01-create-prod-roles.sh)
- Frontend runtime config: [`infra/frontend/config.json`](../../infra/frontend/config.json)
- Outer proxy (nginx): [`infra/reverse-proxy/grading.hrlab.uz.nginx.conf`](../../infra/reverse-proxy/grading.hrlab.uz.nginx.conf)
- Outer proxy (Traefik): [`infra/reverse-proxy/grading.hrlab.uz.traefik.md`](../../infra/reverse-proxy/grading.hrlab.uz.traefik.md)
- CD workflow: [`.github/workflows/deploy.yml`](../../.github/workflows/deploy.yml)
- Image build/scan/push: existing [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml)

---

## 1. Architecture on the shared VPS

```
                Internet (TLS)
                      |
        +-------------------------------+
        |  EXISTING host reverse proxy  |   <- nginx OR Traefik (already there)
        |  (terminates TLS per vhost)   |       serves the OTHER subdomains too
        +-------------------------------+
          | grading.hrlab.uz only |
          |  /api  ->             |  ->  127.0.0.1:18080  grading-api  (prod)
          |  /     ->             |  ->  127.0.0.1:18081  grading-frontend
          +-----------------------+
                                          (internal docker network grading-prod)
                                                 |
                                          grading-postgres   <- NO host port,
                                          (grading-pg-data-prod)   isolated
```

- App containers bind **127.0.0.1 only** on unique high ports (18080 / 18081).
- The dedicated `grading-postgres` has **no published host port** — only the
  grading containers on `grading-prod` reach it. It cannot collide with or
  affect the other subdomains' Postgres on `:5432`.
- Same-origin: the SPA reads `/config.json -> apiBaseUrl:"/api"` and CSP is
  `connect-src 'self'`, so API and SPA must share the `grading.hrlab.uz` origin.
  The OUTER proxy performs the `/api` vs `/` split.

---

## 2. Prerequisites

- A user account on the VPS with `sudo` and SSH key access.
- Docker Engine + the Docker Compose v2 plugin installed.
- The existing reverse proxy (nginx or Traefik) is identified and you can add a
  vhost/router for `grading.hrlab.uz` without disturbing other vhosts.
- A DNS zone for `hrlab.uz` you can edit.
- An OIDC issuer (Keycloak/Auth0/etc.) reachable from the VPS for real JWT.

---

## 3. First-time VPS setup (run ONCE)

### 3.1 Install Docker (if not already present)

```sh
# Debian/Ubuntu
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker "$USER"   # re-login afterwards
docker compose version            # confirm Compose v2
```

> If other subdomains already use Docker, skip this — just confirm the version.

### 3.2 Create the deploy directory + .env.prod

```sh
sudo mkdir -p /opt/grading
sudo chown "$USER":"$USER" /opt/grading
cd /opt/grading

# Bring the template from the repo (or scp it), then fill in real values.
# DO NOT commit /opt/grading/.env.prod anywhere.
cp /path/to/repo/.env.prod.example .env.prod
chmod 600 .env.prod
nano .env.prod      # set strong passwords, JWT issuer/audience, GHCR_REPO, etc.
```

Set, at minimum:
- `GHCR_REPO=aziz-bek01/grading`
- `IMAGE_TAG` — leave as placeholder; CD overwrites it each deploy.
- `POSTGRES_SUPER_USER` / `POSTGRES_SUPER_PASSWORD` (dedicated grading owner).
- `SPRING_DATASOURCE_PASSWORD` (runtime role), `LIQUIBASE_DATASOURCE_PASSWORD`
  (migrator role), `GRADING_AUDIT_READER_PASSWORD` — three DISTINCT secrets.
- `GRADING_JWT_ISSUER_URI`, `GRADING_JWT_AUDIENCE`.
- `GRADING_CORS_ALLOWED_ORIGINS=https://grading.hrlab.uz`.
- `GRADING_TENANCY_MODE=shared`.

### 3.3 GHCR pull access

The images are at `ghcr.io/aziz-bek01/grading/grading-api` and
`.../grading-frontend`. Either:

- **(a)** Make those two GHCR packages **public** (simplest), or
- **(b)** Keep them private and let the VPS log in to GHCR. Create a
  classic PAT with `read:packages` and store it on the VPS:
  ```sh
  echo "<GHCR_READ_PAT>" > "$HOME/.ghcr-token"
  chmod 600 "$HOME/.ghcr-token"
  ```
  The CD workflow runs `docker login ghcr.io` using this file if present.

### 3.4 Database: bundled (default) or external

**Default — bundled isolated container.** Nothing else to do; first deploy will
`up -d grading-postgres`, run the init script
(`infra/db/prod/01-create-prod-roles.sh`) which attaches LOGIN + passwords to
`grading_migrator` / `grading_runtime` / `grading_audit_reader`, then Liquibase
applies changelog 005 (grants/revokes) + the schema.

**External — reuse a Postgres already on the VPS.** In `.env.prod`:
- `GRADING_USE_EXTERNAL_DB=true`
- Point `SPRING_DATASOURCE_URL` and `LIQUIBASE_DATASOURCE_URL` at that server,
  using a **SEPARATE database** dedicated to grading and the three **SEPARATE
  roles**. Create them once by hand (psql as a superuser on that server):
  ```sql
  CREATE DATABASE grading_control_db;
  CREATE ROLE grading_migrator      LOGIN PASSWORD '<migrator_pwd>';
  CREATE ROLE grading_runtime       LOGIN PASSWORD '<runtime_pwd>';
  CREATE ROLE grading_audit_reader  LOGIN PASSWORD '<audit_reader_pwd>';
  GRANT CONNECT ON DATABASE grading_control_db
    TO grading_migrator, grading_runtime, grading_audit_reader;
  ```
  Then do **not** start the `db` profile. Changelog 005 (run by the migrator)
  applies the rest of the grants/revokes inside `grading_control_db`.

> Either way: grading gets its **own database and own roles**. It never shares a
> database or a role with the other subdomains.

### 3.5 Configure the outer reverse proxy

Pick the one that already serves the other subdomains:

- **nginx**: copy
  `infra/reverse-proxy/grading.hrlab.uz.nginx.conf` to
  `/etc/nginx/sites-available/`, symlink into `sites-enabled/`, then
  `sudo nginx -t && sudo systemctl reload nginx`.
  (Adds a single new `server { server_name grading.hrlab.uz; }` block — other
  vhosts untouched.)
- **Traefik**: follow `infra/reverse-proxy/grading.hrlab.uz.traefik.md`
  (Option A file-provider recommended — keeps containers on 127.0.0.1).

### 3.6 DNS + TLS — see §7.

### 3.7 First manual bring-up (sanity, before enabling CD)

```sh
cd /opt/grading
# set IMAGE_TAG to a real tag ci.yml has already pushed, e.g. sha-1a2b3c4
sed -i 's|^IMAGE_TAG=.*|IMAGE_TAG=sha-XXXXXXX|' .env.prod

docker compose -f docker-compose.prod.yml --env-file .env.prod --profile db up -d grading-postgres
docker compose -f docker-compose.prod.yml --env-file .env.prod --profile migrate run --rm grading-migrator
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d grading-api grading-frontend

curl -fsS http://127.0.0.1:18080/actuator/health/readiness
curl -fsS http://127.0.0.1:18081/healthz
```

---

## 4. How auto-deploy works (CD)

1. You push/merge to `main`.
2. `ci.yml` runs the 28-stage pipeline: scans, the blocking release gates
   (tenant-isolation, audit, salary, architecture), builds + scans + pushes the
   `grading-api` and `grading-frontend` images to GHCR tagged `sha-<short>`.
3. On `ci` success, `deploy.yml` triggers (`workflow_run`):
   - `gate` checks CI succeeded AND `VPS_HOST` secret exists. If no VPS secret,
     it **skips cleanly** (CI stays green — safe to merge these files now).
   - `deploy` SSHes in, scp's the compose + infra files to `/opt/grading`,
     overwrites only the `IMAGE_TAG` line in `.env.prod`, pulls the immutable
     images, runs the **migrator one-shot first**, then `up -d` the api +
     frontend, then smoke-tests readiness.
4. If the migrator fails, the deploy fails and the **previous** api/frontend
   keep serving — no half-migrated rollout.

Manual deploy of a specific tag: Actions -> deploy -> Run workflow -> set
`image_tag` (e.g. `sha-1a2b3c4`).

---

## 5. Rollback

Images are immutable and tagged `sha-<short>`; previous tags remain in GHCR and
locally (we only prune *dangling* images).

```sh
cd /opt/grading
# Find the previous good tag:
docker images 'ghcr.io/aziz-bek01/grading/grading-api'

# Point IMAGE_TAG back to the previous good sha and re-up (no DB change):
sed -i 's|^IMAGE_TAG=.*|IMAGE_TAG=sha-PREVGOOD|' .env.prod
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d grading-api grading-frontend

curl -fsS http://127.0.0.1:18080/actuator/health/readiness
```

Or via GitHub: Actions -> deploy -> Run workflow -> `image_tag=sha-PREVGOOD`.

**Database rollback**: schema changes are forward-only by policy. If a migration
caused the issue, restore from the pre-deploy backup checkpoint (see DB
blueprint / backup runbook) — do not auto-rollback DDL. Take a dump before any
risky migration:
```sh
docker exec grading-postgres-prod pg_dump -U grading_owner grading_control_db \
  | gzip > /opt/grading/backups/pre-deploy-$(date -u +%Y%m%dT%H%M%SZ).sql.gz
```

---

## 6. GitHub repo secrets to set

Repo -> Settings -> Secrets and variables -> Actions -> **New repository secret**:

| Secret          | Purpose                                                        |
| --------------- | ------------------------------------------------------------- |
| `VPS_HOST`      | VPS hostname or IP. **Until set, CD skips gracefully.**        |
| `VPS_USER`      | SSH user with docker access (e.g. `deploy`).                   |
| `VPS_SSH_KEY`   | PRIVATE SSH key (PEM) whose public key is in the VPS `authorized_keys`. |
| `VPS_SSH_PORT`  | SSH port. Optional — defaults to `22`.                         |
| `GHCR_USER`     | Optional — GHCR username for private-image login. Defaults to the actor. |

> All secrets are injected at deploy time. None are committed. App secrets
> (DB passwords, JWT, etc.) live ONLY in `/opt/grading/.env.prod` on the VPS.

Also set the GitHub **Environment** `production` (Settings -> Environments) and
add required reviewers if you want a manual approval gate before each CD run.

---

## 7. DNS + TLS for grading.hrlab.uz

### DNS
Add an **A record** (and AAAA if you have IPv6) for `grading.hrlab.uz` pointing
at the VPS public IP. Do not change records for the other subdomains.

```
grading   A    <VPS_PUBLIC_IPV4>
grading   AAAA <VPS_PUBLIC_IPV6>   # optional
```

Verify: `dig +short grading.hrlab.uz`.

### TLS
- **nginx + certbot**:
  ```sh
  sudo mkdir -p /var/www/certbot
  # Ensure the :80 server block (from the provided conf) is live first.
  sudo certbot certonly --webroot -w /var/www/certbot -d grading.hrlab.uz
  sudo systemctl reload nginx
  # Renewal: certbot installs a systemd timer / cron automatically.
  ```
- **Traefik**: the cert resolver (e.g. `letsencrypt`) configured for the other
  subdomains issues the cert automatically when the router comes up (HTTP-01 or
  DNS-01 per your existing setup). No manual certbot needed.

Verify: `curl -fsS https://grading.hrlab.uz/healthz`.

---

## 8. Coexistence / safety checklist (other subdomains + DBs untouched)

Run/confirm each item before and after first deploy:

- [ ] **No host-port collisions.** `docker compose -f docker-compose.prod.yml
      config` shows api/frontend bound to `127.0.0.1:18080` / `127.0.0.1:18081`
      only, and `grading-postgres` has **no `ports:`** block.
      Confirm those high ports were free: `sudo ss -ltnp | grep -E '18080|18081'`
      returns nothing before first deploy.
- [ ] **Dedicated DB.** grading uses its own database (`grading_control_db`) and
      its own roles (`grading_migrator/runtime/audit_reader`). It never connects
      to another subdomain's database or role.
- [ ] **Namespaced everything.** Containers `grading-*-prod`, network
      `grading-prod`, volume `grading-pg-data-prod`. `docker ps`,
      `docker network ls`, `docker volume ls` show no name clashes with existing
      objects.
- [ ] **No global proxy clobbering.** Only a single new `server_name
      grading.hrlab.uz` block / Traefik router named `grading-*` was added.
      `nginx -t` passes; reload did not drop other vhosts (`curl` an existing
      subdomain still works).
- [ ] **TLS scoped.** The new cert covers `grading.hrlab.uz` only; other certs
      untouched.
- [ ] **No shared volumes/bind mounts** into other apps' data dirs. The only
      bind mounts are read-only: `infra/db/prod` (initdb) and
      `infra/frontend/config.json`.
- [ ] **Resource limits set** on every grading container so a grading spike
      can't starve neighbouring subdomains (`deploy.resources.limits`).
- [ ] **DevAuthFilter NOT active.** API logs show profile `prod`; hitting a
      business endpoint without a Bearer token returns 401 (not dev-header auth).
- [ ] **Prod Liquibase contexts.** Migrator logs show contexts
      `control-plane,seeds,mode-shared` — NO `dev`, NO `test-roles`.

---

## 9. Common operations

```sh
cd /opt/grading
C="docker compose -f docker-compose.prod.yml --env-file .env.prod"

$C ps                         # status
$C logs -f grading-api        # tail api logs
$C logs -f grading-migrator   # last migration run
$C restart grading-api        # restart api only
$C --profile migrate run --rm grading-migrator   # re-run migrations manually
```

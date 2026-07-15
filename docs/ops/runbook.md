# grading.hrlab.uz — Operator Runbook (Owner Operational Readiness, Batch 7)

> Single-VPS (2 GB, shared) Docker-Compose production. Source of truth for the
> roll sequence is `.github/workflows/deploy.yml` and `docker-compose.prod.yml`
> (read-only here — this runbook only documents them). Everything secret lives in
> `/opt/grading/.env.prod` ON THE VPS and is **never** committed.
>
> Conventions used below:
> - `REMOTE_DIR` = `/opt/grading` (the CD `REMOTE_DIR`).
> - `$COMPOSE` = `docker compose -f docker-compose.prod.yml --env-file .env.prod`
>   (run it from `/opt/grading`).
> - Public site = `https://grading.hrlab.uz`; api on `127.0.0.1:18080`,
>   frontend on `127.0.0.1:18081` (loopback only — the **outer** host proxy
>   terminates TLS and splits `/api` → 18080, everything else → 18081).
> - **Placeholders** are written like `<VPS_HOST>`, `<PAGER_WEBHOOK_URL>`. Never
>   paste real secrets/hostnames into this file or any committed file.

---

## 0. Quick reference (the 30-second card)

| I need to… | Go to |
| --- | --- |
| Redeploy current `main` | [1.1](#11-normal-deploy-git-push--ci) |
| Redeploy / roll back to a specific tag | [1.2](#12-redeploy-an-existing-tag--rollback) |
| Understand the migrate one-shot + the profile gotcha | [1.3](#13-the-liquibase-migrate-one-shot--the-migrate-profile-gotcha) |
| Triage a paged alert | [3. Alert-response playbook](#3-alert-response-playbook) |
| Restore the database | [4.4 Restore drill](#44-restore-drill-stepbystep) |
| Rotate a leaked / expiring secret | [5. Secrets rotation](#5-secrets-rotation-runbook) |

**Golden rule (do not bypass):** no production roll proceeds if tenant isolation,
salary-data protection, audit trail, security scan, backup readiness, or smoke
tests are not verified. On this single-node MVP the deploy gate is "do both
images build + does the migrate one-shot + smoke test pass" — the heavier QA /
security gates run in `ci.yml` for visibility (see deploy.yml header).

---

## 1. Deploy & rollback

### 1.1 Normal deploy (git push → CI)

The `deploy` workflow runs automatically on every push to `main`:

1. **build-images** — builds `grading-api` + `grading-frontend` from the repo
   Dockerfiles, pushes to GHCR as the immutable `sha-<short>` tag (+ `latest`).
2. **gate** — proceeds only if the `VPS_HOST` secret is set; otherwise images are
   pushed and the deploy is skipped cleanly (nothing touches the server).
3. **deploy** — SSH to the VPS and run, in order:
   - SCP deploy artifacts to `/opt/grading`
     (`docker-compose.prod.yml`, `infra/db/prod/*`, `infra/frontend/config.json`,
     `infra/reverse-proxy/*`).
   - Pin the new tag into `.env.prod` (`IMAGE_TAG=sha-<short>`).
   - `docker login ghcr.io` with the ephemeral run token.
   - `$COMPOSE --profile db --profile migrate pull` (pull immutable images).
   - If bundled DB: `$COMPOSE --profile db up -d grading-postgres`, wait healthy.
   - **`$COMPOSE --profile migrate run --rm grading-migrator`** (one-shot Liquibase).
   - If `GRADING_STORAGE_TYPE=minio`: bring up `grading-minio`, wait healthy.
   - `$COMPOSE up -d grading-api grading-frontend` (roll to new tag).
   - Prune unused images (`--filter until=24h`).
4. **smoke test** — poll the api container healthcheck, then
   `curl /actuator/health/readiness` (api), `/healthz` (frontend), and a
   best-effort public `https://grading.hrlab.uz/`.

**Operator action for a normal release:** merge to `main` → watch the `deploy`
run in GitHub Actions → confirm the smoke-test step is green. Then run the
[post-deploy verification checklist](#16-postdeploy-verification-checklist).

### 1.2 Redeploy an existing tag / rollback

`deploy.yml` supports `workflow_dispatch` with an `image_tag` input. When you
pass an **existing** tag it **skips the build** and just (re)deploys that tag —
this is the supported rollback path.

**Rollback procedure (preferred — via CI, fully recorded):**

1. Find the previous good tag. GHCR shows pushed tags; or on the VPS:
   ```
   grep '^IMAGE_TAG=' /opt/grading/.env.prod        # current tag
   docker images 'ghcr.io/<owner>/grading/grading-api' --format '{{.Tag}}\t{{.CreatedAt}}'
   ```
2. GitHub → Actions → **deploy** → **Run workflow** → set
   `image_tag = sha-<previous good short sha>` → Run.
3. CI re-runs migrate (no-op if the schema is unchanged) and rolls api+frontend
   back to that image. Watch the smoke step.
4. Run [post-deploy verification](#16-postdeploy-verification-checklist).

> **Application rollback is image-tag only and is always safe** (immutable tags,
> no `latest` in prod). **Database rollback is NOT automatic** — see the gotcha
> in [1.3](#13-the-liquibase-migrate-one-shot--the-migrate-profile-gotcha) and
> the DB-rollback rules in [6](#6-rollback-strategy-deep-dive).

**Manual rollback on the VPS (break-glass, only if Actions is unavailable):**

```
cd /opt/grading
# 1. pin the previous tag
sed -i "s|^IMAGE_TAG=.*|IMAGE_TAG=sha-<previous>|" .env.prod
# 2. authenticate to GHCR (use a short-lived token, NOT a committed PAT)
echo "<GHCR_TOKEN>" | docker login ghcr.io -u "<GHCR_ACTOR>" --password-stdin
COMPOSE="docker compose -f docker-compose.prod.yml --env-file .env.prod"
# 3. pull + roll (do NOT re-run migrate when rolling BACKWARD — see 1.3)
$COMPOSE --profile db --profile migrate pull
$COMPOSE up -d grading-api grading-frontend
docker logout ghcr.io
# 4. smoke
curl -fsS http://127.0.0.1:18080/actuator/health/readiness
curl -fsS http://127.0.0.1:18081/healthz
```
**Record every manual change** (who/when/why/tag) in the release log — no silent
production changes.

### 1.3 The Liquibase migrate one-shot + the migrate-profile gotcha

The migrator is a **separate, one-shot container** (`grading-migrator`), not the
api running migrations at boot:

- It reuses the `grading-api` image (the boot jar embeds Liquibase + changelogs).
- It connects as **`grading_migrator`** (DDL role) — a *different* credential
  from the runtime `grading_runtime` (DML) role. **Never** merge these.
- It runs `liquibase update` with web disabled, exits 0, and `--rm` removes it.
- It must complete **before** api rolls. A non-zero exit **fails the deploy and
  the old api keeps serving** — that is the intended safe behaviour.
- The runtime api has `SPRING_LIQUIBASE_ENABLED=false`; the app **never**
  migrates at boot.

**The migrate-profile gotcha (what was fixed — do not regress it):**
The migrator sets `SPRING_PROFILES_ACTIVE=migrate`. There is **no
`application-migrate.yml`**, so Spring falls back to the base `application.yml`
whose `liquibase.contexts` are exactly `control-plane,seeds,mode-shared` — i.e.
**NO `dev`, NO `test-roles`**. This matters because:
- `test-roles` ships **well-known dev passwords** and must NEVER run in prod;
  prod role passwords come from `.env.prod` via
  `infra/db/prod/01-create-prod-roles.sh` (first-init only), not from Liquibase.
- `DevAuthFilter` does NOT bind under `migrate` (it is not in
  `DevAuthFilter.ALLOWED_PROFILES`).
- The migrator hard-pins `GRADING_STORAGE_TYPE=local` (env-locked, not
  overridable) so the DDL job can never reach MinIO.

> If you ever see the migrator pulling in `dev`/`test-roles` contexts, or
> seeding dev passwords in prod, that is the regression — stop and check the
> active profile and `spring.liquibase.contexts`.

**Migration safety rules (enforced):**
- Take a **pre-migration backup checkpoint** before any release that ships a
  schema change ([4.3](#43-pre-migration-backup-checkpoint)).
- Migrations must be **reversible or have a written manual rollback plan**.
- **Never** run a destructive migration (drop/rename column, type narrowing)
  without explicit owner approval AND a fresh backup.
- Validate the migration on staging before prod (where staging exists). Watch
  the migrator logs and duration:
  ```
  docker logs grading-migrator-prod 2>&1 | tail -n 50   # if still present pre-prune
  ```

---

## 2. Incident response — severity matrix

| Sev | Definition (examples) | Response target (ack / mitigate) | Who | Comms |
| --- | --- | --- | --- | --- |
| **SEV1** | Cross-tenant data leak; salary-data leak; full production outage (`GradingApiDown`); data corruption; **audit-trail write failure** for sensitive actions | **Ack ≤ 15 min, mitigate ≤ 1 h** | Incident commander + owner + security | Status page + direct client notice if tenant data affected |
| **SEV2** | Major module unavailable; report/import worker stuck or **dead-letter present** (`GradingWorkerDeadLetterPresent`); high 5xx/latency; **failed backup**; integrations down | **Ack ≤ 30 min, mitigate ≤ 4 h** | On-call engineer | Internal channel; client notice if SLA-impacting |
| **SEV3** | Degraded performance; isolated worker failure; non-critical UI issue; single warning alert with no user impact | **Ack ≤ 1 business day** | On-call engineer | Internal ticket |

**On any SEV1/SEV2:** open an incident, assign an incident commander, post in the
incident channel, and start a timeline. After resolution write a blameless
postmortem (timeline, root cause, corrective actions with owners + due dates).

**Privacy during incidents:** logs and metrics are tenant-safe by design — the
Batch-6 metrics carry only `type`/`outcome`, never `tenant_id`/`user_id`/salary.
Do **not** paste raw request bodies, JWTs, tokens, or salary values into tickets,
chat, or postmortems.

---

## 3. Alert-response playbook

Source of truth for the rules: `infra/observability/alerts/grading-alerts.yml`.
Each entry: **what fired → what it means → first actions**.

### 3.1 `GradingApiDown` (critical)
**Means:** Prometheus cannot scrape `up{job="grading-api"}` for 2 min — the api
process is down, crash-looping, or `/actuator/prometheus` is unreachable.
**First actions:**
1. `docker ps --filter name=grading-api-prod` — is it up / restarting?
2. `docker inspect -f '{{.State.Health.Status}}' grading-api-prod`
3. `curl -fsS http://127.0.0.1:18080/actuator/health/readiness` (loopback) and
   `/actuator/health/liveness`.
4. `docker logs --tail 200 grading-api-prod` — look for OOM
   (`ExitOnOutOfMemoryError`), datasource failures (wrong/rotated DB password),
   or JWT/JWKS errors.
5. If OOM-killed: check host memory (`free -m`, `docker stats --no-stream`) —
   on the 2 GB box a runaway neighbour can starve the JVM. The api `mem_limit`
   is 820m with a small swap cushion; do not raise it without re-checking the
   whole-box memory budget.
6. If the process is healthy but Prometheus still can't scrape: it's a scrape
   path/auth problem, not an outage — see
   [prometheus.scrape.example.yml](../../infra/observability/prometheus/prometheus.scrape.example.yml).
   `/actuator/prometheus` is **non-public** (auth/internal only).
7. Recover: `$COMPOSE up -d grading-api`. If a bad release caused it, **roll back
   to the previous tag** ([1.2](#12-redeploy-an-existing-tag--rollback)).
**Escalates to SEV1.** This alert *inhibits* the worker alerts below (the api
being down explains worker silence) — see the Alertmanager inhibition rule.

### 3.2 `GradingWorkerDeadLetterPresent` (critical) — the DLQ playbook
**Means:** `grading_worker_dead_letter_current > 0` — one or more async jobs
(export/import/report) exhausted their retry budget and are in `DEAD_LETTER`. A
**tenant operation failed permanently** and needs human triage.
**First actions:**
1. Confirm scope from metrics (which job type): in Grafana / Prometheus,
   `sum(increase(grading_worker_outcome_total{outcome="dead_letter"}[1h])) by (type)`.
2. Find the failing jobs in the worker logs by correlation/tenant MDC (logs are
   tenant-safe — they carry `correlationId`/`tenantId` but no salary/PII):
   ```
   docker logs --since 2h grading-api-prod 2>&1 | grep -i dead_letter
   docker logs --since 2h grading-api-prod 2>&1 | grep -iE 'DEAD_LETTER|correlationId'
   ```
3. Inspect the DLQ rows in the database. The worker jobs and their terminal
   state are persisted in the control-plane schema (run as `grading_audit_reader`
   for read-only, or the runtime role). Identify the job table from the schema,
   then:
   ```
   docker exec -it grading-postgres-prod \
     psql -U grading_audit_reader -d grading_control_db \
     -c "SELECT id, tenant_id, type, status, attempts, last_error, updated_at
         FROM <worker_job_table>
         WHERE status = 'DEAD_LETTER'
         ORDER BY updated_at DESC LIMIT 50;"
   ```
   (Substitute the actual job/queue table name from the schema. Do **not** SELECT
   payload columns that may contain salary/PII into a ticket.)
4. **Root-cause the dependency** named in `last_error` — common causes: object
   store (MinIO) unreachable, a malformed import file, a report template error,
   or a transient DB lock. Cross-check
   [3.3 FailureRateHigh](#33-gradingworkerfailureratehigh-warning) and the MinIO
   runbook below.
5. **Replay / re-drive** once the root cause is fixed. The worker pipeline has a
   `WorkerReQueuer` (emits `retry_dispatched`). Re-drive through the app's
   supported admin re-queue path (preferred) — do NOT hand-edit job rows to
   re-run unless that is the documented re-drive mechanism, and never bypass
   tenant isolation. Confirm recovery:
   `grading_worker_dead_letter_current` returns to 0 and
   `grading_worker_outcome_total{outcome="succeeded"}` increments for the
   re-driven type.
**Severity: SEV2** (SEV1 if the failed operation exposed or corrupted tenant
data).

### 3.3 `GradingWorkerDeadLetterRate` (warning)
**Means:** new dead-letter transitions in the last 15 min, by `type` — a
defence-in-depth signal so a process restart that resets the gauge to 0 cannot
hide a genuine spike. **First actions:** same as 3.2; treat as the trend version
of "dead-letter present." Investigate even if the gauge currently reads 0.

### 3.4 `GradingWorkerFailureRateHigh` (warning)
**Means:** > 50% of a worker type's attempts FAILED (retryable) over 10 min —
usually a **downstream outage** (object store / data source / report template),
not a one-off. **First actions:**
1. Identify `type` from the alert label.
2. If object storage is enabled: check MinIO
   (`docker inspect -f '{{.State.Health.Status}}' grading-minio-prod`;
   `docker logs --tail 100 grading-minio-prod`). See [3.7](#37-object-storage-minio-issues).
3. Check DB health (3.1 step 4 covers datasource errors) and report-template
   changes shipped in the last release.
4. Watch whether failures convert to dead-letters; if so, escalate to 3.2.

### 3.5 `GradingWorkerGenerationSlow` (warning)
**Means:** p95 of `grading_worker_generation_seconds` for a `type` > 120 s over
10 min — the **in-process executor may be backing up** (single-node, in-process
workers). **First actions:**
1. Check host load / memory (`docker stats --no-stream`, `free -m`) — slow
   generation on a 2 GB box is often CPU/IO contention from a neighbour.
2. Check input size (a very large import/report) and object-store latency.
3. If sustained and user-impacting, throttle new heavy jobs and consider rolling
   back a release that changed the generation path. SEV2/SEV3 by impact.

### 3.6 `GradingErrorLogSpike` (warning) — coarse audit-failure signal
**Means:** > 20 ERROR-level log events in 5 min (`logback_events_total`).
**Audit-write failures and worker dead-letters surface here.** **First actions:**
1. `docker logs --since 15m grading-api-prod 2>&1 | grep -i ERROR | tail -n 100`.
2. **Specifically rule out audit-write failure** — the audit trail is mandatory
   and append-only. Grep for audit-path errors and confirm audit rows are still
   being written:
   ```
   docker exec -it grading-postgres-prod \
     psql -U grading_audit_reader -d grading_control_db \
     -c "SELECT max(created_at) FROM <audit_table>;"
   ```
   If audit writes have stopped for sensitive actions → **SEV1** (audit-trail
   failure), freeze the affected flow, page the owner.
3. Correlate with `grading_worker_outcome_total` (dead-letters) and 3.2.

---

### 3.7 Object storage (MinIO) issues
Only relevant when `GRADING_STORAGE_TYPE=minio`. The S3 API (:9000) is
internal-only; the console (:9001) is loopback-only (reach via SSH tunnel:
`ssh -L 9001:127.0.0.1:19001 <vps>`).
1. `docker inspect -f '{{.State.Health.Status}}' grading-minio-prod`
2. `docker logs --tail 100 grading-minio-prod`
3. If unhealthy and the deploy gate is blocking the roll: the api keeps serving
   the previous version (intended). Fix MinIO or temporarily set
   `GRADING_STORAGE_TYPE=local` and redeploy to unblock, then re-enable.

### 3.8 Certificate expiry
TLS is terminated by the **outer** host proxy (nginx/Traefik — see
`infra/reverse-proxy/`), not by this compose stack. Renewals are handled by the
host's ACME/cert tooling. If a public TLS warning fires:
`curl -vI https://grading.hrlab.uz 2>&1 | grep -i 'expire\|issuer'` and check the
outer proxy's cert renewal. This is outside the grading compose stack.

### 3.9 Platform super-admin granted/revoked (blast-radius control)
**Means:** the platform super-admin role (`HRLAB_SUPER_ADMIN`) was just granted to
or revoked from a user. This is the **highest-blast-radius** authorization change
in the system — a super admin can act in **every ACTIVE tenant** (cross-tenant
switcher, "Fix A"). Every such transition emits, on top of the normal
`USER_ROLE_ASSIGNED`/`USER_ROLE_REMOVED` row:
- an **append-only audit action** — `PLATFORM_SUPER_ADMIN_GRANTED` /
  `PLATFORM_SUPER_ADMIN_REVOKED` (actor = who did it, `entity_type=User`,
  `entity_id` = the target user, reason carries the `user_role` id + code path);
- a **WARN-level, greppable log line** for alerting (no email/pager infra):
  ```
  marker=SUPER_ADMIN_GRANT PLATFORM_SUPER_ADMIN granted actorUserId=… targetUserId=… tenantId=… context=…
  ```
  (revoke uses `marker=SUPER_ADMIN_REVOKE`). Ids only — no PII/secrets.

**Alert rule (recommend):** fire on any log matching `marker=SUPER_ADMIN_GRANT`
(Loki: `{app="grading-api"} |= "marker=SUPER_ADMIN_GRANT"`). Treat every grant as
a change to review: confirm the actor and target were expected and that a change
ticket exists. Cross-check the audit trail:
```
docker exec -it grading-postgres-prod \
  psql -U grading_audit_reader -d grading_control_db \
  -c "SELECT created_at, tenant_id, actor_user_id, entity_id, reason
      FROM <audit_table>
      WHERE action IN ('PLATFORM_SUPER_ADMIN_GRANTED','PLATFORM_SUPER_ADMIN_REVOKED')
      ORDER BY created_at DESC LIMIT 50;"
```
An **unexpected** grant is a potential privilege-escalation → **SEV1** (page
security); freeze the actor and review the hash-chained trail.

**MFA is enforced at the IdP (Zitadel), not the app.** `HRLAB_SUPER_ADMIN`
accounts **MUST** have multi-factor authentication **required by a Zitadel login
policy**. The grading API is an **OIDC resource server** — it only *validates*
already-issued access tokens; it does **not** run the login/MFA flow and therefore
**cannot** itself enforce MFA. The enforcement point is Zitadel's login policy
(org/instance-level "force MFA"), so every super-admin's second factor is a
prerequisite the IdP owns. Operators must verify, when provisioning or reviewing a
super admin, that the IdP login policy that applies to that account requires MFA.
Do not treat the app's role-grant audit/alert as an MFA control — it records the
grant; the IdP guarantees the login is MFA-protected.

---

## 4. Backup, PITR & restore

> The dedicated grading Postgres (`grading-postgres-prod`) has **no host port**
> and its data lives in the named volume `grading-pg-data-prod`. Database =
> `grading_control_db` (placeholder; matches `POSTGRES_DB`). Back up from inside
> the container or via the docker network.

### 4.1 Strategy & cadence (single-VPS pragmatic plan)

| Layer | Method | Cadence | Retention | Encryption | Off-box? |
| --- | --- | --- | --- | --- | --- |
| Logical (primary) | `pg_dump -Fc` (custom format) | **Daily** (low-traffic window) + before every schema-change release | 7 daily, 4 weekly, 3 monthly | Encrypt at rest (`gpg`/`age`) **before** leaving the box | **Yes** — copy to off-box object storage |
| WAL / PITR (target) | `archive_command` → WAL archive + periodic `pg_basebackup` | Continuous WAL + weekly base | Enough WAL to cover RPO window | Encrypted archive | Yes |
| Object store (MinIO) | bucket versioning + periodic `mc mirror` | Daily mirror | Match DB retention | Encrypted target | Yes |

**RPO / RTO targets (MVP):** RPO ≤ 15 min (requires WAL archiving/PITR; the daily
`pg_dump` alone gives ~24 h RPO — start with `pg_dump`, add WAL PITR to hit 15
min). RTO ≤ 2 h. **Backup success = 100% daily** — a failed backup is a SEV2 and
blocks the next release (backup-health is a release gate).

**Rules:** no unencrypted backup ever leaves the box; backups must not live only
in the same failure domain (copy off-box); restrict access to the backup target;
never log backup credentials.

### 4.2 Daily logical backup (pg_dump) — copy/paste

```
set -euo pipefail
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
OUT="/opt/grading/backups/grading_${STAMP}.dump"
mkdir -p /opt/grading/backups
# Dump as the superuser inside the container (custom format = parallel restore).
docker exec grading-postgres-prod \
  pg_dump -U "<POSTGRES_SUPER_USER>" -d "<POSTGRES_DB>" -Fc \
  > "${OUT}"
# Encrypt BEFORE it leaves the box (placeholder recipient/key).
gpg --encrypt --recipient "<BACKUP_GPG_RECIPIENT>" "${OUT}"
rm -f "${OUT}"                       # keep only the encrypted copy on disk
# Off-box copy (placeholder target — rclone/mc/scp to encrypted object storage).
# rclone copy "${OUT}.gpg" "<OFFBOX_BACKUP_TARGET>"
echo "backup OK: ${OUT}.gpg"
```
Verify the backup is non-empty and recent in monitoring; alert on age > 26 h.

### 4.3 Pre-migration backup checkpoint

**Mandatory before any release that ships a schema change.** Run [4.2](#42-daily-logical-backup-pg_dump--copypaste)
immediately before triggering the deploy, label the file
`grading_pre-<image_tag>.dump.gpg`, and record the tag + checkpoint filename in
the release log. This is the rollback floor for the DB.

### 4.4 Restore drill (step-by-step)

Run this **monthly** against a scratch DB (never overwrite production blindly).
Document the date, operator, dump used, and elapsed time as restore evidence.

**A. Restore into a throwaway database (validation, zero prod impact):**
```
set -euo pipefail
# 1. decrypt the chosen backup to a local file
gpg --decrypt grading_<STAMP>.dump.gpg > /tmp/restore.dump
# 2. create a scratch DB inside the running postgres container
docker exec grading-postgres-prod \
  psql -U "<POSTGRES_SUPER_USER>" -d postgres \
  -c "CREATE DATABASE grading_restore_drill;"
# 3. restore (parallel jobs; ignore benign role-already-exists notices)
docker exec -i grading-postgres-prod \
  pg_restore -U "<POSTGRES_SUPER_USER>" -d grading_restore_drill --no-owner -j2 \
  < /tmp/restore.dump
# 4. smoke-validate row counts on key tables (tenant-safe — counts only)
docker exec grading-postgres-prod \
  psql -U "<POSTGRES_SUPER_USER>" -d grading_restore_drill \
  -c "SELECT 'tenants', count(*) FROM <tenant_table>
      UNION ALL SELECT 'audit', count(*) FROM <audit_table>;"
# 5. clean up the drill DB
docker exec grading-postgres-prod \
  psql -U "<POSTGRES_SUPER_USER>" -d postgres \
  -c "DROP DATABASE grading_restore_drill;"
rm -f /tmp/restore.dump
echo "restore drill OK"
```

**B. PITR restore (recover to a point in time — disaster path):**
1. Stop the api (`$COMPOSE stop grading-api`) so nothing writes.
2. Provision a fresh data dir from the latest `pg_basebackup`.
3. Create `recovery.signal` and set `restore_command` + `recovery_target_time`
   = the point in time just before the incident.
4. Start Postgres; it replays archived WAL up to the target and promotes.
5. Re-point the stack at the recovered cluster, run the smoke test, then restart
   the api.
> PITR requires WAL archiving to be configured first ([4.1](#41-strategy--cadence-single-vps-pragmatic-plan)).
> Until then the realistic RPO is the daily `pg_dump` interval.

**C. Full environment restore (box lost):** rebuild VPS → install Docker →
restore `/opt/grading` (compose + `infra/` artifacts) → recreate `.env.prod` from
the secret store (NOT from git) → restore the encrypted dump per A into the real
`POSTGRES_DB` → restore the MinIO bucket (`mc mirror`) → roll the known-good
image tag → full smoke. Target RTO ≤ 2 h.

**Restore is a release gate input:** if the most recent backup is missing,
stale, or the monthly drill failed, **do not deploy** (backup readiness gate).

---

## 5. Secrets rotation runbook

All secrets live ONLY in `/opt/grading/.env.prod` on the VPS (placeholders in
`.env.prod.example`). Never commit, never log, never bake into an image. Rotate
on a schedule and immediately on suspected exposure.

> **Order of operations principle:** for credentials the app validates on every
> request/connection, add/allow the new value, roll the app, then retire the old
> value — to avoid a window where the app holds the wrong credential.

### 5.1 Database passwords (runtime / migrator / audit_reader)
**Blast radius:** wrong value → api can't connect (→ `GradingApiDown`) or the
migrate one-shot fails (→ deploy aborts, old api keeps serving). These are three
**separate** roles (`grading_runtime`, `grading_migrator`,
`grading_audit_reader`) created with LOGIN by
`infra/db/prod/01-create-prod-roles.sh` and granted by changelog 005 — keep them
distinct; never collapse them.
**Steps (runtime role shown; same shape for the others):**
```
cd /opt/grading
# 1. set the new password ON THE ROLE inside Postgres
docker exec grading-postgres-prod \
  psql -U "<POSTGRES_SUPER_USER>" -d "<POSTGRES_DB>" \
  -c "ALTER ROLE grading_runtime PASSWORD '<NEW_RUNTIME_PASSWORD>';"
# 2. update .env.prod (the matching variable)
sed -i "s|^SPRING_DATASOURCE_PASSWORD=.*|SPRING_DATASOURCE_PASSWORD=<NEW_RUNTIME_PASSWORD>|" .env.prod
# 3. roll the api so it picks up the new env (migrator picks up on next migrate)
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d grading-api
# 4. verify
curl -fsS http://127.0.0.1:18080/actuator/health/readiness
```
For `grading_migrator` (`LIQUIBASE_DATASOURCE_PASSWORD`) the change is exercised
on the next migrate run; for `grading_audit_reader`
(`GRADING_AUDIT_READER_PASSWORD`) update the env and any audit-query consumer.
Also rotate `POSTGRES_SUPER_PASSWORD` if the superuser may be exposed.

### 5.2 JWT / OIDC signing
The api **validates** tokens (resource server); it does not sign them — signing
keys live in the IdP (issuer `GRADING_JWT_ISSUER_URI`, JWKS
`GRADING_JWT_JWK_SET_URI`). **Blast radius:** rotating the IdP signing key
invalidates tokens signed with the old key; the api re-fetches JWKS, so brief
401s are possible until clients re-auth. **Steps:** rotate the key in the IdP →
ensure the new key is published in JWKS (the api caches JWKS; allow propagation)
→ keep the old key in JWKS until existing tokens expire → confirm logins succeed.
If the issuer/audience itself changes, update `GRADING_JWT_ISSUER_URI` /
`GRADING_JWT_AUDIENCE` / `GRADING_JWT_JWK_SET_URI` in `.env.prod` and roll the
api. The ZITADEL machine-user PAT (`GRADING_IDP_ZITADEL_TOKEN`) is a high-value
identity-minting secret — rotate it in ZITADEL, update `.env.prod`, roll the api;
blast radius = admin-invite IdP provisioning stops working until updated.

### 5.3 MinIO credentials (the new object-storage creds)
`MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` are **both** the MinIO server root
creds **and** the api's S3 access/secret key (single pair configures both).
**Blast radius:** rotating breaks api↔MinIO until both sides match; report/import
artifact read+write fail (→ `GradingWorkerFailureRateHigh` / dead-letters) while
mismatched. **Steps (do both halves, then roll, in one window):**
```
cd /opt/grading
# 1. generate strong, unique values (example)
NEW_USER="grading_s3_$(openssl rand -hex 4)"
NEW_PASS="$(openssl rand -base64 30)"
# 2. update .env.prod (server creds == api S3 creds)
sed -i "s|^MINIO_ROOT_USER=.*|MINIO_ROOT_USER=${NEW_USER}|"      .env.prod
sed -i "s|^MINIO_ROOT_PASSWORD=.*|MINIO_ROOT_PASSWORD=${NEW_PASS}|" .env.prod
COMPOSE="docker compose -f docker-compose.prod.yml --env-file .env.prod"
# 3. recreate MinIO with the new root creds, then roll the api with matching keys
$COMPOSE --profile storage up -d --force-recreate grading-minio
# wait healthy
for i in $(seq 1 30); do
  [ "$(docker inspect -f '{{.State.Health.Status}}' grading-minio-prod 2>/dev/null)" = healthy ] && break
  sleep 3
done
$COMPOSE up -d grading-api
# 4. verify a round-trip (trigger a small export/report; watch worker outcomes)
```
> MinIO root rotation rotates the **root** identity. If you later move to scoped
> service accounts (recommended beyond MVP), rotate those instead and keep root
> offline.

### 5.4 General rotation hygiene
- Different secrets per environment; production secrets reachable only by
  production workloads.
- No developer direct access to prod secrets; **break-glass only**, and every
  break-glass access is recorded (who/when/why) in the release/incident log.
- After any rotation, re-run the post-deploy smoke + a sensitive-flow check.

---

## 6. Rollback strategy (deep dive)

- **Application:** previous immutable image tag via
  [1.2](#12-redeploy-an-existing-tag--rollback). Always safe, no `latest`.
- **Database:** **not automatic.** Forward-only by default. If a release shipped
  a schema change you must roll back, use the pre-migration backup checkpoint
  ([4.3](#43-pre-migration-backup-checkpoint)) and a **pre-approved** manual
  rollback plan. Prefer **additive, backward-compatible** migrations so an app
  rollback works without a DB rollback. **Avoid destructive migrations.**
- **When rolling the app backward, do NOT re-run migrate** if the older image's
  Liquibase changelog is a subset — the schema is already ahead; re-running is a
  no-op at best and a mismatch at worst. Verify changelog compatibility first.
- **Object storage:** flip `GRADING_STORAGE_TYPE` back to `local` and redeploy to
  fall back to local-FS; the `grading-minio-data` volume is retained.
- **Feature flags:** disable risky features (AI assist, compensation, advanced
  reporting, integrations) to neutralise a bad path without a redeploy where
  flags exist.
- Always run **post-rollback smoke tests** + [post-deploy verification](#16-postdeploy-verification-checklist).

---

## 7. On-call basics & escalation

**On-call owns:** acknowledging alerts within the [severity targets](#2-incident-response--severity-matrix),
running the [alert playbook](#3-alert-response-playbook), and deciding
mitigate-vs-rollback. Keep a personal copy of: VPS SSH access, the GHCR token
path, the secret-store location, and this runbook.

**Escalation ladder:**
1. **On-call engineer** — triage + mitigate (SEV3, and first response to SEV2).
2. **Incident commander** — coordinate SEV1/SEV2, own comms + timeline.
3. **Owner** (`az.asqarov@gmail.com`) — sign-off on rollback, destructive DB
   actions, break-glass, and any tenant-data/salary/audit (SEV1) incident.
4. **Security** — any cross-tenant leak, salary exposure, or audit-trail failure
   (always SEV1, page immediately).

**Comms:** internal incident channel for all SEVs; status page + direct client
notice for SEV1 affecting tenant data or any SLA breach. Use the postmortem
template after every SEV1/SEV2.

---

## 8. Post-deploy verification checklist

Run after every deploy / rollback (the CD smoke step covers 1–3; do 4–8 by hand):

- [ ] 1. API readiness: `curl -fsS http://127.0.0.1:18080/actuator/health/readiness`
- [ ] 2. Frontend health: `curl -fsS http://127.0.0.1:18081/healthz`
- [ ] 3. Public origin: `curl -fsS https://grading.hrlab.uz/ -o /dev/null`
- [ ] 4. Deployed tag matches intent: `grep '^IMAGE_TAG=' /opt/grading/.env.prod`
- [ ] 5. Migrator exited 0 (deploy log) and no `dev`/`test-roles` context ran.
- [ ] 6. No new alerts firing (`GradingApiDown`, dead-letter, error-log spike).
- [ ] 7. Dead-letter gauge is 0 and worker outcomes look normal (Grafana
      `grading-workers` dashboard).
- [ ] 8. A sensitive flow still writes audit rows (audit `max(created_at)` is
      advancing) and salary fields remain protected.
- [ ] 9. Backup readiness confirmed (last backup recent + drill green).
- [ ] 10. Release logged (tag, commit SHA, migrations, approver, rollback plan).

---

## 9. Related files

- Roll sequence (read-only): `.github/workflows/deploy.yml`,
  `docker-compose.prod.yml`
- DB role bootstrap: `infra/db/prod/01-create-prod-roles.sh`
- Env template (placeholders): `.env.prod.example`
- Alert rules (Batch 6): `infra/observability/alerts/grading-alerts.yml`
- Metrics inventory: `infra/observability/README.md`
- Workers dashboard: `infra/observability/dashboards/grading-workers.json`
- Alertmanager routing: `infra/observability/alertmanager/alertmanager.yml`
- Prometheus scrape example: `infra/observability/prometheus/prometheus.scrape.example.yml`

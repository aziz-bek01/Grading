# MVP 1 — DevOps / SRE Blueprint — grading.hrlab.uz

Owner: devops-sre subagent
Status: Draft v1.0 (MVP 1 baseline)
Audience: backend-engineer, frontend-engineer, qa-engineer, security-engineer, hr-product-owner, database-architect
Single source of truth: `архитектура.md` §21 (DevOps & Deployment), §22 (Testing Strategy)

---

## 0. Document map

| § | Topic |
|---|-------|
| 1 | DevOps / SRE objectives for MVP 1 |
| 2 | Scope (in / out of MVP 1) |
| 3 | Environment strategy |
| 4 | Local development setup |
| 5 | Docker strategy |
| 6 | docker-compose.local stack |
| 7 | CI/CD pipeline (28 stages) |
| 8 | Kubernetes deployment plan |
| 9 | Helm / Kustomize structure |
| 10 | PostgreSQL HA strategy |
| 11 | Liquibase migration strategy (control plane + tenant schema) |
| 12 | Tenant schema migration workflow |
| 13 | Secrets management (Vault / KMS / Sealed Secrets) |
| 14 | Vault / KMS usage (tenant-specific keys) |
| 15 | Object storage strategy |
| 16 | Redis strategy |
| 17 | Observability architecture |
| 18 | Logging rules |
| 19 | Metrics & dashboards |
| 20 | Alerting rules |
| 21 | Backup and restore plan |
| 22 | Disaster recovery plan |
| 23 | Release management process |
| 24 | Rollback strategy |
| 25 | Security gates (blocking) |
| 26 | Tenant isolation / salary / audit release gates |
| 27 | Operational runbooks (18) |
| 28 | DevOps backlog per agent |
| 29 | MVP 1 operational readiness checklist |
| 30 | Risks & mitigations |
| 31 | Next steps (post-MVP 1) |

---

## 1. DevOps / SRE objectives for MVP 1

1. Deliver a **production-grade, secure, repeatable** deployment pipeline for the modular monolith (`grading-api`) + frontend + 4 async workers.
2. Establish **zero-secrets-in-Git** posture from day one.
3. Make **tenant isolation, salary permission, audit trail** automated **blocking release gates** in CI/CD.
4. Stand up **observability stack** (metrics, logs, traces, alerts) before any production-like deploy.
5. Establish **PITR backups + tested restore drill** before production cut-over.
6. Document **18 operational runbooks** and incident severity matrix.
7. Hit MVP 1 SLOs: **99.5% API availability**, **p95 < 500 ms** for common reads, **RPO ≤ 15 min**, **RTO ≤ 2 h**.
8. Ensure every release has: **version + commit SHA + immutable image digest + migration list + rollback plan + four-gate sign-off** (QA GO + Security ship + DevOps op-GO + PO accept).

---

## 2. Scope

**In MVP 1**
- Tenant isolation foundation (control plane + schema-per-tenant)
- Users / roles / permissions
- Project workspace
- Organization structure basic
- Position catalog, job profile
- Basic methodology builder
- Scoring engine, grade assignment
- Audit trail
- Localization foundation
- All 11 scope items above containerized, deployed, observable, backed up.

**Deferred to later MVPs**
- DB-per-tenant for enterprise clients (provision workflow placeholder only)
- Compensation / salary range engine (salary permission gate still designed and tested, but no salary fields persisted in MVP 1)
- AI Assist (feature flag wired, ai-gateway worker deployed but disabled)
- Integrations worker (deployed, idle)
- Premium isolation mode
- OWASP ZAP DAST in pipeline (manual in staging only for MVP 1)

---

## 3. Environment strategy

| Env | Purpose | Hostname pattern | Data | Secrets source | Auto-deploy | Approval |
|-----|---------|------------------|------|----------------|-------------|----------|
| **local** | Developer machine | `localhost`, `*.localtest.me` | Seed fixtures + synthetic | `.env.local` (gitignored), dev-only static keys | n/a | none |
| **dev** | Integration branch | `grading.dev.hrlab.uz` | Synthetic | Vault `dev/*` | every merge to `develop` | none |
| **test** | Automated QA suites | `grading.test.hrlab.uz` | Synthetic + QA fixtures | Vault `test/*` | every nightly | none |
| **staging** | Production-like rehearsal | `grading.staging.hrlab.uz` | Anonymized prod-shape | Vault `staging/*` | every RC tag | DevOps |
| **production** | Live SaaS | `grading.hrlab.uz`, `*.grading.hrlab.uz` | Real tenant data | Vault `prod/*` (RBAC-walled) | release tag + 4 approvals | 4-gate sign-off |

**Environment separation rules** (HARD):
- Secrets, encryption keys, OAuth client IDs, JWT signers — **different per environment**.
- **No production data** in dev/test; staging may use **anonymized** prod-shape data, refreshed quarterly with PII scrub.
- **No developer direct access** to production secrets; **break-glass procedure** (audited, time-boxed, 2-person).
- Each environment has its **own PostgreSQL cluster, Redis, object-storage bucket prefix, Vault path, KMS key ring**.
- Each env has its **own Kubernetes namespace**: `grading-dev`, `grading-test`, `grading-staging`, `grading-prod`. (Plus shared `observability`, `ingress-nginx`, `cert-manager`, `vault` infra namespaces.)

---

## 4. Local development setup

**Goal**: a developer can clone the repo and have the full stack running in **≤ 15 min**, with **no production secrets** and **no real credentials**.

Required tooling: Docker Desktop 24+, JDK 21, Node 20+, pnpm, Maven 3.9+, `kubectl`, `helm`, `make`, `git`, `pre-commit`.

**Commands** (target `make` interface):
```
make bootstrap        # install pre-commit hooks, fetch deps
make up               # docker-compose up -d (postgres, redis, minio, mailhog)
make migrate          # run Liquibase against local DB
make seed             # load demo tenant + admin user
make api              # run grading-api locally (Spring Boot devtools)
make frontend         # run Vite dev server
make test             # unit + integration
make tenant-isolation # run mandatory tenant isolation test pack locally
make down             # stop stack, preserve volumes
make nuke             # tear down + remove volumes (warning prompt)
```

**Local secret handling**:
- `.env.local.example` checked in (placeholders only).
- `.env.local` gitignored.
- Pre-commit hook runs **`gitleaks`** and **`trufflehog`** against staged files; blocks commit on hit.
- No `KMS_KEY_ID`, `OAUTH_CLIENT_SECRET`, `SMTP_PASSWORD` ever in repo.
- For local OIDC, run **Keycloak** in compose (preset realm `grading-local`).

**Local domain mapping**: `*.localtest.me → 127.0.0.1` (resolves automatically, supports wildcard for multi-tenant subdomain testing).

---

## 5. Docker strategy

**Build principles**:
- **Multi-stage builds** for every image (build stage → distroless / minimal runtime stage).
- **Non-root** users (UID 1000, named `app`).
- **Read-only root filesystem** in K8s; writable volume mounted at `/tmp` and `/var/cache/app` only.
- **No `latest` tag in production**; production tags = `vMAJOR.MINOR.PATCH-<git-sha-short>` and pinned by **digest** in K8s.
- **Image labels** (OCI): `org.opencontainers.image.version`, `revision` (SHA), `created`, `source`, `licenses`.
- **SBOM** generated per image (`syft`) and uploaded as a CI artifact + attached to release.
- **Image signing** with `cosign` (keyless OIDC) — verified by K8s admission policy post-MVP.
- **Image scan** with `trivy` (severity ≥ HIGH = block) before push.
- **No secrets baked in**; runtime config via env / projected ConfigMaps / Secrets.
- **JVM options** via `JAVA_TOOL_OPTIONS` (env-driven), container-aware (`-XX:+UseContainerSupport`).
- **Graceful shutdown**: 30-second `terminationGracePeriodSeconds`; Spring `server.shutdown=graceful`.

**Images produced** (MVP 1):
| Image | Base | Purpose | Listens |
|-------|------|---------|---------|
| `grading-api` | `eclipse-temurin:21-jre-jammy` (distroless candidate) | Spring Boot modular monolith | 8080 (HTTP), 8081 (mgmt/actuator) |
| `grading-frontend` | `nginx:1.27-alpine` | Static React + runtime config inject | 8080 |
| `grading-import-worker` | same as api (shared jar) | Async import jobs | 8081 (mgmt) |
| `grading-report-worker` | same | Async report generation | 8081 |
| `grading-ai-gateway` | same (disabled MVP 1) | AI provider broker | 8081 |
| `grading-integration-worker` | same (idle MVP 1) | Outbound integrations | 8081 |

**Image promotion**: built once on PR → registry. Same digest promoted dev → test → staging → prod. **No rebuild between environments.**

---

## 6. docker-compose.local stack

`infra/docker-compose/docker-compose.local.yml` services:

| Service | Image | Purpose |
|---------|-------|---------|
| `postgres` | `postgres:16-alpine` | Primary DB; volume mounted |
| `redis` | `redis:7-alpine` | Cache + queue |
| `minio` | `minio/minio:latest` | S3-compatible object storage (NOT in prod images) |
| `keycloak` | `quay.io/keycloak/keycloak:25` | Local OIDC provider; preset realm |
| `mailhog` | `mailhog/mailhog` | Captured outbound email |
| `vault-dev` | `hashicorp/vault:1.17` (dev mode) | Local secrets (root token printed in logs — local only) |
| `prometheus` | `prom/prometheus` (optional) | Local metrics scrape |
| `grafana` | `grafana/grafana` (optional) | Local dashboards |

Networks: `grading-local` (bridge). Volumes: `pg_data`, `minio_data`, `keycloak_data`. **No production data**. Health checks on every service. Compose `profiles` to keep observability optional.

---

## 7. CI/CD pipeline — 28 stages

Reference implementation: **GitHub Actions** primary, GitLab CI mirror.

| # | Stage | Tool | Blocking? | Output |
|---|-------|------|-----------|--------|
| 1 | Checkout (shallow + LFS) | `actions/checkout` | yes | source tree |
| 2 | Validate branch + commit (conventional commits, signed) | custom | yes | metadata |
| 3 | Secret scan | `gitleaks`, `trufflehog` | yes (any HIGH) | SARIF |
| 4 | Dependency scan (Java + npm) | OWASP DC, `npm audit`, `osv-scanner` | yes (CVSS ≥ 7) | SARIF |
| 5 | Static analysis (SAST) | SonarQube, Semgrep, Spotbugs, ESLint | yes (blocker) | SARIF |
| 6 | Backend unit tests | Maven + JUnit 5 | yes | JUnit XML, JaCoCo |
| 7 | Frontend unit tests | Vitest | yes | JUnit XML, coverage |
| 8 | Backend integration tests (Testcontainers) | Maven + Testcontainers | yes | JUnit XML |
| 9 | API contract tests | RestAssured / Pact | yes | JUnit XML |
| 10 | **Tenant isolation test pack** | dedicated `@Tag("tenant-isolation")` | **yes — release gate** | JUnit XML |
| 11 | **Salary permission test pack** | dedicated `@Tag("salary-permission")` | **yes — release gate** | JUnit XML |
| 12 | **Audit trail test pack** | dedicated `@Tag("audit")` | **yes — release gate** | JUnit XML |
| 13 | Build backend Docker image (multi-arch optional) | Buildx | yes | OCI image |
| 14 | Build frontend Docker image | Buildx | yes | OCI image |
| 15 | Container image scan | Trivy (HIGH/CRITICAL block) | yes | SARIF |
| 16 | Generate SBOM | Syft (SPDX + CycloneDX) | yes | SBOM artifact |
| 17 | Push image (signed) | Buildx + Cosign | yes (on `main`/tag) | image digest |
| 18 | Deploy to dev (auto) | Helm upgrade | yes (on `develop`) | dev release |
| 19 | Apply Liquibase migrations (controlled K8s Job) | liquibase Job | yes | migration log |
| 20 | Smoke tests (dev) | Playwright + cURL probes | yes | report |
| 21 | Deploy to staging (auto on RC tag) | Helm upgrade | yes (on RC) | staging release |
| 22 | E2E tests (staging) | Playwright suite | yes | report |
| 23 | Security checks (staging) | ZAP baseline, k6 (light) | yes (critical block) | report |
| 24 | **Manual release approval** | GitHub Environments | yes (4-gate sign-off) | approval record |
| 25 | Deploy to production | Helm upgrade w/ pinned digest | yes | prod release |
| 26 | Post-deploy verification | smoke + tenant isolation prod-safe | yes | report |
| 27 | Monitoring check (Prometheus / alerts quiet) | scripted query | yes | report |
| 28 | Release notes + rollback checkpoint | release-please + Helm history snapshot | yes | release record |

**Branch policy**: PR → `develop` (triggers stages 1–20); RC tag `vX.Y.Z-rc.N` → staging (1–23); release tag `vX.Y.Z` → production (1–28 with manual gate at 24).

**Pipeline-wide blocking gates** (also enforced):
- build failed
- any unit/integration/contract/tenant/salary/audit test failed
- critical or high vulnerabilities unresolved (CVSS ≥ 7, no documented exception)
- secrets detected in commit or image
- Docker image scan failed
- Liquibase migration failed (or staging dry-run drift)
- smoke test failed
- readiness probe failed post-deploy
- production backup not healthy in last 24 h
- rollback plan missing in release notes
- observability dashboard missing for any newly added service
- alert rules missing for any critical service

---

## 8. Kubernetes deployment plan

**Cluster topology (MVP 1)**: single managed K8s cluster per region (UZ + DR region later). Node pools:
- `system` (control-plane add-ons)
- `general` (api, frontend, workers)
- `db` (PostgreSQL, taints + nodeSelector; consider managed DB later)
- `observability` (Prometheus, Loki, Grafana)

**Namespaces**: `grading-dev`, `grading-test`, `grading-staging`, `grading-prod`, `observability`, `ingress-nginx`, `cert-manager`, `vault`, `external-secrets`.

**Workloads** (per env):

| Kind | Name | Replicas (prod) | HPA | PDB | Notes |
|------|------|-----------------|-----|-----|-------|
| Deployment | `grading-api` | 3 (min) | 3–10, cpu 70% | minAvailable=2 | stateless |
| Deployment | `grading-frontend` | 2 | 2–6 | minAvailable=1 | nginx |
| Deployment | `grading-import-worker` | 2 | 2–6 by queue depth | minAvailable=1 | |
| Deployment | `grading-report-worker` | 2 | 2–6 | minAvailable=1 | |
| Deployment | `grading-ai-gateway` | 1 (disabled by flag) | 1–4 | minAvailable=0 | feature-flagged |
| Deployment | `grading-integration-worker` | 1 (idle MVP 1) | 1–4 | minAvailable=0 | |
| StatefulSet | `postgresql` (Patroni or CNPG) | 3 (1 primary + 2 replicas) | n/a | minAvailable=2 | HA; managed DB option preferred for prod |
| Deployment | `redis` (or managed) | sentinel/HA in prod | n/a | n/a | session + queue |
| External | object storage | n/a | n/a | n/a | S3-compatible (e.g. UZCloud / AWS S3 / managed) |
| External | Vault | HA cluster | n/a | n/a | managed or self-hosted |

**Networking**:
- Ingress: `ingress-nginx` (with ModSecurity) terminating TLS, certs from `cert-manager` (Let's Encrypt prod + ACME DNS-01 for wildcard tenant subdomains).
- **NetworkPolicies**: default deny; explicit allows: api → postgres, api → redis, api → object-storage, workers → redis, workers → postgres, ingress → api/frontend, observability scrape rules.
- TLS internal: mTLS between pods is a Phase-2 ambition (cert-manager + service mesh later). MVP 1: TLS at ingress, plaintext intra-cluster behind NetworkPolicy.

**Pod security baseline** (mandatory):
- `runAsNonRoot: true`, `runAsUser: 1000`
- `allowPrivilegeEscalation: false`
- `readOnlyRootFilesystem: true` (with explicit `emptyDir` for `/tmp`)
- `capabilities.drop: [ALL]`
- `seccompProfile: RuntimeDefault`
- Pod Security Standards: `restricted` enforced on `grading-prod`.
- ServiceAccount per workload; no automount where not needed.
- No `hostNetwork`, no `hostPath`, no privileged.

**Probes** (mandatory):
- `livenessProbe`: `/actuator/health/liveness` (api/workers), `/healthz` (frontend), failureThreshold=3
- `readinessProbe`: `/actuator/health/readiness`, includes DB + Redis check
- `startupProbe`: for JVM cold start (failureThreshold high)

**Resources** (starting envelope per pod, tune in staging):
- api: req 500m / 1Gi, lim 2 / 2Gi
- frontend: req 50m / 64Mi, lim 200m / 128Mi
- workers: req 250m / 512Mi, lim 1 / 1Gi
- postgres: req 1 / 4Gi, lim 2 / 8Gi (prod sized properly later)

---

## 9. Helm / Kustomize structure

Choice for MVP 1: **Helm umbrella** + **Kustomize overlays** at the values layer.

```
infra/helm/
  grading-api/
    Chart.yaml
    values.yaml             # safe defaults
    values-dev.yaml
    values-test.yaml
    values-staging.yaml
    values-prod.yaml        # min replicas, HPA, PDB, restrictive
    templates/
      deployment.yaml
      service.yaml
      ingress.yaml
      hpa.yaml
      pdb.yaml
      networkpolicy.yaml
      serviceaccount.yaml
      externalsecret.yaml   # External Secrets Operator → Vault
      configmap.yaml
      _helpers.tpl
  grading-frontend/         # same pattern
  grading-worker/           # reusable for 4 workers (instance via values)
  grading-umbrella/         # composes all charts for an environment
    Chart.yaml
    values-<env>.yaml
```

**Required values supported** (all charts):
- `image.repository`, `image.tag`, `image.digest`, `image.pullPolicy`
- `replicaCount`
- `env` (list, supports `valueFrom: secretKeyRef`)
- `externalSecrets[]` (paths into Vault, refresh interval)
- `resources.requests/limits`
- `ingress.enabled/hosts/tls`
- `probes.{liveness,readiness,startup}`
- `autoscaling.{enabled,min,max,targetCPU,custom}`
- `podSecurityContext`, `containerSecurityContext`
- `serviceAccount.{create,annotations}`
- `nodeSelector`, `tolerations`, `affinity`
- `pdb.{enabled,minAvailable}`
- `networkPolicy.{ingress,egress}` rules
- `featureFlags` (env-specific)
- `tenantConfig` placeholders (per-env tenant baseline list)

**Release artifacts kept**: `helm history grading-umbrella -n grading-prod` retained for 20 revisions → enables fast rollback.

---

## 10. PostgreSQL HA strategy

**MVP 1 target**: HA PostgreSQL — either **managed** (preferred: UZCloud / AWS RDS Multi-AZ / Azure Flexible Server) or **self-hosted with CloudNativePG (CNPG)** on K8s.

| Aspect | Strategy |
|--------|----------|
| Topology | 1 primary + 2 replicas, synchronous to one, async to the other |
| Failover | automatic via CNPG / managed service (< 60 s) |
| Connections | PgBouncer (transaction pooling) in front |
| Roles | `liquibase_migrator` (DDL), `grading_app` (DML, no DDL), `readonly_reporting` (SELECT only), `auditor` (insert-only on `audit_log`) |
| TLS | required in transit (`sslmode=verify-full`) |
| Encryption at rest | required (KMS-backed volume / managed feature) |
| Backups | PITR + daily full (see §21) |
| Replication lag SLO | < 5 s normal, alert > 30 s |
| Connection limit | sized to PgBouncer pool × pods; HPA-aware |
| Slow query log | enabled, ship to Loki |
| Tenant DB-per-tenant | provision workflow stubbed, used only for enterprise after MVP 1 |

---

## 11. Liquibase migration strategy

**Two-tier model** (matches §21 + arch doc multi-tenant model):

```
db/changelog/
  control-plane/
    db.changelog-master.xml
    v1.0/
      001-create-tenant-registry.xml
      002-create-users.xml
      003-create-tenant-keys-metadata.xml
      ...
  tenant-schema/
    db.changelog-master.xml
    v1.0/
      001-create-organization.xml
      002-create-positions.xml
      003-create-job-profiles.xml
      004-create-methodology.xml
      005-create-evaluation.xml
      006-create-audit-log.xml
      ...
```

**Execution model**:
- **Control plane changelog** runs against `public` schema of the shared control-plane DB (or dedicated DB). Tracks `tenant_registry`, `users`, global RBAC, tenant key references, feature flags.
- **Tenant schema changelog** runs **per tenant schema**. For each tenant, set `liquibase.defaultSchemaName=<tenant_schema>` and run the same master file.
- Both run via **dedicated Kubernetes Job** (`grading-migrator`) using image `grading-migrator:<release-sha>` containing Liquibase + the changelog JAR. **Not the app pod**.
- App pods on startup **verify** `databasechangelog` checksum but do **not** apply migrations.
- Migration user (`liquibase_migrator`) **separate** from runtime user (`grading_app`); runtime user has no DDL.

**Rules** (HARD):
1. **Pre-migration backup checkpoint** (PITR marker + on-demand full) is mandatory in staging+prod.
2. **No destructive migrations** (`DROP COLUMN`, `RENAME`, `DROP TABLE`, type narrowing) without explicit approval, feature flag, and 2-phase migration plan.
3. **Reversibility**: every changeSet has a `rollback` block or an explicit, approved manual rollback note.
4. **Idempotency**: changeSets use `preConditions` (`onFail="MARK_RAN"` only when safe).
5. Migrations must be **validated in staging** against an anonymized prod snapshot before prod.
6. **Migration duration monitored**; alert if > 5 min in prod (or > 25% of staging baseline).
7. Migration logs **must not contain tenant data** (no `SELECT` payload dumping; use Liquibase parameters carefully).
8. **Failure handling**: any tenant-schema migration failure → halt batch, alert, leave tenant in `migration_failed` status, do not release.

---

## 12. Tenant schema migration workflow

**New tenant provisioning** (atomic, scripted, replayable):

```
1. Create tenant_registry row (status = PROVISIONING)
2. Allocate tenant_schema name (deterministic: t_<short_uuid>)
3. Generate tenant-specific encryption key in KMS / Vault (envelope key)
4. Run tenant-schema changelog against new schema as liquibase_migrator
5. Create RLS policies (where applicable) tying rows to tenant_id / schema
6. Seed default dictionaries (factor catalog baseline, default roles)
7. Provision object-storage namespace: s3://grading-prod/tenants/<tenant_id>/
   - prefix-level ACL, lifecycle policy, server-side encryption with tenant KMS key
8. Run tenant isolation smoke test against new tenant (synthetic user reads + cross-tenant attempt)
9. Mark tenant_registry.status = ACTIVE; emit audit event TENANT_PROVISIONED
10. If any step fails: full transactional rollback (drop schema, revoke key, mark FAILED, alert)
```

**Tenant schema upgrade on release** (executed by `grading-migrator` Job):
```
for tenant in tenant_registry where status = ACTIVE order by created_at:
  acquire advisory lock for tenant
  snapshot schema metadata (optional — for fast rollback)
  apply tenant-schema-changelog
  record duration + result in tenant_migration_status table
  release lock
  on failure → alert, halt batch, mark tenant in degraded mode
```

**Per-tenant status tracking** table `control_plane.tenant_migration_status (tenant_id, changeset_id, applied_at, duration_ms, status, error_message)` — exposed to Grafana.

---

## 13. Secrets management

**Source of truth**: HashiCorp Vault (or cloud KMS-backed secret store). Delivered to K8s via **External Secrets Operator (ESO)** → `Secret` objects rendered at runtime.

**Hierarchy**:
```
vault/
  prod/
    grading-api/
      db-credentials
      redis-password
      oidc-client-secret
      jwt-signing-keys
      object-storage-credentials
      smtp-credentials
      ai-provider-keys           (locked, MVP 1)
      integration-api-keys       (locked, MVP 1)
    grading-migrator/
      db-migrator-credentials
    tenants/
      <tenant_id>/
        encryption-key-id        (KMS reference, not the key itself)
        salary-field-key-id      (KMS reference; MVP 3+)
  staging/...
  test/...
  dev/...
```

**Hard rules**:
- **No secrets in Git** — enforced by gitleaks + trufflehog in pre-commit and CI.
- **No secrets in frontend builds** (frontend gets only public OIDC client ID + public config via runtime-injected env JSON served by nginx).
- **No secrets in logs** (see §18).
- **Separate secrets per environment** — no key reuse across envs.
- **Rotate**: DB credentials 90 days, JWT signing keys 180 days, OIDC client secrets 365 days, KMS data keys per-tenant 365 days (envelope-key model: rotating envelope key without re-encrypting all data).
- **Production secrets accessible only to production workload identities** (Vault auth via K8s service account JWT, bound to `grading-prod` namespace).
- **Developer access** to prod secrets = **break-glass only**, audited, time-limited (1 h), 2-person approval.

**Sealed Secrets** (Bitnami) used as a fallback / bootstrapping mechanism for cluster-level config (e.g. ESO's initial token) when Vault is offline — encrypted with cluster public key, safe to commit.

---

## 14. Vault / KMS usage — tenant-specific encryption keys

**Envelope encryption** model for sensitive tenant data:
- **Master Key** per environment, stored in cloud KMS (non-exportable).
- **Tenant Data Encryption Key (DEK)** per tenant: a symmetric key encrypted with the master key, persisted in `control_plane.tenant_keys`.
- Application requests **decrypt DEK** through KMS at runtime; DEK cached in-memory with TTL ≤ 10 min; never persisted unencrypted; never logged.
- **Salary fields** (MVP 3+) and **attachment-at-rest** (MVP 2+) encrypted with tenant DEK at the column / object level.
- **Rotation**: rotate envelope master key (re-encrypt all DEKs) annually; rotate tenant DEK on demand (re-encrypt affected ciphertext via background job).
- **Break-glass key access** is audited and requires KMS-level role; not granted to app workloads.

MVP 1 enforces the **mechanism** (key generation per tenant during provisioning, KMS calls, audit) even though salary data is not yet stored — to validate the model before MVP 3.

---

## 15. Object storage strategy

**S3-compatible** (cloud-provider managed strongly preferred; MinIO acceptable for local/dev/test only — **never prod**).

| Aspect | Rule |
|--------|------|
| Naming | `s3://grading-<env>/tenants/<tenant_id>/projects/<project_id>/...` |
| Buckets | **No public buckets**; all `BlockPublicAccess=ON` |
| Encryption at rest | SSE-KMS with tenant-scoped KMS key (envelope) |
| Encryption in transit | TLS only |
| Access | IAM/STS for workloads; signed URLs (≤ 15 min TTL) for end-user downloads, scoped to tenant + project + object |
| Versioning | enabled (prod, staging) — for accidental delete recovery |
| Lifecycle | report artifacts: 90 days hot → 1 year cold → delete (configurable per tenant); audit attachments: 7 years (per regulation) |
| Replication | cross-region in prod (DR) |
| Access logging | enabled, shipped to Loki + immutable S3 access log bucket |
| Tenant boundary | object key prefix enforced server-side via IAM condition + verified client-side; cross-tenant access attempts logged as security event |

---

## 16. Redis strategy

| Aspect | Rule |
|--------|------|
| Purpose | session cache, idempotency keys, rate-limit counters, lightweight queue (Spring Integration / Redisson) |
| Mode | HA — Sentinel (self-hosted) or managed (preferred) with TLS |
| Auth | AUTH password from Vault; ACL users per workload (api / workers) |
| Persistence | AOF on for queue durability in prod |
| Keys | namespaced per tenant: `t:<tenant_id>:...`; never store salary or PII; TTL mandatory |
| Eviction | `allkeys-lru` for caches, `noeviction` for queue DB |
| Sensitive data | never store JWT secrets, never raw salary; only tenant-scoped opaque tokens / idempotency markers |

---

## 17. Observability architecture

**Stack** (deployed via Helm in `observability` namespace):
- **Prometheus** (kube-prometheus-stack): scrapes `actuator/prometheus`, kube-state, node-exporter, postgres-exporter, redis-exporter, blackbox-exporter (probes), nginx-ingress metrics.
- **Grafana**: dashboards (see §19), SSO via OIDC, no anonymous access.
- **Loki + Promtail / Grafana Alloy**: structured JSON logs from all pods.
- **Tempo** (preferred over Jaeger for OTLP + Grafana native integration): traces from app via OpenTelemetry Java agent and OTel-JS frontend SDK.
- **Alertmanager**: routes alerts to Slack (`#grading-ops`), email (on-call), PagerDuty/Opsgenie (SEV1).
- **OpenTelemetry Collector**: sidecar / DaemonSet, unifies traces + optional metrics.
- **Blackbox exporter**: external probes against `grading.hrlab.uz` from outside cluster for true availability SLO.

**Every workload exposes**:
- `/actuator/health/liveness`, `/actuator/health/readiness`
- `/actuator/prometheus` (basic auth or NetworkPolicy-scoped)
- traces via OTLP gRPC to collector
- structured JSON logs to stdout (no file logs)

**Mandatory log fields**: `timestamp`, `level`, `service`, `version`, `environment`, `traceId`, `spanId`, `correlationId`, `tenantId` (when safe), `userId` (when safe), `event`, `message`.

---

## 18. Logging rules

**HARD rules**:
1. Structured **JSON only** (Logback `LogstashEncoder` or equivalent).
2. **No salary data** in any log line (regex redactor at appender level; CI guard scans recent log samples).
3. **No JWT, no OAuth tokens, no API keys, no passwords, no encryption keys** in logs.
4. **No raw request body** for sensitive endpoints (auth, salary in MVP 3, user create, tenant provision, audit export).
5. **PII redaction**: email partially masked (`a***@domain`), phone last-4 only — outside audit log.
6. **CorrelationId** generated/propagated via `X-Correlation-Id` header; `traceId` from OTel; both in every log line.
7. **tenantId, userId** included where safe; never include in error messages returned to client.
8. **Cross-tenant access attempts**, **permission denials**, **failed auth**, **salary export attempts**, **audit write failures** logged at WARN/ERROR with explicit event codes (`SEC_CROSS_TENANT_ATTEMPT`, `SEC_PERMISSION_DENIED`, `SEC_AUDIT_WRITE_FAIL`, etc.).
9. **Audit log is a separate stream**: written to `audit_log` table (immutable, append-only) **and** mirrored to a tamper-evident sink (object storage with versioning + Object Lock). Application logs and audit logs are **never mixed**.
10. **Log retention**: app logs 30 days hot (Loki) + 1 year cold (object storage); audit logs 7 years (object storage with Object Lock); security events 1 year hot.

CI check: a smoke test in stage 20 / 26 greps last 5 min of logs for forbidden patterns (`password=`, `Bearer ey`, `salary`, regex for SSN/INN) — any hit fails the deploy verification.

---

## 19. Metrics & dashboards

**Dashboards** (Grafana, JSON in `infra/grafana/dashboards/`):

| Dashboard | Key panels |
|-----------|------------|
| `grading-api` | RPS, error rate, latency p50/p95/p99, top endpoints, JVM heap/GC, thread pool, DB pool, Redis ops/s |
| `postgresql` | conn pool usage, query latency, slow queries, lock waits, replication lag, disk usage, dead tuples, vacuum status, backup success |
| `workers` | queue length, job duration, failed jobs, retry rate, DLQ size, oldest job age |
| `report-generation` | reports/min, p95 duration, failure rate, output size distribution |
| `import` | import jobs/min, validation failure rate, rows processed/s |
| `security & audit` | auth failures/min, permission denials, **cross-tenant access attempts**, **salary export attempts** (MVP 3 ready), audit events/s, **audit write failures (must be 0)** |
| `tenant overview` | per-tenant: requests, errors, active users, storage usage, migration status |
| `infrastructure` | pod restarts, CPU/memory headroom, node disk usage, ingress 4xx/5xx, cert expiry, object storage errors |
| `SLO` | availability burn rate, latency budget, RPO/RTO tracking |

**Top-line application metrics**: `http_server_requests_seconds`, `http_server_errors_total`, `auth_failures_total`, `permission_denied_total`, `cross_tenant_access_attempts_total`, `audit_events_total`, `audit_write_failures_total`, `report_generation_duration_seconds`, `import_duration_seconds`, `liquibase_migration_duration_seconds`, `tenant_active_count`.

---

## 20. Alerting rules

**Critical (SEV1 / SEV2) — page on-call**:

| Alert | Threshold | Sev |
|-------|-----------|-----|
| API down | blackbox probe fail 2 min | SEV1 |
| Frontend down | blackbox 2 min | SEV1 |
| Database down | postgres-exporter scrape fail 1 min OR `up==0` | SEV1 |
| Redis down | exporter fail 2 min | SEV2 |
| High 5xx rate | > 2% of requests 5 min | SEV2 |
| High latency | p95 > 1 s for 10 min | SEV2 |
| **Cross-tenant access attempts spike** | `rate(cross_tenant_access_attempts_total[5m]) > 0.1/s` | **SEV1** |
| **Salary export spike** | `rate(salary_export_total[10m]) > baseline×3` (MVP 3+, wiring in MVP 1) | **SEV1** |
| **Audit write failure** | any `audit_write_failures_total > 0` over 1 min | **SEV1** |
| Failed production migration | Liquibase job status=Failed | SEV1 |
| Failed backup (24 h) | last successful backup > 26 h ago | SEV1 |
| Tenant isolation smoke fail (post-deploy) | smoke test exit ≠ 0 | SEV1 |
| Auth failure spike | > 50 failures/min/tenant for 5 min | SEV2 |
| Worker queue stuck | oldest job age > 15 min | SEV2 |
| Report generation failures | > 5% in 30 min | SEV2 |
| Object storage errors | > 1% of ops in 10 min | SEV2 |
| Disk almost full | > 85% on PG / node | SEV2 |
| **Certificate expiring** | < 21 days to expiry on any prod cert | SEV2 (warning) → SEV1 at 7 days |
| Replication lag | > 30 s for 5 min | SEV2 |
| Pod CrashLoopBackOff | any restart > 3/15 min | SEV2 |
| HPA at max | sustained 30 min at max replicas | SEV3 |

Routing: SEV1 → PagerDuty/Opsgenie + Slack `#grading-incidents`; SEV2 → Slack + email; SEV3 → Slack only. All alerts have a **runbook link** (see §27).

---

## 21. Backup and restore plan

**PostgreSQL**:
- **PITR** via continuous WAL archiving to object storage (encrypted bucket).
- **Daily full base backup** at 02:00 cluster TZ (low traffic), encrypted at rest with KMS.
- **Retention**: daily 7 days, weekly 5 weeks, monthly 12 months, yearly 7 years (audit / regulatory).
- **Cross-region replication** of backup bucket (DR).
- **Backup monitoring**: Prometheus exporter; alert on missing or failed.

**Object storage**:
- Versioning enabled (recovers accidental delete).
- Lifecycle to archive tier after 90 days.
- Cross-region replication for audit-bearing prefixes.

**Vault / KMS**:
- KMS keys: cloud provider handles durability + rotation; document key IDs in DR plan.
- Vault: integrated storage snapshots daily, encrypted, off-cluster.

**Restore drills**:
- **Monthly** automated drill: restore last night's backup to a sandbox cluster, run smoke + isolation tests. Result archived as evidence (release gate input).
- **Quarterly** full DR drill (full region failover simulation).

**Test restore success** is a **blocking release gate** (must be green ≤ 30 days).

---

## 22. Disaster recovery plan

**Targets (MVP 1)**: **RPO ≤ 15 min**, **RTO ≤ 2 h**.

**Scenarios + responses** (cross-referenced to runbooks §27):

| Scenario | Action | RTO target |
|----------|--------|------------|
| Single pod failure | K8s self-heal (rolling), no manual action | < 5 min |
| Single node failure | K8s reschedule; PG failover via CNPG | < 10 min |
| Postgres primary failure | automatic failover; verify lag, drain old primary | < 15 min |
| Logical corruption (bad release) | rollback release + DB rollback plan; PITR if needed | < 2 h |
| **Tenant-level data corruption** | restore tenant schema from PITR snapshot of `tenant_<id>` schema into a recovery DB → diff + reimport (runbook 10) | < 4 h |
| Region outage | failover to DR region (post-MVP 1 — documented placeholder) | 4 h (MVP 1: best-effort) |
| Lost encryption key | break-glass restore from KMS region replica; document re-key procedure | 2 h |
| Audit log tampering attempt | logged via Object Lock + immutable backup, alert SEV1 | n/a |

**Tenant-level restore** is supported via per-schema PITR — confirmed by drill.

---

## 23. Release management process

**Versioning**: SemVer (`vMAJOR.MINOR.PATCH`). Pre-release: `vX.Y.Z-rc.N`. Hotfix: `vX.Y.Z+1`.

**Release record** (auto-generated, stored in `releases/` and as GitHub Release):
- version, commit SHA, image digests (per workload)
- migration list (control plane + tenant schema)
- feature list / changelog
- security findings status (SAST/SCA/container scan summary, accepted exceptions with IDs)
- test summary (unit/integration/E2E counts, coverage)
- **tenant isolation test result** (must be PASS)
- **salary permission test result** (must be PASS)
- **audit test result** (must be PASS)
- known risks
- **rollback plan** (image tag to roll back to, DB rollback steps, feature flag toggles)
- **release approver list** (4-gate sign-off below)
- post-deploy verification checklist
- runbook references for new/changed services

**Release process** (matches `архитектура.md` §21.2):
1. PR merged to `develop` after code review (≥ 1 approval, all checks).
2. Build immutable artifacts (single build, promoted by digest).
3. Deploy to dev (auto).
4. Run tests (auto in dev).
5. Tag `vX.Y.Z-rc.N` → deploy to staging.
6. Full regression + security checks on staging.
7. Create release candidate record.
8. **PO accepts scope** (hr-product-owner gate).
9. **Security ships** (security-engineer gate).
10. **QA approves test result** (qa-engineer gate).
11. **DevOps approves operational readiness** (this agent — checklist §29).
12. Tag `vX.Y.Z` → deploy to production (Helm with pinned digest).
13. Monitor 60 min — automated checks + on-call eyes.
14. Close release or trigger rollback.

A release **cannot** advance to step 12 without all 4 gates green.

---

## 24. Rollback strategy

| Layer | Mechanism | Time |
|-------|-----------|------|
| Application | `helm rollback grading-umbrella -n grading-prod` to previous revision (digest-pinned) | < 5 min |
| Frontend | same — previous nginx image digest | < 5 min |
| Config (ConfigMap) | helm rollback restores prior values | < 5 min |
| Feature flag | toggle via control plane (no redeploy) | < 1 min |
| Database — non-destructive change | new release that re-adds removed column / reverses logic | minutes |
| Database — destructive change (rare) | **pre-approved manual rollback** documented in release notes + PITR if needed | hours |
| Workers | rolling rollback like api | < 5 min |
| Migration failure mid-run | Liquibase rollback to tag taken at pre-migration checkpoint; halt deploy | minutes |

**Rules**:
- **No release without a rollback plan** (release gate).
- Avoid destructive migrations — use the **expand → migrate → contract** pattern (add new, dual-write, backfill, switch read, drop old in later release).
- Run **post-rollback smoke tests** + tenant isolation smoke before declaring stable.
- Helm history retained ≥ 20 revisions.
- Image registry retention ≥ 1 year for prod-deployed digests.

---

## 25. Security gates (blocking conditions)

A release **MUST NOT** proceed if any of the following are true:

- [ ] Build failed
- [ ] Any unit / integration / contract test failed
- [ ] **Tenant isolation test pack** failed (see §26)
- [ ] **Salary permission test pack** failed (see §26)
- [ ] **Audit trail test pack** failed (see §26)
- [ ] Critical or High CVE unresolved (CVSS ≥ 7) with no documented, approved exception
- [ ] Secrets detected (gitleaks/trufflehog/image scan)
- [ ] Container image scan failed (HIGH/CRITICAL)
- [ ] Liquibase migration failed in staging or dry-run
- [ ] Smoke test failed in any env
- [ ] Readiness probe never went green in dev/staging
- [ ] Production backup status not healthy in last 24 h
- [ ] No rollback plan in release notes
- [ ] Observability dashboard missing for any new service
- [ ] Alert rules missing for any new critical service
- [ ] Restore drill > 30 days old
- [ ] Last incident postmortem actions overdue (SEV1/2 within 14 days)
- [ ] No on-call assigned for next 24 h

---

## 26. Tenant isolation / salary permission / audit release gates

These are **the three pillars** of the release gate, automated and blocking.

### 26.1 Tenant isolation release gate
Mandatory test suite (mirroring `архитектура.md` §22.2). Tagged `@Tag("tenant-isolation")`. Includes:
- Tenant A user cannot view/open/query/export any Tenant B resource by API or UUID (positions, projects, evaluations, attachments, search results, analytics).
- Manipulated `tenant_id` in JWT / header is rejected; only server-derived tenant context is trusted.
- Stale context tokens rejected.
- Object Storage signed URLs scoped to tenant prefix; cross-prefix attempts denied + audited.
- Cross-tenant attempts emit `SEC_CROSS_TENANT_ATTEMPT` audit event with no data leakage in response body.
- BOLA: repository methods enforce `findByIdAndTenantIdAndProjectId(...)` — verified via architecture test (ArchUnit).

Gate: 100% pass; any fail blocks merge to `develop`, blocks RC, blocks production.

### 26.2 Salary permission release gate
Even though MVP 1 stores no salary, the **mechanism** is gated now:
- Endpoints with `@RequiresSalaryPermission` (placeholder) deny without explicit grant.
- Frontend hide ≠ enforcement: backend always re-checks.
- Salary-bearing DTOs / serializers gated; tests confirm a user without permission gets **field omission** (not nulling — schema-level removal).
- Salary export attempts audited.

Gate: 100% pass.

### 26.3 Audit trail release gate
- Every sensitive action (user create/update/delete, role assignment, permission change, project create, methodology approval, grade assignment, export, login, failed login, cross-tenant attempt, tenant provisioning) writes an `audit_log` row.
- `audit_log` is append-only (no UPDATE/DELETE permission for `grading_app`).
- `audit_write_failures_total == 0` over the test run.
- Audit log mirrored to immutable object-storage sink.
- Audit retention policy enforced (7 years).
- A canary test asserts that simulated audit write failure raises SEV1 path (alert + dead-letter).

Gate: 100% pass.

---

## 27. Operational runbooks (18)

Stored in `infra/docs/runbooks/`. Each follows the template: **Symptoms / Detection / Severity / Immediate actions / Diagnosis / Remediation / Escalation / Communication / Post-incident**.

1. API outage
2. Frontend outage
3. Database outage / failover
4. Failed Liquibase migration (control plane)
5. Failed tenant-schema migration (single tenant)
6. Failed deployment
7. Application rollback (helm)
8. High latency
9. Report worker stuck
10. Import worker failure
11. Object storage access issue
12. Redis outage
13. Secret rotation (DB / JWT / OAuth)
14. Backup restore (full + tenant-level)
15. **Cross-tenant access alert** (SEV1)
16. **Salary data exposure incident** (SEV1)
17. **Audit log write failure** (SEV1)
18. Certificate expiry / renewal

Bonus (kept in same folder, not in the 18):
- AI provider outage (post-MVP 1)
- Vault outage / break-glass
- DR region failover
- Tenant provisioning failure

Every alert in §20 links to its runbook.

---

## 28. DevOps backlog per agent

### 28.1 Backend-engineer
- Expose Spring Boot Actuator endpoints: `health/liveness`, `health/readiness` (with DB + Redis checks), `prometheus`, `info`.
- Configure structured JSON logging (Logback + LogstashEncoder) with mandatory fields (§17, §18).
- Implement `CorrelationIdFilter`; propagate via OpenTelemetry baggage.
- Implement graceful shutdown (`server.shutdown=graceful`).
- Externalize all config via env vars; no hardcoded secrets; support `valueFrom` style injection.
- Implement `liquibase_migrator` vs `grading_app` user separation; app starts in **validate-only** mode.
- Implement `findByIdAndTenantIdAnd...` repository pattern; add ArchUnit test forbidding `findById(...)` on tenant-scoped entities.
- Wire `@RequiresSalaryPermission` (placeholder) and audit-event emitter.
- Implement audit log writer with mandatory event codes; expose `audit_write_failures_total` metric.
- Provide `/api/v1/system/version` returning git SHA + build time (for release verification).
- Expose business metrics (Micrometer): cross-tenant attempts, audit events, auth failures, permission denials.

### 28.2 Frontend-engineer
- Runtime config injection (no secrets in build) — `/config.json` served by nginx, populated by ConfigMap.
- Set secure headers in nginx: HSTS, CSP, X-Frame-Options, Referrer-Policy, X-Content-Type-Options.
- Implement `correlationId` header propagation on outbound API calls.
- Implement structured client error reporting (no PII / no tokens in error payloads).
- Provide `/healthz` for K8s probes.
- Build pipeline produces gzip + brotli assets.
- No `console.log` of sensitive data; logging guard in CI.
- Show app version + env in footer (dev/test/staging) — hidden in prod.

### 28.3 QA-engineer
- Author and own the three blocking test packs: `@Tag("tenant-isolation")`, `@Tag("salary-permission")`, `@Tag("audit")`.
- Maintain Playwright smoke pack runnable in any env via `BASE_URL`.
- Define performance baselines (k6 scripts) for staging.
- Author restore-drill verification test (read tenant data + isolation check after restore).
- Provide synthetic load generator for `grading-import-worker` / `grading-report-worker`.
- Track flake rate; flaky tests in blocking packs are SEV2 quality incidents.
- Sign off "QA approves test result" gate per release.

### 28.4 Security-engineer
- Own SAST / SCA / container-scan / IaC-scan / Kubernetes-manifest-scan rule sets and thresholds.
- Maintain CVE exception registry (with expiry).
- Run quarterly threat model refresh; feed findings into release gates.
- Own Vault paths layout, ESO config, rotation policy.
- Own break-glass procedure + audit review.
- Validate log redaction (sampling check) before release.
- Sign off "Security ship" gate per release.

### 28.5 Database-architect
- Own changelog structure (control plane + tenant schema separation).
- Approve every destructive migration; require expand/contract pattern.
- Maintain `tenant_migration_status` view + dashboard.
- Own PostgreSQL HA topology, role separation, PgBouncer config.
- Own PITR + backup retention; provide restore-drill SQL scripts.
- Provide schema diagram + drift detection in CI.

### 28.6 HR-product-owner
- Sign off "PO accept" gate per release.
- Approve tenant provisioning baseline dictionaries.
- Define feature flag defaults per env.

---

## 29. MVP 1 operational readiness checklist

GO / NO-GO checklist signed by DevOps before production cut-over.

**Infrastructure**
- [ ] K8s cluster (prod) provisioned, hardened, Pod Security Standards `restricted` enforced
- [ ] Namespaces, NetworkPolicies (default-deny + allow rules), RBAC, ServiceAccounts in place
- [ ] Ingress + cert-manager + valid wildcard cert for `*.grading.hrlab.uz`
- [ ] PostgreSQL HA running, replication lag < 5 s baseline
- [ ] Redis HA running
- [ ] Object storage bucket created with KMS encryption, no public access, versioning on
- [ ] Vault production cluster up; ESO connected; sample secret rendered

**Pipeline & artifacts**
- [ ] CI/CD pipeline green end-to-end with all 28 stages
- [ ] Secret scan + dep scan + SAST + container scan + SBOM + signing all wired
- [ ] Image tags use SemVer + SHA, no `latest`; digests pinned in prod values
- [ ] Helm umbrella `values-prod.yaml` reviewed + locked

**Database & migrations**
- [ ] Control-plane migrations applied successfully in staging on anonymized snapshot
- [ ] Tenant-schema migration tested for at least 3 tenants in staging
- [ ] `liquibase_migrator` vs `grading_app` roles separated; runtime user has no DDL
- [ ] Per-tenant migration status table + dashboard live
- [ ] Pre-migration backup checkpoint procedure rehearsed

**Security gates**
- [ ] Tenant isolation test pack: 100% pass on staging
- [ ] Salary permission test pack: 100% pass
- [ ] Audit trail test pack: 100% pass; `audit_write_failures_total == 0`
- [ ] No HIGH/CRITICAL CVE without approved exception
- [ ] Log-redaction sampling check passed
- [ ] Break-glass procedure documented + drilled

**Observability**
- [ ] Prometheus / Grafana / Loki / Tempo / Alertmanager deployed
- [ ] All 9 dashboards loaded
- [ ] All §20 alerts firing in test scenarios
- [ ] PagerDuty/Opsgenie integration verified (test page)
- [ ] On-call rotation set for next 4 weeks

**Backup & DR**
- [ ] PITR enabled, WAL archiving healthy
- [ ] Daily full backup executed and verified in last 24 h
- [ ] Restore drill executed in last 30 days, evidence archived
- [ ] Tenant-level restore procedure documented + tested
- [ ] Cross-region backup replication active

**Release readiness**
- [ ] Release notes template populated
- [ ] Rollback plan attached to release
- [ ] Runbooks 1–18 published and linked from alerts
- [ ] Four-gate sign-off captured (PO + Security + QA + DevOps)
- [ ] Post-deploy verification script ready
- [ ] Communication plan ready (status page + customer comms template)

**Decision**: DevOps GO / NO-GO — signed by devops-sre owner.

---

## 30. Risks & mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Tenant isolation regression slips past tests | Med | SEV1 (data leak) | mandatory pack + ArchUnit + post-deploy smoke + runtime alert on cross-tenant attempts |
| Liquibase migration time grows non-linearly with tenant count | Med | SEV2 (long deploys) | parallelize per-tenant migration with concurrency cap; alert on duration; expand/contract pattern |
| Vault outage during deploy | Low | SEV1 (no secrets) | ESO caching; sealed-secrets fallback; runbook 12 (extended); regional Vault HA |
| Secret leak via container layer | Low | SEV1 | trivy + cosign + admission policy + image label review |
| Salary mechanism not exercised in MVP 1 → false confidence | Med | SEV2 (MVP 3 surprise) | run salary permission pack against placeholder endpoints; threat model includes future fields |
| Restore drill skipped under release pressure | Med | SEV2 (silent DR risk) | release gate blocks when drill > 30 days |
| Frontend leaks tokens in logs | Med | SEV2 | CI guard scans bundle + sample logs; CSP-only `Bearer` redactor |
| Cert expiry during weekend | Low | SEV1 | cert-manager auto-renew + 21-day alert |
| Single-region production = no real DR | High | SEV1 if region fails | document RPO/RTO limits in MVP 1; plan DR region in MVP 2 |

---

## 31. Next steps (post-MVP 1)

1. Multi-region DR with active-passive PostgreSQL replication.
2. Service mesh (Istio / Linkerd) for mTLS + fine-grained authz.
3. Cosign verification as admission policy (block unsigned images).
4. DAST in pipeline (OWASP ZAP full scan in staging on RC).
5. DB-per-tenant provisioning for enterprise tier (extend §12 workflow).
6. SLO improvement: 99.5% → 99.9%.
7. Chaos engineering (LitmusChaos) — pod kill, network partition, DB failover drills.
8. Compensation module (MVP 3) → fully wire salary KMS keys, salary export alert thresholds, salary permission UI hide.
9. Tenant cost / usage metering pipeline (chargeback).
10. Move from Helm umbrella to GitOps (ArgoCD / Flux) once team is comfortable.

---

End of MVP 1 DevOps/SRE Blueprint v1.0.

---

## 32. Implementation post-Phase 0+1 — landed artifacts

This section is **the implementation log** for the blueprint above.
Updated each time a DevOps slice is merged.

### 2026-05-23 — Phase 0+1 baseline shipped

| Artifact | Path |
|----------|------|
| Backend Dockerfile (multi-stage, non-root uid 10001, healthcheck) | [`backend/Dockerfile`](../../backend/Dockerfile) |
| Backend `.dockerignore` | [`backend/.dockerignore`](../../backend/.dockerignore) |
| Frontend Dockerfile (Vite build → nginx-unprivileged uid 101, port 8080) | [`frontend/Dockerfile`](../../frontend/Dockerfile) |
| Hardened nginx config (CSP, X-Frame-Options DENY, HSTS, SPA fallback, no server tokens) | [`frontend/nginx.conf`](../../frontend/nginx.conf) |
| Frontend `.dockerignore` | [`frontend/.dockerignore`](../../frontend/.dockerignore) |
| Local stack with `--profile full` (postgres + redis + minio + api + frontend) | [`backend/docker-compose.yml`](../../backend/docker-compose.yml) |
| GitHub Actions CI pipeline — 28 jobs, BLOCKING gates for tenant-isolation / audit / salary / architecture | [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) |
| Commitlint config | [`.github/commitlint.config.cjs`](../../.github/commitlint.config.cjs) |
| `CODEOWNERS` — devops + security gates on `infra/`, `.github/`, security-sensitive code, DB role/audit grants | [`.github/CODEOWNERS`](../../.github/CODEOWNERS) |
| `grading-api` Helm chart (Chart.yaml, values + dev/staging/prod overrides, deployment, service, ingress, hpa, pdb, networkpolicy, serviceaccount, configmap, liquibase Job hook, _helpers) | [`infra/helm/grading-api/`](../../infra/helm/grading-api/) |
| `grading-frontend` Helm chart (same shape; runtime-config ConfigMap mounted at /config.json) | [`infra/helm/grading-frontend/`](../../infra/helm/grading-frontend/) |
| Infra README — build / test / lint / kind quick-start | [`infra/README.md`](../../infra/README.md) |
| JUnit `@Tag` annotations applied for CI gate routing | tenant-isolation: `TenantIsolationIntegrationTest`; audit: `AuditAppendOnlyTest`, `AuditRoleGrantsTest`, `CrossTenantAuditRecordingTest`; salary: `SalaryEncryptionConverterTest`, `SensitiveFieldSerializerTest`, `MaskingPatternLayoutTest`; architecture: `ArchitectureTest` |
| Surefire tag-filter wiring | [`backend/pom.xml`](../../backend/pom.xml) (Surefire plugin block; CI uses `-Dgroups=<tag>`) |

### Hardening highlights enforced by the chart templates

- `_helpers.tpl` **fails the template** if `image.tag` is empty or `latest`.
- `runAsNonRoot: true` + `readOnlyRootFilesystem: true` + `capabilities.drop: [ALL]` on every container, in both charts.
- `seccompProfile: RuntimeDefault` everywhere.
- `automountServiceAccountToken: false` by default — no API access from app pods.
- `NetworkPolicy` enabled by default with explicit ingress (ingress-controller only) and explicit egress (kube-dns + postgres + redis; S3 / OIDC go via cluster egress firewall).
- `HorizontalPodAutoscaler` + `PodDisruptionBudget` enabled in staging + prod overrides.
- Liquibase runs as a **pre-install + pre-upgrade Helm hook Job** that consumes a **separate `existingSecret`** (`grading-api-db-migrator`) — DDL-privileged credentials never reach the runtime pods.

### Cross-agent handoffs

- **database-architect**: define the exact env-var schema the Liquibase Job expects (`SPRING_DATASOURCE_URL/USERNAME/PASSWORD` + any `LIQUIBASE_CONTEXTS`). Confirm the changelog runs cleanly when `--spring.main.web-application-type=none` is set, and document tenant-schema iteration (per-tenant Job vs in-process loop).
- **security-engineer**: own OWASP ZAP baseline rules; promote stage 23 from placeholder to real scan once staging DNS exists. Sign off image-scan severity threshold + CVE exception registry pipeline.
- **qa-engineer**: own the smoke + E2E suite that stages 20 and 22 currently stub. Provide Playwright fixtures with synthetic tenants for the post-deploy verification.

### Open items for production cutover

1. Real Vault cluster + external-secrets-operator; replace `existingSecrets: []` examples with `SecretStore` + `ExternalSecret` manifests per env.
2. Real container registry path (replace `ghcr.io/hrlab-uz/grading/*`).
3. Real DNS for `grading.dev.hrlab.uz`, `grading.staging.hrlab.uz`, `grading.hrlab.uz`.
4. Real cert-manager `ClusterIssuer` URL (Let's Encrypt prod or internal CA).
5. Replace placeholder GitHub team handles in `CODEOWNERS` with real teams.
6. Wire stage 19/21/25 to actual `kubectl/helm` against a real kubeconfig (currently they print `::notice::Placeholder`).
7. Build the kube-prometheus-stack + Loki + Tempo overlays (deferred until clusters exist).
8. Add Worker Helm charts (import / report / ai-gateway / integration) in MVP 2.


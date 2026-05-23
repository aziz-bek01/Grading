# infra/ — grading.hrlab.uz infrastructure-as-code

This directory holds everything DevOps owns for **grading.hrlab.uz**.
The canonical design lives in
[`docs/mvp1/04-devops-sre-blueprint.md`](../docs/mvp1/04-devops-sre-blueprint.md).

## Layout

```
infra/
  helm/
    grading-api/                 Helm chart for the Spring Boot API
      Chart.yaml
      values.yaml                # safe defaults — no `latest`, no secrets
      values-dev.yaml
      values-staging.yaml
      values-production.yaml
      templates/
        deployment.yaml
        service.yaml
        ingress.yaml
        hpa.yaml
        pdb.yaml
        networkpolicy.yaml
        configmap.yaml
        serviceaccount.yaml
        job-liquibase.yaml       # pre-install/pre-upgrade Helm hook
        _helpers.tpl
    grading-frontend/            Helm chart for the React/nginx SPA
      …same layout, plus runtime-config ConfigMap
```

Dockerfiles live next to the code they build:

```
backend/Dockerfile               # multi-stage, Java 21, non-root uid 10001
backend/.dockerignore
backend/docker-compose.yml       # postgres + redis + minio (+ full profile)
frontend/Dockerfile              # multi-stage, Vite build + nginx-unprivileged
frontend/nginx.conf              # security headers + SPA fallback
frontend/.dockerignore
```

CI lives at the repository root:

```
.github/workflows/ci.yml         # 28-stage pipeline (see blueprint §7)
.github/CODEOWNERS
.github/commitlint.config.cjs
```

## Build images locally

```sh
# Backend
docker build \
  --build-arg IMAGE_VERSION=0.1.0-local \
  --build-arg IMAGE_REVISION=$(git rev-parse --short HEAD) \
  --build-arg IMAGE_CREATED=$(date -u +%Y-%m-%dT%H:%M:%SZ) \
  -f backend/Dockerfile -t grading-api:dev backend

# Frontend
docker build \
  --build-arg IMAGE_VERSION=0.1.0-local \
  -f frontend/Dockerfile -t grading-frontend:dev frontend
```

## Run the full local stack

```sh
cd backend
docker compose --profile full up -d
# Postgres: localhost:5432, Redis: 6379, MinIO: 9000/9001,
# API: 8080, Frontend: 8081
```

## Validate the CI workflow locally

```sh
# https://github.com/nektos/act
act -W .github/workflows/ci.yml -j validate
act -W .github/workflows/ci.yml -j backend-unit-test --container-architecture linux/amd64
```

`act` is best-effort — Testcontainers + ghcr.io login do not work without docker-in-docker. For a full dry-run, prefer pushing a `dependabot/`-style branch.

## Install Helm chart to a local `kind` cluster

```sh
kind create cluster --name grading-local
kubectl create namespace grading-dev

# Build & load images (so they don't have to be pushed anywhere).
docker build -f backend/Dockerfile -t grading-api:dev backend
docker build -f frontend/Dockerfile -t grading-frontend:dev frontend
kind load docker-image grading-api:dev      --name grading-local
kind load docker-image grading-frontend:dev --name grading-local

# A Secret for the API. NEVER use these passwords elsewhere.
kubectl -n grading-dev create secret generic grading-api-db \
  --from-literal=SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/grading_control_db \
  --from-literal=SPRING_DATASOURCE_USERNAME=grading_app \
  --from-literal=SPRING_DATASOURCE_PASSWORD=grading_app_pwd

# Migrator credentials (DDL — different from runtime user!)
kubectl -n grading-dev create secret generic grading-api-db-migrator \
  --from-literal=SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/grading_control_db \
  --from-literal=SPRING_DATASOURCE_USERNAME=grading_migrator \
  --from-literal=SPRING_DATASOURCE_PASSWORD=grading_migrator_pwd

helm install grading-api      infra/helm/grading-api      -n grading-dev \
  -f infra/helm/grading-api/values-dev.yaml \
  --set image.repository=grading-api --set image.tag=dev --set image.pullPolicy=Never \
  --set migrator.image.repository=grading-api --set migrator.image.tag=dev \
  --set migrator.image.pullPolicy=Never

helm install grading-frontend infra/helm/grading-frontend -n grading-dev \
  -f infra/helm/grading-frontend/values-dev.yaml \
  --set image.repository=grading-frontend --set image.tag=dev --set image.pullPolicy=Never
```

## Validation commands

```sh
# Lint Dockerfiles
hadolint backend/Dockerfile
hadolint frontend/Dockerfile

# Lint Helm charts
helm lint infra/helm/grading-api
helm lint infra/helm/grading-frontend

# Render Helm charts for review
helm template infra/helm/grading-api      -f infra/helm/grading-api/values-dev.yaml      --set image.tag=sha-abc1234
helm template infra/helm/grading-frontend -f infra/helm/grading-frontend/values-dev.yaml --set image.tag=sha-abc1234

# Validate Actions workflow
actionlint .github/workflows/ci.yml
```

## Hard rules (lifted from the blueprint, repeated here)

- `image.tag` is **never** `latest`. Charts will `fail` at template time.
- Containers are **non-root** (uid 10001 backend, uid 101 frontend).
- All deployments have **liveness + readiness + (api) startup** probes.
- All deployments have **resource requests AND limits**.
- All deployments have **read-only root filesystem** + dropped capabilities.
- Secrets enter pods via `envFrom: secretRef` referencing **K8s Secrets
  populated by external-secrets-operator from Vault** — never committed.
- Release-gate test packs (`tenant-isolation`, `audit`, `salary`,
  `architecture`) are **blocking jobs** in `ci.yml`.

## Open items before production cutover

1. Replace placeholder GitHub team handles in `CODEOWNERS`.
2. Provision a real Vault cluster + external-secrets-operator;
   wire ESO `SecretStore` + `ExternalSecret` manifests per env.
3. Replace `ghcr.io/hrlab-uz/grading/*` with the real image registry path.
4. Provision DNS + cert-manager `ClusterIssuer` for
   `*.dev.hrlab.uz` / `*.staging.hrlab.uz` / `grading.hrlab.uz`.
5. Bring up the kube-prometheus-stack chart + dashboards (devops-sre §17).
6. Author the four worker Helm charts (import / report / ai-gateway /
   integration) when those services land in MVP 2+.
7. Set up cosign image signing + admission policy (kept out of MVP 1 scope).

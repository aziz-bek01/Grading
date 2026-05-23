# grading.hrlab.uz — Backend

Spring Boot 3.x / Java 21 modular monolith powering the grading SaaS platform.

> Architectural source of truth: [/архитектура.md](../архитектура.md) and the MVP-1 blueprints under `/docs/mvp1/`. Read them before adding new modules.

## Modules

Implemented in Phase 0 + Phase 1:

| Package                              | Role                                                                |
|--------------------------------------|---------------------------------------------------------------------|
| `uz.hrlab.grading.common`            | Cross-cutting (API envelope, exceptions, persistence audit base)    |
| `uz.hrlab.grading.tenancy`           | `Tenant`, `ClientCompany`, `TenantContext`, admin tenant endpoint   |
| `uz.hrlab.grading.access`            | `User`, `Role`, `Permission`, `UserTenantMembership`, RBAC + ABAC   |
| `uz.hrlab.grading.security`          | Spring Security config, `DevAuthFilter`, `TenantContextFilter`      |
| `uz.hrlab.grading.audit`             | Append-only `AuditService` with SHA-256 hash chain                  |

Added in Phase 2:

| Package                              | Role                                                                |
|--------------------------------------|---------------------------------------------------------------------|
| `uz.hrlab.grading.project`           | `Project` aggregate (DRAFT/ACTIVE/LOCKED/ARCHIVED), CRUD + lock     |
| `uz.hrlab.grading.organization`      | `Department` aggregate, tree builder, parent cycle prevention       |
| `uz.hrlab.grading.position`          | `Position` aggregate, search by department/jobFamily/status         |
| `uz.hrlab.grading.access.domain.*`   | ABAC scope policies: `DepartmentScopePolicy`, `ProjectMembershipPolicy`, `ApprovedEntityFilterPolicy` |
| `uz.hrlab.grading.access.application.ConsultantTenantAssignmentPolicy` | Consultant ↦ user_tenant_memberships verification |
| `uz.hrlab.grading.access.application.AbacGate` | Composes scope policies, writes `ACCESS_DENIED_BY_ABAC` audit on deny |
| `uz.hrlab.grading.tenancy.infrastructure.TenantSchemaProvisioner` | Programmatic Liquibase runner for schema-per-tenant mode |

Phase 3+ modules (jobanalysis, methodology, evaluation, gradestructure, compensation, workflow, analytics, reporting, integration, localization, aiassist) are wired into the package tree but ship in subsequent iterations.

## Tenancy mode (`grading.tenancy.mode`)

Two values, configured via `application.yml` or env var `GRADING_TENANCY_MODE`:

| Mode                 | When                                       | What happens                                                                                          |
|----------------------|--------------------------------------------|-------------------------------------------------------------------------------------------------------|
| `shared` (default)   | Local dev, integration tests, MVP 1 demo   | Tenant tables (`projects`, `departments`, `positions`) live in `public`; `tenant_id` scopes every row |
| `schema_per_tenant`  | Production target (post-MVP 1 rollout)     | `TenantSchemaProvisioner.provision(tenant)` creates `tenant_{slug}` schema and applies tenant changelog |

`db/changelog/tenant-schema/db.changelog-tenant.yaml` is the single source of truth — applied either as part of the main Liquibase run (context `mode-shared`) or programmatically per tenant.

## Phase 2 — sample curl

Assume the dev profile is running and a default user is mocked via headers.

```sh
# Create project
curl -X POST http://localhost:8080/api/v1/projects \
  -H 'Content-Type: application/json' \
  -H 'X-Dev-User: 11111111-1111-1111-1111-111111111111' \
  -H 'X-Dev-Tenant: 22222222-2222-2222-2222-222222222222' \
  -H 'X-Dev-Roles: HRLAB_SUPER_ADMIN' \
  -H 'X-Dev-Permissions: PROJECT_CREATE,PROJECT_EDIT,ORG_EDIT,POSITION_CREATE,POSITION_READ,ORG_READ,PROJECT_READ' \
  -d '{"code":"PRJ-ACME-2026","nameI18n":{"ru-RU":"АКМЭ грейдинг","en-US":"ACME grading"}}'

# Department tree
curl 'http://localhost:8080/api/v1/departments/tree?projectId=<projectId>' -H 'X-Dev-...'

# Create position
curl -X POST http://localhost:8080/api/v1/positions \
  -H 'Content-Type: application/json' -H 'X-Dev-...' \
  -d '{"projectId":"<projectId>","departmentId":"<deptId>","code":"POS-001","titleI18n":{"ru-RU":"Старший аналитик"}}'

# List positions (paginated, default page=0 size=20, max 200)
curl 'http://localhost:8080/api/v1/positions?projectId=<projectId>&page=0&size=20' -H 'X-Dev-...'

# Lock project
curl -X POST 'http://localhost:8080/api/v1/projects/<id>/lock' -H 'X-Dev-...'
```

## Local development

### Prerequisites

* JDK 21 (Temurin recommended)
* Docker Desktop / Docker Engine (for Postgres + future Redis / MinIO)

Maven is provided through the `mvnw` wrapper — no need to install Maven locally.

### Start dependencies

```sh
docker compose up -d postgres
```

### Run the backend

```sh
./mvnw spring-boot:run
```

The app starts on `http://localhost:8080` with the `local` profile.

* Health: `GET /actuator/health`
* OpenAPI: `GET /v3/api-docs`, Swagger UI: `/swagger-ui.html`

### Dev authentication (local profile ONLY)

Production uses OAuth2 / OIDC bearer tokens. For local development we ship `DevAuthFilter`, which constructs a `TenantContext` from headers:

| Header                | Example                                       |
|-----------------------|-----------------------------------------------|
| `X-Dev-User`          | `11111111-1111-1111-1111-111111111111`        |
| `X-Dev-Tenant`        | `22222222-2222-2222-2222-222222222222`        |
| `X-Dev-Projects`      | `<uuid>,<uuid>` (comma-separated)             |
| `X-Dev-Roles`         | `HRLAB_SUPER_ADMIN,HRLAB_PROJECT_MANAGER`     |
| `X-Dev-Permissions`   | `TENANT_CREATE,POSITION_READ`                 |
| `X-Dev-Departments`   | `<uuid>,<uuid>`                               |
| `X-Dev-Salary`        | `false`                                       |
| `X-Dev-Locale`        | `ru-RU`                                       |

`DevAuthFilter`'s constructor refuses to start if no dev profile is active — there is no path where these headers reach a deployed environment.

### Create your first tenant

```sh
curl -X POST http://localhost:8080/api/v1/admin/tenants \
  -H "Content-Type: application/json" \
  -H "X-Dev-User: 11111111-1111-1111-1111-111111111111" \
  -H "X-Dev-Permissions: TENANT_CREATE" \
  -d '{
    "slug": "acme",
    "displayName": "Acme Holding",
    "defaultLocale": "ru-RU",
    "isolationMode": "SCHEMA",
    "companyLegalName": "Acme LLC",
    "companyBrandName": "Acme",
    "companyIndustry": "Holding"
  }'
```

## Tests

```sh
# Pure unit tests always run. Testcontainers tests run automatically if a
# Docker daemon is reachable; otherwise they are skipped (and you'll see
# "Skipped: N" in the surefire output — not "Errors: N"). To fail the build
# instead of skipping on a missing daemon (CI behavior), export
# GRADING_REQUIRE_DOCKER=true.
./mvnw test
```

**Docker Desktop 4.55 on Windows** has a known regression that causes Testcontainers to receive a `BadRequestException` from the npipe engine API. Workarounds:

1. In Docker Desktop settings, enable **"Expose daemon on tcp://localhost:2375 without TLS"**, then `export DOCKER_HOST=tcp://localhost:2375`.
2. Or downgrade to Docker Desktop ≤ 4.50.

Until either fix is applied the integration tests skip on the developer machine but the rest of the suite still passes.

Test classes implemented through Phase 1 remediation:

| Class                                  | Purpose                                                                                |
|----------------------------------------|----------------------------------------------------------------------------------------|
| `GradingApplicationTests`              | Spring application context loads                                                       |
| `TenantContextTest`                    | `TenantContextHolder` lifecycle, `requireActive()` contract                            |
| `DevAuthFilterTest`                    | `DevAuthFilter` refuses to start outside dev; bootstraps a context                     |
| `LiquibaseMigrationTest`               | All Liquibase changesets apply cleanly against Testcontainers Postgres                 |
| `TenantIsolationIntegrationTest`       | Cross-tenant access via `findByIdAndTenantId` is denied (BOLA proof)                   |
| `AuditAppendOnlyTest`                  | Audit log repository exposes neither `update` nor `delete`; hash chain links events    |
| `JwtAudienceValidatorTest`             | F-02 — JWT with wrong `aud` is rejected, empty allowlist default-denies                |
| `CorsAllowlistIntegrationTest`         | F-16 — disallowed origin gets no `Access-Control-Allow-Origin`; allowed origin passes  |
| `CrossTenantAuditRecordingTest`        | F-05 / D-002 — cross-tenant attempt writes an `AuditEvent`, not just a log line        |
| `SensitiveFieldSerializerTest`         | F-12 — `@Sensitive` fields are OMITTED from JSON when context lacks the permission     |
| `MaskingPatternLayoutTest`             | F-13 — Logback layout redacts `Bearer`, salary, password, token, api_key patterns      |
| `SalaryEncryptionConverterTest`        | F-10 — converter skeleton round-trips, embeds key id / version, never leaks plain     |
| `ArchitectureTest`                     | D-003 — 5 ArchUnit rules: TenantAwareRepository, no JPA in api, naming, etc.          |

## Hard rules (also see `.claude/agents/backend-engineer.md`)

* `tenant_id` is sourced **only** from the security context — never from request body / query / path on business endpoints.
* Tenant-scoped repositories must extend `TenantAwareRepository` (see below) — they expose only `findByIdAndTenantId` / `findAllByTenantId(...)`. Bare `findById`, `findAll`, and `delete*` are NOT inherited. ArchUnit enforces this.
* `BigDecimal` for scores and money — never `double` / `float`.
* `UUID` IDs, `OffsetDateTime` timestamps, enums for statuses.
* Approved methodologies are immutable (Phase 4 will enforce at trigger + service level).
* Audit log is append-only — `AuditService` has no update / delete operations.

## Required environment variables (production)

| Env var                              | Purpose                                                    | Example                              |
|--------------------------------------|------------------------------------------------------------|--------------------------------------|
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` | IdP discovery URL (Keycloak realm)        | `https://idp.hrlab.uz/realms/grading`|
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUDIENCE`   | Comma-separated allowed `aud` claim values | `grading.hrlab.uz`                   |
| `GRADING_CORS_ALLOWED_ORIGINS`       | CORS allowlist (comma-separated origins). Empty = no browser may call the API. | `https://grading.hrlab.uz` |
| `GRADING_SALARY_KEY_ID`              | Salary encryption key id (MVP 3 envelope encryption)       | `vault:keys/salary/v1`               |
| `GRADING_SALARY_KEY_VERSION`         | Salary encryption key version                              | `1`                                  |
| `GRADING_REQUIRE_DOCKER`             | When `true`, RequiresDocker hard-fails instead of skipping. Set in CI. | `true`         |

Local profile defaults (in `application-local.yml`):

* CORS allowlist = `http://localhost:5173`
* Salary key id = `dev-stub`, version `1`
* No JWT issuer — `DevAuthFilter` handles auth via `X-Dev-*` headers.

## TenantAwareRepository

Located at `uz.hrlab.grading.common.infrastructure.TenantAwareRepository<T, ID>`.

Tenant-scoped data repositories **MUST** extend this interface, not `JpaRepository`:

```java
public interface PositionRepository
        extends TenantAwareRepository<PositionJpaEntity, UUID> {

    Page<PositionJpaEntity> findAllByProjectIdAndTenantId(UUID projectId, UUID tenantId, Pageable page);
}
```

Inherited methods:

* `Optional<T> findByIdAndTenantId(ID id, UUID tenantId)` — canonical anti-BOLA lookup.
* `Page<T> findAllByTenantId(UUID tenantId, Pageable pageable)`.
* `boolean existsByIdAndTenantId(ID id, UUID tenantId)`.
* `<S extends T> S save(S entity)`.
* `long count()` — diagnostics only.

`findById(ID)`, `findAll()`, `delete*`, `saveAll`, and `saveAndFlush` are **NOT** inherited — extending JPA's bare repository is the BOLA hazard `TenantAwareRepository` was created to prevent.

**Control-plane repositories** that are not tenant-scoped (`TenantRepository`, `UserRepository`, `RoleRepository`, `PermissionRepository`, `UserTenantMembershipRepository`, `SystemAuditLogRepository`) may continue to extend `JpaRepository` / `Repository`; they are explicitly whitelisted in `ArchitectureTest`.

## ArchUnit architecture rules

`src/test/java/uz/hrlab/grading/architecture/ArchitectureTest.java` enforces:

1. Tenant-scoped repositories must NOT extend `JpaRepository` (control-plane repos are exempted by name).
2. Controllers must NOT return JPA entities directly.
3. Domain layer must NOT depend on infrastructure.
4. Controllers must live under an `..api..` package.
5. Spring Data repositories must end with `Repository`.

Adding a new rule:

1. Add a `@Test`-annotated method to `ArchitectureTest`.
2. Use `noClasses().that()...should()...because("...")` or `classes().that()...`.
3. Run `mvn test -Dtest=ArchitectureTest` to verify.
4. If a legitimate exception is needed, prefer adding the class to a tight whitelist in the rule (by simple name) rather than disabling the rule.

## Testcontainers / CI

Pure unit tests always run. Integration tests (those extending `AbstractIntegrationTest`) require Docker via Testcontainers.

* **Local dev**: if Docker is not reachable the integration tests are **skipped**, not failed. You'll see `Skipped: N` in the surefire output.
* **CI**: set `GRADING_REQUIRE_DOCKER=true`. With this flag the `RequiresDocker` JUnit 5 extension fails the suite if Docker is unavailable, so misconfigured runners cannot silently skip the BOLA / audit-append-only proofs.

Example CI invocation:

```sh
GRADING_REQUIRE_DOCKER=true ./mvnw test
```

## Roadmap

| Phase | Scope                                                                  | Status      |
|-------|------------------------------------------------------------------------|-------------|
| 0     | Skeleton, common module, Liquibase wiring, OpenAPI                     | done        |
| 1     | Tenancy + access + security + audit foundation                         | done        |
| 2     | Project, organization, position                                        | not started |
| 3     | Job profile + job analysis                                             | not started |
| 4     | Methodology builder (immutable approved versions)                      | not started |
| 5     | Scoring engine (3 modes, BigDecimal, audit)                            | not started |
| 6     | Grade structure (no overlaps, auto-assignment)                         | not started |
| 7     | Compensation foundation                                                | not started |
| 8     | Audit + report scaffolding (per-tenant partitioned audit_logs)         | not started |
| 9     | Tenant isolation test pack (TI-01..TI-18), audit pack, salary pack     | partial     |

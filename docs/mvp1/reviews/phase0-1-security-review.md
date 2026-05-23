# Phase 0+1 — Security Review Report

**Product:** grading.hrlab.uz
**Reviewer agent:** security-engineer
**Date:** 2026-05-23
**Benchmark:** `docs/mvp1/02-security-blueprint.md` (v1.0)
**Reference architecture:** `архитектура.md` §7, §8, §22, §25 (ADR-001…ADR-012)
**Verdict:** **SHIP with conditions** (see §13).

---

## 1. Review scope

This review covers the Phase 0 (skeleton) and Phase 1 (tenancy/access/audit foundation) deliverables on `backend/` and `frontend/` against the binding Security Blueprint (sections 1–22) and the security-engineer hard rules. Specifically reviewed:

* Backend Java sources under `backend/src/main/java/uz/hrlab/grading/**` (security, tenancy, access, audit, common modules — 64 files glob'd, all relevant security touch-points read in full).
* Liquibase changelogs under `backend/src/main/resources/db/changelog/**` (4 control-plane, 3 seed files).
* Application configs: `application.yml`, `application-local.yml`, `application-test.yml`, `backend/docker-compose.yml`, `pom.xml`.
* Frontend TS/TSX under `frontend/src/**` (61 files glob'd, security touch-points read in full): `httpClient.ts`, `tokenStorage.ts`, `authStore.ts`, `SalaryValue.tsx`, `TenantSelector.tsx`, `permissionUtils.ts`, route guards, `devAuth.ts`, `i18n/index.ts`.
* Frontend configs: `package.json`, `.env.example`.

Out of scope for this review (deferred): persistence layer for tenant business entities (no business tables shipped yet), AI gateway, file/object storage, export pipelines, Kubernetes manifests, CI/CD pipeline definitions.

---

## 2. Architecture conformance (vs `архитектура.md` §8)

| Architecture clause | Status | Evidence |
|---------------------|--------|----------|
| OAuth2/OIDC resource server, JWT only | **Conformant** | `SecurityConfig.java:61` `oauth2ResourceServer().jwt()`, RS256 via `NimbusJwtDecoder.withIssuerLocation(issuerUri)` |
| Deny-by-default authorization | **Conformant** | `SecurityConfig.java:60` `.anyRequest().authenticated()` after explicit `permitAll` allowlist; `@EnableMethodSecurity` enabled |
| JWT claim model (`active_tenant_id`, `active_project_ids`, `roles`, `permissions`, `salary_data_permission`, `department_scope`, `locale`) | **Conformant** | `JwtClaimNames.java`, `JwtTenantContextResolver.java:28-34` |
| Tenant context derived ONLY from JWT | **Conformant** | `TenantContextFilter.java:81-83` reads from `JwtAuthenticationToken` only; `JwtTenantContextResolver.resolve(Jwt)` is the sole resolver |
| RBAC + ABAC model | **Partially conformant** | RBAC primitives in `PermissionService.java`; ABAC scaffolding in `AbacPolicy.java` + `TenantAwarePolicy.java` (interfaces only, no concrete policies yet — acceptable for Phase 1 because no business entities exist) |
| Salary protection foundation (codes seeded, default-off claim, masking primitive) | **Conformant** | `PermissionCodes.SALARY_*`, seed `001-default-permissions.yaml:37-40`, `user_tenant_memberships.salary_data_permission BOOLEAN DEFAULT FALSE NOT NULL`, frontend `<SalaryValue>` defaults to lock state |
| Audit log: append-only, hash-chained, control-plane | **Partially conformant** | Hash chain implemented (`JpaAuditService.computeHash` SHA-256 over canonical fields). DB-level `INSERT/SELECT-only` grant for runtime user is **not yet enforced via Liquibase** — see Finding F-04 below |
| Tenant_id NOT NULL on every client-data table | **Deferred (no business tables shipped)** | Control-plane tables present; tenant business tables arrive Phase 2 |

**Overall:** the Phase 0+1 implementation faithfully implements the security primitives mandated by §8 of the architecture and §5–11 of the blueprint. Outstanding gaps are explicitly framed as deferred (encryption converter, RLS activation, role-permission grants) rather than skipped.

---

## 3. Tenant isolation controls verification

| Control | Status | Evidence / Notes |
|---------|--------|------------------|
| Tenant context from JWT only | **PASS** | `JwtTenantContextResolver.resolve(Jwt)` is the only resolver; no controller/DTO reads `tenant_id` |
| Business endpoints reject `tenant_id` in body/query | **PASS** | Only controller shipped (`AdminTenantController`) is admin-only and explicitly gated by `@PreAuthorize("hasAuthority('TENANT_CREATE')")`; `CreateTenantRequest` has no `tenant_id` field |
| `TenantContextFilter` clears context in `finally` | **PASS** | `TenantContextFilter.java:68-73` clears `TenantContextHolder` and MDC keys in finally |
| `TenantContextHolder.requireActive()` rejects null | **PASS** | `TenantContextHolder.java:25-32` throws `IllegalStateException` (sanitized) |
| Banned `findById` on tenant business data | **PASS for current scope** | No tenant business repositories exist yet. Control-plane repos that legitimately use `findById` are documented as such (`UserRepository`, `TenantRepository`) |
| `findByIdAndTenantId` pattern in tenant-scoped repos | **PASS** | `ClientCompanyRepository.findByIdAndTenantId`; `UserTenantMembershipRepository.findByUserIdAndTenantId` |
| Repository-tenant-aware abstraction (`TenantAwareRepository<T,ID>`) | **GAP (deferred)** | The `TenantAwareRepository` interface mandated by blueprint §5.2 has not yet been created. Acceptable for Phase 1 because no business entities are shipped, but **must land before any business module repository** — see Finding F-01 |
| Cache key strategy | **N/A (no caches yet)** | No Caffeine/Redis caches wired in Phase 1; deferred |
| Object storage namespace | **N/A (no S3 wiring yet)** | MinIO container reserved (`docker-compose.yml:32-43`) but no application code touches it |
| Audit on cross-tenant attempt | **PASS** | `GlobalExceptionHandler.handleTenantAccessDenied()` logs to `security.audit` channel and returns 404. The structured audit record via `AuditService` is **not** written here (see Finding F-05) |

---

## 4. Authentication verification

| Control | Status | Evidence |
|---------|--------|----------|
| OAuth2 Resource Server with JWT decoder | **PASS** | `SecurityConfig.jwtDecoder()` uses `NimbusJwtDecoder.withIssuerLocation(...)` which mandates signature, issuer, expiration validation |
| `alg: none` rejection | **PASS (inherited)** | Nimbus default rejects unsigned tokens; recommend an explicit `JwtDecoders.fromIssuerLocation` test in QA pack (already in blueprint AUTH-02) |
| Issuer / audience validation | **Partially PASS** | Issuer auto-validated by `withIssuerLocation`. **Audience claim (`aud=grading.hrlab.uz`) NOT explicitly enforced** — see Finding F-02 |
| Stateless sessions | **PASS** | `SessionCreationPolicy.STATELESS` |
| CSRF disabled (Bearer-only) | **PASS / acceptable** | `csrf.disable()` is correct for Bearer-token APIs; CSP and HSTS deferred to ingress (devops-sre) |
| `DevAuthFilter` profile-gated | **PASS (strong)** | `DevAuthFilter.ALLOWED_PROFILES = Set.of("local","test","dev")`; constructor throws `IllegalStateException` if started outside those profiles; only instantiated when `SecurityConfig.devAuthFilterIfActive(env)` returns non-null |
| No password storage in backend | **PASS** | Backend never stores passwords; `users.external_idp_subject` is the IdP linkage column |
| JTI denylist / forced revocation | **GAP (deferred)** | Not implemented in Phase 1; Redis stub container exists. Acceptable until business endpoints arrive — see Finding F-03 |
| MFA enforcement (`acr` claim) for HRLab admins | **GAP (deferred)** | No `acr` check anywhere in code. Acceptable because no privileged endpoint exists beyond `AdminTenantController` — but **must land before user-management endpoints** in Phase 2 |

---

## 5. Authorization verification

| Control | Status | Evidence |
|---------|--------|----------|
| Permission catalog seeded | **PASS** | 34 codes in `001-default-permissions.yaml` matching `PermissionCodes.java` |
| Role catalog seeded | **PASS** | 11 roles in `002-default-roles.yaml` matching architecture §8.3 |
| `role_permissions` join table populated | **GAP (acknowledged)** | Table exists; seed comment: *"Role-permission grants for MVP 1 will be authored by HR product owner + security engineer in a follow-up changeset."* This is a known gap and **must land before any role can do anything in MVP 1** — see Finding F-06 |
| `@PreAuthorize` on admin endpoints | **PASS** | `AdminTenantController.create` guarded by `@PreAuthorize("hasAuthority('TENANT_CREATE')")` |
| ABAC policy skeleton | **PASS** | `AbacPolicy<T>`, `TenantAwarePolicy<T>` with `requireSameTenant`, `isInActiveProject` helpers |
| PermissionService default-deny | **PASS** | `PermissionService.has()` returns false when context is null |
| Salary gate (`canViewSalary()` requires BOTH boolean flag AND `SALARY_VIEW`) | **PASS** | `PermissionService.java:51-54` enforces both. Frontend mirrors in `permissionUtils.canViewSalary` |

---

## 6. Audit verification

| Control | Status | Evidence |
|---------|--------|----------|
| `system_audit_log` schema matches blueprint §9.3 | **PASS** | All 17 fields present including `tenant_id`, `project_id`, `actor_user_id`, `action`, `entity_type`, `entity_id`, `before_json`/`after_json` (JSONB), `reason`, `ip_address`, `user_agent`, `correlation_id`, `trace_id`, `created_at`, `hash_prev`, `hash_current` |
| Indexes (tenant+created_at, action, actor, entity) | **PASS** | All four indexes present in `003-create-system-audit.yaml:42-49` |
| Repository hides UPDATE/DELETE | **PASS** | `SystemAuditLogRepository` extends `Repository<…>` (not `JpaRepository`) and only declares `save`, `findById`, `count`, two queries — no delete/update methods inherited |
| `@Transactional(REQUIRES_NEW)` on audit insert | **PASS** | `JpaAuditService.record` runs in its own transaction, so audit is never rolled back by caller failure |
| DB-level INSERT/SELECT-only grant for runtime user | **MISSING** | No Liquibase grant statement enforces this. Currently relies on repository interface only. **High severity** — see Finding F-04 |
| SHA-256 hash chain | **PASS (with reservation)** | Canonical fields concatenated with `\|` separator; `prev_hash` chained per `(tenant_id)`. **Canonicalization is NOT RFC 8785 JCS** — uses Jackson `ORDER_MAP_ENTRIES_BY_KEYS=true` over JsonNode and string concatenation with `OffsetDateTime.toString()`. Acceptable for Phase 1 because (a) it is deterministic within a single JVM version, (b) the chain can be re-canonicalized later for forensics. **Document the chosen canonicalization scheme** — see Finding F-07 |
| `ordinal` per-tenant monotonic field | **MISSING** | Blueprint §9.2 requires `ordinal`; schema has none. Chain currently links via `findLastHash(tenantId)` ordered by `created_at DESC` — vulnerable to same-instant inserts under concurrent writers. **Medium severity** — see Finding F-08 |
| Append-only test (`AuditAppendOnlyTest`) | **REFERENCED but not verified** | Repository javadoc references it but I did not read test sources |
| Hash chain anchor / WORM | **GAP (devops-sre, deferred)** | Daily anchor upload to WORM bucket is a devops-sre task |
| Salary redaction in before/after JSON | **PARTIAL** | Comment in `AuditEvent.java:11-13` says "caller redacts salary fields". There is **no automatic redaction filter** — Jackson serializer runs raw. **Medium severity** — see Finding F-09 |

---

## 7. Salary protection foundation

| Control | Status | Evidence |
|---------|--------|----------|
| `SALARY_*` permission codes defined | **PASS** | 4 codes seeded |
| Codes unassigned to any role | **PASS** | Seed comment explicitly defers; `dev/devAuth.ts` super-admin excludes salary permissions explicitly (good defensive default) |
| `salary_data_permission` column default-false NOT NULL | **PASS** | `user_tenant_memberships.salary_data_permission BOOLEAN DEFAULT FALSE NOT NULL` |
| JWT claim `salary_data_permission` parsed as boolean default false | **PASS** | `JwtTenantContextResolver.java:33` uses `Boolean.TRUE.equals(...)` — null-safe |
| Field-level encryption converters (`EncryptedStringConverter`, `EncryptedNumberConverter`) | **MISSING** | No `@Convert` JPA converter implemented yet (Grep for `MaskingPatternLayout|encrypt` returned no results in main sources). Blueprint §8 requires them shipped now even if not yet applied. **High severity** — see Finding F-10 |
| Tenant-specific DEK column reservation | **MISSING** | `tenants.encryption_key_ref VARCHAR(200)` reserved (good) but no separate `tenant_encryption_key` table for envelope-encrypted DEK as blueprint §8 specifies. **Medium severity (deferred to database-architect)** — Finding F-11 |
| `@Sensitive("SALARY")` annotation + Jackson filter | **MISSING** | No annotation, no Jackson module. Blueprint §8 mandates the primitive in MVP 1. **High severity** — see Finding F-12 |
| Frontend `<SalaryValue>` defaults to lock state | **PASS** | `SalaryValue.tsx:32-42` correctly renders the lock state when `!canViewSalary() \|\| value === undefined \|\| value === null` |
| Logback `MaskingPatternLayout` for salary/token | **MISSING** | No Logback config or `MaskingPatternLayout` in `src/main/resources`. **High severity** — see Finding F-13 |

---

## 8. API security

| Control | Status | Evidence |
|---------|--------|----------|
| Error envelope shape `{code, message, correlationId, traceId, timestamp, fieldErrors?}` | **PASS** | `ErrorResponse.java` with `@JsonInclude(NON_NULL)`; `GlobalExceptionHandler` populates correlationId via MDC |
| No stack traces leaked | **PASS** | `application.yml:30-31` `server.error.include-stacktrace=never, include-message=never`; `GlobalExceptionHandler.handleUnexpected` returns generic `INTERNAL_ERROR` |
| 404 for cross-tenant probing | **PASS** | `TenantAccessDeniedException` mapped to **HTTP 404** in `GlobalExceptionHandler.java:41-47` with `securityLog.warn("CROSS_TENANT_ACCESS_ATTEMPT path=...")` |
| Bean Validation on DTOs | **PASS** | `CreateTenantRequest` uses `@NotBlank`, `@Pattern`, `@Size` |
| Mass assignment protection | **PARTIAL** | Use of Java `record` DTOs prevents accidental setters, but `@JsonIgnoreProperties(ignoreUnknown=false)` is not configured. Default Jackson allows unknown fields silently (warning only). **Medium severity** — see Finding F-14 |
| No JPA entity returned from controllers | **PASS** | `AdminTenantController` returns `TenantResponse` DTO via `TenantResponse.from(domain)` |
| Pagination defaults / max | **N/A (no list endpoints)** | Acceptable for Phase 1 |
| Rate limiting | **MISSING** | No Bucket4j or other rate limiter. Blueprint §10 (API-6) mandates 100 req/min/user globally + 10 req/min/user on `/auth`. **High severity (deferred to ingress)** — see Finding F-15 |
| CORS allowlist (no `*`) | **HIGH-SEVERITY GAP** | `SecurityConfig.java:53` uses `cors(Customizer.withDefaults())` with **no explicit `CorsConfigurationSource` bean defined**. Spring's default is to delegate to per-`@CrossOrigin` annotations or fall back permissively. **High severity** — see Finding F-16 |
| Security headers (HSTS, X-Frame-Options, CSP, Referrer-Policy) | **MISSING** | No header configuration in Spring Security config. Blueprint §10 (API-15) lists 5 mandatory headers. **High severity (can be set at ingress)** — see Finding F-17 |
| Content-Type lock (`application/json` only) | **MISSING** | Not configured; default Spring accepts `application/xml` if Jackson XML is on classpath. Phase 1 has no XML dep so risk is low; still recommend explicit. **Low severity** — see Finding F-18 |

---

## 9. Frontend security

| Control | Status | Evidence |
|---------|--------|----------|
| Tokens stored in memory only (no localStorage/sessionStorage) | **PASS** | `tokenStorage.ts:12` — single module-scoped variable `memoryToken`. `authStore.ts` reads/writes only via `tokenStorage`. Grep for `localStorage` on tokens returns zero hits |
| `localStorage` used only for non-sensitive UI prefs | **PASS** | Only `i18n/index.ts:28-30` caches **locale** in localStorage. Acceptable — locale is not sensitive |
| No `console.log(token …)` or salary | **PASS** | Single `console.warn` in `httpClient.ts:66` logs only status + URL + correlationId — explicitly excludes body and token, gated by `env.isDev` |
| `<PermissionGate>` component | **PASS** | `frontend/src/shared/components/access/PermissionGate.tsx` exists |
| `<SalaryValue>` masking | **PASS** | Default render path is the lock state |
| `permissionUtils.canViewSalary` requires BOTH flag and `SALARY_VIEW` | **PASS** | `permissionUtils.ts:27-30` |
| `TenantSelector` — no manual `tenant_id` text input | **PASS** | `TenantSelector.tsx` renders a typed `listbox` over `user.tenants` array only |
| Route guards (`RequireAuth`, `RequirePermission`, `RequireSalaryPermission`, `RequireAuditPermission`) | **PASS** | All four files exist and behave correctly (fall through to `Outlet` or `NoAccessState`) |
| `dangerouslySetInnerHTML` forbidden | **PASS for current code** | No occurrence found. ESLint rule is not yet wired — **Low** — see Finding F-19 |
| Logout calls IdP `end_session_endpoint` | **MISSING** | `authStore.signOut()` only clears in-memory state. Blueprint FE-13 mandates IdP logout. **Medium severity** — see Finding F-20 |
| CSP / strict headers | **N/A (ingress-owned)** | Deferred to devops-sre |
| Frontend bundle secrets scan | **MISSING** | No CI rule yet. `VITE_DEV_AUTH=true` in `.env.example` is non-secret. **Low severity** — see Finding F-21 |

---

## 10. Secrets

| Item | Status | Evidence |
|------|--------|----------|
| No production secrets in Git | **PASS** | Only `docker-compose.yml` and `application-local.yml` contain placeholder credentials (`grading_app_pwd`, `grading_minio_pwd`). Both files carry comments explicitly marking them as local-only |
| Documented as dev-only | **PASS** | `docker-compose.yml:3` comment: *"Credentials below are LOCAL-ONLY placeholders; never reuse in any deployed env."* `application-local.yml:3` comment: *"DO NOT use this profile in any deployed environment."* |
| Frontend env: no secret patterns | **PASS** | `frontend/.env.example` contains only public config (`VITE_API_BASE_URL`, `VITE_API_PROXY`, `VITE_DEFAULT_LOCALE`, `VITE_DEV_AUTH`) |
| Vault wiring | **DEFERRED (devops-sre)** | No app code yet reads Vault; acceptable for Phase 1 |
| Pre-commit `gitleaks` / `trufflehog` | **MISSING (CI)** | Not in scope of this code review but **required before merging to main long-term** — captured in action items |

**Verdict on secrets:** **PASS for Phase 0+1.** No real secrets committed, dev-only credentials are unambiguously labelled.

---

## 11. Findings

Format: Finding / Severity / Affected area / Risk / Exploit scenario / Required fix / Acceptance criteria / Test case / Owner.

### F-01

* **Finding:** `TenantAwareRepository<T, ID>` interface mandated by blueprint §5.2 is not yet introduced.
* **Severity:** Medium (Phase 1) → **escalates to Critical the moment the first tenant business entity ships.**
* **Affected area:** `backend/src/main/java/uz/hrlab/grading/**/infrastructure/*Repository.java`
* **Risk:** A developer adding a tenant business repo can simply extend `JpaRepository` and inherit `findById`/`findAll`, exposing every other tenant's data.
* **Exploit scenario:** Adding `PositionRepository extends JpaRepository<…>` ships `findById(UUID)`; attacker calls `GET /api/v1/positions/{T_B uuid}` and reads cross-tenant data.
* **Required fix:** Create `uz.hrlab.grading.common.persistence.TenantAwareRepository<T, ID>` that does **not** extend `JpaRepository` (use Spring Data `Repository` base interface) and exposes only `findByIdAndTenantId`, `findAllByTenantId(...)`, `Page<…> findAllByTenantId(UUID, Pageable)`, etc. Add an ArchUnit test that rejects any `*Repository` extending `JpaRepository` for entities annotated `@TenantScoped`.
* **Acceptance criteria:** Architecture test fails when a new tenant repo extends `JpaRepository`. All future tenant repos use the new base interface.
* **Test case:** TI-02..TI-06 once a real entity exists; ArchUnit `TenantRepositoryArchitectureTest`.
* **Owner:** backend-engineer.

### F-02

* **Finding:** JWT `aud` (audience) claim not explicitly validated.
* **Severity:** High
* **Affected area:** `security/SecurityConfig.java:91-96`
* **Risk:** A valid token issued by the same Keycloak realm for a different client/service could authenticate against `grading.hrlab.uz`.
* **Exploit scenario:** Attacker obtains a JWT issued for `hrlab-dashboard` client and replays it against `grading.hrlab.uz` — `iss` matches, `aud` is not checked, so the token is accepted.
* **Required fix:** Add an explicit `JwtAudienceValidator` and combine with the default validators via `DelegatingOAuth2TokenValidator` on the `NimbusJwtDecoder`. Reject tokens whose `aud` does not contain `grading.hrlab.uz`.
* **Acceptance criteria:** AUTH-04 test (token with wrong `aud`) returns 401.
* **Test case:** AUTH-04 (blueprint §17.1).
* **Owner:** backend-engineer.

### F-03

* **Finding:** JTI / sub denylist for forced revocation not implemented.
* **Severity:** Medium (deferred — no business endpoints yet)
* **Affected area:** `security/SecurityConfig.java`
* **Risk:** Deactivated users continue to have valid short-TTL JWTs until expiry; can act for up to 15 minutes.
* **Required fix:** Introduce a Redis-backed JTI denylist consulted by a custom `OAuth2TokenValidator` in the resource-server config. Populate it on `USER_DEACTIVATED` and `LOGOUT` events.
* **Acceptance criteria:** AUTH-08 and AUTH-10 (blueprint §17.1) pass.
* **Test case:** AUTH-08, AUTH-10.
* **Owner:** backend-engineer + devops-sre.

### F-04

* **Finding:** Audit `system_audit_log` table has no DB-level grant restricting runtime user to INSERT/SELECT only.
* **Severity:** **High**
* **Affected area:** `db/changelog/control-plane/003-create-system-audit.yaml`
* **Risk:** A compromised application or rogue developer with runtime DB credentials can `UPDATE` or `DELETE` audit rows.
* **Exploit scenario:** Insider with `grading_app` credentials runs `DELETE FROM public.system_audit_log WHERE actor_user_id=…` to cover tracks.
* **Required fix:** Add a Liquibase changeset that:
  1. Creates the three documented DB roles (`grading_migrator`, `grading_runtime`, `grading_readonly`).
  2. Grants `INSERT, SELECT` only on `public.system_audit_log` and `public.tenant_audit_logs` to `grading_runtime`.
  3. Explicitly **revokes** `UPDATE, DELETE` from `grading_runtime` on these two tables.
  4. Includes a CI integration test that asserts `pg_class` / `information_schema.table_privileges` after migration matches the expected grant matrix.
* **Acceptance criteria:** AUD-06 and AUD-07 (blueprint §17.4) pass — attempting UPDATE/DELETE as runtime user fails with permission denied.
* **Test case:** AUD-06, AUD-07.
* **Owner:** database-architect (primary), backend-engineer (test wiring), devops-sre (role provisioning in deployed envs).

### F-05

* **Finding:** Cross-tenant access attempt is logged to a `security.audit` SLF4J channel (`GlobalExceptionHandler.java:45`) but no structured `AuditEvent` is written via `AuditService`.
* **Severity:** Medium
* **Affected area:** `common/api/GlobalExceptionHandler.java`
* **Risk:** Forensic queries against `system_audit_log` will miss `CROSS_TENANT_OR_PROJECT_ACCESS_ATTEMPT` events; only an unstructured log line exists. Audit pack AUD-05 fails.
* **Required fix:** From `handleTenantAccessDenied`, inject `AuditService` and call `auditService.record(AuditEvent.builder().action(AuditAction.CROSS_TENANT_OR_PROJECT_ACCESS_ATTEMPT).actorUserId(ctx.userId()).tenantId(ctx.tenantId())...build())`. The action constant already exists in `AuditAction.java:67`.
* **Acceptance criteria:** AUD-05 passes — `system_audit_log` contains a row with `action=CROSS_TENANT_OR_PROJECT_ACCESS_ATTEMPT` after any cross-tenant probe.
* **Test case:** AUD-05.
* **Owner:** backend-engineer.

### F-06

* **Finding:** `role_permissions` join table is created but contains **zero rows**. No role currently grants any permission.
* **Severity:** **High** (functional blocker — but also a security gap because the system has no defined "least privilege" defaults yet).
* **Affected area:** `db/changelog/seeds/`
* **Risk:** Either (a) Phase 2 ships with no working RBAC and devs work around it (creating insecure shortcuts), or (b) someone hand-fills role-permissions without security review and accidentally grants `SALARY_*` to a non-salary role.
* **Required fix:** Author `seeds/004-default-role-permissions.yaml` jointly with hr-product-owner and security-engineer. Hard rules to encode in the seed:
  * `SALARY_*` granted to **no** role in MVP 1 (blueprint §8 hard rule).
  * `AUDIT_READ` granted only to `EXTERNAL_AUDITOR`, `HRLAB_SUPER_ADMIN`.
  * `USER_ACCESS_MANAGE` granted only to `HRLAB_SUPER_ADMIN`, `CLIENT_COMPANY_ADMIN`.
  * `TENANT_CREATE` granted only to `HRLAB_SUPER_ADMIN`.
  * Mirror the matrix in `04-rbac-permission-matrix.md` (PRD owner).
* **Acceptance criteria:** Seed runs cleanly; integration test verifies that `HRLAB_SUPER_ADMIN` does NOT have any `SALARY_*` permission and `EXTERNAL_AUDITOR` does have `AUDIT_READ`.
* **Test case:** `RolePermissionMatrixTest`.
* **Owner:** hr-product-owner + security-engineer (matrix), backend-engineer (seed).

### F-07

* **Finding:** Audit hash chain canonicalization is not RFC 8785 JCS — uses ad-hoc `|`-separated concatenation including `OffsetDateTime.toString()`.
* **Severity:** Low (Phase 1 — chain is internally consistent; cross-system forensic verification harder)
* **Affected area:** `audit/infrastructure/JpaAuditService.computeHash`
* **Risk:** Independent reproduction of the hash (e.g. by a third-party auditor's Python tooling) requires precise knowledge of Java `OffsetDateTime.toString()` formatting and Jackson key ordering. Tamper detection within the chain still works.
* **Required fix:** Document the canonicalization rules in `02-security-blueprint.md` §9.2 explicitly (field order, separator, time format `ISO_OFFSET_DATE_TIME`, JSON canonicalization rule). Optionally migrate to RFC 8785 JCS using `com.fasterxml.jackson.dataformat:jackson-dataformat-cbor` + `json-canonicalization` lib in MVP 2.
* **Acceptance criteria:** Documentation block exists; reproducer tool exists or is roadmapped.
* **Test case:** `AuditHashChainCanonicalizationTest` verifies known input → known hex output.
* **Owner:** security-engineer (doc), backend-engineer (test).

### F-08

* **Finding:** No monotonic per-tenant `ordinal` column on `system_audit_log`.
* **Severity:** Medium
* **Affected area:** `db/changelog/control-plane/003-create-system-audit.yaml`; `audit/infrastructure/JpaAuditService.record`
* **Risk:** Under concurrent writers, `findLastHash(tenantId)` (`SystemAuditLogRepository:33-37`) reads the latest hash before insert, but two writers in the same millisecond may both pick the same `prev_hash`, producing a fork instead of a chain. Hash-chain replay verifier would not detect tampering in either branch.
* **Required fix:** Add `ordinal BIGINT NOT NULL` with a per-tenant Postgres sequence or `INSERT … RETURNING ordinal` using `row_number()`. Combine `(tenant_id, ordinal)` as a unique key. Make the insert + hash computation transactional via `SELECT … FOR UPDATE` on a per-tenant anchor row, or use `pg_advisory_xact_lock(hashtext(tenant_id::text))`.
* **Acceptance criteria:** Concurrent insert test produces a strict linear chain per tenant. AUD-08 still passes.
* **Test case:** `AuditHashChainConcurrencyTest` runs 100 parallel inserts and asserts no duplicate `(tenant_id, ordinal)` and no broken chain.
* **Owner:** backend-engineer + database-architect.

### F-09

* **Finding:** Audit `before_json`/`after_json` have no automatic salary/secret redaction filter.
* **Severity:** Medium (Phase 1 — no salary entities yet) → **High once salary entities ship.**
* **Affected area:** `audit/application/AuditEvent.java`, `audit/infrastructure/JpaAuditService.serialize`
* **Risk:** A caller forgets to redact salary fields, the raw values land in `system_audit_log` and survive forever (append-only).
* **Required fix:** Introduce `AuditPayloadRedactor` that recursively walks the `JsonNode` and replaces values of fields whose name matches the regex `(?i)(salary|compensation|fixed_pay|variable_pay|total_cash|total_compensation|password|token|secret|api[_-]?key)` with `"<redacted>"`. Apply in `JpaAuditService.serialize` BEFORE writing to the row.
* **Acceptance criteria:** SAL-05 (blueprint §17.3) passes — audit with salary-shaped field contains `<redacted>`.
* **Test case:** SAL-05.
* **Owner:** backend-engineer.

### F-10

* **Finding:** `EncryptedStringConverter` / `EncryptedNumberConverter` JPA `@Convert` primitives are not implemented.
* **Severity:** **High** (blueprint §8 mandates them shipped in MVP 1 even if no live columns use them)
* **Affected area:** `backend/src/main/java/uz/hrlab/grading/common/persistence/`
* **Risk:** When salary columns arrive in MVP 3 the team will be tempted to ship raw values "temporarily" — there will be no ready primitive to slot in.
* **Required fix:** Implement `AttributeConverter<String, String>` and `AttributeConverter<BigDecimal, String>` using envelope encryption: read tenant DEK from a `TenantEncryptionKeyResolver` (initially a stub backed by a config value, swappable for Vault Transit). Use AES-GCM 256 with random 96-bit IV; persist IV + ciphertext as base64. Unit-test round-trip + verify ciphertext does not equal plaintext.
* **Acceptance criteria:** Unit tests pass; converters are wireable via `@Convert`; no live column is encrypted yet (no salary columns shipped).
* **Test case:** `EncryptedStringConverterTest`, `EncryptedNumberConverterTest`.
* **Owner:** backend-engineer + database-architect (DEK table) + devops-sre (KMS).

### F-11

* **Finding:** No `tenant_encryption_key` control-plane table for envelope-encrypted DEKs.
* **Severity:** Medium
* **Affected area:** `db/changelog/control-plane/`
* **Risk:** Once F-10 lands, the converter needs somewhere to look up per-tenant DEKs.
* **Required fix:** Add changeset creating `public.tenant_encryption_key (id UUID PK, tenant_id UUID FK NOT NULL, kek_alias VARCHAR(120) NOT NULL, dek_ciphertext TEXT NOT NULL, dek_iv TEXT NOT NULL, version BIGINT, rotated_at TIMESTAMPTZ, created_at TIMESTAMPTZ DEFAULT now(), UNIQUE(tenant_id, kek_alias))`. Document rotation procedure.
* **Acceptance criteria:** Table exists; unique index enforces one active DEK per tenant per KEK alias.
* **Test case:** `TenantEncryptionKeyMigrationTest`.
* **Owner:** database-architect.

### F-12

* **Finding:** `@Sensitive("SALARY")` annotation + Jackson serializer module missing.
* **Severity:** **High** (blueprint §8 mandates the primitive)
* **Affected area:** `backend/src/main/java/uz/hrlab/grading/common/api/`
* **Risk:** When salary DTOs ship, devs will hand-roll null-write logic and miss a code path, exposing raw values.
* **Required fix:** Create `@Sensitive(value=…, omitWhenMissingPermission=true)` annotation. Register a Jackson `BeanSerializerModifier` (or `JsonFilter`) that consults `TenantContextHolder.get().hasPermission(value)` and either omits the property or writes a structured mask. Unit-test against a `SalaryFieldDTO`.
* **Acceptance criteria:** SAL-01, SAL-02 pass.
* **Test case:** SAL-01, SAL-02.
* **Owner:** backend-engineer.

### F-13

* **Finding:** No Logback `MaskingPatternLayout` configured.
* **Severity:** **High** (blueprint §14 LOG-2)
* **Affected area:** `backend/src/main/resources/logback-spring.xml` (does not exist)
* **Risk:** A future log statement accidentally logs a JWT or salary string; sensitive data hits centralized log storage.
* **Required fix:** Add `logback-spring.xml` with `ch.qos.logback.classic.encoder.PatternLayoutEncoder` wrapped by a `MaskingPatternLayout` (or use `logback-masking-pattern` library) with regex pack:
  * `Bearer\s+[\w\.\-]+` → `Bearer ***`
  * `"(password|token|api[_-]?key|secret)"\s*[:=]\s*"[^"]*"` → `"$1":"<masked>"`
  * salary field-name regex as above.
  Also enforce structured JSON output in prod profile and mandatory MDC fields (`tenantId`, `userId`, `correlationId`).
* **Acceptance criteria:** Test that emits a log line containing a fake JWT and salary number greps the captured output for the masked substitutions.
* **Test case:** `LogMaskingPatternTest`.
* **Owner:** backend-engineer + devops-sre.

### F-14

* **Finding:** DTOs do not reject unknown JSON properties.
* **Severity:** Medium
* **Affected area:** `tenancy/api/CreateTenantRequest.java` (and all future DTOs)
* **Risk:** Client sends `{"slug":"x", "tenant_id":"<T_B>", "salary_data_permission": true}` — Jackson silently drops the extras, but mass-assignment becomes possible if a DTO ever has a setter for a sensitive field.
* **Required fix:** Add a base `@JsonIgnoreProperties(ignoreUnknown=false)` Jackson configuration for sensitive write endpoints (admin tenant create, future role-assignment endpoint, future salary endpoint). Alternatively, set `spring.jackson.deserialization.fail-on-unknown-properties=true` globally in `application.yml` for safety.
* **Acceptance criteria:** TI-14 / "unknown field" hardening test (blueprint §17.5) passes — extra fields produce 400.
* **Test case:** TI-12, TI-14, blueprint §17.5.
* **Owner:** backend-engineer.

### F-15

* **Finding:** No rate limiting.
* **Severity:** High (deferred to ingress)
* **Affected area:** `SecurityConfig.java`, ingress
* **Risk:** Brute force on future `/auth`, scraping on future `/search`, DoS on any endpoint.
* **Required fix:** Either (a) add `bucket4j-spring-boot-starter` with rules per blueprint §10 API-6, or (b) document Nginx/Envoy rate-limit rules at ingress in devops-sre blueprint and verify with a smoke test.
* **Acceptance criteria:** Blueprint §17.5 rate-limit smoke test passes in staging.
* **Test case:** `RateLimitSmokeTest`.
* **Owner:** devops-sre (preferred) or backend-engineer.

### F-16

* **Finding:** CORS is configured via `cors(Customizer.withDefaults())` with **no explicit `CorsConfigurationSource` bean**.
* **Severity:** **High** (hard rule violation candidate — blueprint §10 API-7 forbids `*` and requires explicit allowlist)
* **Affected area:** `security/SecurityConfig.java:53`
* **Risk:** Default CORS in Spring Security applies an empty/permissive configuration; without a `CorsConfigurationSource` bean, no origins are explicitly allowed and depending on the surrounding setup browsers may either block legitimate origins (UX bug) or — worse, if combined with a `@CrossOrigin("*")` on any future controller — leak responses to attacker sites.
* **Required fix:** Add an explicit `@Bean CorsConfigurationSource` returning a `UrlBasedCorsConfigurationSource` that allows only:
  * `https://grading.hrlab.uz`
  * `https://staging.grading.hrlab.uz`
  * `http://localhost:5173` (dev profile only)
  with methods `GET, POST, PUT, PATCH, DELETE, OPTIONS`, allowed headers including `Authorization, Content-Type, X-Correlation-Id, Accept-Language`, `allowCredentials=false`. Forbid `*`. Add an integration test asserting that `Origin: https://evil.example` yields no `Access-Control-Allow-Origin` header.
* **Acceptance criteria:** `CorsAllowlistTest` passes.
* **Test case:** `CorsAllowlistTest`.
* **Owner:** backend-engineer.

### F-17

* **Finding:** No HTTP security headers configured (HSTS, X-Content-Type-Options, X-Frame-Options, Referrer-Policy, CSP, Permissions-Policy).
* **Severity:** High (can be set at ingress; still document expectations in app)
* **Affected area:** `SecurityConfig`, ingress manifests
* **Required fix:** Either (a) add `.headers(h -> h.contentSecurityPolicy(...).referrerPolicy(...)...)` in Spring config, or (b) define them in the K8s ingress and add an integration smoke test (`HttpSecurityHeadersTest`) that calls a deployed staging URL and asserts presence.
* **Acceptance criteria:** All five headers present in responses on staging.
* **Test case:** `HttpSecurityHeadersTest`.
* **Owner:** devops-sre (primary), backend-engineer (smoke test).

### F-18

* **Finding:** Backend does not explicitly enforce `Content-Type: application/json`.
* **Severity:** Low
* **Affected area:** Controllers
* **Required fix:** Use `@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)` on all write endpoints and exclude `jackson-dataformat-xml` from dependencies (already absent in `pom.xml`).
* **Acceptance criteria:** Test sends `Content-Type: application/xml` and gets 415.
* **Test case:** `ContentTypeLockTest`.
* **Owner:** backend-engineer.

### F-19

* **Finding:** No ESLint rule forbidding `dangerouslySetInnerHTML`, `console.log` in prod, or `localStorage` for tokens/salary.
* **Severity:** Low
* **Affected area:** `frontend/eslint.config.*`
* **Required fix:** Add eslint rules:
  * `react/no-danger: error`
  * `no-console: ['error', { allow: ['warn', 'error'] }]` (with production build flag stripping `warn` too)
  * Custom rule or `no-restricted-syntax` forbidding `localStorage.setItem` keys matching `/token|salary|jwt/i`.
* **Acceptance criteria:** ESLint CI step fails when violations are introduced.
* **Test case:** Synthetic PR with violation fails CI.
* **Owner:** frontend-engineer.

### F-20

* **Finding:** Logout flow does not call IdP `end_session_endpoint`.
* **Severity:** Medium
* **Affected area:** `frontend/src/features/auth/authStore.ts:38-46`
* **Risk:** A user "logs out" in the SPA, in-memory token is cleared, but the IdP session cookie persists — next `/login` silently re-authenticates without challenge.
* **Required fix:** Extend `signOut()` to perform `window.location.href = idpEndSessionUrl(...)` after clearing local state. Pass `id_token_hint` and `post_logout_redirect_uri=/login`.
* **Acceptance criteria:** After logout, navigating to a protected route requires re-entering credentials (or re-running MFA).
* **Test case:** Manual + Playwright E2E.
* **Owner:** frontend-engineer + devops-sre (Keycloak `post_logout_redirect_uris` allowlist).

### F-21

* **Finding:** No CI step scans the frontend bundle for accidental secret patterns.
* **Severity:** Low
* **Affected area:** CI
* **Required fix:** Add `gitleaks` (or `trufflehog`) job that scans `dist/**` after `npm run build`. Also a check that `import.meta.env.VITE_*` variables in code base never include `*_SECRET` or `*_KEY` patterns.
* **Acceptance criteria:** CI job fails when a synthetic secret string is committed in `frontend/src`.
* **Owner:** devops-sre.

---

## 12. Top 20 risks re-evaluation

| # | Risk (from blueprint §19) | Phase 0+1 status |
|---|---------------------------|-------------------|
| R-01 | Cross-tenant leak via missed tenant filter | **Mitigated for current scope** — no business repos yet; `TenantAwarePolicy.requireSameTenant` exists; **F-01 must close before Phase 2** |
| R-02 | BOLA/IDOR | Same as R-01 — mitigated structurally; needs `TenantAwareRepository` base |
| R-03 | Backend trusts `tenant_id` from frontend | **Mitigated** — `CreateTenantRequest` has no `tenant_id`; `JwtTenantContextResolver` is sole source |
| R-04 | JWT validation misconfiguration | **Partially mitigated** — `iss`+`exp`+signature ok; **`aud` missing — F-02** |
| R-05 | Audit mutated/deleted by app path | **Partially mitigated** — repo interface limits methods; **DB grant missing — F-04** |
| R-06 | Salary primitives leak data | **Foundation incomplete** — codes seeded; **encryption converters and `@Sensitive` annotation missing — F-10, F-12** |
| R-07 | Secrets in Git | **Mitigated** — only dev placeholders, clearly labelled |
| R-08 | Misconfigured CORS | **NOT MITIGATED — F-16** |
| R-09 | Stack traces leak | **Mitigated** — `application.yml` + `GlobalExceptionHandler` |
| R-10 | Privileged role without MFA | **Deferred** — no privileged endpoint beyond admin tenant create; `acr` check not yet implemented |
| R-11 | Background worker tenant confusion | **N/A — no workers** |
| R-12 | Cache poisoning across tenants | **N/A — no caches** |
| R-13 | Approved methodology silently edited | **N/A — no methodology entity** |
| R-14 | Audit retention/restore not exercised | **Deferred (devops-sre)** |
| R-15 | Stale token after user removal | **Deferred — F-03** |
| R-16 | XSS via stored job profile fields | **N/A — no rich text yet**; F-19 covers ESLint baseline |
| R-17 | Mass assignment | **Partial — F-14** |
| R-18 | Permissive CSP / missing headers | **NOT MITIGATED — F-17** |
| R-19 | Dependency CVEs | **Deferred (devops-sre)** |
| R-20 | Formula injection in CSV/Excel | **N/A — no exports** |

---

## 13. Release security gate decision

**Decision: SHIP with conditions.**

Phase 0+1 is foundation code with no business endpoints exposing tenant data. The implemented primitives (JWT-derived tenant context, deny-by-default Spring Security, append-only audit repository interface, hash chain, 404-for-cross-tenant probing, frontend in-memory token + salary masking) materially reduce risk. **No hard cybersecurity rule has been violated** in the code that exists today. The gaps identified are deferred features that are not yet exercised by any user flow.

However, the foundation can only be merged to `main` and used as the base for Phase 2 if the following **non-negotiable conditions** are scheduled and closed before any business endpoint ships:

### Conditions (must close before Phase 2 entry)

1. **F-02** — Explicit JWT audience validator. (1 day)
2. **F-04** — Liquibase DB role grants for `system_audit_log` + `tenant_audit_logs` (INSERT/SELECT only on runtime user). (1–2 days, database-architect)
3. **F-06** — Seed `role_permissions` with the agreed matrix, with SALARY_* granted to no role. (1 day, hr-product-owner + security-engineer)
4. **F-16** — Explicit `CorsConfigurationSource` allowlist with integration test. (0.5 day)
5. **F-05** — Cross-tenant attempts written as structured `AuditEvent`, not just a log line. (0.5 day)

### Strongly recommended before Phase 2

6. F-01 — `TenantAwareRepository<T,ID>` base interface + ArchUnit rule.
7. F-10 / F-12 / F-13 — Salary masking + encryption + log masking primitives (so Phase 2 entities can opt in).
8. F-17 — Security headers at ingress, with smoke test.
9. F-08 — `ordinal` column + per-tenant lock for audit hash chain.

### Acceptable to defer past Phase 2 but tracked

* F-03, F-07, F-09, F-11, F-14, F-15, F-18, F-19, F-20, F-21.

---

## 14. Action items per agent

### backend-engineer (5 critical → must close before Phase 2)

* F-02 — JWT `aud` validator wired to `NimbusJwtDecoder` via `DelegatingOAuth2TokenValidator`.
* F-05 — Convert cross-tenant `securityLog.warn` into a real `AuditService.record(CROSS_TENANT_OR_PROJECT_ACCESS_ATTEMPT)`.
* F-16 — Add `CorsConfigurationSource` bean + integration test.
* F-01 — `TenantAwareRepository<T, ID>` + ArchUnit rule banning `JpaRepository.findById` on `@TenantScoped` entities. Land before first tenant business repo.
* F-10 / F-12 / F-13 — Implement `EncryptedStringConverter`, `EncryptedNumberConverter`, `@Sensitive` Jackson module, Logback `MaskingPatternLayout`.

### frontend-engineer

* F-20 — Wire logout to IdP `end_session_endpoint`.
* F-19 — Add ESLint rules (`react/no-danger`, `no-console` prod-strict, custom `localStorage` ban).
* Add ESLint+CI grep that rejects `tenant_id` appearing as a free-text input in any form schema.

### database-architect

* F-04 — Author role grant changeset (migrator/runtime/readonly) + INSERT-only on audit tables. **Highest priority.**
* F-11 — `tenant_encryption_key` table with envelope-encrypted DEK column.
* F-08 — Add `ordinal BIGINT NOT NULL` column + per-tenant uniqueness on `system_audit_log`.

### devops-sre

* F-15 — Rate limiting at ingress (or Bucket4j) with documented thresholds.
* F-17 — HSTS, CSP, X-Frame-Options, X-Content-Type-Options, Referrer-Policy at ingress + smoke test.
* F-21 — gitleaks/trufflehog in CI, frontend bundle scan.
* F-03 — Provision Redis JTI denylist (when Phase 2 user-management endpoints arrive).
* F-13 (partner) — Logback masking config + centralized log redaction.
* Provision Keycloak with `acr=mfa` for HRLab admin roles.

### hr-product-owner

* F-06 — Author the role × permission matrix with security-engineer (`SALARY_*` granted to no role; `AUDIT_READ` only to `EXTERNAL_AUDITOR` and `HRLAB_SUPER_ADMIN`; `TENANT_CREATE` only to `HRLAB_SUPER_ADMIN`). Land as `04-rbac-permission-matrix.md` and as Liquibase seed `004-default-role-permissions.yaml`.
* Update every user story AC template to include "returns 404 on cross-tenant access" and "audit event recorded" where applicable.

### qa-engineer

* Stand up the integration test base class that issues two tenants (T_A, T_B) and runs the 18-scenario tenant isolation pack on every endpoint as soon as it ships.
* Author AUTH-01..AUTH-10 pack.
* Author AUD-01..AUD-10 pack (already partially possible with current audit primitives).
* Implement controller-scan static analysis (Semgrep) that rejects any controller whose request body schema mentions `tenant_id`.
* Add an `AuditAppendOnlyTest` that verifies F-04 (DB-level grants).

---

— end of report —

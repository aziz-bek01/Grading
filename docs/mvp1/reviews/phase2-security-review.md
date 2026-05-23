# Phase 2 — Security Review Report

**Product:** grading.hrlab.uz
**Reviewer agent:** security-engineer
**Date:** 2026-05-23
**Benchmark:** `docs/mvp1/02-security-blueprint.md` (v1.0)
**Predecessors:** `docs/mvp1/reviews/phase0-1-security-review.md`
**Reference architecture:** `архитектура.md` §8, §9, §13; `docs/mvp1/role-permissions-matrix.md`
**Verdict:** **SHIP with conditions** (see §14).

---

## 1. Review scope

Phase 2 introduces the first three tenant-business modules — **Project / Department / Position** — and the foundational ABAC layer that gates every read against four scope policies. This review covers:

* Backend modules `backend/src/main/java/uz/hrlab/grading/{project,organization,position}/**` (controllers, application services, JPA entities, repositories, domain policies — read in full).
* New ABAC layer under `access/application/` and `access/domain/` (`AbacGate`, `AbacRequest`, `ScopePolicy`, `PolicyDecision`, `DepartmentScopePolicy`, `ConsultantTenantAssignmentPolicy`, `ProjectMembershipPolicy`, `ApprovedEntityFilterPolicy`).
* Tenant-schema Liquibase changelogs (`tenant-schema/001-create-projects.yaml`, `002-create-organization.yaml`, `003-create-positions.yaml`) and the master `db.changelog-tenant.yaml`.
* `tenancy/infrastructure/TenantSchemaProvisioner.java` (programmatic Liquibase invocation for schema-per-tenant mode).
* `common/infrastructure/TenantAwareRepository.java` (Phase 0+1 remediation now exercised).
* The remediation surface for Phase 0+1 findings F-01…F-21 (re-verified end-to-end).
* Frontend Phase 2 deliverables under `frontend/src/features/{projects,organization,positions}/**`, the rewritten `UserMenu`, the `AuditLogStatCard` PermissionGate wrap, and the MSW mock handlers at `frontend/src/shared/api/mocks/handlers.ts`.

Out of scope for this review (deferred): job profile module, methodology/scoring/grade, real Keycloak integration, file uploads, AI gateway, K8s manifests beyond CI.

---

## 2. Phase 0+1 findings closure

| ID | Description | Phase 0+1 verdict | Closure evidence | Status |
|----|-------------|-------------------|------------------|--------|
| F-01 | `TenantAwareRepository<T,ID>` base | Deferred (Critical at Phase 2 entry) | `common/infrastructure/TenantAwareRepository.java` exists; **all 3 Phase 2 tenant repos extend it** and NOT `JpaRepository` | **CLOSED** |
| F-02 | JWT `aud` validator | High | `security/JwtAudienceValidator.java` + `SecurityConfig.java:156-157` wires `DelegatingOAuth2TokenValidator(withIssuer, withAudience)` | **CLOSED** |
| F-03 | JTI denylist for forced revocation | Medium (deferred) | Not implemented yet; acceptable until user-management endpoints arrive | **OPEN (deferred)** |
| F-04 | DB role grants (INSERT/SELECT-only on audit) | High | `control-plane/005-db-role-grants.yaml` exists | **CLOSED** (subject to QA verification of post-deploy grant matrix) |
| F-05 | Cross-tenant attempt → structured audit | Medium | `GlobalExceptionHandler.recordCrossTenantAttempt` at lines 65-92 calls `auditService.record(...action=CROSS_TENANT_OR_PROJECT_ACCESS_ATTEMPT...)`; also fired from `CreatePositionUseCase` on cross-project department reference | **CLOSED** |
| F-06 | `role_permissions` seed (SALARY_* unassigned) | High | `seeds/004-default-role-permissions.yaml` exists; matrix in `role-permissions-matrix.md` | **CLOSED** |
| F-07 | Hash chain canonicalization documented | Low | Not re-verified in this review | OPEN (low) |
| F-08 | Audit `ordinal` per-tenant monotonic | Medium | Not yet added; concurrency window remains | OPEN |
| F-09 | Audit payload salary redactor | Medium | Not re-verified | OPEN |
| F-10 | Salary encryption converters | High | `common/persistence/SalaryEncryptionConverter.java` exists | **CLOSED** |
| F-11 | `tenant_encryption_key` table | Medium | Not present in control-plane changelogs | OPEN |
| F-12 | `@Sensitive` annotation + Jackson modifier | High | `common/api/Sensitive.java`, `SensitiveFieldSerializerModifier.java`, `SensitiveFieldJacksonConfig.java` all present | **CLOSED** |
| F-13 | Logback `MaskingPatternLayout` | High | `backend/src/main/resources/logback-spring.xml` and `common/logging/MaskingPatternLayout.java` exist | **CLOSED** |
| F-14 | Reject unknown JSON properties on sensitive DTOs | Medium | Not re-verified | OPEN |
| F-15 | Rate limiting | High (ingress) | Devops-sre scope; not in backend | DEFERRED |
| F-16 | Explicit `CorsConfigurationSource` allowlist | High | `SecurityConfig.java:108-122` declares `@Bean CorsConfigurationSource` returning `UrlBasedCorsConfigurationSource` | **CLOSED** |
| F-17 | Security headers (HSTS/CSP/X-Frame-Options) | High (ingress) | Devops-sre scope | DEFERRED |
| F-18 | Content-Type lock | Low | Not re-verified | OPEN |
| F-19 | ESLint hardening rules | Low | Not re-verified | OPEN |
| F-20 | Logout → IdP `end_session_endpoint` | Medium | `authStore.signOut()` and new `UserMenu.handleSignOut` clear in-memory state, QueryClient cache and navigate to `/login` but **do NOT call IdP `end_session_endpoint`**. Grep for `end_session_endpoint`/`endSessionEndpoint` returns zero matches in `frontend/src` | **OPEN — still unresolved** |
| F-21 | Frontend bundle secret scan | Low | Devops-sre scope | DEFERRED |

**Closure tally:** 8 of the 8 conditional remediation findings called out in the Phase 0+1 verdict (F-02 / F-04 / F-05 / F-06 / F-10 / F-12 / F-13 / F-16) are **CLOSED**. Additionally **F-01 is closed** because Phase 2 actually exercises the `TenantAwareRepository` base interface for the first time and all three new repos comply. F-03/F-08/F-09/F-11/F-14/F-18/F-19/F-20/F-21 remain open as in Phase 0+1.

---

## 3. Architecture conformance for Phase 2

| Architecture clause | Status | Evidence |
|---------------------|--------|----------|
| §9 Domain Model — Project, Department, Position aggregates | Conformant | `project/domain/Project.java`, `organization/domain/Department.java`, `position/domain/Position.java`, all with tenant_id, status enum, value records |
| §13.2 API rules — no `tenant_id` in business request/path/query | Conformant for paths/bodies; **`GET /projects?tenantId=` is sent by the frontend as a query parameter** (`projectApi.ts:20`). Backend `ProjectController.list` does NOT bind a `tenantId` request parameter — only `page` and `size` — so the value is silently ignored. No security impact (tenant from JWT) but worth removing from the frontend client to keep the contract clean | `ProjectController.java:64-71`; `projectApi.ts:18-23` |
| §13 — 404 for cross-tenant / wrong-tenant probing | Conformant | All `*UseCase` and `Find*Query` resolve via `findByIdAndTenantId(...).orElseThrow(TenantAccessDeniedException::new)`; mapped to 404 by `GlobalExceptionHandler:54-63` |
| §13 — 409 Conflict on locked/archived edits | Conformant | `ProjectLockedException` thrown by `UpdateProjectUseCase`, `LockProjectUseCase`, `CreateDepartmentUseCase`, `UpdateDepartmentUseCase`, `CreatePositionUseCase`, `UpdatePositionUseCase`; mapped to 409 in `GlobalExceptionHandler:118-121` |
| §13 — pagination envelope | Conformant | `PageResponse<T>` is the wire format for `GET /projects` and `GET /positions` (controllers use `PageResponse.of(page, mapper)`) |
| §9 — Project status machine DRAFT→ACTIVE→LOCKED→ARCHIVED with LOCKED terminal-for-edits | Conformant | `UpdateProjectUseCase:36-39` refuses edits when status ∈ {LOCKED, ARCHIVED}; idempotent re-lock at `LockProjectUseCase:33` |
| §9 — Department parent/child invariants (no self-parent, no cycles, same project) | Conformant | `DepartmentValidationPolicy` enforces all three; native CTE `findDescendants` is tenant-scoped; DB has `CHECK (id <> parent_id)` |
| §9 — Position belongs to one Department of the same Project | Conformant | `CreatePositionUseCase:55-77` validates dept is in same tenant + same project + not archived |

---

## 4. Tenant isolation verification — Phase 2

| Scenario | Mechanism | Status |
|----------|-----------|--------|
| Direct UUID GET `/projects/{T_B id}` returns 404 | `FindProjectQuery.findById` → `projects.findByIdAndTenantId(id, ctx.tenantId())` → `orElseThrow(TenantAccessDeniedException)` | **PASS** |
| Direct UUID GET `/departments/{T_B id}` returns 404 | `FindDepartmentQuery.findById:33-34` same pattern | **PASS** |
| Direct UUID GET `/positions/{T_B id}` returns 404 | `FindPositionQuery.findById:36-37` same pattern | **PASS** |
| POST `/positions` with `projectId` from another tenant | `CreatePositionUseCase:48-49` → 404; **also** when `departmentId` matches a row in another project of same tenant, code audits `CROSS_TENANT_OR_PROJECT_ACCESS_ATTEMPT` and returns 400 with the same generic message used for "not found" (no existence oracle) | **PASS (with audit)** |
| POST `/departments` with parent_id from another project | `DepartmentValidationPolicy.validateParentForCreate:33-37` throws `DEPARTMENT_PARENT_NOT_FOUND` 400 with the same generic message — no leak | **PASS** |
| PATCH `/positions/{id}` moving department across tenants | `UpdatePositionUseCase:60-66` re-checks via `findByIdAndTenantId` AND `newDept.getProjectId().equals(entity.getProjectId())`; both must match | **PASS** |
| List `GET /projects` shows only T_A | `FindProjectQuery.list:46-49` filters by `ctx.tenantId()` and excludes ARCHIVED | **PASS** |
| List `GET /positions?projectId=T_B uuid` | `enforceCanListInProject(ctx, projectId)` first runs ABAC; for non-bypass roles the project must be in `ctx.projectIds()` else DENY → 404 | **PASS** |
| `GET /departments/tree?projectId=T_B uuid` | Same ABAC gate as above; additionally a `DEPARTMENT_MANAGER` is further restricted by `filterByDepartmentScope` | **PASS** |
| Department `findDescendants` native CTE — cross-tenant? | The recursive CTE filters every level by `tenant_id = :tenantId`, and the caller already resolved root via `findByIdAndTenantId` | **PASS** |

No bare `findById` is invoked on any of the three Phase 2 entities. All five Phase 2 read paths set tenant via `TenantContextHolder.requireActive()` and use `findByIdAndTenantId`. Writes always set `tenantId = ctx.tenantId()` — never from body. The `tenantId` column of every JPA entity is `updatable = false`, so even if a developer added a setter later, Hibernate would refuse to update the column.

---

## 5. ABAC policy verification — all four policies

### 5.1 `ProjectMembershipPolicy` (`access/domain/ProjectMembershipPolicy.java`)

* **Intent:** the requested `projectId` must be in `ctx.projectIds()`.
* **Bypass:** HRLAB_SUPER_ADMIN, HRLAB_PROJECT_MANAGER.
* **Result if `projectId` is null:** `NOT_APPLICABLE` (listing without a project context).
* **Result if `ctx.projectIds()` is null/empty (non-bypass role):** **DENY** — fail-closed. **Correct for HRLAB_CONSULTANT / VIEWER / DEPARTMENT_MANAGER**, but creates a UX issue for **`CLIENT_COMPANY_ADMIN` and `CLIENT_HR_DIRECTOR`** whose role scope per `role-permissions-matrix.md` is `TENANT` (they are meant to see all projects within their tenant without explicit per-project membership). See **finding F-201**.

### 5.2 `DepartmentScopePolicy` (`access/domain/DepartmentScopePolicy.java`)

* **Intent:** for `DEPARTMENT_MANAGER`, the target `departmentId` must be within `ctx.departmentScope()`.
* **Bypass:** HRLAB_SUPER_ADMIN, HRLAB_PROJECT_MANAGER, HRLAB_CONSULTANT, HRLAB_ANALYST, CLIENT_COMPANY_ADMIN, CLIENT_HR_DIRECTOR.
* **Result if `departmentId` is null:** `NOT_APPLICABLE`.
* **Edge case:** Other roles (`VIEWER`, `EXTERNAL_AUDITOR`, `CLIENT_HR_SPECIALIST`, `EVALUATION_COMMITTEE_MEMBER`) that are not `DEPARTMENT_MANAGER` evaluate to `NOT_APPLICABLE`. Their authorization is enforced by `ProjectMembershipPolicy` and `ApprovedEntityFilterPolicy`. **OK**.

### 5.3 `ConsultantTenantAssignmentPolicy` (`access/application/ConsultantTenantAssignmentPolicy.java`)

* **Intent:** an `HRLAB_CONSULTANT` or `HRLAB_ANALYST` must be present in `public.user_tenant_memberships` for `(ctx.userId, ctx.tenantId)`.
* **Bypass:** HRLAB_SUPER_ADMIN, HRLAB_PROJECT_MANAGER.
* **Result if `userId` or `tenantId` is null:** `NOT_APPLICABLE` (correct — TenantContextFilter would have rejected the request earlier).
* **DB call per ABAC evaluation** — adds one `EXISTS` query to every read. Acceptable but should be cached (consultant assignment changes are infrequent). See **finding F-205**.

### 5.4 `ApprovedEntityFilterPolicy` (`access/domain/ApprovedEntityFilterPolicy.java`)

* **Intent:** `VIEWER` / `EXTERNAL_AUDITOR` only see entities in {`ACTIVE`, `LOCKED`, `APPROVED`}.
* **Bypass:** HRLAB_SUPER_ADMIN, HRLAB_PROJECT_MANAGER (when bundled with viewer role).
* **Result if `status` is null:** `NOT_APPLICABLE`. Correct.
* **Caveat:** the check is **case-insensitive** via `toUpperCase()` — safe.

### 5.5 `AbacGate` composer (`access/application/AbacGate.java`)

* Iterates all `ScopePolicy` beans; on first `DENY` records `ACCESS_DENIED_BY_ABAC` audit event with `policy=<policyName>` reason, then throws `TenantAccessDeniedException` (404 via `GlobalExceptionHandler`). **Audit-on-deny verified.**
* `recordDenial` swallows audit persistence failures (logs error) — correct trade-off; audit failure must not block the 404.
* **Concern:** the gate is invoked only on `Find*Query` paths (`enforceCanReadProject`, `enforceCanReadDepartment`, `enforceCanReadPosition`, `enforceCanListInProject`). **Writes (`Create*UseCase`, `Update*UseCase`, `Archive*UseCase`) do NOT invoke `AbacGate`** — they only rely on `@PreAuthorize("hasAuthority('…')")` and tenant filter. This means a CLIENT_HR_SPECIALIST with `POSITION_CREATE` but assigned to project P_A could attempt to create a position in another in-tenant project P_B by passing its `projectId`. The `findByIdAndTenantId` resolve will succeed (same tenant); only the unrelated `existsByTenantIdAndProjectIdAndCode` code-uniqueness check would run. **See finding F-202.**

---

## 6. Liquibase tenant-schema security

### 6.1 `001-create-projects.yaml`

* `tenant_id UUID NOT NULL` — present.
* `UNIQUE INDEX uq_projects_tenant_code ON projects (tenant_id, code)` — tenant-scoped uniqueness. **PASS.**
* `CHECK status IN (DRAFT, ACTIVE, LOCKED, ARCHIVED)` — enum-safe.
* Indexes on `tenant_id`, `tenant_id+status`, `created_at`. **PASS.**
* **No FK on `tenant_id` to `public.tenants(id)`** — see finding **F-203**.

### 6.2 `002-create-organization.yaml` (departments)

* `tenant_id UUID NOT NULL`, `project_id UUID NOT NULL`. **PASS.**
* `UNIQUE INDEX (tenant_id, project_id, code)` — correct tenant+project scope.
* `FOREIGN KEY (project_id) REFERENCES projects(id)` — present.
* `FOREIGN KEY (parent_id) REFERENCES departments(id)` — self-FK present.
* `CHECK (id <> parent_id)` — DB-level self-parent prevention. **PASS.**
* **But:** the FK on `project_id` does NOT include a tenant predicate. If `projects(id)` has the same UUID space across tenants (in shared mode), the FK alone cannot enforce that the referenced project is in the same tenant. Application layer enforces it (`CreateDepartmentUseCase:42`) but DB cannot fail-closed. **Acceptable defense-in-depth gap** (shared schema only). Schema-per-tenant mode (post-MVP) closes it. Tracked as finding **F-204**.

### 6.3 `003-create-positions.yaml`

* `tenant_id NOT NULL`, `project_id NOT NULL`, `department_id NOT NULL`. **PASS.**
* `UNIQUE INDEX (tenant_id, project_id, code)` — correct.
* `FK positions → projects`, `FK positions → departments` — present.
* Same observation as F-204: FK cannot enforce cross-FK consistency (position.project_id must equal department.project_id). Application enforces it; DB cannot.

### 6.4 `db.changelog-tenant.yaml`

* Includes the three files in order; uses `relativeToChangelogFile: true`. **PASS.**
* Context label `mode-shared` is honored by both the master Liquibase run AND `TenantSchemaProvisioner.update(new Contexts("mode-shared"), …)` so the same DDL applies in both modes. Acceptable.

### 6.5 Cycle prevention at DB

* Self-parent is blocked by `CHECK (id <> parent_id)`.
* Multi-step cycles (A→B→A, A→B→C→A) are **not** blocked at the DB level — would require a recursive trigger or pg constraint. Application enforces via `DepartmentValidationPolicy.validateParentForUpdate` calling `findDescendants` (tenant-scoped recursive CTE). **Acceptable for Phase 2** but documented as **finding F-206** for future hardening.

---

## 7. `TenantSchemaProvisioner` security

`backend/src/main/java/uz/hrlab/grading/tenancy/infrastructure/TenantSchemaProvisioner.java` (90 lines).

* **Input validation:** `sanitize(schema)` enforces regex `^tenant_[a-z0-9_]+$` before interpolating into the `CREATE SCHEMA IF NOT EXISTS` statement (line 82-87). Throws `IllegalArgumentException` otherwise. **PASS — SQL injection via tenant slug is structurally impossible.**
* `tenant.schemaName()` is sourced from the domain `Tenant` object (persisted in control plane), not from user input. The slug used to build it is itself validated at tenant creation (`CreateTenantRequest.@Pattern`).
* Liquibase context is fixed to `"mode-shared"` (line 74). No user-controlled context binding.
* The connection is obtained from the runtime `DataSource` and closed via try-with-resources. **PASS.**
* `db.setDefaultSchemaName(schemaName)` is set before `liquibase.update(...)`, so DDL targets the new schema only. **PASS.**
* **Reservation:** in SHARED mode (current MVP 1) the method is a no-op (line 54-57). When schema-per-tenant goes live, the runtime DB role still needs grants in the new schema — that wiring is reserved for devops-sre. Recorded as finding **F-207**.
* **Defense-in-depth:** the call site that triggers provisioning (admin tenant create) is already gated by `TENANT_CREATE` permission (HRLAB_SUPER_ADMIN only) — verified in `AdminTenantController`.

---

## 8. API security — every Phase 2 endpoint

| Endpoint | `@PreAuthorize` | Tenant from | DTO | Body fields with `tenant_id`? | 404 vs 403 | Locked → 409? |
|----------|-----------------|------------|-----|--------------------------------|-----------|---------------|
| POST `/api/v1/projects` | `PROJECT_CREATE` | JWT context | `CreateProjectRequest` | None | n/a | n/a |
| GET `/api/v1/projects` | `PROJECT_READ` | JWT context | n/a | None | only T_A returned | n/a |
| GET `/api/v1/projects/{id}` | `PROJECT_READ` | JWT context | n/a | None | 404 on wrong tenant | n/a |
| PATCH `/api/v1/projects/{id}` | `PROJECT_EDIT` | JWT context | `UpdateProjectRequest` | None | 404 | **409** when LOCKED/ARCHIVED |
| POST `/api/v1/projects/{id}/archive` | `PROJECT_EDIT` | JWT context | n/a | None | 404 | idempotent |
| POST `/api/v1/projects/{id}/lock` | `METHODOLOGY_LOCK` ∨ `PROJECT_EDIT` | JWT context | n/a | None | 404 | 409 on ARCHIVED |
| POST `/api/v1/departments` | `ORG_EDIT` | JWT context; `projectId` from body validated against tenant | `CreateDepartmentRequest` | None | 404/400 | 409 if project locked |
| GET `/api/v1/departments/tree?projectId=...` | `ORG_READ` | JWT context + ABAC gate | n/a | None | 404 | n/a |
| GET `/api/v1/departments/{id}` | `ORG_READ` | JWT context | n/a | None | 404 | n/a |
| PATCH `/api/v1/departments/{id}` | `ORG_EDIT` | JWT context | `UpdateDepartmentRequest` | None | 404 | 409 if project/dept locked |
| POST `/api/v1/departments/{id}/archive` | `ORG_EDIT` | JWT context | n/a | None | 404 | idempotent |
| POST `/api/v1/positions` | `POSITION_CREATE` | JWT context; `projectId`/`departmentId` from body validated | `CreatePositionRequest` | None | 404/400 | 409 if project locked |
| GET `/api/v1/positions?projectId=...` | `POSITION_READ` | JWT context + ABAC gate | n/a | None | 404 | n/a |
| GET `/api/v1/positions/{id}` | `POSITION_READ` | JWT context | n/a | None | 404 | n/a |
| PATCH `/api/v1/positions/{id}` | `POSITION_EDIT` | JWT context | `UpdatePositionRequest` | None | 404 | 409 if project locked |
| POST `/api/v1/positions/{id}/archive` | `POSITION_EDIT` | JWT context | n/a | None | 404 | idempotent |

All 16 endpoints carry a `@PreAuthorize` guard. **None** of the six write DTOs declares a `tenant_id` field. **No JPA entity is returned from a controller** — every method maps via `*Response.from(domain)`. Pagination is enforced with `MAX_PAGE_SIZE = 200` in `FindProjectQuery` and `FindPositionQuery`. The error envelope is uniform (`code`, `message`, `correlation_id`, `traceId`, optional `fieldErrors`).

### 8.1 Mass assignment risk

Update use cases use field-by-field merging:
```
if (cmd.nameI18n() != null) entity.setNameI18n(new HashMap<>(cmd.nameI18n()));
```
**No setters for `tenantId`, `projectId`, `id`, `version` are exposed in entities.** Records-as-DTOs disable Jackson mass-assignment by design. **PASS.**

### 8.2 Defense-in-depth on listing

`FindPositionQuery.list:52-58` force-overrides the requested `status` filter to `ACTIVE` when the caller is a VIEWER or EXTERNAL_AUDITOR — even before `ApprovedEntityFilterPolicy` evaluates per row. **Excellent defense-in-depth.**

---

## 9. Frontend security verification — Phase 2

| Item | Status | Evidence |
|------|--------|----------|
| `UserMenu` Sign out clears token + query cache + redirects | **PASS** | `UserMenu.tsx:51-56` calls `signOut()` (token clear via `tokenStorage.clear()` in `authStore`), `queryClient.clear()`, then `navigate(routes.login, {replace:true})` |
| `UserMenu` Sign out calls IdP `end_session_endpoint` | **FAIL** | Grep for `end_session_endpoint` returns zero hits in `frontend/src`. F-20 remains open |
| `AuditLogStatCard` wrapped in `<PermissionGate permission={AUDIT_READ}>` | **PASS** | `AuditLogStatCard.tsx:18-26` |
| No new `localStorage.setItem` for tokens or salary | **PASS** | Only locale persisted (`i18n/index.ts:28-30`) |
| No `console.log(token...)`, no `console.log(salary)` | **PASS** | Only `console.info('[api] mock adapter enabled')` and dev-only `console.warn` of status+URL+correlationId in `httpClient.ts` |
| Multilingual input fields don't inject HTML | **PASS (by default)** | `pickLocalized(...)` returns plain strings rendered through React default escaping; no `dangerouslySetInnerHTML` in any Phase 2 component |
| MSW mocks accept `tenant_id` from body? | **FAIL — finding F-208** | `handlers.ts:79` reads `body.tenant_id ?? 'tenant-acme'` on POST /projects, and the projects table view passes `tenantId` as a query string in `projectApi.ts:20`. The real backend ignores both; the mock layer is dev-only, BUT this teaches developers a bad pattern and contradicts API-13 |
| `tenant_id` rendering in `ProjectWorkspacePage` | **Informational** | The frontend stores `tenant_id` returned by the backend on the active project object — this is the *response*, not request; acceptable |
| Multilingual i18n strings — no sensitive test data | **PASS** | i18n bundles do not contain test salary values |
| `ProjectCreatePayload` — does it carry `tenant_id`? | **PASS** | `projectTypes.ts:17-23` defines only `code, name, description, start_date, end_date` |
| `dangerouslySetInnerHTML` | **PASS** | No occurrence in any Phase 2 component |

---

## 10. Project Lock immutability

* `Project.status = LOCKED` blocks `UpdateProjectUseCase:36-39` → `ProjectLockedException` → 409 via `GlobalExceptionHandler:118-121`. **PASS.**
* `Project.status = LOCKED` blocks new department creation (`CreateDepartmentUseCase:44-46`) and edits (`UpdateDepartmentUseCase:49-52`). **PASS.**
* `Project.status = LOCKED` blocks new position creation (`CreatePositionUseCase:50-53`) and edits (`UpdatePositionUseCase:53-56`). **PASS.**
* `LockProjectUseCase` is idempotent (re-lock returns silently) but refuses to lock when already ARCHIVED. **PASS.**
* Lock event is audited with `AuditAction.PROJECT_LOCKED`. **PASS.**
* **Concern:** there is no DB-side guard. A malicious DB-level UPDATE could change `status` back to `ACTIVE`. The runtime user has UPDATE on the table by design (needed for normal edits and archive). A trigger that rejects `status='LOCKED' → other` transitions would be the next step. Tracked as **finding F-209** (Low; defense-in-depth).

---

## 11. Department cycle prevention

* **Self-parent:** rejected at DB (`CHECK (id <> parent_id)`) and at service (`DepartmentValidationPolicy:49-52`). **PASS.**
* **Multi-step cycle:** rejected at service level via `findDescendants` recursive CTE (`DepartmentRepository:27-37`) — tenant-scoped, will pick up all descendants and refuse if the new parent is among them.
* **No DB-level cycle constraint** — see F-206 (Medium; future hardening). For Phase 2 this is acceptable because (a) only `ORG_EDIT` callers can mutate, (b) the service layer always runs the descendant check, (c) the audit captures the move.

---

## 12. Findings (F-2xx series)

### F-201

* **Finding:** `ProjectMembershipPolicy` denies tenant-wide roles when `ctx.projectIds()` is empty.
* **Severity:** **Medium** (functional + indirect security risk).
* **Affected area:** `access/domain/ProjectMembershipPolicy.java:33-43`.
* **Risk:** `CLIENT_COMPANY_ADMIN` and `CLIENT_HR_DIRECTOR` are TENANT-scoped per `role-permissions-matrix.md` §3 and are expected to see all projects in their tenant. Today, if their JWT does not enumerate every projectId they get a 404 on every project read — they cannot perform tenant-wide administration, which encourages workarounds (e.g. seeding them as members of every project) that bypass least-privilege.
* **Exploit scenario:** an operator, frustrated by the UX failure, manually grants the admin every project as a "fix" — they now appear in audit logs as having explicit membership in projects they were not deliberately assigned to, breaking accountability.
* **Required fix:** extend the bypass set in `ProjectMembershipPolicy` to include `CLIENT_COMPANY_ADMIN` and `CLIENT_HR_DIRECTOR`. Update the docstring. Add an ABAC unit test for each role × project-of-its-tenant (PERMIT) and × project-of-another-tenant (DENY by tenant filter earlier in the stack).
* **Acceptance criteria:** ABAC unit test pack covers all 11 roles × {own-tenant project listed, own-tenant project not listed, cross-tenant project}.
* **Test case:** new `ProjectMembershipPolicyTest` cases for the two roles.
* **Owner:** backend-engineer.

### F-202

* **Finding:** Write paths (`Create*UseCase`, `Update*UseCase`, `Archive*UseCase`) do not invoke `AbacGate`.
* **Severity:** **Medium**.
* **Affected area:** all three modules' application layer.
* **Risk:** A `CLIENT_HR_SPECIALIST` who has `POSITION_CREATE` AND is a project member of project P_A could create a position in an unrelated in-tenant project P_B by passing `projectId=P_B` in the body. Tenant filter alone is insufficient — project membership is not re-checked. `@PreAuthorize("hasAuthority('POSITION_CREATE')")` enforces RBAC, not ABAC.
* **Exploit scenario:** Specialist user U has `POSITION_CREATE` and `ctx.projectIds = {P_A}`. They POST `/positions` with `projectId = P_B` (same tenant). Currently: 201 Created; the position lives in P_B. Expected: 404 (with `ACCESS_DENIED_BY_ABAC` audit).
* **Required fix:** add `abacGate.enforceCanWriteInProject(ctx, projectId)` at the top of every Create/Update/Archive use case for Project (already covered by tenant filter — single project), Department, Position. Reuse `ProjectMembershipPolicy`. For Archive/Update where the projectId is derived from the loaded entity, call the gate AFTER the entity is loaded (so a 404 hides existence).
* **Acceptance criteria:** Integration test `PositionCreateForeignProjectTest` for U_A (member of P_A only) attempting POST `/positions {projectId=P_B}` returns 404 and writes `ACCESS_DENIED_BY_ABAC` audit row.
* **Test case:** above + repeat for Department.
* **Owner:** backend-engineer.

### F-203

* **Finding:** Phase 2 tenant tables (`projects`, `departments`, `positions`) have no FK on `tenant_id → public.tenants(id)`.
* **Severity:** **Low** (Phase 2 — schema-per-tenant mode planned).
* **Affected area:** `tenant-schema/001..003-*.yaml`.
* **Risk:** A bug or malicious insert could place `tenant_id = <unknown UUID>`; subsequent tenant-scoped queries would silently miss the row, or it could collide with a real tenant if the UUID later exists. No immediate cross-tenant leak, but referential integrity is weaker than the blueprint §12 DB-1 mandate.
* **Required fix:** add `FOREIGN KEY (tenant_id) REFERENCES public.tenants(id)` to each table in shared mode. In schema-per-tenant mode the column may stay (defense in depth) but the FK is dropped since the foreign schema is unreachable.
* **Acceptance criteria:** Liquibase changeset adds the three FKs; integration test fails to insert a row with a non-existent tenant_id.
* **Test case:** `TenantFkConstraintTest`.
* **Owner:** database-architect.

### F-204

* **Finding:** Cross-FK consistency (`positions.project_id == departments.project_id`, `departments.project_id == projects(id where tenant_id == departments.tenant_id)`) is enforced only at the application layer.
* **Severity:** **Low** (defense in depth).
* **Affected area:** `tenant-schema/002, 003`.
* **Risk:** A direct DB write or a missed application path could insert a position into a department of a different project / tenant. Application is currently consistent, but the DB cannot fail-closed.
* **Required fix:** add a CHECK / trigger that rejects insert/update when `position.project_id <> (SELECT project_id FROM departments WHERE id = position.department_id)`. Alternatively, add a composite FK `(department_id, project_id) → departments(id, project_id)` after declaring `(id, project_id)` as a unique constraint on `departments`.
* **Acceptance criteria:** Native SQL probe attempting the cross-project insert fails.
* **Test case:** `CrossProjectReferenceTest` (DB integration test).
* **Owner:** database-architect.

### F-205

* **Finding:** `ConsultantTenantAssignmentPolicy` runs a DB `EXISTS` query on every ABAC evaluation.
* **Severity:** **Low**.
* **Affected area:** `access/application/ConsultantTenantAssignmentPolicy.java`.
* **Risk:** N+1 on tenant-membership lookup; under load, every consultant's read incurs an extra round-trip. No security impact, but DoS-adjacent on the membership table.
* **Required fix:** Cache per request (use Spring's `@RequestScope` bean) or short-TTL Caffeine (key = `(userId, tenantId)`, TTL ≤60s). Invalidate on `ROLE_ASSIGNED` / `ROLE_REVOKED` audit events when MVP 2 wires Redis.
* **Acceptance criteria:** Two reads in the same request execute one DB query, not two.
* **Test case:** `ConsultantTenantAssignmentPolicyCacheTest`.
* **Owner:** backend-engineer.

### F-206

* **Finding:** No DB-level multi-step cycle prevention on `departments.parent_id`.
* **Severity:** **Low** (covered by service layer).
* **Affected area:** `tenant-schema/002-create-organization.yaml`.
* **Risk:** A direct DB UPDATE (e.g. by a migration script or rogue operator) could introduce a cycle. The recursive CTE used by the tree builder would loop until OOM / timeout.
* **Required fix:** Either (a) add a TRIGGER that runs a recursive descendant check on UPDATE/INSERT, or (b) add a `LIMIT` clause to the application-layer `findDescendants` query so even a corrupt DB does not OOM the API.
* **Acceptance criteria:** Direct SQL `UPDATE departments SET parent_id = <descendant_id>` is rejected by trigger.
* **Test case:** `DepartmentCycleDbTest`.
* **Owner:** database-architect.

### F-207

* **Finding:** `TenantSchemaProvisioner` does not grant `grading_runtime` access to the newly created schema's objects.
* **Severity:** **Low (Phase 2)** → **High when schema-per-tenant ships**.
* **Affected area:** `tenancy/infrastructure/TenantSchemaProvisioner.java:53-80`.
* **Risk:** When a tenant is provisioned in `SCHEMA_PER_TENANT` mode (post-MVP), the runtime DB user will not have SELECT/INSERT/UPDATE/DELETE on the new schema and every business query will fail with `permission denied for relation projects`.
* **Required fix:** After `liquibase.update(...)`, execute `GRANT USAGE ON SCHEMA <tenant_schema> TO grading_runtime; GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA <tenant_schema> TO grading_runtime; ALTER DEFAULT PRIVILEGES IN SCHEMA <tenant_schema> GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO grading_runtime;`. The role-name must come from `TenancyProperties` (configurable), not from user input.
* **Acceptance criteria:** Integration test in schema-per-tenant mode reads/writes succeed for the runtime user after provisioning.
* **Test case:** `TenantSchemaProvisionerGrantTest`.
* **Owner:** devops-sre (provisioning Vault credentials) + database-architect (grant SQL) + backend-engineer (wire).

### F-208

* **Finding:** MSW mock handler accepts `tenant_id` from POST `/projects` request body; frontend `projectApi.ts` sends `tenantId` as query parameter on GET `/projects`.
* **Severity:** **Low** (dev-only; backend rejects both).
* **Affected area:** `frontend/src/shared/api/mocks/handlers.ts:79`; `frontend/src/features/projects/api/projectApi.ts:18-23`.
* **Risk:** Developers learn a wrong contract: the mock accepts body.tenant_id (line 79: `tenant_id: body.tenant_id ?? 'tenant-acme'`), which directly contradicts Security Blueprint API-13. They will be surprised when real backend ignores it, possibly papering over with hard-coded fallback values. The query param `?tenantId=...` from `fetchProjects` is silently ignored by the real backend but creates the impression that the client must specify a tenant.
* **Required fix:**
  1. In `handlers.ts:79` replace `body.tenant_id ?? 'tenant-acme'` with a server-side resolution from a (mock) auth context — e.g. `mockDb.activeTenantId` — and never honor body.tenant_id. Optionally throw 400 if body contains `tenant_id`.
  2. In `projectApi.ts:18-23` remove the `tenantId` query param; the JWT-driven backend computes the active tenant.
* **Acceptance criteria:** `mocks/handlers.test.ts` asserts that body.tenant_id is dropped/rejected; `useProjects` no longer threads tenantId through fetcher; PR adds a Vitest assertion that no axios request to `/projects` carries a `tenantId` param.
* **Test case:** `mocks.projectsRejectsTenantIdFromBody.test.ts`.
* **Owner:** frontend-engineer.

### F-209

* **Finding:** No DB-side guard against status downgrade from LOCKED → ACTIVE.
* **Severity:** **Low** (defense in depth).
* **Affected area:** `tenant-schema/001-create-projects.yaml`.
* **Risk:** A direct DB UPDATE by the runtime user (the role has UPDATE on the table) could un-lock a project. The audit would not capture it.
* **Required fix:** Either revoke UPDATE on the `status` column from `grading_runtime` and route all status transitions through an SQL function `set_project_status(id, new_status)` that enforces the state machine, OR add a row-level trigger that rejects transitions LOCKED→{DRAFT, ACTIVE}.
* **Acceptance criteria:** Direct `UPDATE projects SET status='ACTIVE' WHERE status='LOCKED'` returns "permission denied" or a trigger error.
* **Test case:** `ProjectStatusTransitionDbTest`.
* **Owner:** database-architect.

### F-210 (carries forward F-20)

* **Finding:** Logout does not invoke IdP `end_session_endpoint`.
* **Severity:** **Medium** (unchanged from Phase 0+1).
* **Affected area:** `frontend/src/features/auth/authStore.ts:38-46`, `UserMenu.tsx:51-56`.
* **Risk:** SPA-side logout clears the in-memory token but the Keycloak SSO cookie persists. Next `/login` silently re-authenticates without re-running MFA.
* **Required fix:** unchanged from Phase 0+1 F-20 — call IdP `end_session_endpoint` with `id_token_hint` and `post_logout_redirect_uri`.
* **Acceptance criteria:** After logout, navigating to a protected route requires re-credentialing.
* **Test case:** Playwright E2E (must accept that Keycloak is real for that test).
* **Owner:** frontend-engineer + devops-sre.

---

## 13. Top 20 risks — Phase 2 re-evaluation

| # | Risk | Phase 0+1 → Phase 2 status |
|---|------|-----------------------------|
| R-01 | Cross-tenant leak via missed tenant filter | **Mitigated** — all 3 new repos extend `TenantAwareRepository`; no bare findById |
| R-02 | BOLA/IDOR | **Mitigated** for reads; **partial** for writes — see F-202 |
| R-03 | Backend trusts `tenant_id` from frontend | **Mitigated** — DTOs verified; only MSW mock violates (F-208) |
| R-04 | JWT validation misconfiguration | **Mitigated** — F-02 closed |
| R-05 | Audit mutated/deleted | **Mitigated** — F-04 closed |
| R-06 | Salary primitives leak | **Foundation in place** — F-10/F-12/F-13 closed |
| R-07 | Secrets in Git | **Mitigated** |
| R-08 | Misconfigured CORS | **Mitigated** — F-16 closed |
| R-09 | Stack traces leak | **Mitigated** |
| R-10 | Privileged role without MFA | **Deferred** — no privileged endpoint beyond admin tenant create yet |
| R-11 | Worker tenant confusion | N/A — no workers |
| R-12 | Cache poisoning across tenants | N/A — no caches; F-205 raises future cache concern |
| R-13 | Approved methodology silently edited | N/A — no methodology; Project Lock equivalent **Mitigated** |
| R-14 | Audit retention | Deferred (devops-sre) |
| R-15 | Stale token | **Deferred** — F-03 still open |
| R-16 | XSS via stored fields | **Acceptable** for Phase 2 — React default escaping; rich text not yet shipped |
| R-17 | Mass assignment | **Mitigated** — record DTOs + selective setters |
| R-18 | Permissive CSP | **Deferred** — devops-sre |
| R-19 | Dependency CVEs | **Deferred** — devops-sre |
| R-20 | Formula injection in CSV/Excel | N/A — no exports |

Two new Phase-2 risks worth tracking:

* **R-21 — ABAC bypass on writes (F-202).** Same severity as R-01/R-02 in MVP 3.
* **R-22 — Tenant-wide admin role lockout (F-201).** Encourages insecure workarounds.

---

## 14. Release security gate decision

**Decision: SHIP with conditions.**

Phase 2 materially advances the security posture: the three new modules consistently use `TenantAwareRepository`, deny-by-default `@PreAuthorize`, JWT-only tenant context, 404-on-cross-tenant probing with audit, 409-on-locked-edits, and four composable ABAC policies that audit every deny. The eight conditional remediation findings from Phase 0+1 (F-02/04/05/06/10/12/13/16) are closed. F-01 (TenantAwareRepository) is exercised for the first time and complies. No hard cybersecurity rule has been violated:

* No `findById` on tenant data.
* No `tenant_id` in any business DTO/path/query (frontend client cleanup tracked in F-208 but backend rejects it).
* No JPA entity returned to controllers.
* Salary masking primitives intact.
* Audit append-only path intact.
* CORS explicit allowlist intact.

**Conditions before Phase 3 entry (non-negotiable):**

1. **F-202** — Add `abacGate.enforceCanWriteInProject(ctx, projectId)` on every Create/Update/Archive use case for Department and Position; ABAC test pack covers cross-project writes. *(Medium severity but writes are the next ABAC frontier and must close before methodology/evaluation ships.)*
2. **F-201** — Extend `ProjectMembershipPolicy` bypass to `CLIENT_COMPANY_ADMIN` and `CLIENT_HR_DIRECTOR`; add an 11-role × 3-scenario ABAC test.
3. **F-208** — MSW mock must reject `tenant_id` from request body; remove `?tenantId=` from `fetchProjects`.

**Strongly recommended before Phase 3:**

4. F-203 — FK on `tenant_id` to `public.tenants(id)` for the three new tables.
5. F-204 — Cross-FK consistency (positions/department + departments/project) at DB level.
6. F-207 — Schema-per-tenant grant block in `TenantSchemaProvisioner` (Critical when SCHEMA_PER_TENANT mode is turned on).
7. F-205 — Per-request cache for `ConsultantTenantAssignmentPolicy`.
8. F-206 — DB-level cycle prevention + recursive CTE `LIMIT`.
9. F-209 — Status-transition trigger or column-grant revoke.
10. F-210 (F-20 carry-forward) — Logout → IdP `end_session_endpoint`.

**Acceptable to defer past Phase 3 but tracked:** F-03, F-07, F-08, F-09, F-11, F-14, F-15, F-17, F-18, F-19, F-21.

---

## 15. Action items per agent

### backend-engineer (3 critical → must close before Phase 3)

* **F-202** — Invoke `AbacGate` from every write use case (Project/Department/Position). New helper `enforceCanWriteInProject(ctx, projectId)` reusing `ProjectMembershipPolicy` + a write-only flag if needed.
* **F-201** — Add `CLIENT_COMPANY_ADMIN` and `CLIENT_HR_DIRECTOR` to `ProjectMembershipPolicy` bypass set.
* **F-205** — Per-request cache on `ConsultantTenantAssignmentPolicy`.

### frontend-engineer

* **F-208** — Stop sending `tenant_id` / `tenantId` to `/projects`; mock handler must reject body.tenant_id.
* **F-210** (F-20) — Wire `end_session_endpoint` on signOut.

### database-architect

* **F-203** — FK on `tenant_id` for the three new tables.
* **F-204** — Cross-FK consistency check for positions/departments and departments/projects.
* **F-206** — Recursive cycle trigger on `departments`.
* **F-207** — Schema-per-tenant grant block (paired with devops-sre).
* **F-209** — Project status downgrade trigger.

### devops-sre

* F-207 — Provide `grading_runtime` role name via config; verify grants after each new tenant in staging.
* F-15 / F-17 / F-21 — ingress rate limit, security headers, frontend bundle scan (carried from Phase 0+1).

### qa-engineer

* Stand up the integration test base class issuing T_A and T_B users and replay scenarios TI-02..TI-06 against the new endpoints.
* Implement the ABAC unit-test pack (4 policies × 11 roles × {PERMIT, DENY, NOT_APPLICABLE}).
* Add `mocks.projectsRejectsTenantIdFromBody.test.ts` for the frontend.
* Verify F-04 grant matrix in staging (CI assertion).
* Tenant Isolation Pack TI-02..TI-06 for Project/Department/Position.

### hr-product-owner

* Update Department/Position user-story AC templates to include "ABAC denies cross-project write attempts with 404 + `ACCESS_DENIED_BY_ABAC` audit row" once F-202 lands.
* Add a story for "tenant-wide admin can list every project in their tenant without explicit project membership" (covers F-201 from the user-facing side).

---

— end of report —

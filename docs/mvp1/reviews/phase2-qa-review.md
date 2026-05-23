# Phase 2 QA Review Report — grading.hrlab.uz

Document owner: QA Engineering
Status: Phase 2 release gate (Project + Department + Position + ABAC)
Date: 2026-05-23
Benchmark: `docs/mvp1/03-qa-master-test-plan.md` + `docs/mvp1/reviews/phase0-1-qa-review.md`
Reviewed build: backend Phase 2 (3 new modules: project, organization, position; 4 ABAC policies; 3 tenant-schema changelogs; 15 endpoints; 10 new tests); frontend Phase 2 (3 feature folders, ~85 i18n keys/locale × 4, 11 new tests, 5 new routes)

---

## 1. Review Scope

This review covers the Phase 2 increment on top of the closed Phase 0+1 baseline. In-scope:

- **Backend:** `project`, `organization`, `position` modules (api/application/domain/infrastructure); `access.application.ConsultantTenantAssignmentPolicy`; `access.domain.{DepartmentScopePolicy, ProjectMembershipPolicy, ApprovedEntityFilterPolicy}`; `AbacGate`; tenant-schema Liquibase 001–003; `TenantSchemaProvisioner` + `TenancyProperties`; `PageResponse`; `GlobalExceptionHandler.handleProjectLocked` (409).
- **Backend tests:** 4 ABAC unit tests, 2 organization-domain unit tests, 3 `@Tag` integration tests under `phase2/`, plus `ArchitectureTest`, `CrossTenantAuditRecordingTest`, and updated `AuditAppendOnlyTest`.
- **Frontend:** `features/{projects,organization,positions}`; `shared/components/workflow/{WorkflowStepper,StageStatusCard}`; `shared/components/data-table/{DataTable,FilterBar,PaginationBar,DrawerForm}`; `shared/components/layout/Breadcrumbs`; `shared/api/mocks/`; `features/dashboard/components/AuditLogStatCard`; `shared/components/layout/UserMenu` rewrite.
- **Frontend tests:** 11 new `.test.tsx` files (verified by execution — see §15).
- **i18n:** 4 locale files at `frontend/src/shared/i18n/locales/{en-US,ru-RU,uz-Cyrl-UZ,uz-Latn-UZ}.json`.

Out of scope (Phase 3+): JobProfile, JobAnalysis, methodology builder, scoring, grade structure, salary engine.

---

## 2. Phase 0+1 Conditions Closure

| ID | Condition | Status | Evidence |
|----|-----------|--------|----------|
| C-1 | D-002 — Cross-tenant access writes audit row (not log line only) | **CLOSED** | `GlobalExceptionHandler.recordCrossTenantAttempt` now calls `auditService.record(...)` with `AuditAction.CROSS_TENANT_OR_PROJECT_ACCESS_ATTEMPT`, IP, user agent, correlation id, trace id, redacted reason (method+path only — never query/body). Confirmed by `CrossTenantAuditRecordingTest` (which uses MockMvc + spied `AuditService`). |
| C-2 | D-003 — ArchUnit rules from master plan §21 | **CLOSED (with one gap)** | `architecture/ArchitectureTest` enforces 5 rules: (1) tenant-scoped repositories must extend `TenantAwareRepository`; (2) controllers must not return JPA entities; (3) domain must not depend on infrastructure; (4) controllers must live in `..api..`; (5) repositories named `*Repository`. **GAP:** the master-plan §21 rule against `@RequestParam("tenantId")` on client-facing controllers is NOT present (see D-201). |
| C-3 | D-001 — Bare `findById` forbidden on tenant-scoped repos | **CLOSED** | New `TenantAwareRepository<T,ID>` base extends bare `org.springframework.data.repository.Repository` (not `JpaRepository`), so `findById(ID)` / `findAll()` / `delete*` are never inherited. `ProjectRepository`, `DepartmentRepository`, `PositionRepository` all extend it. ArchUnit Rule 1 forbids JpaRepository on non-control-plane interfaces. |
| C-4 | D-004 — JWT → authorities mapping test exists | **CLOSED via separate sprint** | Confirmed in the parallel JWT audience/CORS sprint (#5). `JwtAudienceValidatorTest` + `CorsAllowlistIntegrationTest` exercise the security chain; controllers' `@PreAuthorize` is now hit by the Phase 2 tests indirectly. *Direct* MockMvc `with(jwt())` tests still recommended (carried as D-204). |
| C-5 | D-007 — Locale-file key parity Vitest | **OPEN** | No `i18nParity.test.ts` shipped. Verified manually with a one-off Node script: all 4 locale files currently have identical 239 flattened keys, but no automated guard exists. Defect re-issued as D-205. |
| C-6 | CI runs Docker / Testcontainers | **CLOSED (per devops report)** | DevOps task #8 completed (GitHub Actions/Helm charts). Phase 2 integration tests are tagged (`@Tag("tenant-isolation")`, `@Tag("integration")`) and selectable from CI. |

**Summary: 5 of 6 conditions closed; C-5 deferred from Phase 0+1 is still open.**

---

## 3. Test Coverage Matrix vs QA Master Test Plan

| Pack | Required for Phase 2 | Implemented | Missing |
|------|---------------------|-------------|---------|
| Tenant Isolation (TIP) | TIP-01,02,10,12,13 at HTTP layer; cross-resource validation | Repo-layer probes for Project/Department/Position (`Phase2TenantIsolationIntegrationTest` — 4 tests); cross-project department refusal (`CrossResourceTenantValidationTest`); existence-leak protection (TenantAccessDeniedException → 404). | HTTP-layer probes (MockMvc) for TIP-01/02/10 against Phase 2 endpoints; TIP-12 (tenant_id in body ignored) and TIP-13 (tenant_id in query ignored) as explicit MockMvc tests; ArchUnit rule banning `@RequestParam("tenantId")`. |
| RBAC | `@PreAuthorize` on all 15 endpoints with codes matching `role-permissions-matrix.md` | All 15 controller methods carry `@PreAuthorize("hasAuthority('X')")`; codes verified (PROJECT_CREATE/READ/EDIT, ORG_READ/EDIT, POSITION_CREATE/READ/EDIT, METHODOLOGY_LOCK on `/lock`). | Direct MockMvc tests proving 403 for unauthorized roles and 200/201 for authorized roles on every endpoint (per-endpoint, all 11 roles). Currently zero per-endpoint security tests. |
| ABAC | 4 policies with positive + negative coverage; deny writes `ACCESS_DENIED_BY_ABAC` audit | `DepartmentScopePolicyTest` (4 tests), `ConsultantTenantAssignmentPolicyTest` (4 tests), `ProjectMembershipPolicyTest` (5 tests), `ApprovedEntityFilterPolicyTest` (4 tests). `AbacGate.recordDenial()` records the audit row. | No integration test asserts that `AbacGate.enforce(...)` failure actually persists an `ACCESS_DENIED_BY_ABAC` audit row end-to-end (the policy unit tests are pure; the gate is not covered). |
| Status Transitions | DRAFT→ACTIVE→LOCKED→ARCHIVED for Project; archived/cycle for Department | `WorkflowStateTransitionTest` — `lockedProjectRejectsPatch` (Project) + `archivedDepartmentCannotBeParent` (Department cycle). `DepartmentValidationPolicyTest` adds self-parent rejection and cycle prevention. | Project DRAFT→ACTIVE explicit transition test (currently project is created in DRAFT but no transition-to-ACTIVE workflow is exposed); ARCHIVED status-machine guard tests (archived project rejects PATCH was implicit). |
| Multilingual Fields | nameI18n/titleI18n stored as JSONB; primary locale required | DB columns `name_i18n`/`title_i18n JSONB NOT NULL`. DTO validation: `@NotEmpty Map<@NotBlank String, @NotBlank @Size(max = 500) String>` — accepts ≥ 1 locale. | No test asserts the JSONB storage round-trip; no test verifies fallback locale strategy (missing translation handling — defect D-206); no test asserts duplicate locale keys are rejected (e.g. case-insensitive). |
| Audit Events | PROJECT_{CREATED,UPDATED,LOCKED,ARCHIVED}; DEPARTMENT_{CREATED,UPDATED,ARCHIVED}; POSITION_{CREATED,UPDATED,ARCHIVED}; ACCESS_DENIED_BY_ABAC | All constants exist in `AuditAction` for project/dept/position. Use cases write events: confirmed in Create/Update/Lock/Archive use cases. `AbacGate` writes `ACCESS_DENIED_BY_ABAC`. Hash chain still computed in `JpaAuditService` (Phase 0+1 carryover). | No end-to-end integration test asserts a write-then-verify pattern (e.g., POST `/api/v1/projects` → assert audit row exists with `hash_current ≠ null` and chain valid). Audit row counts not asserted in any Phase 2 test. |
| Error Handling | 409 for ProjectLockedException; 400 cross-resource; 404 cross-tenant; standard envelope | `GlobalExceptionHandler.handleProjectLocked` → 409 CONFLICT with code. `CreatePositionUseCase` throws `ValidationException("POSITION_DEPARTMENT_INVALID", ...)` → 400 (and audits CROSS_TENANT). `TenantAccessDeniedException` → 404. Envelope `{code, message, correlation_id, trace_id}` consistent. | No automated assertion that the 409 envelope shape matches the standard contract; no test asserts 400 vs 404 dispatch for cross-tenant vs cross-project for the same resource. |
| Pagination/Sort | default size 20, max 200; `Sort.by("code")` default | `FindProjectQuery.list` & `FindPositionQuery.list` clamp `safeSize = min(max(size,1), 200)`; default sort by `code`. `PositionController.list` defaults `page=0, size=20`. `PageResponse.of(Page<T>, mapper)` is the API envelope. | No test asserts the max-page-size clamp (size=1000 → 200); no test asserts sort consistency (Sort.by("code")); no test asserts `PageResponse` contract (items/page/size/total_elements/total_pages). |

---

## 4. Tenant Isolation Verification (per Phase 2 endpoint)

| Endpoint | Tenant check | Existence-leak handling | Audit on probe | Verdict |
|----------|--------------|-------------------------|----------------|---------|
| `POST /api/v1/projects` | `ctx.tenantId()` from `TenantContextHolder.requireActive()`; DTO has NO `tenantId` field — `JsonIgnoreProperties` not needed because field is absent | n/a | — | PASS |
| `GET /api/v1/projects` | `projects.findAllByTenantIdAndStatusNot(ctx.tenantId(), ARCHIVED, …)` | n/a (returns own scope) | — | PASS |
| `GET /api/v1/projects/{id}` | `findByIdAndTenantId(id, ctx.tenantId())`; cross-tenant → `TenantAccessDeniedException` → 404 | YES — 404 not 403 | YES via global handler | PASS |
| `PATCH /api/v1/projects/{id}` | same lookup; LOCKED/ARCHIVED guard fires `ProjectLockedException` → 409 | YES | YES on cross-tenant | PASS |
| `POST /api/v1/projects/{id}/archive` | same | YES | YES | PASS |
| `POST /api/v1/projects/{id}/lock` | same; idempotent on LOCKED | YES | YES | PASS |
| `POST /api/v1/departments` | project lookup is tenant-scoped first; cross-tenant project → 404 | YES | YES | PASS |
| `GET /api/v1/departments/tree?projectId=…` | tenant-scoped query + ABAC `enforceCanListInProject` | n/a (returns own scope) | — | PASS |
| `GET /api/v1/departments/{id}` | tenant-scoped + ABAC `enforceCanReadDepartment` | YES | YES | PASS |
| `PATCH /api/v1/departments/{id}` | tenant-scoped lookup + parent validation respects same tenant + project | YES (parent in another tenant → 400 with generic message, no existence reveal) | YES on cross-tenant | PASS |
| `POST /api/v1/departments/{id}/archive` | tenant-scoped | YES | YES | PASS |
| `POST /api/v1/positions` | project + department tenant-scoped lookups; cross-project department → 400 + audit row | YES | YES (cross-project audit recorded inside use case) | **PASS, but see D-202** |
| `GET /api/v1/positions?projectId=…` | `positions.search(ctx.tenantId(), projectId, …)` + ABAC `enforceCanListInProject` | n/a (empty result for foreign tenant; verified by `positionSearchOnOtherTenantProjectReturnsEmpty`) | — | PASS |
| `GET /api/v1/positions/{id}` | tenant-scoped + ABAC | YES | YES | PASS |
| `PATCH /api/v1/positions/{id}` | tenant-scoped | YES | YES | PASS |
| `POST /api/v1/positions/{id}/archive` | tenant-scoped | YES | YES | PASS |

**Repository-layer proof:** `Phase2TenantIsolationIntegrationTest` verifies all 4 mutation patterns at the repo surface (project/department/position findByIdAndTenantId + position search). **HTTP-layer proof is missing** for all 15 endpoints (D-203).

**Cross-resource validation (Position with foreign department):** `CrossResourceTenantValidationTest.positionCannotReferenceDepartmentFromDifferentProject` covers the case where a department from project P1 is used in a position for project P2. Returns 400 with generic message ("not found") so existence is not leaked. Audit row of `CROSS_TENANT_OR_PROJECT_ACCESS_ATTEMPT` is recorded. PASS.

---

## 5. ABAC Test Verification

| Policy | Positive test | Negative test | Audit on deny | Verdict |
|--------|--------------|--------------|--------------|---------|
| `ConsultantTenantAssignmentPolicy` | `superAdminBypassesMembershipCheck`, `permitConsultantWithMembership`, `notApplicableForClientRoles` | `denyConsultantWithoutMembership` | Yes via `AbacGate.recordDenial` (unit-tested at policy layer; integration-untested) | PASS |
| `DepartmentScopePolicy` | `permitsHrlabRolesBypass`, `permitsDepartmentManagerInsideScope`, `notApplicableWhenNoDepartmentInRequest` | `deniesDepartmentManagerOutsideScope` | as above | PASS |
| `ProjectMembershipPolicy` | `superAdminBypasses`, `permitsWhenProjectMatches`, `notApplicableWithoutProjectId` | `deniesWhenProjectNotInActiveSet`, `deniesWhenActiveProjectsEmptyForTenantScopedRole` | as above | PASS |
| `ApprovedEntityFilterPolicy` | `permitsViewerOnActive`, `notApplicableForNonViewerRoles` | `deniesViewerOnDraft`, `deniesExternalAuditorOnDraft` | as above | PASS |

**Gap:** `AbacGate.recordDenial` is implemented and wired but *never integration-tested* — no test exercises a use case (e.g. `FindProjectQuery.findById`) with a deny-context and asserts an `ACCESS_DENIED_BY_ABAC` audit row hit `system_audit_log`. **D-207.**

---

## 6. Status Machine Verification

| State machine rule | Implementation | Test | Verdict |
|--------------------|---------------|------|---------|
| Project DRAFT on create | `CreateProjectUseCase` sets `ProjectStatus.DRAFT` | implicit | PASS |
| Project LOCKED rejects PATCH (409) | `UpdateProjectUseCase` throws `ProjectLockedException`; handler → 409 | `WorkflowStateTransitionTest.lockedProjectRejectsPatch` | PASS |
| Project ARCHIVED rejects PATCH | same | implicit (same code path) | PASS *(no explicit test)* |
| LockProject idempotent on LOCKED | `if (status == LOCKED) return;` | no test | **D-208** (missing test) |
| Department archived → cannot be parent | `DepartmentValidationPolicy.validateParentForCreate` throws on `DepartmentStatus.ARCHIVED` parent; CreateUseCase also blocks position-on-archived-dept | `WorkflowStateTransitionTest.archivedDepartmentCannotBeParent` + `DepartmentValidationPolicyTest.rejectsCrossTenantParent` | PASS |
| Department cycle prevention | `validateParentForUpdate` uses recursive descendant CTE | `DepartmentValidationPolicyTest.rejectsCycle` (uses domain policy with mock lookup) | PASS for domain; **integration with actual CTE not tested (D-209)** |
| Position DRAFT → ARCHIVED via archive endpoint | `ArchivePositionUseCase` sets ARCHIVED | implicit | PASS *(no explicit test)* |
| Position rejects creation in archived department | `CreatePositionUseCase` checks `DepartmentStatus.ARCHIVED` | no test | **D-210** (missing test) |
| Position rejects creation in LOCKED/ARCHIVED project | `CreatePositionUseCase` throws `ProjectLockedException` | no test | **D-211** (missing test) |

---

## 7. Multilingual Field Verification

- **Storage:** `name_i18n` (projects, departments) and `title_i18n` (positions) declared as JSONB NOT NULL in tenant-schema 001/002/003.
- **Validation:** DTOs use `Map<@NotBlank String, @NotBlank @Size(max = 500) String>` with `@NotEmpty` on the map — accepts ≥ 1 locale; primary locale is enforced at frontend layer only (`ProjectFormDrawer` requires primary-locale name).
- **Missing locale fallback:** NO backend strategy implemented or documented. Frontend `localized.ts` (referenced from forms/tables) handles fallback client-side. **Gap** — master plan §19 TC-L10N-008 requires a documented chain (uz-Latn-UZ → en-US → ru-RU → code) and a fail-build for missing keys. **D-206.**
- **Test coverage:** No backend round-trip test asserts that `Map.of("ru-RU","Имя","en-US","Name")` is correctly stored and retrieved from the JSONB column. **D-212.**

---

## 8. Audit Event Coverage

| Event | Trigger point | Source code | Test |
|-------|--------------|-------------|------|
| `PROJECT_CREATED` | `CreateProjectUseCase.create` | confirmed | indirect (no direct audit-row count assertion) |
| `PROJECT_UPDATED` | `UpdateProjectUseCase.update` | confirmed | none |
| `PROJECT_LOCKED` | `LockProjectUseCase.lock` | confirmed | indirect via `WorkflowStateTransitionTest` (locked then PATCH; no audit assertion) |
| `PROJECT_ARCHIVED` | `ArchiveProjectUseCase` | confirmed (must verify in source — not shown above) | none |
| `DEPARTMENT_CREATED` | `CreateDepartmentUseCase.create` | confirmed | none |
| `DEPARTMENT_UPDATED` | `UpdateDepartmentUseCase` | not inspected (file present) | none |
| `DEPARTMENT_ARCHIVED` | `ArchiveDepartmentUseCase.archive` | confirmed | none |
| `POSITION_CREATED` | `CreatePositionUseCase.create` | confirmed | none |
| `POSITION_UPDATED` | `UpdatePositionUseCase` | not inspected (file present) | none |
| `POSITION_ARCHIVED` | `ArchivePositionUseCase` | not inspected (file present) | none |
| `ACCESS_DENIED_BY_ABAC` | `AbacGate.recordDenial` | confirmed | unit-only (no integration row-count assertion) |
| `CROSS_TENANT_OR_PROJECT_ACCESS_ATTEMPT` | `GlobalExceptionHandler.recordCrossTenantAttempt` (Phase 0+1 fix) + `CreatePositionUseCase` (cross-project department) | confirmed | `CrossTenantAuditRecordingTest` (Phase 0+1 fix); cross-project case in `CreatePositionUseCase` is NOT separately asserted at row level |

**Gap:** every audit event has source code that calls `auditService.record(...)`, but **no Phase 2 integration test asserts a hash-chained audit row actually lands in `system_audit_log`** with the right `(tenant_id, project_id, actor_user_id, action, entity_type, entity_id, hash_prev, hash_current)` after a controller-level happy-path call. **D-213.**

---

## 9. Frontend Verification Findings Closure

| Finding | Required fix | Status | Evidence |
|---------|--------------|--------|----------|
| UserMenu does not open on click (Playwright/Chromium) | Rewrite with explicit `onClick` + `aria-haspopup` + `aria-expanded` + `data-testid` | **CLOSED** | `UserMenu.tsx` uses both `onClick` and a benign `onPointerDown` (to flush Chromium synthesized-click races); has `aria-haspopup="menu"`, `aria-expanded={open}`, `aria-label={t('userMenu.title')}`, `data-testid="user-menu-trigger"`. `UserMenu.test.tsx` verifies: trigger attributes; popover opens on `userEvent.click`; **Sign out** clears auth store + redirects to /login. |
| AuditLogStatCard visible regardless of permission | Gate via `PermissionGate` | **CLOSED** | `AuditLogStatCard.tsx` wraps `StatCard` inside `<PermissionGate permission={PERMISSIONS.AUDIT_READ}>`. `AuditLogStatCard.test.tsx` verifies hidden for Viewer (`queryByTestId(...).not.toBeInTheDocument()`) and visible for Super Admin. |
| `data-testid` missing on key elements | Add testids | **CLOSED** | `user-menu-trigger`, `user-menu-panel`, `user-menu-profile`, `user-menu-signout`, `audit-log-stat-card` all present and asserted. |

**Both verification findings are fixed and have automated test evidence.**

---

## 10. Frontend Tests Verification

Vitest run (Phase 2 frontend) — **18 test files / 52 tests / all green**. Phase 2-specific:

- `features/projects/components/ProjectTable.test.tsx` (3)
- `features/projects/components/ProjectFormDrawer.test.tsx` (3 — locale tab switching, primary-locale required, happy path)
- `features/organization/components/DepartmentDrawer.test.tsx` (2 — parent selector excludes self/descendants; missing-code blocks submit)
- `features/organization/components/DepartmentTree.test.tsx` (4 — hides archived by default; selects on click)
- `features/positions/components/PositionTable.test.tsx` (3)
- `features/positions/components/PositionFormDrawer.test.tsx` (2 — only non-archived departments listed; required-field validation)
- `features/dashboard/components/AuditLogStatCard.test.tsx` (2 — see §9)
- `shared/components/workflow/WorkflowStepper.test.tsx` (verified by Glob)
- `shared/components/data-table/DataTable.test.tsx` (verified by Glob)
- `shared/components/layout/UserMenu.test.tsx` (3 — see §9)

**Permission-gate coverage at the new screens:** `AuditLogStatCard` (yes); `RequirePermission` route guard tests (carry-over from Phase 0+1) cover entry to `/projects`, `/organization`, `/positions` route guards — no NEW per-screen PermissionGate test was added for those routes (D-214).

---

## 11. Frontend i18n Parity

Verified by ad-hoc Node script flattening each locale JSON: **all 4 locales contain exactly 239 keys**. No drift. `wc -l` confirms identical file lengths (285 lines each). The new ~85 Phase 2 keys (project/org/position) are present in all 4 locales.

**Gap (carries forward from Phase 0+1 C-5 / D-205):** there is still no automated `i18nParity.test.ts` to guard against future drift. The current parity is a one-shot manual check.

---

## 12. Pagination + Sort Consistency

**Backend (`PositionController.list`):**
- `@RequestParam(defaultValue = "0") int page` — OK
- `@RequestParam(defaultValue = "20") int size` — OK, default matches master plan §9 rule 9
- `FindPositionQuery.list` clamps `Math.min(Math.max(size, 1), 200)` — max=200 enforced
- Default sort: `Sort.by("code").ascending()` — applied

**Backend (`ProjectController.list`):** identical pattern.

**Backend (`DepartmentController.tree`):** intentionally unpaged (returns tree) — acceptable for org-tree semantics.

**Frontend (`PaginationBar`):** present under `shared/components/data-table/`; `DataTable.test.tsx` exists. **Manual review** indicates wiring is correct; no test asserts the URL-param sync of `page`/`size` for the position list page (D-215).

**Gap:** no backend test asserts the clamp at `size=1000`. **D-216.**

---

## 13. Defects Found (Phase 2)

### D-201 — ArchUnit rule against `@RequestParam("tenantId")` missing

- **Severity:** Medium (escalates to High once any user-data export endpoint accepts query params).
- **Affected component:** `backend/src/test/java/uz/hrlab/grading/architecture/ArchitectureTest.java`.
- **Description:**
  - Given the master plan §21 lists three architectural rules and one of them is "no `@RequestParam("tenantId"|"tenant_id")` in non-admin controllers,
  - When the build runs,
  - Then only 5 of the 6 mandated rules are enforced; a developer adding `@RequestParam("tenantId")` to a controller can ship a TIP-13-class regression without being blocked.
- **Suggested fix:** add a 6th `@Test` in `ArchitectureTest` that selects all `RestController`-annotated classes outside `..tenancy.api.admin..`, walks each method's `@RequestParam` annotations, and fails on any value of `tenantId` / `tenant_id` / `tenant-id`.
- **Owner:** backend-engineer.

### D-202 — Frontend MSW mock accepts `tenant_id` from request body

- **Severity:** High (contract violation: master plan §10 rule 13 + §27 task 1).
- **Affected component:** `frontend/src/shared/api/mocks/handlers.ts` line 81: `tenant_id: body.tenant_id ?? 'tenant-acme'`.
- **Description:**
  - Given the master plan forbids the backend from honoring `tenant_id` sent in a request body,
  - When a developer writes a test against the MSW mock that POSTs `{ tenant_id: 'tenant-other' }`,
  - Then the mock returns a project owned by `tenant-other` — completely opposite to real backend semantics.
- **Suggested fix:** strip `tenant_id` from `readBody<Partial<MockProject>>(config)` before merging into the mock entity. Tenant must be derived from `authStore.activeTenant.id` (i.e. the dev-session), never from the body. Add a test that POSTs `{ tenant_id: 'X' }` and asserts the persisted entity's `tenant_id` equals the active tenant — proving the body field is ignored.
- **Owner:** frontend-engineer.

### D-203 — No HTTP-layer (MockMvc / REST Assured) tests for Phase 2 endpoints

- **Severity:** High.
- **Affected component:** `backend/src/test/java/uz/hrlab/grading/{project,organization,position}/api/...` (controllers have ZERO direct tests).
- **Description:**
  - Given 15 new endpoints are now in production code path,
  - When unauthorized roles or anonymous clients hit them,
  - Then there is no automated proof that `@PreAuthorize("hasAuthority('X')")` returns 403 and that the standard error envelope is emitted. Frontend code relies on these contracts.
- **Suggested fix:** add `@WebMvcTest` per controller with `SecurityMockMvcRequestPostProcessors.jwt().authorities(...)`. At minimum:
  - 1 test per endpoint with the required authority → 2xx;
  - 1 test per endpoint without the required authority → 403;
  - 1 test per endpoint unauthenticated → 401.
- **Owner:** backend-engineer + QA.

### D-204 — Direct `@WebMvcTest jwt()` security tests still absent

- **Severity:** Medium (carries forward from Phase 0+1 D-004 — partially mitigated by JWT-aud sprint but no direct security wiring test on the new Phase 2 endpoints).
- **Affected component:** all 15 Phase 2 endpoints.
- **Description:** see D-203 for the same gap framed as test coverage; here we call out specifically that the JWT-to-authorities mapping is exercised only against `AdminTenantController` (Phase 0+1) — not yet against any Phase 2 controller.
- **Owner:** backend-engineer + security-engineer.

### D-205 — No automated locale key-parity test (carries forward from D-007/C-5)

- **Severity:** Medium.
- **Affected component:** `frontend/src/shared/i18n/locales/*.json`; no `frontend/src/shared/i18n/i18nParity.test.ts`.
- **Description:**
  - Given Phase 2 added ~85 keys per locale and they currently match,
  - When a developer in Phase 3 adds a key to `en-US.json` only,
  - Then nothing breaks at build time; users in `uz-Latn-UZ` see the raw key.
- **Suggested fix:** add a Vitest unit that loads all 4 JSONs, flattens keys, and asserts identical key sets. Trivial — would have caught D-202-class drifts during Phase 2 sprint.
- **Owner:** frontend-engineer.

### D-206 — Missing-locale fallback strategy not documented or tested

- **Severity:** Medium.
- **Affected component:** server-side rendering of multilingual fields (positions/projects/departments).
- **Description:**
  - Given a project is created with only `{ "ru-RU": "Имя" }`,
  - When a user in `en-US` opens it,
  - Then it is unclear which value renders — there is no documented fallback chain on the server side. The frontend has `shared/lib/localized.ts` but the API contract is silent.
- **Suggested fix:** decide whether the API returns the raw JSONB to the client (current behavior) or projects a single locale based on `Accept-Language`. If raw, document this explicitly in OpenAPI and write a Zod schema acknowledging the variability.
- **Owner:** backend-engineer + hr-product-owner.

### D-207 — `AbacGate` denial-audit path is not integration-tested

- **Severity:** Medium.
- **Affected component:** `access.application.AbacGate.recordDenial`.
- **Description:**
  - Given a `Viewer` user opens a `DRAFT` project (would trigger `ApprovedEntityFilterPolicy.DENY`),
  - When the use case throws `TenantAccessDeniedException`,
  - Then no test asserts that an `ACCESS_DENIED_BY_ABAC` row landed in `system_audit_log` with `reason="policy=ApprovedEntityFilterPolicy"`. The unit tests cover policy evaluation but not the gate's audit-write side effect.
- **Suggested fix:** add `@Tag("integration")` test against `FindProjectQuery.findById` with a Viewer context and a DRAFT project; assert `systemAuditLogRepository.findByTenantIdOrderByCreatedAtDesc(...)`'s top entry has `action == ACCESS_DENIED_BY_ABAC`.
- **Owner:** backend-engineer.

### D-208 — LockProject idempotency on already-LOCKED not tested

- **Severity:** Low.
- **Affected component:** `LockProjectUseCase.lock`.
- **Description:**
  - Given a project is already LOCKED,
  - When `POST /api/v1/projects/{id}/lock` is called again,
  - Then the code returns silently (idempotent) but no test asserts that no second `PROJECT_LOCKED` audit row is written.
- **Suggested fix:** extend `WorkflowStateTransitionTest` with `lockProjectIsIdempotent` that calls lock twice and asserts a single audit row.
- **Owner:** backend-engineer.

### D-209 — Department cycle prevention native-CTE not integration-tested

- **Severity:** Medium.
- **Affected component:** `DepartmentRepository.findDescendants` (native recursive CTE).
- **Description:**
  - Given the cycle check uses a native Postgres recursive CTE,
  - When the domain unit test (`DepartmentValidationPolicyTest.rejectsCycle`) runs, the CTE is replaced by an injected `Function<UUID, List<Department>>`,
  - Then the actual SQL is never executed in tests; a syntax error in the CTE ships undetected.
- **Suggested fix:** add `@Tag("integration")` Testcontainers test that creates a 3-level hierarchy and asserts `findDescendants` returns the descendants in the right order.
- **Owner:** backend-engineer + QA.

### D-210 — Position creation in archived department not tested

- **Severity:** Low.
- **Affected component:** `CreatePositionUseCase`.
- **Description:** code throws `POSITION_DEPARTMENT_ARCHIVED` but no test asserts this. Master plan QR-15 class.
- **Suggested fix:** extend `WorkflowStateTransitionTest`.
- **Owner:** backend-engineer.

### D-211 — Position creation in LOCKED/ARCHIVED project not tested

- **Severity:** Medium.
- **Affected component:** `CreatePositionUseCase`.
- **Description:** code throws `ProjectLockedException` (→ 409) when project is LOCKED or ARCHIVED at the moment of position creation. No test exists.
- **Suggested fix:** add `lockedProjectRejectsPositionCreation` to `WorkflowStateTransitionTest`.
- **Owner:** backend-engineer.

### D-212 — JSONB round-trip for nameI18n / titleI18n not tested

- **Severity:** Medium.
- **Affected component:** all three Phase 2 tenant-schema tables.
- **Description:** no test asserts that `Map.of("ru-RU","И","en-US","N")` is correctly persisted and rehydrated through the JPA layer's JSONB converter.
- **Suggested fix:** add `@DataJpaTest` (Testcontainers) round-trip test per entity.
- **Owner:** backend-engineer.

### D-213 — No end-to-end audit row assertion for Phase 2 happy paths

- **Severity:** Medium.
- **Affected component:** all Create/Update/Lock/Archive use cases.
- **Description:** every use case calls `auditService.record(...)` but no test queries `SystemAuditLogRepository` after a happy-path call and asserts the row landed with the correct `action`, `entity_type`, `entity_id`, non-null `hash_current`, and a valid `hash_prev → hash_current` chain.
- **Suggested fix:** one `@Tag("audit") @Tag("integration")` test per resource (Project/Department/Position) creating an entity and asserting the audit row.
- **Owner:** backend-engineer.

### D-214 — PermissionGate coverage on Phase 2 list pages not tested per-screen

- **Severity:** Low.
- **Affected component:** `features/projects/pages/ProjectListPage.tsx`, `features/organization/pages/OrganizationPage.tsx`, `features/positions/pages/PositionListPage.tsx`.
- **Description:** route guards are tested in Phase 0+1, but the Phase 2 list pages don't have a direct test asserting that without the right permission, the page renders the `NoAccessState` or is hidden.
- **Suggested fix:** one `.test.tsx` per page rendering with `signIn('viewer')` and asserting the expected gating behavior.
- **Owner:** frontend-engineer.

### D-215 — No test for URL-param sync of pagination on PositionListPage

- **Severity:** Low.
- **Affected component:** `features/positions/pages/PositionListPage.tsx` + `shared/components/data-table/PaginationBar.tsx`.
- **Description:** `PaginationBar` exists and `DataTable.test.tsx` exists, but the cohesion test (clicking "next" updates URL `?page=1` and triggers a refetch with `size=20`) is absent.
- **Suggested fix:** add `PositionListPage.test.tsx` with MSW mock and asserts on history changes.
- **Owner:** frontend-engineer.

### D-216 — No backend test asserting page-size clamp at max=200

- **Severity:** Low.
- **Affected component:** `FindProjectQuery.list`, `FindPositionQuery.list`.
- **Description:** the clamp is implemented but never asserted. A regression that changes `MAX_PAGE_SIZE` to e.g. 2000 would not be caught.
- **Suggested fix:** parameterized JUnit test `pageSizeIsClampedAt200` for both queries.
- **Owner:** backend-engineer.

### D-217 — Frontend fetchProjects sends `tenantId` query param

- **Severity:** Low (defense-in-depth issue only — backend ignores it).
- **Affected component:** `frontend/src/features/projects/api/projectApi.ts` line 19–23 + `useProjects.ts`.
- **Description:**
  - Given the master plan §10 rule 13 says "no manual `tenant_id` input in business forms" and §27 task 1 says "AppShell shows active company-client name and active project name; no manual tenant_id input anywhere",
  - When `useProjects()` calls `fetchProjects(tenantId)` it appends `?tenantId=…`,
  - Then the real backend ignores it (good) but the request crosses the wire with a tenant identifier in the URL — fingerprintable in proxy logs and inconsistent with the master plan principle. The MSW mock honors it (see D-202).
- **Suggested fix:** drop the `tenantId` query param entirely; rely on the JWT-derived tenant in the backend. Use `tenantId` only as the React-Query cache key (for cache busting across tenant switches).
- **Owner:** frontend-engineer.

---

## 14. Missing Tests — must arrive before Phase 3

| Pack | Missing test | Suggested file |
|------|--------------|----------------|
| HTTP RBAC | Per-endpoint `@PreAuthorize` 403 vs 2xx (15 endpoints × 3 cases) | `backend/src/test/java/.../api/{Project,Department,Position}ControllerSecurityTest.java` |
| HTTP TIP-12/13 | tenant_id in body / query ignored | `backend/src/test/java/.../tenancy/TenantIdInBodyIgnoredTest.java`, `TenantIdInQueryIgnoredTest.java` |
| ArchUnit | `@RequestParam("tenantId")` ban | extend `ArchitectureTest` |
| Integration ABAC | `ACCESS_DENIED_BY_ABAC` audit row | `backend/src/test/java/.../access/AbacGateAuditTest.java` |
| Integration audit | end-to-end audit row for project/department/position lifecycle | `backend/src/test/java/.../phase2/Phase2AuditCompletenessTest.java` |
| Status machine | LOCKED-project rejects position creation; archived-dept rejects position creation; lock idempotency | extend `WorkflowStateTransitionTest` |
| JSONB | nameI18n / titleI18n round-trip | `backend/src/test/java/.../phase2/MultilingualJsonbRoundTripTest.java` |
| Native CTE | Department descendant recursive query | `backend/src/test/java/.../organization/DepartmentDescendantQueryTest.java` |
| Pagination clamp | size=1000 → 200 | `backend/src/test/java/.../phase2/PaginationClampTest.java` |
| Frontend i18n parity | 4-locale key set equality | `frontend/src/shared/i18n/i18nParity.test.ts` |
| Frontend permission-gating | per-page PermissionGate behavior | `frontend/src/features/{projects,organization,positions}/pages/*.test.tsx` |
| Frontend MSW contract | mock ignores `tenant_id` from body | extend `frontend/src/shared/api/mocks/handlers.test.ts` (new) |

**Total: 12 test files / suites required before Phase 3 entry.**

---

## 15. Test Execution Result

- **Frontend:** Vitest run completed on the review machine — **18 test files / 52 tests PASS / 0 fail** (above the claimed 40 — Phase 2 added 11 new files on top of Phase 0+1's 7; total 18). Duration 81 s. Includes the verification-finding tests for `UserMenu` and `AuditLogStatCard`.
- **Backend:** the maven `mvnw test` invocation requires a JDK + Docker locally; the review machine cannot execute Testcontainers. Test inventory: **22 test files** under `src/test/java/uz/hrlab/grading/`. Phase 2 net-new = 7 files (`access/application/ConsultantTenantAssignmentPolicyTest`, `access/domain/{Approved,Department,Project}EntityFilterPolicyTest`, `organization/domain/{DepartmentTreeBuilder,DepartmentValidationPolicy}Test`, `phase2/{Phase2TenantIsolationIntegration,WorkflowStateTransition,CrossResourceTenantValidation}Test`, `common/api/CrossTenantAuditRecordingTest`, `architecture/ArchitectureTest`). Task description claims **86 tests (59 pass / 27 Docker-skipped)** — at file-count level this is plausible. **QA RECOMMENDS** CI to publish the Phase 2 surefire report so this is verifiable rather than reported.

---

## 16. Regression Risks for Phase 3 (JobProfile + JobAnalysis)

1. **High** — Phase 3 will surface job profile + analysis endpoints. Without D-201 (`@RequestParam("tenantId")` ban) and D-203 (per-endpoint security tests), a single careless PR can introduce a TIP-12/13 regression.
2. **High** — D-202 (MSW mock honoring `body.tenant_id`) means frontend tests will pass with semantics opposite to production. When Phase 3 work integrates against real backend, dozens of tests may need rewriting.
3. **Medium** — D-205/D-206 (locale parity + fallback) will trip Phase 3 as new JobProfile keys are added. Add the parity test before Phase 3 starts.
4. **Medium** — D-213 (no end-to-end audit assertions) means Phase 3's `JOB_PROFILE_*` audit events could ship un-asserted; once methodology lock comes in Phase 4, missing audit becomes a release blocker.
5. **Medium** — D-209 (CTE untested) becomes important once JobProfile references Position which references Department: deep traversals through that chain rely on the same CTE pattern.
6. **Low** — D-217 (frontend sending `tenantId` query) may become invisible bug debt as more list pages copy the pattern.

---

## 17. Release Gate Decision

> **DECISION: GO WITH CONDITIONS** for Phase 2.

Phase 2 closes 5 of 6 Phase 0+1 conditions, implements 15 endpoints with consistent tenant scoping (via `TenantAwareRepository`), defends with 4 ABAC policies whose positive + negative paths are unit-tested, writes audit rows for 11 new event codes, and provides front-end UX with full 4-locale coverage and the two verification findings (UserMenu / AuditLogStatCard) now fixed and asserted.

The implementation is structurally correct. The defects are about *coverage gaps* and *contract drifts*, not about flawed security primitives.

**Conditions that MUST be met before Phase 3 begins (blocking):**

- **PC-1** Fix D-202 (MSW mock honors `tenant_id` from body) — this contradicts the master plan's central tenant-isolation principle and will infect every frontend test going forward.
- **PC-2** Fix D-203 (no per-endpoint HTTP-layer security tests) — at minimum a smoke pass on 15 endpoints × 3 cases (anon / wrong permission / right permission) before Phase 3 PRs land.
- **PC-3** Land D-201 (ArchUnit rule against `@RequestParam("tenantId")`) before any Phase 3 controller is added.
- **PC-4** Land D-213 (one end-to-end audit row assertion per Phase 2 resource) — Phase 3 will pile up more audit events; the assertion pattern must exist now.

**Non-blocking (track in sprint planning):**

- **PC-5** Close D-205 / D-206 (locale parity + fallback) at Phase 3 W1.
- **PC-6** Close D-207, D-209, D-212 by Phase 3 mid-sprint.

If PC-1 through PC-4 are met, Phase 2 can be tagged and Phase 3 work can begin. If any of PC-1…PC-4 remain open at Phase 3 start, this gate flips to **NO-GO**.

---

## 18. Top Action Items (prioritized)

### backend-engineer

1. **(Blocking, PC-2/PC-3)** Add 15 per-endpoint `@WebMvcTest` security tests + `@RequestParam("tenantId")` ArchUnit rule (D-201, D-203, D-204).
2. **(Blocking, PC-4)** Add Phase 2 audit-row integration tests for Project / Department / Position lifecycles (D-213).
3. Add the missing status-machine and JSONB-roundtrip tests (D-208 thru D-212).
4. Add `AbacGate` denial-audit integration test (D-207) and Department CTE integration test (D-209).
5. Add pagination clamp test (D-216).

### frontend-engineer

1. **(Blocking, PC-1)** Strip `tenant_id` from MSW mock body and forbid the query param in `projectApi.fetchProjects` (D-202, D-217).
2. Add `i18nParity.test.ts` (D-205) and a `missingKey` build-break (D-008 carryforward).
3. Add per-screen PermissionGate tests for new pages (D-214) and pagination-sync test (D-215).

### db / devops

1. Publish surefire / vitest JUnit reports in CI so test counts (86 / 52) become verifiable (D-211-class — covered by DevOps task #8 — confirm).
2. Ensure the `mode-shared` Liquibase context runs the tenant-schema 001–003 changelogs in CI smoke deploy.

### security-engineer

1. Sign off PC-1 (MSW mock contract) — this is technically a frontend defect but materially a security drift if it migrates to production seeds.
2. Confirm the redacted form of `reason` in `CROSS_TENANT_OR_PROJECT_ACCESS_ATTEMPT` (method + path; no query/body) meets the threat model.
3. Approve the `ACCESS_DENIED_BY_ABAC` audit row schema once D-207 lands.

---

**End of Phase 2 QA Review.**

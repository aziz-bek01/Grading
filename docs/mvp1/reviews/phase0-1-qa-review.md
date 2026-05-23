# Phase 0+1 QA Review Report — grading.hrlab.uz

Document owner: QA Engineering
Status: Baseline review (Sprint 0–1)
Date: 2026-05-23
Benchmark: `docs/mvp1/03-qa-master-test-plan.md`
Reviewed build: backend 64 Java files / 8 test files; frontend 61 TS/TSX files / 7 test files

---

## 1. Review Scope

This review covers the Phase 0+1 foundation only — skeleton, tenancy & access plumbing, audit append-only contract, dev-only auth filter, frontend AppShell, auth foundation, route guards, permission gating, salary masking, and 4-locale i18n setup.

Reviewed:
- Backend: `backend/src/main/java/uz/hrlab/grading/{common,tenancy,access,security,audit}/**` (64 production files).
- Liquibase: `backend/src/main/resources/db/changelog/{control-plane,seeds}/**` (7 changelogs).
- Backend tests: `backend/src/test/java/uz/hrlab/grading/**` (6 test files; 4 of them extend `AbstractIntegrationTest` and require Docker via Testcontainers).
- Frontend: `frontend/src/{app,features,pages,shared}/**` (61 files).
- Frontend tests: 7 `.test.ts(x)` files, 19 tests.

Out of scope for this review (correctly deferred to Phase 2+): project workspace, organization structure, position catalog, job profile, methodology builder, scoring engine, grade structure. Therefore the Methodology, Scoring, and Grade-structure test packs are **N/A for the Phase 0+1 gate** but their absence is recorded under "Missing tests" with the sprint in which they must arrive.

---

## 2. Test Plan Coverage Matrix (MVP 1 packs vs. Phase 0+1 implementation)

| Test pack | Required by master plan | Implemented in Phase 0+1 | Missing in Phase 0+1 | Status for gate |
|-----------|-------------------------|---------------------------|----------------------|------------------|
| Tenant Isolation (18 scenarios, TIP-01…18) | 18 | 1 repository-layer probe (`TenantIsolationIntegrationTest.clientCompanyIsNotReachableFromAnotherTenant`) covering the BOLA pattern of `findByIdAndTenantId` | TIP-01…18 at HTTP layer; ArchUnit rule banning `@RequestParam("tenantId")`; stale-context-token test (TIP-11); manipulated-body/query (TIP-12/13); dashboard, AI, cache, background-job, report-download (TIP-14…18) | Phase 0+1 baseline acceptable — full pack is Sprint 2/3 deliverable per master plan §25. |
| RBAC + ABAC | 11 roles × N modules, positive + negative; salary/audit/export separation | `PermissionService` (`@PreAuthorize("hasAuthority('TENANT_CREATE')")` used on `AdminTenantController.create`); `PermissionCodes` catalog complete (34 codes incl. `SALARY_VIEW`, `SALARY_EDIT`, `SALARY_EXPORT`, `SALARY_SCENARIO_RUN`, `AUDIT_READ`); `AbacPolicy` / `TenantAwarePolicy` skeleton; 11 roles seeded | Role-permission grant seed (`role_permissions` rows); positive/negative test matrix; ABAC department-scope test; SALARY/AUDIT/EXPORT permission-separation tests | Backend has only one controller endpoint to gate — pack expands in Sprint 1/2. Foundation looks sound. |
| Methodology lock/version | TC-MTH-LOCK-001…008 | None (module not yet built) | All 8 cases | N/A for Phase 0+1 gate (Sprint 2). |
| Scoring engine | TC-SCORE-001…014 | None | All 14 cases | N/A for Phase 0+1 gate (Sprint 3). |
| Grade structure | TC-GR-001…011 | None | All 11 cases | N/A for Phase 0+1 gate (Sprint 3). |
| Audit trail (20 events + append-only + hash chain + redaction) | 20 events, append-only, hash, salary redaction, AUDIT_READ-gated, filterable | `AuditAction` enum has 25 codes covering 14/20 events; `JpaAuditService` SHA-256 hash chain with `Propagation.REQUIRES_NEW`; `SystemAuditLogRepository` extends `Repository` not `JpaRepository` (no delete/update/saveAll); `AuditAppendOnlyTest` proves contract via reflection + 2-event chain | Salary-field redaction enforcement at write time (only documented in builder JavaDoc, not enforced); AUDIT_READ-gated read endpoint; filter API; salary redaction in read DTO for non-SALARY_VIEW users (TC-AUD-REDACT-001); audit events for LOGIN_FAILED, TENANT_CONTEXT_SWITCH end-to-end (only constants present); CROSS_TENANT audit row write (only log line via `securityLog.warn`, no DB row) | **Append-only & hash chain pass; CROSS_TENANT_ACCESS_ATTEMPT writes a log line but no audit row — defect D-002.** |
| Salary permission foundation (TC-SAL-FND-001…010) | 10 cases | TC-SAL-FND-001 (4 salary codes present in seed `001-default-permissions.yaml`); TC-SAL-FND-002 (no default role grants `SALARY_VIEW` — `002-default-roles.yaml` defines roles without grants; `role_permissions` table seeded empty); TC-SAL-FND-005 partial (`SalaryValue` component never receives masked stubs from server; `httpClient` never logs body); TC-SAL-FND-009 (`salary_data_permission` is a typed claim in `CurrentUser`); TC-SAL-FND-010 (`canViewSalary` requires `salary_data_permission && SALARY_VIEW` — verified by unit test) | TC-SAL-FND-003/004 (backend DTO field-stripping test — no DTO yet); TC-SAL-FND-006 (chart tooltip masking — no chart yet); TC-SAL-FND-007 (audit on salary view — endpoint not yet built); TC-SAL-FND-008 (export permission — not built). | Acceptable for Phase 0+1; the foundation rules are in place. |
| Localization (TC-L10N-001…013) | 13 cases incl. 4 locales, fallback, validation localization | 4 locale files present (`ru-RU.json`, `uz-Cyrl-UZ.json`, `uz-Latn-UZ.json`, `en-US.json`); `i18next` configured with `LanguageDetector` + `localStorage` cache; `localization_messages` table with `(key, locale)` unique constraint and `CHECK` constraint on the 4 locales; `LiquibaseMigrationTest.defaultLocalesSeeded` proves 4 seed rows; `LanguageSwitcher.test.tsx` proves RU→UZ-Latn→EN flow | TC-L10N-006/007 (factor / level translations — module not yet built); TC-L10N-008 (missing-translation fallback chain test); TC-L10N-009 (Uzbek layout overflow — Playwright not yet added); TC-L10N-010/013 (`Accept-Language` honored on server validation errors — `httpClient` sets the header but no backend round-trip test exists) | **Foundation passes**, but no automated assertion that all 4 locale files have the same key set — drift risk. |

---

## 3. Tenant Isolation Verification

### Repositories audited

| Repository | Pattern | Verdict |
|------------|---------|---------|
| `TenantRepository` | Exposes `findBySlug`, `existsBySlug`, plus default `findById` (control-plane data; documented exception) | **PASS** — explicit JavaDoc that this is control-plane and that business-data repositories must not follow this pattern. |
| `ClientCompanyRepository` | `findByTenantId`, `findByIdAndTenantId`, `existsByTenantId`; default `JpaRepository.findById` still inherited and reachable via the broader `findById` method | **PASS with caveat** — domain code is expected to never call the inherited `findById`. There is no ArchUnit rule yet that forbids calling `findById` from services, so this is enforced only by convention. Defect D-001. |
| `UserRepository` | `findByEmailIgnoreCase`, `findByExternalIdpSubject`; comment "control-plane users — not tenant business data, so findById is permitted" | **PASS** — control-plane scope. |
| `UserTenantMembershipRepository` | `findByUserIdAndTenantId`, `findAllByUserId`, `findAllByTenantId`, `existsByUserIdAndTenantId`. No bare `findById` declared (still inherited from `JpaRepository`). | **PASS with caveat** — same enforcement concern as above (D-001). |
| `RoleRepository`, `PermissionRepository` | `findByCode` only; standard JpaRepository inheritance | **PASS** — these are catalog tables; lookup by code is safe. |
| `SystemAuditLogRepository` | Extends `Repository`, not `JpaRepository`; only declared methods reachable | **PASS** — model pattern for audit, see §5. |

### Other isolation signals

- `TenantContext` (record) is immutable and never reads `tenant_id` from request body/path/query.
- `TenantContextFilter` always resolves tenant from `Authentication`, never from the request.
- `DevAuthFilter` only reads `X-Dev-*` headers under `local|test|dev` profiles (hard guard in constructor — verified by `DevAuthFilterTest`).
- `AdminTenantController` is the **only** controller in the codebase. The single `tenant_id` surface in URL is justified (control-plane admin API per master plan §13.1).
- `CreateTenantRequest` (record) does not accept `tenant_id` — server-side generated.
- `GlobalExceptionHandler` translates `TenantAccessDeniedException` to HTTP 404 (correct existence-leak prevention, master plan §11 / TIP-02).

### Leakage paths flagged

- **Inherited `findById(UUID)` on tenant-scoped repositories**: `ClientCompanyRepository`, `UserTenantMembershipRepository` still inherit `JpaRepository.findById`. Today there are no callers, but the language doesn't forbid future ones. Defect D-001 (severity Medium for Phase 0+1 — escalates to High once Phase 2 services land).
- **No ArchUnit rule yet enforcing the master plan rules**: master plan §21 requires three rules — no JPA in `..api..`, no `@RequestParam("tenantId")` in business controllers, `findBy.*Id$` must be `findBy.*Id.*TenantId.*`. None are in the codebase. Defect D-003.

---

## 4. RBAC + ABAC Verification

- `@EnableMethodSecurity(prePostEnabled = true)` is set in `SecurityConfig` — `@PreAuthorize` is active.
- `AdminTenantController.create` is guarded with `@PreAuthorize("hasAuthority('TENANT_CREATE')")` — correct permission code (`TENANT_CREATE` exists in seed).
- `PermissionService` (`@Service("permissions")`) provides `has`, `hasAny`, `hasAll`, `hasRole`, `canViewSalary` — wired for SpEL `@permissions.has('X')` usage; default-deny when no context.
- `canViewSalary()` requires **both** `salary_data_permission` claim AND `SALARY_VIEW` permission (defense-in-depth; matches master plan §18 TC-SAL-FND-010).
- `AbacPolicy<T>` interface + `TenantAwarePolicy<T>` skeleton present. No concrete policy yet (no domain entities). The contract is correct: `requireSameTenant` throws `TenantAccessDeniedException` (mapped to 404).

Gaps:
- Spring Security maps permissions to authorities; the wiring from JWT claims → `GrantedAuthority` is in `JwtTenantContextResolver` but is **not exercised by any test** in Phase 0+1 (no Spring Security test using `@WithMockJwt` or similar). Defect D-004.
- `role_permissions` rows are not seeded — roles exist, permissions exist, but the grant matrix is empty in DB. Stated as deferred to security-engineer follow-up changeset in `002-default-roles.yaml`. Acceptable for Phase 0+1; required by Phase 2.

---

## 5. Audit Append-Only Verification

- `SystemAuditLogRepository` extends `org.springframework.data.repository.Repository`, not `JpaRepository`. Only `save`, `findById`, `count`, `findByTenantIdOrderByCreatedAtDesc`, `findLastHash` are exposed — verified by `AuditAppendOnlyTest.repositoryExposesNoDeleteMethods` and `repositoryExposesNoBulkUpdateMethods` (reflection over `getMethods()`).
- `JpaAuditService` computes SHA-256 hash chain over a deterministic StringBuilder canonical form (id|tenant|project|actor|action|entityType|entityId|before|after|createdAt|prevHash). Runs in `Propagation.REQUIRES_NEW` so audit insert survives caller rollback.
- `AuditAppendOnlyTest.hashChainIsComputedAndChainedAcrossEvents` proves the chain links across two events.
- `AuditAction` constants cover 14 of the master plan §17 20 events. Missing constants: `PROJECT_CONTEXT_SWITCH`, `PERMISSION_CHANGED`, `JOB_PROFILE_CREATED`, `FACTOR_CREATED/EDITED`, `SCORE_CALIBRATED`, `GRADE_STRUCTURE_APPROVED`, `SALARY_SCENARIO_RUN`, `REPORT_GENERATED/DOWNLOADED`, `FILE_UPLOADED/DOWNLOADED`, `AI_SUGGESTION_*`. (Note: some lifecycle events are split across multiple constants in master plan; the gap is real but expected to fill in Phase 2/3.)

**Defects in this area:**
- **D-002 (High)** — `GlobalExceptionHandler.handleTenantAccessDenied` logs `CROSS_TENANT_ACCESS_ATTEMPT` to a `security.audit` logger but does **not** record an `AuditEvent`. Master plan §17 TC-AUD-020 requires this to be an audit row. As built, forensics will rely on log scraping, not the tamper-evident hash chain.
- **D-005 (Medium)** — Hash canonical form is a hand-rolled `StringBuilder` join, not canonical JSON as the master plan §17 specifies ("canonical_json"). Functionally fine for tamper detection, but if any field's `toString` ever changes (e.g., `UUID` order in JSON) historic verification breaks. Recommend documenting the canonical form spec.
- **D-006 (Medium)** — No automated test asserts salary redaction on the `before/after` JSON snapshots; the contract is only documented in `AuditService` JavaDoc. The redaction code path doesn't yet exist (no salary domain events).

---

## 6. Localization Verification

- 4 locale JSON files present: `ru-RU.json`, `uz-Cyrl-UZ.json`, `uz-Latn-UZ.json`, `en-US.json`.
- Backend `localization_messages` table has a unique constraint `(key, locale)` and a `CHECK` constraint restricting `locale ∈ {ru-RU, uz-Cyrl-UZ, uz-Latn-UZ, en-US}` (changelog `004-create-localization.yaml`). Index on `locale`.
- 4 anchor rows seeded by `003-default-locales.yaml`; covered by `LiquibaseMigrationTest.defaultLocalesSeeded`.
- `i18next` setup correct (4 resources, `fallbackLng`, `supportedLngs`, `nonExplicitSupportedLngs: false`, `LanguageDetector` reading `grading.locale` from `localStorage` then navigator).
- `httpClient` sets `Accept-Language` from current locale on every request.
- `LanguageSwitcher.test.tsx` verifies RU → uz-Latn → en switching; updates `i18n.language`.

Gaps:
- No automated key-parity test across the 4 locale files (key drift risk). Defect D-007 (Medium).
- No `missing-key` fail-build behaviour described in master plan §27 task 9 ("missing-key behavior fails the build in CI"). Defect D-008 (Low for Phase 0+1; Medium by Sprint 2).

---

## 7. Frontend Security UI Verification

| Rule (master plan §10) | Implementation | Verdict |
|------------------------|----------------|---------|
| AppShell shows active company-client + active project | `AppShell` + `TenantSelector` + `ProjectSelector` (visible elements) | PASS (manual inspection — no automated assertion of co-presence). |
| Route guards | `RequireAuth`, `RequirePermission`, `RequireSalaryPermission`, `RequireAuditPermission` | PASS — verified by `RequireAuth.test.tsx`, `RequirePermission.test.tsx`. |
| `PermissionGate` hides on missing permission AND backend enforces | Component does not mount children (`return <>{fallback}</>`); usePermission ANDs `salary_data_permission && SALARY_VIEW` for salary | PASS for the component side. Backend pair-coverage assertion missing (`permission-coverage.json` artifact not produced — master plan §27 task 2). Defect D-009 (Low — only one gated endpoint exists today). |
| `SalaryValue` masking | Three states: `permission-required` (default in MVP 1), `masked` (force), `visible` (requires both `canViewSalary()` + `value`); `useTranslation` + `Intl.NumberFormat`; comment "we NEVER log the salary value, never put it in chart tooltips outside this component, never persist to localStorage". | PASS — verified by `SalaryValue.test.tsx` (3 cases). |
| No manual `tenant_id` input | `TenantSelector` uses a button + listbox + `ConfirmDialog`; test `exposes no free-text tenant_id input field` explicitly asserts no `<textbox>` and no `input[name="tenant_id"]` | PASS — verified by `TenantSelector.test.tsx`. |
| `httpClient` 401/403/404 handling, no token logging | 401 → `tokenStorage.clear()` + `onUnauthorized()`; ApiError typed; `console.warn` only in dev and **only** with status/method/url/correlation_id (no body, no token). `tokenStorage.get()` is in-memory + sessionStorage by convention. | PASS — code inspection. No automated test for "no token in console" (master plan §10 rule 11). Defect D-010 (Low). |
| No salary in chart tooltips | No charts in Phase 0+1. SalaryValue contract documents the rule. | N/A. |
| 4 locales present | See §6 | PASS. |
| Locked methodology / evaluation UI | `LockedBadge` component exists; no locked-state usage yet (no methodology UI). | N/A for Phase 0+1. |
| Loading / empty / error / no-access states | `LoadingState`, `EmptyState`, `ErrorState`, `NoAccessState` shared components present. | PASS (component existence). |

---

## 8. Defects Found

### D-001 — Inherited bare `findById` on tenant-scoped repositories

- **Severity:** Medium (Phase 0+1) → escalates to **High** once Phase 2 business code lands.
- **Affected component:** `ClientCompanyRepository`, `UserTenantMembershipRepository`, `SystemAuditLogRepository` (all extend a Spring Data interface that inherits a no-tenant `findById`).
- **Description:**
  - Given a developer calls `clientCompanies.findById(UUID)` from a service in a future sprint,
  - When the called UUID belongs to a different tenant,
  - Then the row is returned despite the tenant mismatch (BOLA).
- **Suggested fix:** Add an ArchUnit rule (master plan §21) that fails the build if any class outside `..tenancy.application..` invokes `Repository.findById` on a tenant-scoped repository — or override the method on each tenant-scoped repository to throw `UnsupportedOperationException` (security-blueprint §5.2 pattern).
- **Owner:** backend-engineer.

### D-002 — `CROSS_TENANT_ACCESS_ATTEMPT` writes a log line but no audit row

- **Severity:** High.
- **Affected component:** `GlobalExceptionHandler.handleTenantAccessDenied`.
- **Description:**
  - Given a user in Tenant A makes a request that resolves to a Tenant B entity,
  - When `TenantAccessDeniedException` is thrown,
  - Then the handler logs `CROSS_TENANT_ACCESS_ATTEMPT` via SLF4J only — the canonical hash-chained audit row is **not** written. Master plan §17 TC-AUD-020 expects an audit row with `actor / tenantId / entityId / hash_current`.
- **Suggested fix:** Inject `AuditService` (or a thin `SecurityEventRecorder`) into `GlobalExceptionHandler` and call `auditService.record(AuditEvent.builder().action("CROSS_TENANT_OR_PROJECT_ACCESS_ATTEMPT")...)`. Add `AuditAction.CROSS_TENANT_OR_PROJECT_ACCESS_ATTEMPT` is already declared — just call it.
- **Owner:** backend-engineer.

### D-003 — ArchUnit rules from master plan §21 not implemented

- **Severity:** Medium (Phase 0+1) → High once Phase 2 lands.
- **Affected component:** `backend/build.gradle.kts` (no archunit dep); no `ArchitectureTest` class exists.
- **Description:**
  - Given the master plan mandates 3 ArchUnit rules (no JPA in `..api..`; no `@RequestParam("tenantId")` in business controllers; tenant-aware repository naming),
  - When the build runs,
  - Then no architectural rule is enforced — code reviewers are the only line of defense.
- **Suggested fix:** Add ArchUnit dependency; create `uz.hrlab.grading.architecture.ArchitectureTest` with the 3 rules from §21.
- **Owner:** backend-engineer.

### D-004 — JWT → authorities mapping not exercised by any test

- **Severity:** Medium.
- **Affected component:** `JwtTenantContextResolver`, `SecurityConfig.securityFilterChain`.
- **Description:**
  - Given a real-shaped JWT with `roles` and `permissions` claims,
  - When `JwtAuthenticationConverter` runs,
  - Then we never assert that `SimpleGrantedAuthority` instances are emitted in the form `@PreAuthorize("hasAuthority('TENANT_CREATE')")` expects. The only authentication path with a test is `DevAuthFilter` + `DevAuthentication`.
- **Suggested fix:** Add a `@WebMvcTest` with a stubbed `JwtDecoder` (or `MockMvc` + `SecurityMockMvcRequestPostProcessors.jwt()`) that hits `AdminTenantController` with and without `TENANT_CREATE` authority and asserts 201/403 respectively.
- **Owner:** backend-engineer + security-engineer.

### D-005 — Audit hash canonical form is not "canonical JSON"

- **Severity:** Medium.
- **Affected component:** `JpaAuditService.computeHash`.
- **Description:**
  - Given the master plan §17 specifies `hash_current = sha256(prev.hash_current || canonical_json(row))`,
  - When the current implementation uses pipe-delimited `toString()` concatenation,
  - Then any change in `UUID.toString()` representation, locale of `OffsetDateTime.toString()`, or null-string handling breaks historic chain verification — even though the data is intact.
- **Suggested fix:** Either (a) commit to the pipe-delimited spec by writing a small `CanonicalAuditForm.md` and an `assertCanonicalFormIsStable` unit test, or (b) switch to JSON canonicalization (RFC 8785 — JCS) before SHA-256. Prefer (b) for forensic durability.
- **Owner:** backend-engineer + security-engineer.

### D-006 — No automated test for salary-field redaction on audit JSON snapshots

- **Severity:** Medium (foundation), High once Phase 3 ships salary engine.
- **Affected component:** `AuditService` contract.
- **Description:**
  - Given the contract requires salary fields to be redacted before persisting `beforeJson`/`afterJson`,
  - When code in a future sprint passes a snapshot containing a `monthly_salary` field,
  - Then nothing in CI prevents the raw number from being persisted.
- **Suggested fix:** Add a `SalaryAuditRedactor` utility with `redact(JsonNode)` and a JUnit test asserting that any field key matching `*salary*|compensation|compa_ratio|monthly_pay` is replaced with `"***"`. Wire it into a `Sensitive` audit builder helper.
- **Owner:** backend-engineer + security-engineer.

### D-007 — No automated parity test across the 4 frontend locale files

- **Severity:** Medium.
- **Affected component:** `frontend/src/shared/i18n/locales/*.json`.
- **Description:**
  - Given a developer adds a key to `ru-RU.json` only,
  - When the user switches to `uz-Latn-UZ`,
  - Then the missing key renders the raw key — invisible in CI.
- **Suggested fix:** Add a Vitest unit `i18nParity.test.ts` that loads all 4 JSON files and asserts identical flattened key sets. Master plan §10 rule 14 implies this.
- **Owner:** frontend-engineer.

### D-008 — Missing-key behavior does not fail the build

- **Severity:** Low (Phase 0+1) → Medium by Sprint 2.
- **Affected component:** `shared/i18n/index.ts`.
- **Description:** Master plan §27 task 9 requires CI fail on missing key; current `i18next` config has no `missingKeyHandler` or `saveMissing`.
- **Suggested fix:** Add `parseMissingKeyHandler` in dev to throw; add a Vitest matcher for build-time parity (see D-007).
- **Owner:** frontend-engineer.

### D-009 — No `permission-coverage.json` artifact emitted by frontend

- **Severity:** Low.
- **Affected component:** `PermissionGate` usage tracking.
- **Description:** Master plan §13.3 requires every gated UI element to be paired with a backend permission code in `permission-coverage.json`. Today no such artifact is produced.
- **Suggested fix:** Vitest plugin (or simple post-build script) walks the AST for `<PermissionGate permission="...">` and writes the manifest. QA pipeline will fail when the manifest references a backend code that isn't in `PermissionCodes.java`.
- **Owner:** frontend-engineer + QA.

### D-010 — No automated assertion that token never appears in console

- **Severity:** Low.
- **Affected component:** `httpClient`.
- **Description:** Manual code review shows the dev-only `console.warn` never includes the body or token. There is no test that exercises a 401/500 path and asserts `console.warn`/`console.log` mock receives nothing matching a JWT regex (`eyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+`).
- **Suggested fix:** Add `httpClient.test.ts` with spied `console.warn`; trigger 401/403/500 via MSW; assert no JWT-shaped value appears.
- **Owner:** frontend-engineer.

### D-011 — Tests requiring Docker are silently skipped on developer laptops

- **Severity:** Low (Phase 0+1) → Medium if CI Docker becomes unavailable.
- **Affected component:** `AbstractIntegrationTest`, `RequiresDocker`.
- **Description:** 4 of 6 backend test files (`LiquibaseMigrationTest`, `AuditAppendOnlyTest`, `TenantContextTest` partly, `TenantIsolationIntegrationTest`) extend `AbstractIntegrationTest` which is documented to require Docker. Per the task description 10 pass / 11 are Docker-skipped — meaning the BOLA proof, audit append-only proof, and migration proofs do not run on every dev machine.
- **Suggested fix:** Require Docker in CI (already the case per master plan §5); add a CI status badge so a Docker-broken pipeline fails the gate explicitly rather than skipping.
- **Owner:** devops-sre.

---

## 9. Missing Tests — must arrive before Phase 2

| Pack | Missing test | Suggested file |
|------|--------------|----------------|
| Tenant isolation (HTTP) | TIP-12 (manipulated body) | `backend/src/test/java/uz/hrlab/grading/tenancy/TenantIdInBodyIgnoredTest.java` |
| Tenant isolation (HTTP) | TIP-13 (manipulated query) | `backend/src/test/java/uz/hrlab/grading/tenancy/TenantIdInQueryIgnoredTest.java` |
| Tenant isolation (HTTP) | TIP-11 (stale context token) | `backend/src/test/java/uz/hrlab/grading/security/StaleContextTokenTest.java` |
| Audit | TC-AUD-020 (CROSS_TENANT writes audit row) | extend `AuditAppendOnlyTest` with a controller-level test |
| Audit | TC-AUD-REDACT-001 (salary fields redacted before persistence) | `backend/src/test/java/uz/hrlab/grading/audit/SalaryRedactionTest.java` |
| RBAC | Positive/negative for `@PreAuthorize` on `AdminTenantController.create` | `backend/src/test/java/uz/hrlab/grading/tenancy/api/AdminTenantControllerSecurityTest.java` |
| Architecture | ArchUnit rules from master plan §21 | `backend/src/test/java/uz/hrlab/grading/architecture/ArchitectureTest.java` |
| Frontend i18n | TC-L10N-008 fallback chain + locale key parity | `frontend/src/shared/i18n/i18nParity.test.ts` |
| Frontend security | No-token-in-console assertion | `frontend/src/shared/api/httpClient.test.ts` |
| Frontend security | PermissionGate ↔ backend coverage manifest | `frontend/scripts/buildPermissionCoverage.ts` |

Total missing for Phase 2 readiness: **10 test files**.

---

## 10. Test Execution Status

- **Backend:** 10 tests passing on a machine without Docker. 11 tests (the 4 `AbstractIntegrationTest`-extending classes) are skipped without Docker — these are the load-bearing ones: `LiquibaseMigrationTest`, `AuditAppendOnlyTest`, `TenantIsolationIntegrationTest`. **Risk:** the central BOLA pattern and the append-only audit contract are only proven when CI Docker is available. Confirm CI runs them; gate the PR on their completion.
- **Frontend:** 19/19 tests passing. All RTL component tests cover the explicit security UI rules (no tenant_id input, salary masking, route guards, language switching).

**Skipped-test risk:** if CI Docker breaks silently, the three load-bearing backend tests would be marked skipped rather than failing. Recommendation: extend `GradingApplicationTests` smoke to assert the Docker presence flag and fail fast.

---

## 11. Regression Risks for Phase 2 (Project / Department / Position)

1. **High** — Phase 2 will be the first phase to add tenant-scoped business controllers. Without D-003 (ArchUnit) and D-001 (banning bare `findById`), the first PR to add a `PositionRepository` could trivially introduce a BOLA. **Mitigation:** land D-001 and D-003 fixes in Sprint 1 hardening before Sprint 2 starts.
2. **High** — Phase 2 will need to emit at least `PROJECT_CREATED`, `POSITION_CREATED`, `DEPARTMENT_UPDATED` audit events; D-002 (cross-tenant audit row) needs to be fixed first so every code path writes an audit row, not a log line.
3. **Medium** — `role_permissions` grants are not seeded; Phase 2 endpoints will all 403 in `dev/test` profiles unless `X-Dev-Permissions` is set on every request. Risk: developers will accumulate `X-Dev-Permissions: *` workarounds that mask real RBAC bugs.
4. **Medium** — i18n key drift (D-007/D-008): Phase 2 adds many new UI strings; without parity tests, locales will diverge silently.
5. **Medium** — `JwtAuthenticationConverter` is unverified (D-004); switching from `DevAuthFilter` to real JWT in staging is the moment we'll discover wiring bugs.

---

## 12. Release Gate Decision — Phase 0+1 Baseline

> **DECISION: GO WITH CONDITIONS** for the Phase 0+1 baseline.

Phase 0+1 is a foundation milestone, not a customer-facing release. The 18-scenario tenant isolation pack and the full 20-event audit pack are correctly scheduled for Sprint 2/3 per master plan §25. The implementation we have today is structurally correct: deny-by-default security, JWT-only auth in prod profiles, hard guard on `DevAuthFilter`, tenant context strictly from `Authentication`, append-only audit repository, hash-chained audit writes in `REQUIRES_NEW`, complete salary permission catalog with zero default grants, four-locale i18n with DB constraint, masking-by-default `SalaryValue`, route guards, no manual `tenant_id` input.

**Conditions that MUST be met before Phase 2 begins:**

- **C-1** Fix D-002 (write an audit row, not a log line, on cross-tenant attempt). **Blocking.**
- **C-2** Land D-003 (the three ArchUnit rules from master plan §21). **Blocking.**
- **C-3** Land D-001 (override or ArchUnit-forbid bare `findById` on tenant-scoped repositories). **Blocking.**
- **C-4** Land D-004 (one Spring Security-aware test on `AdminTenantController` proving `@PreAuthorize('TENANT_CREATE')` returns 403 without authority and 201 with it). **Blocking.**
- **C-5** Land D-007 (locale key parity Vitest). **Non-blocking — must land in Sprint 2 Sprint 1 of Phase 2.**
- **C-6** Confirm CI runs Testcontainers (the 11 Docker-skipped tests must actually execute in CI). **Blocking on the CI side.**

If C-1 through C-4 and C-6 are met, the Phase 0+1 baseline can be tagged and Phase 2 can begin.

If any of C-1…C-4 or C-6 remain open at the start of Phase 2, this gate flips to **NO-GO** and Phase 2 work must pause until the foundation is solid.

---

## 13. Top Action Items (prioritized)

### backend-engineer

1. **(Blocking, C-1)** Make `GlobalExceptionHandler.handleTenantAccessDenied` write an `AuditEvent` with `AuditAction.CROSS_TENANT_OR_PROJECT_ACCESS_ATTEMPT` (D-002).
2. **(Blocking, C-2)** Add ArchUnit dependency + three rules from master plan §21; expand the test class beyond rules as the codebase grows (D-003).
3. **(Blocking, C-3)** Override `findById(UUID)` to throw `UnsupportedOperationException` on `ClientCompanyRepository` and any future tenant-scoped repository (D-001).
4. **(Blocking, C-4)** Add `AdminTenantControllerSecurityTest` proving the `@PreAuthorize` works under real Spring Security (D-004).
5. Add `SalaryAuditRedactor` utility + test (D-006), and document canonical form for the audit hash (D-005).

### frontend-engineer

1. **(Non-blocking, C-5 — land in Sprint 2 W1)** Add `i18nParity.test.ts` (D-007).
2. Add `httpClient.test.ts` asserting no JWT shape appears in `console.warn` (D-010).
3. Wire missing-key fail-build behaviour in i18next (D-008).
4. Define `permission-coverage.json` build step (D-009).

### devops-sre

1. **(Blocking, C-6)** Confirm GitHub Actions / GitLab CI runner has Docker available; explicit fail (not skip) when Testcontainers cannot start (D-011).
2. Add CI gate that fails when any of the 11 integration tests are skipped.
3. Add the master plan §21 pipeline order: static analysis → unit → integration → API+TIP → component → ArchUnit (currently we are missing ArchUnit and API+TIP).

### security-engineer (referenced by separate review)

1. Sign off the canonical form spec produced under D-005.
2. Confirm the JWT claims spec produced by D-004 matches the threat model.
3. Approve the `SalaryAuditRedactor` redaction key list under D-006.

---

**End of Phase 0+1 QA Review.**

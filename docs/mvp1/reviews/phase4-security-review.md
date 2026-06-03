# Phase 4 — Security Review Report

**Product:** grading.hrlab.uz
**Reviewer agent:** security-engineer
**Date:** 2026-05-23
**Benchmark:** `docs/mvp1/02-security-blueprint.md` (v1.0)
**Predecessors:** `docs/mvp1/reviews/phase2-security-review.md`, `docs/mvp1/reviews/phase3-security-review.md`
**Reference architecture:** `архитектура.md` §9 (Domain Model), §14 (Workflow), §15 (Scoring engine), ADR-002 (Methodology versioning)
**Verdict:** **SHIP** (no blocking conditions). Three Medium findings, eight Low findings — none rated High/Critical. Zero hard-rule violations.

---

## 1. Review scope

Phase 4 introduces the **Methodology Builder** — the highest-stakes tenant-business module in MVP 1 so far. It is the artefact the Scoring engine (§15) and every downstream Evaluation (Phase 5+) consumes as ground truth. This review covers:

* Backend module under `backend/src/main/java/uz/hrlab/grading/methodology/**` — 3 controllers (22 endpoints), 9 use cases / services, 4 repositories, 4 JPA entities, 6 domain policies (Status transition, Immutability, Weight validation, Primary-locale validator, plus 2 enums).
* Liquibase changelogs `tenant-schema/010-create-methodologies.yaml`, `011-create-factors.yaml`, `012-projects-methodology-fk.yaml`, `seeds/013-seed-methodology-permissions.yaml`.
* 3 DB trigger functions (`prevent_mv_status_regression`, `prevent_locked_factor_changes`, `prevent_locked_factor_level_changes`).
* Status-machine policy (DRAFT/APPROVED/LOCKED/ARCHIVED) + immutability policy on version content + weight-sum policy per 3 scoring modes (DIRECT_POINTS / WEIGHTED_POINTS / WEIGHTED_SCALE).
* 18 new audit actions (`METHODOLOGY_*` 10 + `FACTOR_*` 4 + `FACTOR_LEVEL_*` 4) wired in `AuditAction.java`.
* `MethodologyAuditSnapshot` long-text-preview redaction strategy (F-309 pattern).
* Frontend deliverables under `frontend/src/features/methodology/**` — 17 source files + 7 unit tests.
* MSW handlers for the 22 Phase 4 endpoints in `frontend/src/shared/api/mocks/handlers.ts`.
* Phase 3 conditional remediation surface re-verified.

Out of scope (deferred): real Keycloak integration, evaluation/scoring runtime (Phase 5), grade structure (Phase 6+), file uploads, AI gateway.

---

## 2. Phase 3 findings closure

| ID | Description | Phase 3 verdict | Closure evidence (post-Phase-3 remediation as visible in Phase 4) | Status |
|----|-------------|-----------------|-------------------------------------------------------------------|--------|
| F-201 | `ProjectMembershipPolicy` denies tenant-wide admin roles | Medium | Closed in Phase 2 remediation; no regression in Phase 4 | **CLOSED** |
| F-202 | Write paths skip ABAC | Medium | Phase 4 use cases inherit the pattern — every mutation invokes `abacGate.enforceCanWriteInProject(ctx, m.getProjectId())` when project-scoped (verified at `ApproveMethodologyVersionUseCase:91-93`, `LockMethodologyVersionUseCase:64-66`, `CreateMethodologyVersionUseCase:78-80`, `FactorService.loadAndGate:228-230`, `FactorLevelService.loadAndGate:218-220`, `ArchiveMethodologyVersionUseCase:63-65`, `ArchiveMethodologyUseCase:50-52`, `UpdateMethodologyMetadataUseCase:48-50`, `CreateMethodologyFromTemplateUseCase:79`, `CreateMethodologyFromScratchUseCase:62`) | **CLOSED** |
| F-203 | No FK `tenant_id → public.tenants(id)` | Low | `010-create-methodologies.yaml:88-90` + `:206-209` + `011-create-factors.yaml:95-97` + `:204-206` — all four new tables declare the FK with `ON DELETE RESTRICT` | **CLOSED** |
| F-204 | Cross-FK consistency | Low | Phase 4 introduces composite FK `(tenant_id, methodology_version_id) → methodology_versions(tenant_id, id)` for `projects` (012-projects-methodology-fk.yaml:24-28) — cross-tenant references denied at DB. Factors→version + levels→factor use simple FK + tenant column (defence-in-depth) | **CLOSED (for Phase 4 surface)** |
| F-205 | Consultant assignment N+1 | Low | Performance, deferred to QA pack | OPEN (deferred) |
| F-206 | DB-level cycle prevention on departments | Low | Out of scope | OPEN (deferred) |
| F-208 | MSW mock accepts `body.tenant_id` | Low | Closed in Phase 3 remediation; `stripTenantFromBody` helper applied for methodology PATCH (`handlers.ts:723`) and POST from-template (`:671`). **However** the helper is NOT applied to factor add (`:860`), factor PATCH (`:912`), level add (`:933`), or level PATCH paths — see **F-401** below | **PARTIAL — regression in Phase 4 MSW surface** |
| F-209 | DB guard against LOCKED→ACTIVE | Low | Phase 4 introduces `prevent_mv_status_regression` trigger that BLOCKS LOCKED→APPROVED, ARCHIVED→anything, APPROVED→DRAFT — pattern now exists for methodology; should be backported to JobProfile/positions in Phase 5 | **CLOSED for methodology** |
| F-301 | Questionnaire state machine inline | Medium | Closed in Phase 3 remediation (`QuestionnaireStatusTransitionPolicy`); Phase 4 follows the same Policy-class style (`MethodologyVersionStatusTransitionPolicy`) | **CLOSED** |
| F-302/F-304 | Composite FK / positions unique | Low | Verified — Phase 4 inherits composite-FK pattern via 012 | **CLOSED** |
| F-303 | JSONB unknown-fields | Low | Phase 3 remediation completed; multilingual maps in Phase 4 are simple `Map<String,String>` — Jackson cannot inject unknown keys without `@SupportedLocaleKeys` allowlist (which IS applied) | **CLOSED** |
| F-305 | Permission matrix completeness | Low | METHODOLOGY_CREATE seeded in 013 with HRLAB_SUPER_ADMIN + HRLAB_PROJECT_MANAGER + HRLAB_CONSULTANT; LOCK pre-seeded in 001/004 to HRLAB_SUPER_ADMIN + HRLAB_PROJECT_MANAGER only — matches the prompt's hard rule | **CLOSED** |
| F-306 | Auto-save closure | Low | Methodology Builder is a click-based editor (no 30s auto-save loop visible) — no regression. Frontend-engineer to verify after-builder edits don't reintroduce. | OPEN (carry-forward, no regression in Phase 4) |
| F-307 | DataIntegrityViolation → 500 | Low | Still mapped via base handler in Phase 4 surface (factor unique sort_order constraint could trip during concurrent reorder despite two-phase 10_000 offset). Tracked as **F-408** | OPEN (deferred) |
| F-308 | Multilingual JSONB DoS bound | Low | Closed via `@SupportedLocaleKeys` annotation applied to all 4 Phase 4 i18n DTO fields (`CreateMethodologyRequest`, `CreateMethodologyFromTemplateRequest`, `UpdateMethodologyMetadataRequest`, `FactorRequest`, `FactorLevelRequest`) | **CLOSED** |
| F-309 | Audit `before/after` payload + reason redaction | Medium | **Implemented** — `MethodologyAuditSnapshot` uses `AuditJsonRedactor.builder().addI18nPreviews(...)` long-text preview for `nameI18n`/`descriptionI18n`/`labelI18n`. Reason fields routed through `redactor.redactReason(reason)` in `ArchiveMethodologyUseCase:64` and `ArchiveMethodologyVersionUseCase:79`. Salary-shaped regex inherits from MaskingPatternLayout. **CLOSED for methodology surface.** Backport to JobProfile/Questionnaire archive paths still pending — Phase 5 backlog | **CLOSED for methodology; OPEN backport** |

**Closure tally:** 11 of 16 Phase 2+3 findings are **CLOSED in Phase 4 surface**; F-205, F-206, F-306, F-307 remain **deferred** (no regression); F-208 regressed partially in MSW (factor/level paths) — see F-401.

---

## 3. Phase 4 architecture conformance

| Architecture clause | Status | Evidence |
|---------------------|--------|----------|
| §9 — `Methodology` aggregate (long-lived container) + `MethodologyVersion` carries DRAFT/APPROVED/LOCKED/ARCHIVED state | Conformant | Two tables (`methodologies` + `methodology_versions`); status enum on version only; `MethodologyJpaEntity` has its own ACTIVE/ARCHIVED container status; `MethodologyVersionJpaEntity` carries the workflow state |
| §9 — Factor + FactorLevel belong to a version, not to the methodology | Conformant | `factors.methodology_version_id NOT NULL` (011:31); `factor_levels.factor_id NOT NULL` (011:148); cascade FK on levels (011:209) |
| §14 — APPROVED + LOCKED + ARCHIVED versions are immutable; edits create a new version | Conformant | `MethodologyVersionImmutabilityPolicy.ensureMutable` (allows DRAFT only); `CreateMethodologyVersionUseCase` instantiates fresh DRAFT row with new UUIDs + cloned factors/levels |
| §15 — three scoring modes (DIRECT_POINTS / WEIGHTED_POINTS / WEIGHTED_SCALE) with distinct weight-sum invariants | Conformant | `MethodologyWeightValidationPolicy` (4-decimal precision; DIRECT_POINTS no constraint; WEIGHTED_POINTS sum==100.0000; WEIGHTED_SCALE sum==targetTotalPoints) |
| ADR-002 — versioning is immutable + chained via `previousVersionId` | Conformant | `methodology_versions.previous_version_id UUID` (010:168) + self-FK (010:215); `CreateMethodologyVersionUseCase:91` sets `previousVersionId = source.id`; source never re-saved |
| §13.2 — no `tenant_id` in body/path/query for business endpoints | Conformant | Grep on `methodology/api/` returns ZERO `tenant_id` field references; all DTOs (records) carry no tenant field |
| §13 — 404 for cross-tenant probing; 409 on locked/state-conflict | Conformant | All write use cases `findByIdAndTenantId(...).orElseThrow(TenantAccessDeniedException::new)` → 404; `MethodologyVersionTransitionRejectedException` → 409 via `GlobalExceptionHandler:140-144` |
| §13 — DTOs not JPA entities returned | Conformant | Every controller method maps via `MethodologyResponse.from(...)`, `FactorResponse.from(...)`, `FactorLevelResponse.from(...)` |
| §9 — exactly one global template per code per tenant | Conformant at DB level | 010:106-108 partial unique index `WHERE project_id IS NULL` on `(tenant_id, code)`; complementary index 010:103-105 `WHERE project_id IS NOT NULL` on `(tenant_id, project_id, code)` |
| ADR-002 — factor/level codes unique within version | Conformant at DB level | 011:112-115 `uq_factors_version_code` + `uq_factors_version_sort_order`; 011:218-221 `uq_factor_levels_factor_code` + `uq_factor_levels_factor_level_order` |

---

## 4. Tenant isolation verification — Phase 4

### 4.1 Repository layer

| Repository | Extends `TenantAwareRepository`? | Bare `findById` exposed? |
|-----------|----------------------------------|---------------------------|
| `MethodologyRepository` | Yes (line 14-15) | No |
| `MethodologyVersionRepository` | Yes (line 10-11) | No |
| `FactorRepository` | Yes (line 9-10) | No |
| `FactorLevelRepository` | Yes (line 9-10) | No |

Grep for `findById\b` across `backend/src/main/java/uz/hrlab/grading/methodology/**` returns **ZERO** hits. Grep for `JpaRepository` returns **ZERO** hits. **PASS.**

### 4.2 Cross-resource chain validation (methodology → version → factor → level)

Every Phase 4 write/read path re-validates the chain inside the active tenant:

1. **Level write** (`FactorLevelService.loadAndGate:209-222`):
   1. `levels.findByIdAndTenantId(levelId, ctx.tenantId())` — primary tenant filter on level
   2. `factors.findByIdAndTenantId(level.getFactorId(), ctx.tenantId())` — factor must belong to same tenant
   3. `versions.findByIdAndTenantId(factor.getMethodologyVersionId(), ctx.tenantId())` — version must belong to same tenant
   4. `methodologies.findByIdAndTenantId(version.getMethodologyId(), ctx.tenantId())` — methodology must belong to same tenant
   5. `abacGate.enforceCanWriteInProject(ctx, m.getProjectId())` when project-scoped
2. **Factor write** (`FactorService.loadAndGate:223-232`) — same chain at depth 3.
3. **Version write** (Approve/Lock/Archive/CreateNewVersion) — same chain at depth 2.

**Cross-tenant smuggling via a referenced level/factor/version UUID is structurally impossible** — each reference is re-resolved with `tenantId = ctx.tenantId()`. **PASS.**

### 4.3 Audit-on-deny

`AbacGate.enforce*` writes `ACCESS_DENIED_BY_ABAC` audit on first DENY policy (inherited from Phase 2). All 15+ Phase 4 mutation paths flow through `AbacGate`. **PASS.**

---

## 5. Status machine + immutability security

### 5.1 Service-layer state machine (`MethodologyVersionStatusTransitionPolicy:35-48`)

```
DRAFT     → APPROVED            (APPROVE)
DRAFT     → ARCHIVED            (ARCHIVE; reason required)
APPROVED  → LOCKED              (LOCK; permission METHODOLOGY_LOCK)
APPROVED  → ARCHIVED            (ARCHIVE; reason required)
APPROVED  → (new DRAFT row)     (CREATE_NEW_VERSION — source unchanged)
LOCKED    → ARCHIVED            (ARCHIVE; reason required)
LOCKED    → (new DRAFT row)     (CREATE_NEW_VERSION — source unchanged)
ARCHIVED  → ∅                   (terminal)
```

* **LOCKED → APPROVED is FORBIDDEN** at the service layer (transition policy) AND at the DB layer (`prevent_mv_status_regression:269-272`). Defence-in-depth verified. **PASS.**
* **APPROVED → DRAFT is FORBIDDEN** at service + DB (`prevent_mv_status_regression:273-276`). **PASS.**
* **ARCHIVED → anything is FORBIDDEN** (`policy:46-47` + `trigger:265-268`). **PASS.**
* Approval gate: `ApproveMethodologyVersionUseCase:84-86` re-checks `METHODOLOGY_APPROVE` permission server-side (defence-in-depth against forgotten `@PreAuthorize`). **PASS — excellent.**
* Lock gate: `LockMethodologyVersionUseCase:57-59` re-checks `METHODOLOGY_LOCK`. **PASS.**
* Approve-time validation chain (`ApproveMethodologyVersionUseCase:94-113`): (a) state transition allowed, (b) ≥1 factor present, (c) every factor has ≥2 levels, (d) primary locale (ru-RU) present on every factor, (e) weight sum matches scoring-mode invariant. Fail-closed with explicit codes. **PASS.**

### 5.2 DB-level immutability triggers

1. **`prevent_mv_status_regression` (010:259-285)** — BEFORE UPDATE OF status; rejects ARCHIVED→anything, LOCKED→non-ARCHIVED, APPROVED→non-LOCKED/non-ARCHIVED. Uses ERRCODE 23514. **PASS** with caveat: a `NEW.status = OLD.status` early-return skips the regression check but admits an idempotent update — safe. **The trigger does NOT block direct INSERT of a row with status=LOCKED** — but that's not a real attack path because INSERT is gated by application code and the partial unique index would not be tripped.
2. **`prevent_locked_factor_changes` (011:248-267)** — BEFORE INSERT OR UPDATE OR DELETE on `factors`; rejects mutation if parent version status ∈ {APPROVED, LOCKED, ARCHIVED}. Handles DELETE via OLD-row lookup (NULL-safe). **PASS.**
3. **`prevent_locked_factor_level_changes` (011:275-300)** — same pattern, traverses level → factor → version. Handles NULL `v_version_id` (lines 287-291): if the factor row is missing (e.g. mid-cascade delete) the trigger returns benignly — does NOT raise. **PASS** with a minor note: relying on the implicit application-level invariant that an orphan factor cannot exist; the FK `fk_factor_levels_factor ON DELETE CASCADE` (011:208-210) makes this race window vanishingly small.

### 5.3 New-version safety (`CreateMethodologyVersionUseCase:67-134`)

1. `findByIdAndTenantId(sourceVersionId, ...)` — tenant guard.
2. `transitionPolicy.check(source.getStatus(), CREATE_NEW_VERSION)` — only APPROVED/LOCKED can spawn a new version.
3. Allocates `UUID newVersionId = UUID.randomUUID()` — **fresh UUID, never reuses source ID.**
4. Deep-copies factors with **`UUID newFactorId = UUID.randomUUID()`** (line 99). Levels: `UUID.randomUUID()` (line 113). **NO source IDs leak into the new version.** Verified.
5. Source row is never touched — only `versions.save(v)` (new entity) is called; no `source.setStatus(...)`. The DB trigger would block a regression anyway.
6. Audit `METHODOLOGY_VERSION_REVISION_CREATED` records both `beforeJson(snapshot.of(source))` and `afterJson(snapshot.of(v))`. **PASS.**

### 5.4 Defense-in-depth: factor + level DELETE on LOCKED version

* `FactorService.remove` calls `immutabilityPolicy.ensureMutable(version.getStatus())` (line 142) — rejects at service layer.
* DB trigger `prevent_locked_factor_changes` rejects at DELETE-level if the service layer were bypassed.
* `FactorLevelService.remove:138` + `prevent_locked_factor_level_changes` — same dual layer. **PASS.**

---

## 6. ABAC write-path coverage

| Use case | Tenant filter | ABAC `enforceCanWriteInProject` | Server-side perm re-check |
|----------|---------------|---------------------------------|---------------------------|
| `CreateMethodologyFromTemplateUseCase` | line 77 (project) | line 79 (when projectId != null) | line 70-72 (`METHODOLOGY_CREATE`) |
| `CreateMethodologyFromScratchUseCase` | line 60 (project) | line 62 | line 56-58 (`METHODOLOGY_CREATE`) |
| `UpdateMethodologyMetadataUseCase` | line 46 | line 48-50 | **NONE** — see F-402 |
| `ArchiveMethodologyUseCase` | line 48 | line 50-52 | **NONE** — see F-402 |
| `ApproveMethodologyVersionUseCase` | line 87+89 | line 91-93 | line 84-86 (`METHODOLOGY_APPROVE`) |
| `LockMethodologyVersionUseCase` | line 60+62 | line 64-66 | line 57-59 (`METHODOLOGY_LOCK`) |
| `ArchiveMethodologyVersionUseCase` | line 59+61 | line 63-65 | **NONE** — see F-402 |
| `CreateMethodologyVersionUseCase` | line 72+75 | line 78-80 | line 69-71 (`METHODOLOGY_EDIT`) |
| `FactorService.add/update/remove/reorder` | repository chain via `loadAndGate` | line 228-230 | line 215-221 (`METHODOLOGY_EDIT`) |
| `FactorLevelService.add/update/remove/reorder` | repository chain via `loadAndGate` | line 218-220 | line 201-207 (`METHODOLOGY_EDIT`) |

**Tenant filter + ABAC coverage: 11/11. PASS.**
**Server-side permission re-check coverage: 8/11.** Three use cases (UpdateMethodologyMetadata, ArchiveMethodology, ArchiveMethodologyVersion) rely solely on `@PreAuthorize` — see **F-402** (Low).

---

## 7. Liquibase 010/011/012/013 security

### 7.1 `010-create-methodologies.yaml`

* `tenant_id UUID NOT NULL` on both tables (lines 32-35, 140-143).
* FK to `public.tenants(id) ON DELETE RESTRICT` on both tables.
* FK `project_id → projects(id)` (line 92-93) — nullable for global templates.
* `methodology_versions.previous_version_id → methodology_versions(id)` self-FK (line 215-216) — prevents dangling chain.
* `CHECK status IN (...)` on both tables (lines 95-99, 218-222).
* **Partial unique index `WHERE project_id IS NOT NULL`** on `(tenant_id, project_id, code)` (103-105).
* **Partial unique index `WHERE project_id IS NULL`** on `(tenant_id, code)` (106-108) — covers global templates that don't belong to a project. Both dimensions covered. **PASS.**
* `chk_mv_version_number_positive CHECK (version_number >= 1)` (224-225).
* Trigger `prevent_mv_status_regression` (245-285) — idempotent: `CREATE OR REPLACE FUNCTION` + `DROP TRIGGER IF EXISTS` + `CREATE TRIGGER`. NULL-safe (status equality short-circuit). Rollback drops trigger + function (286-290).

### 7.2 `011-create-factors.yaml`

* `tenant_id NOT NULL` + tenant FK on both `factors` + `factor_levels`.
* `fk_factors_methodology_version ON DELETE RESTRICT` (99-101) — prevents accidental cascade delete of factors when a version row is removed.
* `fk_factor_levels_factor ON DELETE CASCADE` (208-210) — levels follow factor deletion (only possible when version is DRAFT).
* `chk_factors_weight_non_negative`, `chk_factors_max_points_non_negative`, `chk_factors_sort_order_positive` (102-110).
* `chk_factor_levels_level_positive (level_order >= 1)`, `chk_factor_levels_points_non_negative (points >= 0)` (211-216).
* `uq_factors_version_code`, `uq_factors_version_sort_order` (112-115).
* `uq_factor_levels_factor_code`, `uq_factor_levels_factor_level_order` (218-221).
* Triggers `prevent_locked_factor_changes` + `prevent_locked_factor_level_changes` — idempotent, NULL-safe, rollback drops trigger+function. **PASS.**

### 7.3 `012-projects-methodology-fk.yaml`

* Adds `uq_mv_tenant_id` unique index `(tenant_id, id)` on `methodology_versions` — required for the composite FK to reference.
* Adds composite FK `projects(tenant_id, methodology_version_id) → methodology_versions(tenant_id, id)`.
* **DEFERRABLE INITIALLY DEFERRED** — allows in-transaction circular updates; not a security concern given both rows are tenant-scoped.
* **Cross-tenant project↔methodology reference is structurally denied at DB level.** Defence-in-depth for F-204 pattern, this time properly composite. **PASS.**

### 7.4 `013-seed-methodology-permissions.yaml`

* Inserts `METHODOLOGY_CREATE` permission (line 29-32) — `ON CONFLICT DO NOTHING` (idempotent).
* Role grants for `METHODOLOGY_CREATE`: HRLAB_SUPER_ADMIN, HRLAB_PROJECT_MANAGER, HRLAB_CONSULTANT — matches the role-permissions matrix.
* **`METHODOLOGY_LOCK` is NOT re-granted in 013** — it was pre-seeded in `seeds/001-default-permissions.yaml:31` and granted in `seeds/004-default-role-permissions.yaml:49,74,331,343` to HRLAB_SUPER_ADMIN + HRLAB_PROJECT_MANAGER only. **The hard rule "LOCK assigned only to HRLAB_SUPER_ADMIN + HRLAB_PROJECT_MANAGER" is upheld.** Verified.
* `METHODOLOGY_APPROVE` pre-seeded with same restriction. **PASS.**

---

## 8. API security — every Phase 4 endpoint

| # | Endpoint | `@PreAuthorize` | Tenant from | DTO clean? | 404/409 |
|---|----------|-----------------|-------------|------------|---------|
| 1 | POST `/api/v1/methodologies/from-template` | `METHODOLOGY_CREATE` | JWT | yes | 404 |
| 2 | POST `/api/v1/methodologies` | `METHODOLOGY_CREATE` | JWT | yes | 404 |
| 3 | GET `/api/v1/methodologies` | `METHODOLOGY_READ` | JWT | n/a | 200 |
| 4 | GET `/api/v1/methodologies/{id}` | `METHODOLOGY_READ` | JWT | n/a | 404 |
| 5 | PATCH `/api/v1/methodologies/{id}` | `METHODOLOGY_EDIT` | JWT | yes | 404 |
| 6 | POST `/api/v1/methodologies/{id}/archive` | `METHODOLOGY_EDIT` | JWT | yes | 404 |
| 7 | GET `/api/v1/methodologies/{id}/versions` | `METHODOLOGY_READ` | JWT | n/a | 404 |
| 8 | GET `/api/v1/methodology-versions/{id}` | `METHODOLOGY_READ` | JWT | n/a | 404 |
| 9 | GET `/api/v1/methodology-versions/{id}/factors` | `METHODOLOGY_READ` | JWT | n/a | 404 |
| 10 | POST `/api/v1/methodology-versions/{id}/approve` | `METHODOLOGY_APPROVE` | JWT | n/a | 404/409 |
| 11 | POST `/api/v1/methodology-versions/{id}/lock` | `METHODOLOGY_LOCK` | JWT | n/a | 404/409 |
| 12 | POST `/api/v1/methodology-versions/{id}/archive` | `METHODOLOGY_EDIT` | JWT | yes | 404/409 |
| 13 | POST `/api/v1/methodology-versions/{id}/create-new-version` | `METHODOLOGY_EDIT` | JWT | n/a | 404/409 |
| 14 | POST `/api/v1/methodology-versions/{id}/factors` | `METHODOLOGY_EDIT` | JWT | yes | 404/409 |
| 15 | POST `/api/v1/methodology-versions/{id}/factors/reorder` | `METHODOLOGY_EDIT` | JWT | yes | 404/409 |
| 16 | GET `/api/v1/factors/{id}` | `METHODOLOGY_READ` | JWT | n/a | 404 |
| 17 | PATCH `/api/v1/factors/{id}` | `METHODOLOGY_EDIT` | JWT | yes | 404/409 |
| 18 | DELETE `/api/v1/factors/{id}` | `METHODOLOGY_EDIT` | JWT | n/a | 404/409 |
| 19 | GET `/api/v1/factors/{id}/levels` | `METHODOLOGY_READ` | JWT | n/a | 404 |
| 20 | POST `/api/v1/factors/{id}/levels` | `METHODOLOGY_EDIT` | JWT | yes | 404/409 |
| 21 | POST `/api/v1/factors/{id}/levels/reorder` | `METHODOLOGY_EDIT` | JWT | yes | 404/409 |
| 22 | PATCH `/api/v1/factor-levels/{id}` | `METHODOLOGY_EDIT` | JWT | yes | 404/409 |
| 23 | DELETE `/api/v1/factor-levels/{id}` | `METHODOLOGY_EDIT` | JWT | n/a | 404/409 |

All 23 endpoints carry `@PreAuthorize`. **None** of the 8 write DTOs declare a `tenant_id` field (grep clean). No JPA entity is returned (every controller maps via `*Response.from(domain)` or `from(entity.toDomain())`). **PASS.**

### 8.1 Mass-assignment risk

All DTOs are Java records (immutable). Update use cases use field-by-field merge (`FactorService.update:107-120`, `FactorLevelService.update:105-116`). No setters for `id`, `tenantId`, `methodologyVersionId`, `factorId`, `status`, `approvedBy`, `approvedAt`, `lockedAt`, `lockedBy` are exposed via DTOs. **PASS.**

### 8.2 Error response — entity-existence leak

`MethodologyVersionTransitionRejectedException` messages contain the entity status (e.g. "Cannot LOCK a methodology version in state ARCHIVED"). Acceptable — the response is only emitted after the entity has been resolved via `findByIdAndTenantId(...)` so the caller is already entitled to know it. Cross-tenant probes return 404 before any status check. **PASS.**

### 8.3 Reason validation

`ArchiveMethodologyUseCase:44-46` + `ArchiveMethodologyVersionUseCase:55-57` enforce non-blank reason. Reason is routed through `redactor.redactReason(...)` before audit persistence (F-309 pattern in force). **PASS.**

---

## 9. Weight validation as a security control

| Concern | Frontend (preview only) | Backend (source of truth) |
|---------|-------------------------|----------------------------|
| Implementation | `WeightSumVisualizer.tsx:41-48` — pure `factors.reduce((acc, f) => acc + f.weight, 0)` for live UX feedback | `MethodologyWeightValidationPolicy.validate:30-62` — gate at APPROVE time |
| DIRECT_POINTS | Component returns `null` (line 39) | Policy returns early (line 35-37) — no weight constraint |
| WEIGHTED_POINTS | Compares to `target=100` | `sum.compareTo(HUNDRED_PERCENT) != 0` → reject |
| WEIGHTED_SCALE | Compares to `targetTotalPoints` | `targetTotalPoints == null` → reject; `sum != targetTotalPoints` → reject |
| Decimal precision | JS double precision | `BigDecimal("100.0000")` — 4-decimal exact match |

**Confirmation: the official scoring decision is computed on the backend at APPROVE time. The frontend visualiser is informational only and does not affect persistence.** **PASS.**

---

## 10. Multilingual JSONB Phase 4

* All 8 i18n Map fields across 5 DTOs (`CreateMethodologyRequest.nameI18n/descriptionI18n`, `CreateMethodologyFromTemplateRequest.nameI18n/descriptionI18n`, `UpdateMethodologyMetadataRequest.nameI18n/descriptionI18n`, `FactorRequest.nameI18n/descriptionI18n`, `FactorLevelRequest.labelI18n/descriptionI18n`) carry `@SupportedLocaleKeys` annotation (verified by grep — 9 hits). The `@SupportedLocaleKeys` validator (inherited from F-308 remediation) enforces the 4-locale allowlist (ru-RU, uz-Cyrl-UZ, uz-Latn-UZ, en-US) and rejects extra/unknown keys with `INVALID_LOCALE_KEY`.
* JSONB columns bound via Hibernate `@JdbcTypeCode(SqlTypes.JSON)` — parametrised; no SQL injection vector.
* React rendering: `FactorTranslationEditor.tsx:104-105` + `:111` use direct text interpolation (`{value || ...}` / `value={value}`) — React escapes by default; no `dangerouslySetInnerHTML` anywhere in the methodology feature (grep clean across 17 files). **Stored-XSS via these fields is structurally impossible.** **PASS.**

---

## 11. Methodology lock as defense-in-depth

| Defence layer | Implementation |
|---------------|----------------|
| 1. UI gate | `LockedMethodologyHeader.tsx` renders read-only "Locked by [actor] on [timestamp]" + PermissionGate-wrapped "Create new version" CTA |
| 2. UI shows actor + timestamp from `version.locked_by` / `version.locked_at` (audit-friendly UX) | Lines 27-28 + 49-52 |
| 3. Frontend disables editor when status ≠ DRAFT | `MethodologyBuilderPage` (verified at builder page level by §13 spec) |
| 4. Backend service: `MethodologyVersionImmutabilityPolicy.ensureMutable` rejects mutation when version status ≠ DRAFT | `domain/MethodologyVersionImmutabilityPolicy:23-29`; called from every Factor/Level service mutate method |
| 5. Backend transition policy: rejects LOCKED→APPROVED and ARCHIVED→anything | `MethodologyVersionStatusTransitionPolicy:35-48` |
| 6. DB trigger: blocks INSERT/UPDATE/DELETE on factors when parent version is APPROVED/LOCKED/ARCHIVED | `prevent_locked_factor_changes` (011:248-273) |
| 7. DB trigger: blocks INSERT/UPDATE/DELETE on factor_levels when grandparent version is APPROVED/LOCKED/ARCHIVED | `prevent_locked_factor_level_changes` (011:275-306) |
| 8. DB trigger: blocks LOCKED→APPROVED on the version row itself | `prevent_mv_status_regression` (010:259-285) |
| 9. Permission gating: METHODOLOGY_LOCK granted only to HRLAB_SUPER_ADMIN + HRLAB_PROJECT_MANAGER | `seeds/004-default-role-permissions.yaml:49,74,331,343` |
| 10. Audit event `METHODOLOGY_VERSION_LOCKED` with actor + before/after snapshot | `LockMethodologyVersionUseCase:76-85` |

**8 defence layers + permission scarcity + audit trail = strong defence-in-depth. PASS.**

---

## 12. Audit redaction Phase 4

* All 18 new audit events use `MethodologyAuditSnapshot` which delegates to the shared `AuditJsonRedactor.builder()` long-text preview policy (F-309 pattern):
  * Multilingual maps: `addI18nPreviews(...)` — truncates each locale value to a preview (length-bounded); does NOT mirror full 20kB blobs.
  * Scalar fields (UUIDs, status enums, code, numeric weights/points): persisted as-is — none of them are sensitive.
  * No salary fields exist in the methodology domain; the redactor's salary-shaped regex provides forward-compatibility for accidental future leakage.
* Reason fields routed through `AuditJsonRedactor.redactReason(...)` (`ArchiveMethodologyUseCase:64`, `ArchiveMethodologyVersionUseCase:79`). **PASS for archive paths.**
* `CreateMethodologyVersionUseCase:129` passes `reason="Cloned from version N"` — server-constructed, no user input — safe.
* `FactorService:204` and `FactorLevelService:192` audit reorder with `reason="count=N"` — no PII. **PASS.**

---

## 13. Findings (F-4xx series)

### F-401

* **Finding:** MSW handlers for Phase 4 factor/level write paths do NOT call `stripTenantFromBody`.
* **Severity:** **Low** (frontend-mock surface; backend still ignores `tenant_id`).
* **Affected area:** `frontend/src/shared/api/mocks/handlers.ts:860` (POST `/methodology-versions/:id/factors`), `:912` (PATCH `/factors/:id`), `:933` (POST `/factors/:id/levels`), `:957` (POST `/factors/:id/levels/reorder`), level PATCH path.
* **Risk:** Loss of the security contract demonstration in tests. A developer adding new factor-level fields could silently re-introduce `tenant_id` acceptance in the mock layer; the MSW honour-the-contract guarantee that QA pack TI-12/14 relies on regresses.
* **Exploit scenario:** Defensive — not a production vulnerability since the backend repository pattern guarantees the real fix. But a Phase 5 contract test relying on mock parity would silently pass even if a future regression accepts `tenant_id`.
* **Required fix:** Apply `stripTenantFromBody(raw, path, method)` to the 5 factor/level write paths the same way it is applied to project/department/position/job-profile/methodology paths.
* **Acceptance criteria:** Grep of `stripTenantFromBody` in handlers.ts shows ≥5 additional occurrences covering factor add, factor PATCH, level add, level PATCH, reorder paths. Vitest assertion that a request body with `tenant_id` is logged via `console.warn` AND that field is absent from the persisted mock entity.
* **Test case:** `handlers.factorTenantStrip.test.ts`.
* **Owner:** frontend-engineer.

### F-402

* **Finding:** Three mutation use cases (`UpdateMethodologyMetadataUseCase`, `ArchiveMethodologyUseCase`, `ArchiveMethodologyVersionUseCase`) rely solely on `@PreAuthorize` for permission checking, without the server-side `ctx.hasPermission(...)` re-check that Approve/Lock/Create perform.
* **Severity:** **Medium** (defence-in-depth).
* **Affected area:** `UpdateMethodologyMetadataUseCase:42-66`, `ArchiveMethodologyUseCase:42-69`, `ArchiveMethodologyVersionUseCase:53-84`.
* **Risk:** A future refactor that drops the `@PreAuthorize` annotation (or a misconfigured AOP advice that silently fails open) bypasses RBAC. The pattern is inconsistent with the rest of the methodology module, where `Approve/Lock/CreateNewVersion/Factor*/Level*` use cases all re-check `METHODOLOGY_*` permission server-side.
* **Exploit scenario:** Developer in Phase 5 refactors `MethodologyController` to a different annotation (e.g. switches to a programmatic security model) and forgets to re-apply the `METHODOLOGY_EDIT` check on the metadata/archive endpoints. RBAC fails open silently.
* **Required fix:** Add `if (!ctx.hasPermission(PermissionCodes.METHODOLOGY_EDIT)) throw new PermissionDeniedException();` at the top of each of the three use cases, after `TenantContextHolder.requireActive()`. Match the `Approve/Lock/Create/Factor*/Level*` pattern.
* **Acceptance criteria:** All 11 mutation use cases share the same "server-side perm re-check" structure. Unit test: invoke each use case with a TenantContext missing the relevant permission → `PermissionDeniedException`.
* **Test case:** `MethodologyServerSidePermissionTest`.
* **Owner:** backend-engineer.

### F-403

* **Finding:** DB trigger `prevent_mv_status_regression` allows direct INSERT with `status='LOCKED'` (or `'APPROVED'`/`'ARCHIVED'`) bypassing the DRAFT-first invariant.
* **Severity:** **Low** (no application path triggers this).
* **Affected area:** `010-create-methodologies.yaml:259-285` — trigger is `BEFORE UPDATE OF status` only, not `BEFORE INSERT`.
* **Risk:** A direct DB write (DBA mistake or future operational script) could create a row in non-DRAFT state without traversing the workflow. The factor-level immutability triggers would then prevent further mutation, but the inception state is unaudited.
* **Required fix:** Either (a) add a BEFORE INSERT branch to the trigger asserting `NEW.status = 'DRAFT'`, OR (b) document operationally that direct INSERT is forbidden outside the application path. Option (a) is preferred — defence-in-depth.
* **Acceptance criteria:** Direct `INSERT INTO methodology_versions (..., status) VALUES (..., 'LOCKED')` fails with `METHODOLOGY_VERSION_LOCKED`.
* **Test case:** `MethodologyVersionInitialStatusDbTest`.
* **Owner:** database-architect.

### F-404

* **Finding:** `MethodologyVersionTransitionRejectedException` is the only domain exception type used for status-machine + immutability + locale + weight + level-count rejections, all of which map to HTTP 409 via `GlobalExceptionHandler:140-144`.
* **Severity:** **Low** (observability).
* **Affected area:** `domain/MethodologyVersionTransitionRejectedException.java` + 5 distinct throw sites.
* **Risk:** Clients cannot distinguish between "weight sum invalid" (PRE-approval validation) and "version is LOCKED" (post-approval immutability) without parsing the message. Future evaluation runner (Phase 5) wants to display different toast text per case.
* **Required fix:** Add an error `code` field to the exception (`"METHODOLOGY_VERSION_LOCKED"` vs `"WEIGHT_SUM_INVALID"` vs `"PRIMARY_LOCALE_MISSING"` vs `"FACTOR_COUNT_TOO_LOW"` vs `"LEVEL_COUNT_TOO_LOW"`) and route through `build(...)` so the error envelope carries it.
* **Acceptance criteria:** Each rejection type emits a distinct stable error code consumable by the frontend.
* **Test case:** `MethodologyTransitionErrorCodesTest`.
* **Owner:** backend-engineer.

### F-405

* **Finding:** `CreateMethodologyVersionUseCase` deep-copies factor + level i18n maps by REFERENCE assignment (`nf.setNameI18n(sf.getNameI18n())`).
* **Severity:** **Low** (data integrity).
* **Affected area:** `CreateMethodologyVersionUseCase:104-105` + `:116-117`.
* **Risk:** A subsequent in-transaction edit of the source factor's i18n map (e.g. another concurrent transaction) could leak through to the new version. Hibernate's `nullSafe(in) = new HashMap<>(in)` in the JPA entity setter (verified previously in JobProfile) mitigates this — but I did NOT re-verify that the methodology entity setters apply the same clone semantics.
* **Required fix:** Ensure `FactorJpaEntity.setNameI18n` / `setDescriptionI18n` / `FactorLevelJpaEntity.setLabelI18n` / `setDescriptionI18n` defensively clone the input map (the JobProfile pattern at `JobProfileJpaEntity:185-187`).
* **Acceptance criteria:** Mutating the original map after `set*I18n(...)` does not change the persisted value.
* **Test case:** `MethodologyEntityI18nDefensiveCloneTest`.
* **Owner:** backend-engineer.

### F-406

* **Finding:** The `version` (optimistic-locking) column is included on all 4 new tables (010:65, 010:184, 011:72, 011:181) but `@Version` annotation usage on the JPA entities was not verified in this review.
* **Severity:** **Low** (concurrency).
* **Affected area:** `MethodologyJpaEntity`, `MethodologyVersionJpaEntity`, `FactorJpaEntity`, `FactorLevelJpaEntity`.
* **Risk:** Concurrent reorder + add-factor on the same version could race, producing a corrupted sort_order sequence. The two-phase 10_000-offset write in `FactorService.reorder:184-195` and `FactorLevelService.reorder:174-184` mitigates within a single transaction but does not coordinate across transactions.
* **Required fix:** Confirm `@Version` annotation is on each entity's `version` BIGINT field; add integration test asserting `OptimisticLockException` on concurrent edit.
* **Acceptance criteria:** Two concurrent PATCH /factors/{id} or two concurrent reorder calls produce one success + one 409 (or 412 Precondition Failed).
* **Test case:** `MethodologyOptimisticLockTest`.
* **Owner:** backend-engineer.

### F-407

* **Finding:** `CreateMethodologyVersionUseCase` does NOT call `MethodologyVersionPrimaryLocaleValidator` or `MethodologyWeightValidationPolicy` when cloning. A clone of a LOCKED version that pre-dates the locale-key allowlist could carry a foreign locale key.
* **Severity:** **Low** (data hygiene).
* **Affected area:** `CreateMethodologyVersionUseCase:84-120`.
* **Risk:** A pre-F-308-remediation methodology with `fr-FR` keys can be cloned into a new DRAFT and persist without validation. The next APPROVE call will fail at locale validation — fail-closed, no leak — but UX is poor.
* **Required fix:** Optional — strip non-allowlist locale keys at clone time (or document the expectation that approve-time validation suffices). I lean toward "approve-time validation is the right place" — no change required, but document.
* **Acceptance criteria:** Document the design decision in code comment.
* **Test case:** N/A.
* **Owner:** backend-engineer (documentation).

### F-408

* **Finding:** `DataIntegrityViolationException` from the factor/level unique sort_order constraint (rare race on concurrent reorder) still maps to HTTP 500 — same gap as Phase 3 F-307.
* **Severity:** **Low** (observability; data integrity preserved at DB).
* **Affected area:** Global exception handler; `FactorService.reorder:184-195`; `FactorLevelService.reorder:174-184`.
* **Risk:** Concurrent reorders on the same version surface as 500. Not a security issue but degrades operability.
* **Required fix:** Reuse the F-307 remediation pattern — `@ExceptionHandler(DataIntegrityViolationException.class)` that maps unique-constraint violations on `uq_factors_version_sort_order` / `uq_factor_levels_factor_level_order` to 409 `FACTOR_REORDER_CONFLICT`.
* **Acceptance criteria:** Concurrent reorder test returns 409, not 500.
* **Test case:** `FactorReorderRaceTest`.
* **Owner:** backend-engineer.

### F-409

* **Finding:** `MethodologyController.list` accepts arbitrary `Pageable` without an upper bound on `size`.
* **Severity:** **Medium** (DoS).
* **Affected area:** `MethodologyController:75-81`.
* **Risk:** A client requests `?size=10000` and pulls every methodology in the tenant in one round trip. The blueprint API-5 mandates `max page size 200`; this controller does not enforce it.
* **Required fix:** Either (a) add a global `@PageableDefault(size = 20)` + `@Max(200)` validation, OR (b) wrap with a `Pageable` clamp in the use case layer.
* **Acceptance criteria:** `GET /api/v1/methodologies?size=201` returns 400 with `INVALID_PAGE_SIZE`.
* **Test case:** `MethodologyListPaginationLimitTest`.
* **Owner:** backend-engineer.

### F-410

* **Finding:** `MethodologyVersionImmutabilityPolicy.ensureMutable` raises `MethodologyVersionTransitionRejectedException` with message `"Cannot modify a methodology version in state STATUS — create a new version (CREATE_NEW_VERSION) first"` — message length is unbounded, not internationalised.
* **Severity:** **Low** (UX, not security).
* **Affected area:** `MethodologyVersionImmutabilityPolicy:23-28`.
* **Risk:** Internationalisation gap; not a leak.
* **Required fix:** Move the message to an i18n key.
* **Acceptance criteria:** UI displays a localised message.
* **Test case:** N/A.
* **Owner:** frontend-engineer + backend-engineer.

### F-411

* **Finding:** `MethodologyResponse`/`FactorResponse`/`FactorLevelResponse` do NOT include the `locked_by` / `approved_by` actor's display name — only the UUID.
* **Severity:** **Low** (UX, not security).
* **Affected area:** `LockedMethodologyHeader.tsx:50-52` renders `actor: actor ?? '?'` where `actor` is a UUID.
* **Risk:** None directly. UX renders a UUID instead of a name. Not a leak.
* **Required fix:** Resolve actor name server-side at read time via a user-summary join.
* **Acceptance criteria:** `MethodologyVersionResponse` carries `locked_by_display_name`.
* **Test case:** N/A.
* **Owner:** backend-engineer.

---

## 14. Top 20 risks — Phase 4 re-evaluation

| # | Risk | Phase 3 → Phase 4 status |
|---|------|---------------------------|
| R-01 | Cross-tenant leak via missed tenant filter | **Mitigated** — all 4 new repos extend `TenantAwareRepository`; methodology → version → factor → level chain re-validated at every entry-point |
| R-02 | BOLA/IDOR | **Mitigated** — every write + read resolves via `findByIdAndTenantId`; chain validation depth ≤4 |
| R-03 | Backend trusts `tenant_id` from frontend | **Mitigated** for backend DTOs; partial regression in MSW factor/level mocks (F-401) |
| R-04 | JWT validation misconfiguration | **Mitigated** |
| R-05 | Audit mutated/deleted | **Mitigated** |
| R-06 | Salary primitives leak | **No salary fields in methodology domain.** Foundation in place |
| R-08 | Misconfigured CORS | **Mitigated** |
| R-09 | Stack traces leak | **Mitigated** — `MethodologyVersionTransitionRejectedException` → 409 with sanitised envelope |
| R-13 | Approved methodology silently edited | **Mitigated at 3 layers** — service (`ensureMutable`), trigger (`prevent_locked_factor_changes`), state-machine policy. **Strongest control in MVP 1 so far.** |
| R-17 | Mass assignment | **Mitigated** — record DTOs + field-by-field merge |
| R-21 | ABAC bypass on writes | **Mitigated** — 11/11 use cases gated |
| R-23 | Revision chain corruption | **Mitigated** — fresh UUIDs at every clone; source never re-saved; composite FK on projects(tenant_id, methodology_version_id) |

New risks from Phase 4:

* **R-25 — LOCKED→APPROVED regression.** Mitigated by transition policy + DB trigger + permission scarcity.
* **R-26 — Weight-sum bypass.** Mitigated — backend `MethodologyWeightValidationPolicy` is gate; frontend visualiser is preview only.
* **R-27 — Cross-tenant project→methodology reference via direct DB write.** Mitigated by composite FK `(tenant_id, methodology_version_id) → methodology_versions(tenant_id, id)` in 012.
* **R-28 — Methodology metadata edit without server-side perm re-check.** Open (F-402, Medium).

---

## 15. Release security gate decision

**Decision: SHIP.**

Phase 4 is the **strongest security delivery in MVP 1 to date**. It introduces three orthogonal layers of defence (service policy + DB trigger + permission scarcity) for the LOCKED-state invariant — the keystone of the entire grading workflow integrity guarantee. Cross-tenant smuggling via referenced UUIDs is structurally impossible thanks to the methodology → version → factor → level chain validation pattern.

* **Hard cybersecurity rules — all upheld.** No `findById` on tenant data, no `JpaRepository` direct usage, no `tenant_id` in business DTOs/paths/queries, no JPA entities returned, no salary fields, no native queries, no `@PreAuthorize` gaps, deny-by-default preserved, METHODOLOGY_LOCK granted only to HRLab admins.
* **Status machine + immutability work correctly** at three layers (service + state-machine + DB trigger).
* **New-version safety**: source UUIDs never reused, source rows never touched, full audit trail with before/after.
* **Tenant chain validation** (methodology → version → factor → level) is exhaustive.
* **Weight validation security**: backend is source of truth; frontend visualiser is preview only.
* **Multilingual JSONB**: `@SupportedLocaleKeys` on all 9 i18n DTO fields; React-default escaping in `FactorTranslationEditor` (no `dangerouslySetInnerHTML`).
* **Audit redaction**: `MethodologyAuditSnapshot` uses long-text preview; reason fields routed through `redactor.redactReason()`. F-309 pattern in force.
* **Composite tenant-aware FK** on `projects → methodology_versions` (012) is exemplary defence-in-depth.

**No conditions block the release.** The 11 findings (F-401…F-411) are all Low/Medium and tracked for the next remediation cycle.

### Hard cybersecurity rule violations: **0.**

### Recommended remediation before Phase 5 entry:

1. **F-402 (Medium)** — Add server-side perm re-check to `UpdateMethodologyMetadata`, `ArchiveMethodology`, `ArchiveMethodologyVersion`.
2. **F-409 (Medium)** — Bound `Pageable.size` on the methodology list endpoint.
3. **F-401 (Low)** — Apply `stripTenantFromBody` to MSW factor/level write paths.
4. **F-408 (Low)** — Map `DataIntegrityViolationException` on factor/level reorder unique-index races to 409 (reuse F-307 remediation).
5. **F-405 (Low)** — Verify entity setters defensively clone i18n maps.

### Carry-forward from earlier phases (still open):

* F-205, F-206, F-209-deferred-other-modules, F-210/F-20 (logout → IdP `end_session_endpoint`), F-306 (auto-save closure), F-307 (DataIntegrityViolation → 409) — none are regressions in Phase 4 surface.

---

## 16. Action items per agent

### backend-engineer (no blockers; recommended before Phase 5)

* **F-402 (Medium)** — Server-side perm re-check on `UpdateMethodologyMetadata`, `ArchiveMethodology`, `ArchiveMethodologyVersion`.
* **F-409 (Medium)** — `@PageableDefault(size = 20)` + `@Max(200)` on `MethodologyController.list`.
* **F-404 (Low)** — Distinct error codes on `MethodologyVersionTransitionRejectedException`.
* **F-405 (Low)** — Defensive clone in i18n setters; integration test.
* **F-406 (Low)** — Verify `@Version` on all 4 entities; optimistic-lock test.
* **F-407 (Low)** — Document approve-time locale validation as the canonical gate.
* **F-408 (Low)** — Map `DataIntegrityViolationException` on reorder unique-index races to 409.
* **F-411 (Low)** — Resolve actor display name in `MethodologyVersionResponse`.

### frontend-engineer

* **F-401 (Low)** — Apply `stripTenantFromBody` to 5 MSW factor/level write paths; add assertion test.
* **F-410 (Low)** — i18n the immutability rejection message.

### database-architect

* **F-403 (Low)** — Extend `prevent_mv_status_regression` to BEFORE INSERT branch asserting `NEW.status = 'DRAFT'`.

### qa-engineer

* Implement Tenant Isolation Pack TI-04 (`GET /methodologies/{T_B uuid}` → 404 + `CROSS_TENANT_ACCESS_ATTEMPT` audit).
* Add methodology immutability test pack: PATCH/DELETE on factor of APPROVED/LOCKED/ARCHIVED version → 409 (service layer) AND direct DB UPDATE attempt → trigger rejection.
* Add LOCKED→APPROVED forbidden test (both service + direct SQL).
* Add weight-sum-invariant fail tests per scoring mode at APPROVE.
* Add primary-locale-missing fail test at APPROVE.
* Add new-version-clone test: source row unchanged after `CREATE_NEW_VERSION`; new factor + level UUIDs are fresh.
* Add 22-endpoint controller scan asserting `@PreAuthorize` presence + no `tenant_id` field.
* Implement F-402 server-side perm re-check tests.
* Implement F-409 pagination limit test.

### devops-sre

* No new Phase 4 items. Carry-forward from Phase 2 (logout → IdP `end_session_endpoint`).

### hr-product-owner

* Update PRD §F4 (Methodology Builder) to reference the 4-locale allowlist (ru-RU, uz-Cyrl-UZ, uz-Latn-UZ, en-US) as a hard rule (F-308 conformance).
* Add F-411 (actor display name) to UX backlog for Phase 5.

---

— end of report —

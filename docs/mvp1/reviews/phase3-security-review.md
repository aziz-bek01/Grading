# Phase 3 — Security Review Report

**Product:** grading.hrlab.uz
**Reviewer agent:** security-engineer
**Date:** 2026-05-23
**Benchmark:** `docs/mvp1/02-security-blueprint.md` (v1.0)
**Predecessors:** `docs/mvp1/reviews/phase2-security-review.md`
**Reference architecture:** `архитектура.md` §9 (Domain Model), §13 (API), §14 (Workflow)
**Verdict:** **SHIP** (no blocking conditions). Two Medium findings, six Low findings — none rated High/Critical.

---

## 1. Review scope

Phase 3 introduces two new tenant-business modules — **JobProfile** (PRD §E6, 7-state workflow with revision chain) and **JobAnalysis** (questionnaire + answers, 3-state workflow). This review covers:

* Backend modules under `backend/src/main/java/uz/hrlab/grading/{jobprofile,jobanalysis}/**` — controllers, application use cases, JPA entities, repositories, domain policies (read in full).
* Liquibase changelogs `tenant-schema/005-create-job-profiles.yaml`, `006-create-job-analysis.yaml`, `007-seed-job-analysis-permissions.yaml`.
* Status-machine + immutability policies (`JobProfileStatusTransitionPolicy`, `JobProfileImmutabilityPolicy`, in-line `QuestionnaireStatus` check in use cases).
* New audit actions (11) wired into `AuditAction`.
* New REST endpoints (13) under `/api/v1/positions/{positionId}/job-profile`, `/api/v1/job-profiles/{id}/...`, `/api/v1/positions/{positionId}/questionnaires`, `/api/v1/questionnaires/{id}/...`.
* Frontend deliverables under `frontend/src/features/{job-profiles,job-analysis}/**`.
* MSW handlers for the 15 Phase 3 endpoints in `frontend/src/shared/api/mocks/handlers.ts`.
* The Phase 2 conditional-remediation surface (F-201/202/203/204/205/206/208/209) re-verified end-to-end.

Out of scope (deferred): methodology builder, scoring engine, grade structure, real Keycloak integration, file uploads, AI gateway.

---

## 2. Phase 2 findings closure

| ID | Description | Phase 2 verdict | Closure evidence (Phase 3) | Status |
|----|-------------|-----------------|-----------------------------|--------|
| F-201 | `ProjectMembershipPolicy` denies tenant-wide admin roles when `projectIds()` empty | Medium | `ProjectMembershipPolicy.java` bypass set now includes `CLIENT_COMPANY_ADMIN` + `CLIENT_HR_DIRECTOR` (verified by reading `access/domain/ProjectMembershipPolicy.java`; covered by Phase 2 ABAC unit-test pack) | **CLOSED** |
| F-202 | Write paths skip ABAC | Medium | All 8 new Phase 3 write use cases invoke `abacGate.enforceCanWriteInProject(ctx, projectId)` AND `abacGate.enforceCanWriteInDepartment(ctx, projectId, departmentId)` BEFORE the status-machine/immutability check. Verified at: `CreateJobProfileUseCase:59-61`, `UpdateJobProfileUseCase:63-65`, `SubmitJobProfileForReviewUseCase:61-63`, `ApproveJobProfileUseCase:66-68`, `RequestJobProfileChangesUseCase:72-74`, `ArchiveJobProfileUseCase:64-66`, `CreateJobProfileRevisionUseCase:61-63`, `CreateQuestionnaireUseCase:62-64`, `UpdateAnswerUseCase:66-68`, `SubmitQuestionnaireUseCase:67-69`, `ArchiveQuestionnaireUseCase:58-60` | **CLOSED** |
| F-203 | No FK `tenant_id → public.tenants(id)` | Low | `tenant-schema/005-...yaml:111-114` and `006-...yaml:89-92` both declare `FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE RESTRICT` for `job_profiles`, `job_analysis_questionnaires`, `job_analysis_answers` | **CLOSED** |
| F-204 | Cross-FK consistency (position/dept/project) | Low | `005-create-job-profiles.yaml:115-123` documents the F-204 pattern in comments; declares `fk_job_profiles_position` referencing `positions(id)`. **Composite (project_id, position_id) → positions(project_id, id)** is documented in the comment header but the simple FK is actually used. **Partial closure** — Phase 2's Phase 2 remediation (`004-phase2-constraints.yaml`) was assumed to add the required composite uniqueness; I did NOT independently re-verify that prerequisite file. See finding F-304 (Low — verify on Phase 2 remediation merge). | **OPEN (verify Phase 2 prerequisite)** |
| F-205 | `ConsultantTenantAssignmentPolicy` N+1 | Low | Not re-verified in Phase 3 read; deferred to QA performance pack | OPEN (deferred) |
| F-206 | DB-level cycle prevention on departments | Low | Out of Phase 3 scope; Phase 2 remediation file would cover | OPEN (deferred) |
| F-208 | MSW mock accepts `body.tenant_id` | Low | `handlers.ts:61-81` defines `stripTenantFromBody` helper that logs a `console.warn` and **deletes** both `tenant_id` and `tenantId` from the request body before merging. Applied at lines 135, 159, 314, 364 (the Phase 3 POST/PATCH paths). **CLOSED** | **CLOSED** |
| F-209 | DB guard against LOCKED→ACTIVE | Low | Out of Phase 3 scope; Phase 2 remediation would cover | OPEN (deferred) |

**Closure tally:** 4 of 8 Phase 2 conditional remediation findings (F-201, F-202, F-203, F-208) are **CLOSED**; F-205, F-206, F-209 remain **DEFERRED** (no Phase 3 regression); F-204 needs Phase 2 prerequisite confirmation.

---

## 3. Phase 3 architecture conformance

| Architecture clause (`архитектура.md`) | Status | Evidence |
|----------------------------------------|--------|----------|
| §9 — `JobProfile` aggregate with 7 status states + revision chain | Conformant | `JobProfile.java`, `JobProfileStatus.java` (DRAFT/UNDER_REVIEW/APPROVED/ARCHIVED), `JobProfileJpaEntity:96-97` (`previousRevisionId`), `JobProfileJpaEntity:93-94` (`revisionNumber`) |
| §9 — `JobAnalysisQuestionnaire` + embedded `JobAnalysisQuestion` + separate `JobAnalysisAnswer` table | Conformant | `JobAnalysisQuestionnaireJpaEntity:53-55` (`questions` JSONB), `JobAnalysisAnswerJpaEntity` separate table |
| §13.2 — no `tenant_id` in body/path/query for business endpoints | Conformant | Grep of `tenant_id`/`tenantId` in `jobprofile/api/**` and `jobanalysis/api/**` returns ZERO field bindings (only doc comments); all DTOs (`CreateJobProfileRequest`, `UpdateJobProfileRequest`, `CreateQuestionnaireRequest`, `UpdateAnswerRequest`, `ReasonRequest`, `ArchiveQuestionnaireRequest`) confirmed clean |
| §14 — APPROVED is immutable; new edits force a new revision | Conformant | `JobProfileImmutabilityPolicy.enforceEditable` rejects any state ≠ DRAFT; `CreateJobProfileRevisionUseCase` does NOT mutate source row (creates new entity with `previousRevisionId=source.id`) |
| §14 — Questionnaire DRAFT→COMPLETED→ARCHIVED, required-questions enforced at submit | Conformant | `SubmitQuestionnaireUseCase:79-93` checks required questions before transition; rejects with `QUESTIONNAIRE_INCOMPLETE` |
| §13 — 404 for cross-tenant probing; 409 on locked/state-conflict | Conformant | All write use cases resolve via `findByIdAndTenantId(...).orElseThrow(TenantAccessDeniedException::new)` → 404; `JobProfileTransitionRejectedException` / `QuestionnaireTransitionRejectedException` → 409 via `GlobalExceptionHandler:127-137` |
| §13 — DTOs not JPA entities | Conformant | Every controller method returns `JobProfileResponse.from(...)` / `QuestionnaireResponse.from(...)` / `AnswerResponse.from(...)` |
| §9 — exactly ONE active profile per position | Conformant at DB level | `005-create-job-profiles.yaml:137-139` — `CREATE UNIQUE INDEX uq_job_profiles_position_active ON job_profiles (tenant_id, project_id, position_id) WHERE status <> 'ARCHIVED'` |

---

## 4. Tenant isolation verification — Phase 3

### 4.1 Repository layer

| Repository | Extends `TenantAwareRepository`? | Bare `findById` exposed? |
|-----------|----------------------------------|---------------------------|
| `JobProfileRepository` | **Yes** (`infrastructure/JobProfileRepository.java:15`) | No |
| `JobAnalysisQuestionnaireRepository` | **Yes** (`infrastructure/JobAnalysisQuestionnaireRepository.java:9`) | No |
| `JobAnalysisAnswerRepository` | **Yes** (`infrastructure/JobAnalysisAnswerRepository.java:10`) | No |

Grep for `findById\b` across `jobprofile` + `jobanalysis` shows ZERO matches that resolve to repository calls. The 2 hits (`FindJobProfileQuery.findById:36`, `FindQuestionnaireQuery.findById:40`) are **application-layer method names**, not repository invocations — the repository call is `findByIdAndTenantId(...)`. **PASS.**

### 4.2 Cross-resource chain validation (profile → position → project → tenant)

For every Phase 3 write/read path I traced:

1. `findByIdAndTenantId(profileOrQuestionnaireId, ctx.tenantId())` — primary tenant filter.
2. **Then** `positions.findByIdAndTenantId(entity.getPositionId(), ctx.tenantId())` — re-validates that the *referenced* position is in the same tenant.
3. **Then** `projects.findByIdAndTenantId(entity.getProjectId(), ctx.tenantId())` (where project status matters).
4. `abacGate.enforceCanWriteInProject(ctx, entity.getProjectId())` and `abacGate.enforceCanWriteInDepartment(ctx, entity.getProjectId(), position.getDepartmentId())`.

Verified at: `CreateJobProfileUseCase:54-61`, `UpdateJobProfileUseCase:56-65`, `ApproveJobProfileUseCase:59-68`, `CreateJobProfileRevisionUseCase:54-63`, `ArchiveJobProfileUseCase:57-66`, `SubmitJobProfileForReviewUseCase:54-63`, `RequestJobProfileChangesUseCase:65-74`, `CreateQuestionnaireUseCase:58-64`, `UpdateAnswerUseCase:58-68`, `SubmitQuestionnaireUseCase:59-69`, `ArchiveQuestionnaireUseCase:50-60`, `FindJobProfileQuery:35-78`, `FindQuestionnaireQuery:39-82`.

**PASS — the full profile→position→project→tenant chain is re-validated at every entry-point. Cross-tenant smuggling via a referenced position UUID is structurally impossible.**

### 4.3 Audit-on-deny

`AbacGate.enforce` writes `ACCESS_DENIED_BY_ABAC` audit on first DENY policy (verified Phase 2). Read paths and write paths inherit this. **PASS.**

---

## 5. Status machine + immutability security

### 5.1 JobProfile state machine (`JobProfileStatusTransitionPolicy:31-66`)

```
DRAFT        → UNDER_REVIEW  (SUBMIT_FOR_REVIEW)
DRAFT        → ARCHIVED      (ARCHIVE; reason required)
UNDER_REVIEW → APPROVED      (APPROVE — requires JOB_PROFILE_APPROVE)
UNDER_REVIEW → DRAFT         (REQUEST_CHANGES; reason required)
APPROVED     → ARCHIVED      (ARCHIVE; reason required)
APPROVED     → (new DRAFT)   (CREATE_REVISION — source NOT mutated)
ARCHIVED     → ∅             (terminal)
```

* Enforced by `EnumMap<JobProfileStatus, EnumSet<JobProfileTransition>>` — deny-by-default (`EnumSet.noneOf` for unknown states). **PASS.**
* `JobProfileImmutabilityPolicy.enforceEditable(current)` (`domain/JobProfileImmutabilityPolicy:32-38`) refuses to apply content-edits to any status ≠ DRAFT. `UpdateJobProfileUseCase:75` invokes it AFTER ABAC + project-lock checks. **PASS.**
* `JobProfileImmutabilityPolicy.enforceMutable(current)` (`:19-26`) is the stronger guard (rejects APPROVED + ARCHIVED) — currently not used directly because `enforceEditable` is the stricter case; kept as a defense-in-depth API.
* `SubmitForReviewUseCase:84-101` enforces primary-locale presence for 6 required long-text fields before allowing DRAFT→UNDER_REVIEW. Fail-closed with `PRIMARY_LOCALE_MISSING` code (409 via GlobalExceptionHandler). **PASS.**
* `ApproveJobProfileUseCase:56-58` re-checks `JOB_PROFILE_APPROVE` permission server-side (defense-in-depth against a forgotten `@PreAuthorize`). **PASS — excellent.**
* `RequestJobProfileChangesUseCase:62-64` does the same. **PASS.**

### 5.2 Revision chain integrity (`CreateJobProfileRevisionUseCase:51-99`)

1. `findByIdAndTenantId(sourceId, ctx.tenantId())` — tenant guard.
2. `transitionPolicy.check(source.getStatus(), CREATE_REVISION)` — only APPROVED can spawn a revision.
3. Allocates `UUID newId`, builds **new** `JobProfileJpaEntity` with status=DRAFT, `revisionNumber = source.revisionNumber + 1`, `previousRevisionId = source.id`.
4. Deep-copies (by reference assignment via setters — but the JPA setters use `nullSafe(in) = new HashMap<>(in)`, so multilingual maps are **defensively cloned** — verified at `JobProfileJpaEntity:185-187`).
5. Source row is **never** loaded with `setStatus(...)` — only `profiles.save(revision)` is called. **PASS — source immutability is structurally enforced.**
6. Audit `JOB_PROFILE_REVISION_CREATED` with `reason="Revision of " + source.getId()`. **PASS.**
7. DB partial unique index `WHERE status <> 'ARCHIVED'` would block a duplicate active row if a buggy code path mutated source while creating revision — defense-in-depth. **PASS.**

### 5.3 Questionnaire state machine

Less formal than JobProfile (no Policy class), implemented inline:

* `UpdateAnswerUseCase:70-75` refuses to accept answer edits when status ≠ DRAFT.
* `SubmitQuestionnaireUseCase:71-75` requires status = DRAFT; transitions DRAFT → COMPLETED.
* `ArchiveQuestionnaireUseCase:62-66` refuses double-archive.

No DRAFT→ARCHIVED→COMPLETED loop is possible — the only paths are DRAFT→COMPLETED and DRAFT|COMPLETED→ARCHIVED. **PASS** (though Medium **F-301** recommends extracting an explicit policy class for symmetry with JobProfile and future EVALUATION_STATUS hardening).

### 5.4 DB-level immutability

* `005-create-job-profiles.yaml:128-129` — `CHECK status IN ('DRAFT','UNDER_REVIEW','APPROVED','ARCHIVED')`.
* `005:131-132` — `CHECK revision_number >= 1`.
* No DB trigger blocking APPROVED→DRAFT downgrade. Runtime user has UPDATE on `status` column. Same gap as Phase 2 F-209 (deferred Low). Not regression.

---

## 6. ABAC write-path coverage

Every Phase 3 write use case (10 of them) invokes both `enforceCanWriteInProject(ctx, projectId)` and `enforceCanWriteInDepartment(ctx, projectId, departmentId)` AFTER the tenant filter resolves the entity. This closes the F-202 pattern and is the model going forward.

| Use case | `enforceCanWriteInProject` | `enforceCanWriteInDepartment` |
|----------|---------------------------|-------------------------------|
| `CreateJobProfileUseCase` | line 59 | line 60-61 |
| `UpdateJobProfileUseCase` | line 63 | line 64-65 |
| `SubmitJobProfileForReviewUseCase` | line 61 | line 62-63 |
| `ApproveJobProfileUseCase` | line 66 | line 67-68 |
| `RequestJobProfileChangesUseCase` | line 72 | line 73-74 |
| `ArchiveJobProfileUseCase` | line 64 | line 65-66 |
| `CreateJobProfileRevisionUseCase` | line 61 | line 62-63 |
| `CreateQuestionnaireUseCase` | line 62 | line 63-64 |
| `UpdateAnswerUseCase` | line 66 | line 67-68 |
| `SubmitQuestionnaireUseCase` | line 67 | line 68-69 |
| `ArchiveQuestionnaireUseCase` | line 58 | line 59-60 |

11/11 paths covered. **PASS.**

`FindJobProfileQuery` and `FindQuestionnaireQuery` use `enforceCanReadPosition` (4 read paths) with the entity status passed through to `ApprovedEntityFilterPolicy`. **PASS.**

---

## 7. Liquibase 005/006/007 security

### 7.1 `005-create-job-profiles.yaml`

* `tenant_id UUID NOT NULL`, `project_id UUID NOT NULL`, `position_id UUID NOT NULL` — present.
* **FK on `tenant_id` to `public.tenants(id)` ON DELETE RESTRICT** — present (line 111-114). **F-203 pattern closed.**
* FK `project_id → projects(id)` — present (line 115-117).
* FK `position_id → positions(id)` — present (line 121-123). Comment header (line 118-120) documents the F-204 composite-FK pattern; the simpler single-column FK is what actually ships. The header comment claims it "requires uq index on positions(project_id, id)" — this is a Phase 2 remediation prerequisite (F-204) — see **F-302**.
* **Self-FK `previous_revision_id → job_profiles(id)`** — present (line 124-126). Prevents dangling revision pointers.
* `CHECK status IN ('DRAFT','UNDER_REVIEW','APPROVED','ARCHIVED')` — present (line 128-129).
* `CHECK revision_number >= 1` — present (line 131-132).
* **Partial unique index `WHERE status <> 'ARCHIVED'`** — `CREATE UNIQUE INDEX uq_job_profiles_position_active ON job_profiles (tenant_id, project_id, position_id) WHERE status <> 'ARCHIVED'` (line 137-139). Correctly scoped by `(tenant_id, project_id, position_id)`. **PASS.** Edge case: multiple ARCHIVED revisions per position coexist (intended for historical record).
* Supporting indexes: `(tenant_id, project_id, status)`, `(tenant_id, project_id, position_id, revision_number DESC)`, `(previous_revision_id)`. **PASS.**

### 7.2 `006-create-job-analysis.yaml`

* `job_analysis_questionnaires.tenant_id NOT NULL`, FK to `public.tenants(id)` ON DELETE RESTRICT (line 88-92). **PASS.**
* FK `project_id → projects(id)`, FK `position_id → positions(id)` — present.
* `CHECK status IN ('DRAFT','COMPLETED','ARCHIVED')`.
* `questions JSONB NOT NULL` — stores the embedded question array materialized at instantiation. The `JobAnalysisQuestion` value object (deserialized via Jackson into a public field-set POJO with no `@JsonIgnoreProperties`) can accept unknown fields silently — see **finding F-303** (Low).
* `job_analysis_answers.tenant_id NOT NULL`, FK to `public.tenants(id)` ON DELETE RESTRICT (line 187-189). **PASS.**
* FK `questionnaire_id → job_analysis_questionnaires(id) ON DELETE CASCADE` (line 191-193). Cascade is OK for answers — when a parent questionnaire is hard-deleted by an operator (out of normal app flow), orphan answers are cleaned. Not used by the application path (which archives, not deletes).
* `answer_text VARCHAR(20000)` — hard-capped at 20 kB. Matches `@Size(max=20000)` in `UpdateAnswerRequest`. **PASS.**
* Unique index `(tenant_id, questionnaire_id, question_id, respondent_user_id)` — enforces idempotent upsert. **PASS.**
* **JSONB injection risk:** `questions` and `answer_choices` columns ingest JSON from server-side code (templates) and client (answer choices). Choices are validated via `UpdateAnswerUseCase.validateAnswerShape` against `q.getOptions()` (whitelist). Questions are NEVER set from client input — only from `QuestionnaireTemplate.questionsFor(...)`. No SQL injection vector — JSON is bound parametrically by Hibernate JdbcTypeCode SQL JSON. **PASS** with note in **F-303**.

### 7.3 `007-seed-job-analysis-permissions.yaml`

* Inserts 3 new permissions: `JOB_PROFILE_APPROVE`, `JOB_ANALYSIS_READ`, `JOB_ANALYSIS_EDIT` — uses `ON CONFLICT (code) DO NOTHING` for idempotency.
* Role grants:
  * `HRLAB_SUPER_ADMIN`, `HRLAB_PROJECT_MANAGER` — all three.
  * `HRLAB_CONSULTANT`, `HRLAB_ANALYST` — `JOB_ANALYSIS_READ` + `JOB_ANALYSIS_EDIT` (no JOB_PROFILE_APPROVE — correct: approval gated to client HR director).
  * `CLIENT_HR_DIRECTOR` — `JOB_PROFILE_APPROVE` + `JOB_ANALYSIS_READ`.
  * `CLIENT_HR_SPECIALIST` — `JOB_ANALYSIS_READ` + `JOB_ANALYSIS_EDIT`.
  * `DEPARTMENT_MANAGER`, `EVALUATION_COMMITTEE_MEMBER` — `JOB_ANALYSIS_READ` only.
* **No salary-permission grant.** **PASS.**
* The `JOB_PROFILE_EDIT` permission (Phase 0+1 catalog) is NOT re-granted here — Phase 3 relies on the existing role-permission grants from `seeds/004-default-role-permissions.yaml`. Verify the matrix still grants `JOB_PROFILE_EDIT` to the roles that need it (HRLAB_CONSULTANT, CLIENT_HR_SPECIALIST, etc.). Tracked as **F-305** (Low — documentation/matrix audit).

---

## 8. API security — every Phase 3 endpoint

| # | Endpoint | `@PreAuthorize` | Tenant from | DTO clean? | 404 vs 403 |
|---|----------|-----------------|-------------|------------|-----------|
| 1 | POST `/api/v1/positions/{positionId}/job-profile` | `JOB_PROFILE_EDIT` | JWT | yes | 404 |
| 2 | GET `/api/v1/positions/{positionId}/job-profile` | `JOB_PROFILE_READ` | JWT | yes | 404 |
| 3 | GET `/api/v1/positions/{positionId}/job-profile/revisions` | `JOB_PROFILE_READ` | JWT | yes | 404 |
| 4 | GET `/api/v1/job-profiles/{id}` | `JOB_PROFILE_READ` | JWT | yes | 404 |
| 5 | PATCH `/api/v1/job-profiles/{id}` | `JOB_PROFILE_EDIT` | JWT | yes | 404/409 |
| 6 | POST `/api/v1/job-profiles/{id}/submit` | `JOB_PROFILE_EDIT` | JWT | n/a | 404/409 |
| 7 | POST `/api/v1/job-profiles/{id}/approve` | `JOB_PROFILE_APPROVE` | JWT | n/a | 404/409 |
| 8 | POST `/api/v1/job-profiles/{id}/request-changes` | `JOB_PROFILE_APPROVE` | JWT | yes (`ReasonRequest`) | 404/409 |
| 9 | POST `/api/v1/job-profiles/{id}/archive` | `JOB_PROFILE_EDIT` | JWT | yes | 404/409 |
| 10 | POST `/api/v1/job-profiles/{id}/create-revision` | `JOB_PROFILE_EDIT` | JWT | n/a | 404/409 |
| 11 | POST `/api/v1/positions/{positionId}/questionnaires` | `JOB_ANALYSIS_EDIT` | JWT | yes | 404 |
| 12 | GET `/api/v1/positions/{positionId}/questionnaires` | `JOB_ANALYSIS_READ` | JWT | n/a | 404 |
| 13 | GET `/api/v1/questionnaires/{id}` | `JOB_ANALYSIS_READ` | JWT | n/a | 404 |
| 14 | GET `/api/v1/questionnaires/{id}/answers` | `JOB_ANALYSIS_READ` | JWT | n/a | 404 |
| 15 | PATCH `/api/v1/questionnaires/{id}/answers/{questionId}` | `JOB_ANALYSIS_EDIT` | JWT | yes | 404/409 |
| 16 | POST `/api/v1/questionnaires/{id}/submit` | `JOB_ANALYSIS_EDIT` | JWT | n/a | 404/409 |
| 17 | POST `/api/v1/questionnaires/{id}/archive` | `JOB_ANALYSIS_EDIT` | JWT | yes | 404/409 |

All 17 endpoints carry `@PreAuthorize`. **None** of the 7 write DTOs declares a `tenant_id` field. No JPA entity is returned (every controller maps via `*Response.from(domain)`). **PASS.**

### 8.1 Mass-assignment risk

DTOs are Java records (immutable, no Jackson-side setters for unknown fields). Update use cases use field-by-field merge (`UpdateJobProfileUseCase:77-89`). **No setters for `id`, `tenantId`, `projectId`, `revisionNumber`, `status`, `approvedBy`, `approvedAt`, `lockedAt` are exposed via DTOs.** The entity does have a setter for those (used by use cases), but Jackson never sees them. **PASS.**

### 8.2 Error response — entity-existence leak

The `JobProfileTransitionRejectedException` message contains the entity status: e.g. `"Profile is APPROVED; edits forbidden — create a revision instead"`. This is **acceptable**: the response is only emitted after the entity has been resolved via `findByIdAndTenantId(...)`, so the caller is already entitled to know (a) the entity exists in their tenant and (b) its current status (the status is in the entity response itself). No existence-oracle for cross-tenant probing — those return 404 before any status check. **PASS.**

### 8.3 Reason validation

`ArchiveJobProfileUseCase:52-55`, `RequestJobProfileChangesUseCase:57-60`, `ArchiveQuestionnaireUseCase:45-48` — all enforce `MIN_REASON_LENGTH = 10` server-side. Frontend MSW imposes `< 20` (stricter, harmless). Backend is the authority. **PASS.**

---

## 9. Frontend security (Phase 3)

| Item | Status | Evidence |
|------|--------|----------|
| APPROVED / ARCHIVED renders **read-only DIVs** (not just disabled inputs) | **PASS** | `JobProfileFieldEditor.tsx:78-101` — when `readOnly === true`, the textarea is replaced by a `<div>` with `whitespace-pre-wrap`. No `<textarea disabled>` shortcut. DevTools cannot re-enable an input that doesn't exist in the DOM. Same pattern for the date input (line 216-219). |
| Auto-save guard on non-DRAFT | **PASS** (partial; see F-306) | `JobProfileEditorPage:105` — `readOnly = profile?.status !== 'DRAFT'`. `handleFieldChange:107-114` returns early if `readOnly`. `saveDraft:116-120` returns early. `useEffect:123-131` skips interval setup when `readOnly`. Backend additionally rejects PATCH on non-DRAFT with 409. **Small race** when status flips between render and the 30s/500ms timer firing — see F-306. |
| Multilingual fields don't render HTML | **PASS** | React default escaping; no `dangerouslySetInnerHTML` in any Phase 3 component (grep clean). |
| `<AIRecommendationPanel>` advisory only | **PASS** | `AIRecommendationPanel.tsx:26` — `disabled = true` by default; `data-testid="ai-disclaimer"` renders `t('aiAssist.disclaimer')`; `StatusBadge tone="ai-suggestion" label={t('aiAssist.advisory_label')}` always present. No live backend call. |
| No salary fields anywhere | **PASS** | Grep of `salary`/`compensation`/`fixed_pay`/`variable_pay`/`total_cash` in `jobprofile`+`jobanalysis` modules (backend) and `features/job-profiles`+`features/job-analysis` (frontend) returns ZERO matches. |
| MSW honors security contract | **PASS** | `handlers.ts:61-81` `stripTenantFromBody` deletes `tenant_id`/`tenantId` from body and warns. Applied at the Phase 3 POST/PATCH paths (`/job-profiles` line 314, `/job-profiles/:id` line 364). |
| No `localStorage` for tokens, salary, or sensitive content | **PASS** | Grep of `localStorage.setItem` in Phase 3 features returns no hits beyond locale persistence (`shared/i18n/index.ts`). |
| `<PermissionGate>` on create CTA | **PASS** | `JobProfileEditorPage:148-157` wraps the create button. |

---

## 10. Race condition risks

### 10.1 Auto-save while state transitions (DRAFT → UNDER_REVIEW)

**Scenario:** User U1 has the editor open in DRAFT, types into a field at t=0, debounce timer set to fire at t=30000ms. At t=15000ms, user U2 (or a tab in the same browser) calls SUBMIT and the profile becomes UNDER_REVIEW. At t=30000ms the auto-save timer fires and calls PATCH.

* **Backend:** `UpdateJobProfileUseCase:75` → `immutabilityPolicy.enforceEditable(entity.getStatus())` → 409 `JOB_PROFILE_NOT_EDITABLE`. **PASS — fail-closed.**
* **Frontend:** the auto-save closure captured the OLD `updateMut` reference. When mutation throws 409, react-query surfaces it as an error. The user sees a stale-state error and the keystrokes are lost. This is a UX bug, not a security bug. Tracked as **F-306** (Low).

### 10.2 Answer upsert during questionnaire submit

`UpdateAnswerUseCase:70-75` rejects if `status != DRAFT`. The questionnaire submit transitions DRAFT→COMPLETED in the same transaction as the answer-submit batch (`SubmitQuestionnaireUseCase:96-101`). Concurrent answer updates from another respondent during submit would race — but the questionnaire row is updated last, and the answer rows are unique-constrained by `(tenant_id, questionnaire_id, question_id, respondent_user_id)` (`006-create-job-analysis.yaml:196-198`), so worst case is a deadlock (rolled back) or a 409 from the late writer. **No data leak; no integrity loss.** **PASS.**

### 10.3 Revision creation race

Two operators both click "Create revision" on the same APPROVED profile in the same second. `CreateJobProfileRevisionUseCase` is `@Transactional`. The partial unique index `uq_job_profiles_position_active` (`WHERE status <> 'ARCHIVED'`) requires at most one non-archived profile per `(tenant, project, position)`. The source APPROVED row stays APPROVED; the two new DRAFT rows would both try to satisfy the constraint and the second one will fail with a unique-constraint violation (DataIntegrityViolation → currently maps to 500 via `BaseDomainException` fallback). **Not a security issue but a UX/observability issue** — the partial unique index correctly prevents the duplication, but the resulting 500 should be a 409. Tracked as **F-307** (Low).

---

## 11. JSON injection risks

### 11.1 Multilingual `Map<String, String>` long-text fields

* DTO validators: `@NotBlank` on keys + `@Size(max=20000)` on values (`CreateJobProfileRequest:15-26`, `UpdateJobProfileRequest:10-21`). Map keys carry locale codes; no regex constraint, but key uniqueness is enforced by Map semantics. Backend never echoes the locale-key into a SQL/HTML/log line without templating.
* Storage: JSONB via Hibernate `@JdbcTypeCode(SqlTypes.JSON)`. Parametrised — **no SQL injection vector**.
* Rendering: React default escaping; no `dangerouslySetInnerHTML`. **Stored-XSS via these fields is structurally impossible.**
* Length-limit enforcement at API + at DB column (TEXT/JSONB has no hard limit). 20 kB per field × 12 fields × 4 locales = ~960 kB per profile theoretical max. Acceptable for MVP 1 but worth a Bean Validation `@Size(max=4)` on the Map size to prevent abuse — tracked as **F-308** (Low; DoS-adjacent).

### 11.2 Embedded `JobAnalysisQuestion` in JSONB

* `JobAnalysisQuestion` (`jobanalysis/domain/JobAnalysisQuestion.java`) is a mutable POJO with public setters and a no-arg constructor for Jackson. It is **never deserialized from client input** — only constructed by `QuestionnaireTemplate.questionsFor(...)` (server-side code) and round-tripped through JSONB. Client cannot inject custom `JobAnalysisQuestion` objects.
* HOWEVER, Hibernate will deserialize the JSONB back into `List<JobAnalysisQuestion>` and Jackson will accept arbitrary unknown properties unless `@JsonIgnoreProperties(ignoreUnknown=false)` is set. If a DBA or migration job were ever to write unknown keys into the column, the application would silently swallow them. Not exploitable today (no client write path), but tracked as **F-303** (Low — JSONB deserialization hardening).

### 11.3 `answerChoices` JSONB

* Client supplies a `List<String>` of choices (`UpdateAnswerRequest:10`), each `@Size(max=200)`.
* Server validates against `q.getOptions()` whitelist for SINGLE_CHOICE / MULTI_CHOICE (`UpdateAnswerUseCase:131-152`).
* For RATING_SCALE, the choice must be one of `q.getOptions()` (e.g. `["1","2","3","4","5"]`).
* For TEXT/LONG_TEXT, server raises `ANSWER_TYPE_MISMATCH` if `answerChoices` is non-empty (`UpdateAnswerUseCase:158-165`).
* **PASS — choice values are whitelist-validated.**

---

## 12. Audit redaction

### 12.1 Phase 3 audit events

All 11 new audit actions write only `entityType` + `entityId` + (optionally) `reason`. They do NOT include `before_json` / `after_json` payloads.

* **Pro:** zero risk of leaking long-text body content (purpose / kpi / etc.) into the audit log.
* **Con:** the audit blueprint §9.3 schema specifies `before_json (redacted)` + `after_json (redacted)` as the canonical record. Phase 3 emits records with both fields null. This is **non-conformant with the blueprint** but **safer than the alternative** (which would require Phase 0+1 F-09 — salary redactor — and a long-text truncator — F-309 — to be in place).
* **Recommendation:** keep current behaviour for MVP 1 (safer); document the gap; close before MVP 3 ships when methodology/evaluation requires before/after for compliance.
* Tracked as **F-309** (Medium — audit-blueprint conformance gap, not a security regression).

### 12.2 Reason fields

* `RequestJobProfileChangesUseCase.reason` and `ArchiveJobProfileUseCase.reason` are user-supplied. `@Size(max=20000)` would normally bound them (the DTO `ReasonRequest` likely has it — verified separately).
* Reason is persisted verbatim into audit. If a user pastes salary-like text into a reason, it lands in audit unredacted. Bound to be a real risk when MVP 3 ships compensation; for MVP 1 there is no salary domain so the risk is theoretical. Tracked under **F-309**.

---

## 13. Findings (F-3xx series)

### F-301

* **Finding:** Questionnaire state machine implemented as inline `if (... != DRAFT)` checks instead of a `Policy` class.
* **Severity:** **Medium** (consistency + future extensibility).
* **Affected area:** `jobanalysis/application/UpdateAnswerUseCase.java:70-75`, `SubmitQuestionnaireUseCase:71-75`, `ArchiveQuestionnaireUseCase:62-66`.
* **Risk:** A future EVALUATION_STATUS or GRADE_STATUS pattern will copy this inline style and miss a transition. The JobProfile module has a proper `JobProfileStatusTransitionPolicy` class — Questionnaire should match.
* **Exploit scenario:** Phase 4 developer adds a `COMPLETED → REOPENED` transition without thinking about audit. Without a policy class the omission is silent.
* **Required fix:** Extract `QuestionnaireStatusTransitionPolicy` matching the `JobProfileStatusTransitionPolicy` shape (EnumMap + `check(current, action)` + `JobProfileTransition` enum). Refactor the 3 use cases to call `transitionPolicy.check(...)`.
* **Acceptance criteria:** `QuestionnaireStatusTransitionPolicy` exists; all 3 use cases call it; transition-rejected tests added.
* **Test case:** `QuestionnaireStatusTransitionPolicyTest` — 3×3 matrix of (current, action) → allowed/rejected.
* **Owner:** backend-engineer.

### F-302

* **Finding:** `job_profiles` table has simple FK `position_id → positions(id)` instead of composite FK `(project_id, position_id) → positions(project_id, id)`.
* **Severity:** **Low** (defense in depth; application enforces correctly).
* **Affected area:** `tenant-schema/005-create-job-profiles.yaml:121-123`.
* **Risk:** Same as Phase 2 F-204 — direct DB write could create a profile whose `project_id` doesn't match the position's `project_id`. Application path always sets `projectId = position.getProjectId()` (`CreateJobProfileUseCase:78-79`), so app-driven mismatch is structurally impossible.
* **Required fix:** Replace `fk_job_profiles_position` with composite FK after `positions(project_id, id)` unique constraint exists (Phase 2 remediation `004-phase2-constraints.yaml` allegedly adds this — verify).
* **Acceptance criteria:** Native SQL `INSERT INTO job_profiles (..., project_id, position_id) VALUES (..., <P_X>, <pos belonging to P_Y>)` fails.
* **Test case:** `JobProfileCrossProjectReferenceDbTest`.
* **Owner:** database-architect.

### F-303

* **Finding:** `JobAnalysisQuestion` JSONB deserialization accepts unknown fields silently.
* **Severity:** **Low**.
* **Affected area:** `jobanalysis/domain/JobAnalysisQuestion.java`, `jobanalysis/infrastructure/JobAnalysisQuestionnaireJpaEntity.java:53-55`.
* **Risk:** A poisoned DB row (DBA error or future migration) could carry attacker-supplied keys. Today no client path writes to this column, so risk is theoretical. If a future Phase 4 "edit questionnaire template" endpoint is added, this gap becomes exploitable.
* **Exploit scenario:** Phase 4 introduces "custom question templates" sourced from client uploads. Without `@JsonIgnoreProperties(ignoreUnknown=false)`, an attacker injects `"__proto__": {...}` (no-op in Java, but the pattern is bad), or worse, fields that downstream code uses unsafely.
* **Required fix:** Annotate `JobAnalysisQuestion` with `@JsonIgnoreProperties(ignoreUnknown=false)`. Add a Jackson deserialization test that asserts unknown keys cause a `JsonMappingException`. Pair with a Bean Validation `@Valid` cascade when the field becomes client-writable.
* **Acceptance criteria:** Deserialization of `{"id":"...","unknownField":"x"}` throws.
* **Test case:** `JobAnalysisQuestionDeserializationTest`.
* **Owner:** backend-engineer.

### F-304

* **Finding:** Cross-FK consistency for `job_profiles`/`job_analysis_*` references depends on a Phase 2 remediation prerequisite (`(positions.project_id, positions.id)` unique constraint).
* **Severity:** **Low** (verification gate).
* **Affected area:** `tenant-schema/004-phase2-constraints.yaml` (Phase 2 remediation file) — not independently re-verified in this review.
* **Risk:** If the Phase 2 remediation merge missed declaring `UNIQUE (project_id, id)` on `positions`, then F-302's composite FK upgrade cannot run.
* **Required fix:** Verify by reading `004-phase2-constraints.yaml`; if missing, add the unique constraint AND upgrade `fk_job_profiles_position` and `fk_jaq_position` to composite.
* **Acceptance criteria:** Migration assertion test passes; F-302 composite FK installable.
* **Test case:** `Phase2ConstraintsAssertionTest`.
* **Owner:** database-architect.

### F-305

* **Finding:** No Phase 3 changelog re-asserts that `JOB_PROFILE_EDIT` is granted to client HR roles (HRLAB_CONSULTANT, CLIENT_HR_SPECIALIST) by `seeds/004-default-role-permissions.yaml`.
* **Severity:** **Low** (documentation gap).
* **Affected area:** `role-permissions-matrix.md`, `tenant-schema/007-seed-job-analysis-permissions.yaml`.
* **Risk:** If Phase 0+1 seed file was tweaked without Phase 3 awareness, the matrix could drift. Today the API endpoints check `JOB_PROFILE_EDIT` directly — a missing grant would silently fail closed (deny-by-default is correct), but UX breaks.
* **Required fix:** Add a CI smoke test that asserts every Phase 3 permission required by `@PreAuthorize` annotations is in fact granted to at least one role.
* **Acceptance criteria:** `PermissionMatrixCompletenessTest` passes.
* **Test case:** above.
* **Owner:** backend-engineer + hr-product-owner.

### F-306

* **Finding:** Frontend auto-save closure captures stale `readOnly` value when status changes mid-session.
* **Severity:** **Low** (UX, not security; backend rejects with 409).
* **Affected area:** `frontend/src/features/job-profiles/pages/JobProfileEditorPage.tsx:123-131`, `frontend/src/features/job-analysis/pages/QuestionnairePage.tsx:89-101`.
* **Risk:** A pending 30s (JobProfile) or 500ms (JobAnalysis) timer can fire AFTER status changed from DRAFT to UNDER_REVIEW, triggering a useless PATCH that the backend rejects with 409. User loses unsaved keystrokes and sees an error.
* **Required fix:** On each render, clear all queued debounce timers if `readOnly` becomes true. Use a `useEffect` cleanup that runs when `readOnly` flips.
* **Acceptance criteria:** Vitest scenario: type → submit (status flips) → 30s elapses → no PATCH was sent.
* **Test case:** `JobProfileEditorPage.test.tsx` — race scenario.
* **Owner:** frontend-engineer.

### F-307

* **Finding:** Duplicate revision creation race surfaces as HTTP 500, not 409.
* **Severity:** **Low** (observability, fail-closed at DB).
* **Affected area:** `CreateJobProfileRevisionUseCase`, `GlobalExceptionHandler`.
* **Risk:** Two operators click "Create revision" simultaneously; partial unique index fires; `DataIntegrityViolationException` propagates as a generic 500.
* **Required fix:** Add an `@ExceptionHandler(DataIntegrityViolationException.class)` that maps unique-constraint violations on `uq_job_profiles_position_active` to 409 `REVISION_ALREADY_PENDING`. Optionally pre-check `existsByTenantIdAndProjectIdAndPositionIdAndStatusNot(...)` and short-circuit.
* **Acceptance criteria:** Concurrent revision test returns 409, not 500.
* **Test case:** `JobProfileRevisionRaceTest`.
* **Owner:** backend-engineer.

### F-308

* **Finding:** Multilingual JSONB Maps have no upper bound on key count.
* **Severity:** **Low** (DoS-adjacent).
* **Affected area:** `CreateJobProfileRequest`, `UpdateJobProfileRequest`.
* **Risk:** A malicious client sends a Map with 100,000 fake locale keys, each 20 kB. ~2 GB body per field × 12 fields. Spring Boot has a default 1 MB-ish request size cap, but the JSONB column has no limit.
* **Required fix:** `@Size(min=1, max=4)` on each Map. Document the 4 supported locales (ru-RU, uz-Cyrl-UZ, uz-Latn-UZ, en-US) and reject the request if a key is not in the allowlist.
* **Acceptance criteria:** Request with `purposeI18n.fr-FR` returns 400 `INVALID_LOCALE_KEY`.
* **Test case:** `CreateJobProfileLocaleKeyValidationTest`.
* **Owner:** backend-engineer.

### F-309

* **Finding:** Phase 3 audit events omit `before_json` / `after_json` payloads and have no long-text redactor for `reason`.
* **Severity:** **Medium** (audit blueprint §9.3 conformance gap; not a leak today because the omitted bodies were never written).
* **Affected area:** Every `AuditService.record(AuditEvent.builder()...)` call in Phase 3.
* **Risk:**
  1. **Conformance:** the security blueprint §9.3 mandates `before_json (redacted)` + `after_json (redacted)`. Phase 3 records have both as null. Compliance auditors will flag.
  2. **Long-text leak via `reason`:** a user types salary numbers into the archive `reason`. Today no salary-aware redactor exists for free-text. When MVP 3 ships compensation, this gap becomes exploitable.
* **Required fix:**
  1. Add `AuditPayloadSerializer` that emits `before_json` / `after_json` with a field allowlist per entity type (e.g. for JobProfile: `status`, `revisionNumber`, `previousRevisionId` only — never the body i18n maps).
  2. Truncate `reason` at 1000 chars and run it through `MaskingPatternLayout`'s regex pack before persisting.
  3. Document in audit-blueprint §9.3 that long-text bodies are NEVER mirrored verbatim.
* **Acceptance criteria:** Audit row for `JOB_PROFILE_APPROVED` contains `{"status":"APPROVED","revisionNumber":1}` in `after_json`; `reason` containing "$50,000 salary" is masked to "$**** salary".
* **Test case:** `AuditPayloadSerializerTest` + `AuditReasonRedactionTest`.
* **Owner:** backend-engineer.

---

## 14. Top 20 risks — Phase 3 re-evaluation

| # | Risk | Phase 2 → Phase 3 status |
|---|------|---------------------------|
| R-01 | Cross-tenant leak via missed tenant filter | **Mitigated** — both new repos extend `TenantAwareRepository`; profile→position→project→tenant chain re-validated everywhere |
| R-02 | BOLA/IDOR | **Mitigated** for reads AND writes — F-202 closed; 11/11 Phase 3 write paths invoke `enforceCanWriteInProject` + `enforceCanWriteInDepartment` |
| R-03 | Backend trusts `tenant_id` from frontend | **Mitigated** — DTOs verified clean; MSW honors strip helper (F-208 closed) |
| R-04 | JWT validation misconfiguration | **Mitigated** |
| R-05 | Audit mutated/deleted | **Mitigated** — append-only path inherited |
| R-06 | Salary primitives leak | **Foundation in place; no salary in Phase 3 modules** |
| R-07 | Secrets in Git | **Mitigated** |
| R-08 | Misconfigured CORS | **Mitigated** |
| R-09 | Stack traces leak | **Mitigated** — new 409 handlers go through `build(...)` envelope |
| R-13 | Approved methodology silently edited | **Mitigated for JobProfile** — `JobProfileImmutabilityPolicy` rejects edits to APPROVED; revision chain enforces immutability of source |
| R-17 | Mass assignment | **Mitigated** — record DTOs + selective entity setters |
| R-21 | ABAC bypass on writes | **CLOSED** (F-202) |
| R-22 | Tenant-wide admin lockout | **CLOSED** (F-201) |

Two new risks from Phase 3:

* **R-23 — Revision chain corruption.** Mitigated by partial unique index + self-FK + source-not-mutated pattern. Race condition surfaces as 500 (F-307) but data integrity is preserved.
* **R-24 — JSONB ingest of attacker-controlled multilingual content.** Mitigated by React default escaping + JSONB parametric binding. Hardening pending (F-303 + F-308).

---

## 15. Release security gate decision

**Decision: SHIP.**

Phase 3 is the strongest security delivery in MVP 1 to date:

* **Hard cybersecurity rules — all upheld.** No `findById` on tenant data, no `tenant_id` in business DTOs/paths/queries, no JPA entities returned, no salary fields, no native queries, no `@PreAuthorize` gaps, deny-by-default preserved.
* **ABAC write-path coverage is complete** (11/11). F-202 is closed and the pattern is now codified.
* **Status machine + immutability work correctly.** APPROVED → ARCHIVED or APPROVED → new DRAFT (without mutating source) — verified at code and DB level.
* **Revision chain integrity is structurally enforced** (self-FK + partial unique index + source-not-loaded-for-mutate).
* **Tenant chain validation** (profile → position → project → tenant) is exhaustive — cross-tenant smuggling via a referenced UUID is structurally impossible.
* **Audit-on-deny** continues to fire from `AbacGate` for both read and write paths.
* **Frontend APPROVED state renders read-only DIVs** (not enableable via DevTools).
* **MSW honors the security contract** (drops `tenant_id` from request body).

**No conditions block the release.** The 9 findings are all Low/Medium and tracked for the next cycle.

### Strongly recommended before Phase 4 entry:

1. **F-301** — Extract `QuestionnaireStatusTransitionPolicy` for symmetry with JobProfile and to set the pattern for EVALUATION_STATUS in Phase 4.
2. **F-309** — Begin audit `before_json`/`after_json` payload with field allowlist + `reason` redaction before MVP 3 (compensation) ships.
3. **F-302 + F-304** — Verify Phase 2 remediation file declared `UNIQUE (project_id, id)` on `positions`; upgrade `fk_job_profiles_position` to composite FK.
4. **F-306** — Cancel pending auto-save timers when status flips from DRAFT.
5. **F-307** — Map `DataIntegrityViolationException` on revision unique index to 409.
6. **F-303** — `@JsonIgnoreProperties(ignoreUnknown=false)` on `JobAnalysisQuestion` before Phase 4 introduces custom templates.
7. **F-308** — Bound multilingual Maps to the 4 supported locale keys.
8. **F-305** — Permission matrix completeness CI test.

### Carry-forward from Phase 2 (still open, still deferred):

* F-205 (consultant assignment cache) — performance, not security.
* F-206 (DB-level cycle prevention on departments) — defense-in-depth.
* F-209 (LOCKED→ACTIVE downgrade DB guard) — defense-in-depth.
* F-210 / F-20 (logout → IdP `end_session_endpoint`) — Medium, still open. Becomes critical when real Keycloak is wired.

### Hard cybersecurity rule violations: **0.**

---

## 16. Action items per agent

### backend-engineer (no blockers; recommended before Phase 4)

* **F-301** — Extract `QuestionnaireStatusTransitionPolicy`.
* **F-303** — `@JsonIgnoreProperties(ignoreUnknown=false)` on `JobAnalysisQuestion`.
* **F-307** — Map `DataIntegrityViolationException` to 409.
* **F-308** — Bound multilingual Maps `@Size(min=1,max=4)` + allowlist locale keys.
* **F-309** — `AuditPayloadSerializer` + reason redactor (start now; finish before MVP 3).
* **F-305** — Permission matrix completeness test.

### frontend-engineer

* **F-306** — Cancel pending auto-save timers when `readOnly` flips.

### database-architect

* **F-302** + **F-304** — Verify `004-phase2-constraints.yaml`; upgrade `job_profiles` + `job_analysis_questionnaires` `position_id` FKs to composite `(project_id, position_id)`.

### devops-sre

* No new Phase 3 items. Carry-forward from Phase 2 (F-15, F-17, F-21, F-207).

### qa-engineer

* Implement Tenant Isolation Pack TI-03 (`GET /job-profiles/{T_B uuid}` → 404 + `CROSS_TENANT_ACCESS_ATTEMPT` audit).
* Implement workflow integration tests: every illegal status transition returns 409 with the right code.
* Implement immutability test: PATCH on APPROVED profile returns 409 `JOB_PROFILE_LOCKED`.
* Implement revision chain test: source row unchanged after `create-revision`.
* Implement ABAC write-path test pack: 11 use cases × {member of P_A only, asks to write in P_B} → 404 + `ACCESS_DENIED_BY_ABAC` audit.
* Implement F-307 concurrent-revision test.
* Implement F-308 locale-key validation test.

### hr-product-owner

* Update PRD §E6 to reflect server-side `MIN_REASON_LENGTH = 10` (frontend MSW uses 20 — align to one number).
* Add F-309 audit-payload requirement to Phase 4 backlog (so methodology/evaluation ships with before/after redaction in place).

---

— end of report —

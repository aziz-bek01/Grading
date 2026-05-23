# Phase 3 QA Review Report — grading.hrlab.uz

Document owner: QA Engineering
Status: Phase 3 release gate (Job Profile + Job Analysis + status workflow + audit)
Date: 2026-05-23
Benchmark: `docs/mvp1/03-qa-master-test-plan.md` + `docs/mvp1/reviews/phase2-qa-review.md`
Reviewed build:
- **Backend Phase 3:** 2 new modules (`jobprofile/` 23 files; `jobanalysis/` 21 files); 3 tenant-schema changelogs (005 job_profiles, 006 job_analysis, 007 permission seeds); 11 new audit actions; 3 new permission codes (`JOB_PROFILE_APPROVE`, `JOB_ANALYSIS_READ`, `JOB_ANALYSIS_EDIT`); 2 new 409 exception handlers; 13 new REST endpoints; 7 new test files (~37 new tests; transition + immutability + 2 controller security + 2 phase3 integration + audit catalogue).
- **Frontend Phase 3:** `features/job-profiles/` (15 files incl. 4 tests) + `features/job-analysis/` (10 files incl. 2 tests); 2 new routes (`/job-profile`, `/questionnaire/:id`); MSW handlers + fixtures for 15 endpoints; ~90 new i18n keys × 4 locales.

---

## 1. Review Scope

In-scope:
- **Backend:** `uz.hrlab.grading.jobprofile.*` (domain, application, api, infrastructure), `uz.hrlab.grading.jobanalysis.*`, `AuditAction.JOB_PROFILE_*` + `JOB_ANALYSIS_*`, `PermissionCodes.JOB_PROFILE_APPROVE` + `JOB_ANALYSIS_READ` + `JOB_ANALYSIS_EDIT`, Liquibase tenant-schema `005-create-job-profiles.yaml`, `006-create-job-analysis.yaml`, `007-seed-job-analysis-permissions.yaml`, `GlobalExceptionHandler.handleJobProfileTransition` + `handleQuestionnaireTransition` (both 409).
- **Backend tests:** `JobProfileStatusTransitionPolicyTest`, `JobProfileImmutabilityPolicyTest`, `JobProfileControllerSecurityTest`, `JobAnalysisControllerSecurityTest`, `Phase3JobProfileIntegrationTest`, `Phase3AuditActionsTest`.
- **Frontend:** `features/job-profiles/{api,components,hooks,pages,schemas,types}` and `features/job-analysis/{api,components,hooks,pages,schemas,types}`; MSW handlers and fixtures for `/job-profiles/*` + `/questionnaires/*` + `/positions/{id}/{job-profile,questionnaires}`; PERMISSIONS in `shared/types/permissions.ts`.

Out of scope (Phase 4+): Methodology builder, scoring engine, grade structure, salary engine.

---

## 2. Phase 2 Conditions Closure

| ID | Condition (from Phase 2 review §17) | Status | Evidence |
|----|--------------------------------------|--------|----------|
| **PC-1** | Fix D-202 — MSW mock honors `body.tenant_id` | **CLOSED** | `frontend/src/shared/api/mocks/handlers.ts` lines 61-78 implement `stripTenantFromBody()` that deletes `tenant_id` / `tenantId` and warns. Phase 3 handlers (`handleJobProfiles`, `handleJobAnalysis`) wrap every body read with `stripTenantFromBody(raw, path, method)` — confirmed at handlers.ts L314 and L364. Tested by `shared/api/__tests__/noTenantIdLeak.test.ts` (MSW mock-drops body.tenant_id, derives tenant from `X-Mock-Tenant-Id` header). |
| **PC-2** | Per-endpoint HTTP-layer security tests (15 Phase 2 endpoints × anon/wrong-perm/right-perm) | **CLOSED** | `ProjectControllerSecurityTest`, `DepartmentControllerSecurityTest`, `PositionControllerSecurityTest` all present (per Phase 2 remediation task #13). Phase 3 follows the same pattern with `JobProfileControllerSecurityTest` (11 cases) + `JobAnalysisControllerSecurityTest` (10 cases). |
| **PC-3** | ArchUnit rule against `@RequestParam("tenantId")` | **CLOSED** | `architecture/ArchitectureTest` now declares **Rules 6/7/8**: forbids `@RequestParam`, request-body field, and `@PathVariable` named `tenantId`/`tenant_id`/`tenant-id` outside `..admin..`. Lines 195-296. |
| **PC-4** | End-to-end audit row assertion per Phase 2 resource | **PARTIALLY CLOSED** | Phase 3 ships `Phase3AuditActionsTest` (static catalogue check, not row-level), and Phase 2 carryover `CrossTenantAuditRecordingTest` proves hash-chained row writes. **No happy-path integration test** asserts that POST `/api/v1/projects` (or any other Phase 2/3 mutation) lands a row in `system_audit_log` with the correct (`action`, `entity_type`, `entity_id`, `hash_prev → hash_current` chain, `actor_user_id`). The pattern from Phase 2 remediation appears to address audit-append-only via `AuditAppendOnlyTest`; explicit lifecycle audit-row tests still missing. Re-issued as **D-313**. |
| PC-5 | Close D-205 (i18n parity) + D-206 (fallback) | **CLOSED for parity, OPEN for fallback** | `shared/i18n/__tests__/i18nParity.test.ts` enforces 3 invariants (full union, no orphans, equal counts) over all 4 locales. D-206 (server-side missing-locale fallback) remains undocumented. |
| PC-6 | Close D-207, D-209, D-212 | **NOT VERIFIED** | These were tagged non-blocking; not re-checked in this scope. Marked as carry-over to be confirmed by separate audit. |

**Summary: PC-1, PC-2, PC-3, PC-5 (parity) closed. PC-4 (audit row integration) partially closed — re-issued as D-313.**

---

## 3. Phase 3 Test Coverage Matrix vs QA Master Test Plan

| Pack | Required (per master plan + Phase 3 PRD §E6/§E7) | Implemented | Missing |
|------|--------------------------------------------------|-------------|---------|
| **Tenant Isolation** | TIP-02/03 class repository probes; HTTP-layer probes for new endpoints | `Phase3JobProfileIntegrationTest.profileFromTenantBIsInvisibleToTenantA` (repo); partial-unique-index proof; archived-revision coexistence test. `JobProfileControllerSecurityTest.unknownIdReturns404` (Tenant access denied → 404). Tenant_id-in-body ignored (`createOnPositionIgnoresTenantIdInBody`). | No HTTP-layer integration tests for cross-tenant probe via TenantContext; no `Phase3JobAnalysisIntegrationTest` (questionnaire/answer repo-level tenant probe). |
| **RBAC** | 13 new endpoints × `@PreAuthorize` with correct codes; positive + negative | All 13 controller methods carry `@PreAuthorize`: JobProfileController uses `JOB_PROFILE_READ`/`EDIT`/`APPROVE`; PositionJobProfileController uses `READ`/`EDIT`; QuestionnaireController uses `JOB_ANALYSIS_READ`/`EDIT`; PositionQuestionnaireController uses `JOB_ANALYSIS_READ`/`EDIT`. Negative coverage in both security tests (anon → 401, wrong-perm → 403). | RequestChanges endpoint additional permission check (use case re-checks `JOB_PROFILE_APPROVE`) — covered by `requestChangesRequiresApprovePermission` test (passes when controller @PreAuthorize allows but user lacks APPROVE → 403 via use case). Server-side double-check **verified in source** (RequestJobProfileChangesUseCase.java L62-64). |
| **ABAC** | Every write use case calls `AbacGate.enforceCanWriteInProject` + `enforceCanWriteInDepartment` | **PASS** by source inspection — confirmed in CreateJobProfileUseCase L59-61, UpdateJobProfileUseCase L63-65, ApproveJobProfileUseCase L66-68, RequestJobProfileChangesUseCase L72-74, ArchiveJobProfileUseCase L64-66, CreateJobProfileRevisionUseCase L61-63, SubmitJobProfileForReviewUseCase L61-63, plus CreateQuestionnaireUseCase, UpdateAnswerUseCase L66-68, SubmitQuestionnaireUseCase L67-69, ArchiveQuestionnaireUseCase L58-60. All **10 write use cases** call AbacGate. | No integration test asserts that ABAC denial on Phase 3 writes lands an `ACCESS_DENIED_BY_ABAC` audit row (D-301). |
| **Status Machine (Job Profile)** | 8 valid transitions positive + invalid rejected; ARCHIVED terminal | `JobProfileStatusTransitionPolicyTest` — `validTransitionsArePermitted` (7 valid CSV rows: DRAFT→SUBMIT, DRAFT→ARCHIVE, UNDER_REVIEW→APPROVE, UNDER_REVIEW→REQUEST_CHANGES, UNDER_REVIEW→ARCHIVE, APPROVED→ARCHIVE, APPROVED→CREATE_REVISION) + `invalidTransitionsAreRejected` (13 CSV rows) + `archivedIsTerminal`. **Note:** master plan said "8 valid transitions"; the policy ALLOWED map yields 7 transitions (DRAFT has 2; UNDER_REVIEW has 3; APPROVED has 2; ARCHIVED has 0 = 7). The "8th" implied transition (e.g. APPROVED→APPROVED revision-as-self) is **not a transition** — it is CREATE_REVISION which is already counted. Coverage is COMPLETE. | None. |
| **Immutability (Job Profile)** | APPROVED + ARCHIVED reject `enforceMutable`; UNDER_REVIEW rejects `enforceEditable`; DRAFT passes both | `JobProfileImmutabilityPolicyTest` — 4 tests covering DRAFT/UNDER_REVIEW/APPROVED/ARCHIVED with both `enforceMutable` and `enforceEditable`. **PASS.** | Test does not assert that UPDATE_use case integration with policy actually fails at the JPA layer (only domain unit test). |
| **Revision Chain (APPROVED → new DRAFT)** | New row created with `revisionNumber+1`, `previousRevisionId = source.id`, source unchanged; partial unique index enforces single active profile | `Phase3JobProfileIntegrationTest.partialUniqueIndexBlocksDoubleActiveProfile` + `archivedRevisionsCanCoexist` (3 revisions retrievable, only one non-ARCHIVED). Source code review of `CreateJobProfileRevisionUseCase`: source NOT mutated, new entity has `source.revisionNumber + 1` and `previousRevisionId = source.id` (L67-72), content deep-copied (L73-86), audit reason "Revision of {sourceId}" (L96). **PASS source-side; tested at repo level for storage shape.** | No test exercises the full revision creation USE CASE (TenantContext + AbacGate + transitionPolicy → save). |
| **Multilingual primary-locale validation** | Submit-for-review requires `ru-RU` value on 6 long-text fields | `SubmitJobProfileForReviewUseCase.requirePrimaryLocaleFields` enforces purpose, mainDuties, responsibilityArea, kpi, education, experience. Throws `JOB_PROFILE_TRANSITION_REJECTED` with code `PRIMARY_LOCALE_MISSING` → 409. `JobProfileLocales.PRIMARY_LOCALE = "ru-RU"`. | **No backend test asserts the validation** (D-302). The 6-field list is hardcoded — no test that a field with only `en-US` translation is rejected on submit. |
| **JSONB storage shape** | 12 long-text fields per profile stored as JSONB Map<locale,String>; questions in questionnaire as JSONB array | `JobProfileJpaEntity` uses `@JdbcTypeCode(SqlTypes.JSON)` + `columnDefinition = "jsonb"` for all 12 i18n fields; tenant-schema 005 declares JSONB columns. | No round-trip test (`@DataJpaTest` Testcontainers) for write-then-read symmetry of all 12 fields (carry-over from D-212). |
| **Audit Events (11 new)** | All 11 new event codes present in `AuditAction`; recorded in use cases | `Phase3AuditActionsTest` statically asserts 7 `JOB_PROFILE_*` + 4 `JOB_ANALYSIS_*` constants exist. Source inspection confirms each use case calls `audit.record(...)` with the right `AuditAction`. | **No end-to-end audit-row write assertion** for Phase 3 lifecycles (D-313 carryover from PC-4). **No `before/after` JSON capture** — every Phase 3 use case calls `audit.record()` without `.beforeJson()` / `.afterJson()` — meaning audit rows have NO diff payload, which is the master plan §17 hash-chain rule's payload (see D-303). |
| **Frontend status-locked UI** | DRAFT editable; UNDER_REVIEW disabled-ish; APPROVED read-only divs (not disabled inputs); ARCHIVED banner | `JobProfileEditorPage.test.tsx` — 4 tests: DRAFT shows textareas; APPROVED shows `readonly-purpose-ru-RU` divs (NOT textareas); UNDER_REVIEW shows Approve+Request changes; ARCHIVED shows banner. `JobProfileFieldEditor.tsx` L78-101 conditionally renders `<div>` (readonly) vs `<textarea>` (editable). **PASS.** | No test for UNDER_REVIEW field state (in source, `readOnly = profile?.status !== 'DRAFT'` — UNDER_REVIEW is read-only, but no assertion). |
| **Frontend approval flow** | Approve button hidden without `JOB_PROFILE_APPROVE`; visible with it | `JobProfileActionsBar.tsx` L97-107: `<PermissionGate permission={PERMISSIONS.JOB_PROFILE_APPROVE}>` wraps Approve button. Tested implicitly in `JobProfileEditorPage.test.tsx` UNDER_REVIEW case (super-admin role). | No negative test: user without `JOB_PROFILE_APPROVE` rendering UNDER_REVIEW → button hidden. |
| **Reason-required dialog (archive + request-changes)** | Both use `ReasonRequiredDialog` with ≥20-char min | `JobProfileActionsBar.tsx` L181-201: both `reqChangesOpen` and `archiveOpen` use `ReasonRequiredDialog`. **Backend** enforces `MIN_REASON_LENGTH = 10` (NOT 20 — see D-304). | Master plan / task statement says ≥20 chars; **backend hardcoded to 10** in both `ArchiveJobProfileUseCase` and `RequestJobProfileChangesUseCase`. Mismatch with stated 20-char requirement. |
| **AI advisory marker** | Always-labelled "AI suggestion — human approval required"; no auto-accept | `AIRecommendationPanel.tsx` always renders `<StatusBadge tone="ai-suggestion" label={t('aiAssist.advisory_label')} />` and a `data-testid="ai-disclaimer"`. Accept/Reject buttons are `disabled = true` by default. `AIRecommendationPanel.test.tsx` verifies the disclaimer + status badge. **PASS.** | None. |
| **i18n parity (90 new keys × 4 locales)** | All 4 locales contain same flattened key set | `i18nParity.test.ts` enforces 3 invariants (union, no orphans, count). The pre-existing parity test from PC-5 now also covers Phase 3 keys. **PASS by construction** assuming test was green before merge. | None. |
| **No salary leakage** | JobProfile + JobAnalysis have NO salary fields anywhere | Grep on `jobprofile`, `jobanalysis` backend modules and `job-profiles`, `job-analysis` frontend folders returns **NO matches** for `salary`/`Salary`/`SALARY`. **PASS.** | None. |

---

## 4. Status Machine Verification — Job Profile (7 valid transitions)

| From → Action → To | Implementation | Test | Verdict |
|---------------------|---------------|------|---------|
| DRAFT → SUBMIT_FOR_REVIEW → UNDER_REVIEW | `SubmitJobProfileForReviewUseCase` (status check + primary-locale validation) | `JobProfileStatusTransitionPolicyTest.validTransitionsArePermitted` row 1 | PASS |
| DRAFT → ARCHIVE → ARCHIVED | `ArchiveJobProfileUseCase` (reason ≥10 chars) | row 2 | PASS |
| UNDER_REVIEW → APPROVE → APPROVED | `ApproveJobProfileUseCase` (re-checks `JOB_PROFILE_APPROVE`; sets `approvedAt`, `approvedBy`, `lockedAt`) | row 3 | PASS |
| UNDER_REVIEW → REQUEST_CHANGES → DRAFT | `RequestJobProfileChangesUseCase` (reason ≥10 chars; re-checks `JOB_PROFILE_APPROVE`; clears `submittedAt`/`submittedBy`) | row 4 | PASS |
| UNDER_REVIEW → ARCHIVE → ARCHIVED | `ArchiveJobProfileUseCase` | row 5 | PASS |
| APPROVED → ARCHIVE → ARCHIVED | `ArchiveJobProfileUseCase` | row 6 | PASS |
| APPROVED → CREATE_REVISION → (new) DRAFT | `CreateJobProfileRevisionUseCase` (source row unchanged; new entity with `revisionNumber+1`, `previousRevisionId = source.id`) | row 7 | PASS |
| **ARCHIVED is terminal** | `JobProfileStatusTransitionPolicy.ALLOWED` map for ARCHIVED = `EnumSet.noneOf(...)` | `archivedIsTerminal` test iterates all 5 actions | PASS |

**Invalid transitions covered:** 13 CSV rows in `invalidTransitionsAreRejected` cover (DRAFT,APPROVE), (DRAFT,REQUEST_CHANGES), (DRAFT,CREATE_REVISION), (UNDER_REVIEW,SUBMIT_FOR_REVIEW), (UNDER_REVIEW,CREATE_REVISION), (APPROVED,SUBMIT_FOR_REVIEW), (APPROVED,APPROVE), (APPROVED,REQUEST_CHANGES), and all 5 transitions from ARCHIVED.

**Verdict: state machine COMPLETE and CORRECT.** Master plan said "8 transitions" — actual count is 7 valid transitions out of `4 states × 5 actions = 20` cells (7 valid + 13 invalid = 20). The phrasing "8 valid transitions" in the brief was approximate.

---

## 5. Revision Chain Integrity

| Rule | Implementation | Test |
|------|---------------|------|
| Source row unchanged on CREATE_REVISION | `CreateJobProfileRevisionUseCase.createRevision` — `source` JpaEntity is never mutated; only a new `JobProfileJpaEntity` is `profiles.save(revision)`. | Source-code review only; no integration test asserts source's `updatedAt` is unchanged. |
| New row: `revisionNumber = source.revisionNumber + 1` | L71: `source.getRevisionNumber() + 1` | `Phase3JobProfileIntegrationTest.archivedRevisionsCanCoexist` retrieves 3,2,1 in order. |
| New row: `previousRevisionId = source.id` | L72: `source.getId()` passed | `archivedRevisionsCanCoexist` builds r2 with `previousRevisionId=r1.id` and active with `previousRevisionId=r2.id`. |
| Content deep-copied | L73-86 — all 13 long-text JSONB fields + actualizationDate copied | None — integration test does not assert deep-copy. |
| Partial unique index enforces single active per (tenant, project, position) | `005-create-job-profiles.yaml` L137-139 `CREATE UNIQUE INDEX uq_job_profiles_position_active ... WHERE status <> 'ARCHIVED'` | `partialUniqueIndexBlocksDoubleActiveProfile` triggers `uq_job_profiles_position_active` constraint violation. |
| Revision history retrievable, newest first | `JobProfileRepository.findAllByTenantIdAndProjectIdAndPositionIdOrderByRevisionNumberDesc` | `archivedRevisionsCanCoexist` asserts `.containsExactly(3, 2, 1)`. |

**Verdict: PASS at storage shape. End-to-end use-case test of `CreateJobProfileRevisionUseCase` MISSING (D-305).**

---

## 6. Multilingual Fields Verification

- **Primary locale enforced:** `JobProfileLocales.PRIMARY_LOCALE = "ru-RU"` (hard-coded). `SubmitJobProfileForReviewUseCase` calls `requirePrimaryLocaleFields(entity)` which checks 6 fields (purpose, mainDuties, responsibilityArea, kpi, education, experience). Failure throws code `PRIMARY_LOCALE_MISSING` → 409.
- **JSONB storage:** 12 columns per profile, each declared as `@JdbcTypeCode(SqlTypes.JSON)` with `columnDefinition = "jsonb"`. Liquibase 005 declares JSONB type. Storage tested implicitly by `Phase3JobProfileIntegrationTest` setting `Map.of("ru-RU", "Цель")` and persisting.
- **Missing-locale fallback:** `JobProfileLocales.pickAny()` returns primary if present else first non-blank across ALL_LOCALES. Frontend `pickLocalized()` (in `shared/lib/localized.ts`) mirrors this. **No documented contract / no test asserting the chain order.**
- **Test coverage:** No backend test asserts the validation REJECTS a submit with only `en-US` translation. **D-302.**

**Verdict: PASS storage; primary-locale enforcement implemented but UNTESTED at backend level.**

---

## 7. ABAC Write-Path Verification (Phase 3)

All 10 write use cases verified to call `AbacGate.enforceCanWriteInProject(ctx, projectId)` and (where positionId is known) `AbacGate.enforceCanWriteInDepartment(ctx, projectId, departmentId)`:

| Use Case | enforceCanWriteInProject | enforceCanWriteInDepartment | File:Line |
|----------|--------------------------|----------------------------|-----------|
| `CreateJobProfileUseCase` | ✅ L59 | ✅ L60-61 | CreateJobProfileUseCase.java |
| `UpdateJobProfileUseCase` | ✅ L63 | ✅ L64-65 | UpdateJobProfileUseCase.java |
| `SubmitJobProfileForReviewUseCase` | ✅ L61 | ✅ L62-63 | SubmitJobProfileForReviewUseCase.java |
| `ApproveJobProfileUseCase` | ✅ L66 | ✅ L67-68 | ApproveJobProfileUseCase.java |
| `RequestJobProfileChangesUseCase` | ✅ L72 | ✅ L73-74 | RequestJobProfileChangesUseCase.java |
| `ArchiveJobProfileUseCase` | ✅ L64 | ✅ L65-66 | ArchiveJobProfileUseCase.java |
| `CreateJobProfileRevisionUseCase` | ✅ L61 | ✅ L62-63 | CreateJobProfileRevisionUseCase.java |
| `CreateQuestionnaireUseCase` | ✅ (source-confirmed) | ✅ | CreateQuestionnaireUseCase.java |
| `UpdateAnswerUseCase` | ✅ L66 | ✅ L67-68 | UpdateAnswerUseCase.java |
| `SubmitQuestionnaireUseCase` | ✅ L67 | ✅ L68-69 | SubmitQuestionnaireUseCase.java |
| `ArchiveQuestionnaireUseCase` | ✅ L58 | ✅ L59-60 | ArchiveQuestionnaireUseCase.java |

**Verdict: ABAC write-path COMPLETE in source.** Denial-audit-row integration test still missing for Phase 3 (D-301 carryforward).

---

## 8. Audit Event Verification

11 new event constants:
- `JOB_PROFILE_CREATED`, `JOB_PROFILE_UPDATED`, `JOB_PROFILE_SUBMITTED`, `JOB_PROFILE_CHANGES_REQUESTED`, `JOB_PROFILE_APPROVED`, `JOB_PROFILE_ARCHIVED`, `JOB_PROFILE_REVISION_CREATED` (7)
- `JOB_ANALYSIS_QUESTIONNAIRE_CREATED`, `JOB_ANALYSIS_ANSWER_UPDATED`, `JOB_ANALYSIS_SUBMITTED`, `JOB_ANALYSIS_ARCHIVED` (4)

All present in `AuditAction.java` L47-59. `Phase3AuditActionsTest.allPhase3JobProfileActionsArePresent` + `allPhase3JobAnalysisActionsArePresent` statically validate presence by reflection.

**Source-level audit-write verification:**
- Every Phase 3 use case calls `audit.record(AuditEvent.builder()...build())` — confirmed in all 10 mutation use cases.
- Hash chain still computed in `JpaAuditService` (carryover from Phase 0+1).
- `actorUserId`, `tenantId`, `projectId`, `entityType`, `entityId` populated.
- `reason` populated for ARCHIVE / REQUEST_CHANGES / CREATE_REVISION.

**Gaps:**
- **No `before/after` JSON capture.** None of the use cases call `.beforeJson()` / `.afterJson()` on the AuditEvent builder. The `AuditEvent` class has these fields and `AuditService` is designed for them — but Phase 3 mutations record only the action + entity reference. For an UPDATE event this means the audit log captures *that* a change happened but not *what* changed. **D-303 (Medium).** Acceptable for MVP 1 phase 3 if explicitly documented as deferred; the master plan §17 hash-chain rule envisions before/after.
- **No happy-path integration row-write assertion.** PC-4 from Phase 2 — Phase 3 ships the static catalogue test only. **D-313 (Medium).**

---

## 9. Frontend Status-Aware UI Verification

| State | Behavior | Test | Verdict |
|-------|----------|------|---------|
| DRAFT | Textarea inputs + Save/Submit/Archive buttons | `JobProfileEditorPage.test.tsx` "DRAFT shows editable fields + Save/Submit" | PASS |
| UNDER_REVIEW | Approve + Request changes + Archive (no editing) | "UNDER_REVIEW shows Approve + Request changes for users with approve permission" | PASS (positive only) |
| APPROVED | **Read-only divs** (NOT disabled inputs), AlertOctagon "locked" notice, Create-revision + Archive buttons | "APPROVED renders fields read-only with Create new revision button" — asserts `readonly-purpose-ru-RU` div present AND `textarea-purpose-ru-RU` absent | **PASS — this is the correct pattern (visual locked state, not just `disabled` attr)** |
| ARCHIVED | Archived banner; no edit/approve actions | "ARCHIVED shows archived banner and no edit actions" | PASS |

`JobProfileFieldEditor.tsx` L78-101 — the conditional:
```tsx
readOnly ? (
  loc === active ? <div data-testid={`readonly-${fieldKey}-${loc}`}>...</div> : null
) : (
  <textarea hidden={loc !== active} data-testid={`textarea-${fieldKey}-${loc}`} ... />
)
```
satisfies the requirement to **render `<div>` not `<input disabled>`** when read-only.

---

## 10. Frontend Reason-Required Dialogs

| Trigger | Dialog | ≥20-char enforced? | Test |
|---------|--------|-------------------|------|
| Job Profile archive | `ReasonRequiredDialog` (`JobProfileActionsBar.tsx` L192-201) | UI: dialog enforces (component contract; not directly asserted in tests) | Indirect via integration tests |
| Job Profile request-changes | `ReasonRequiredDialog` (L181-190) | UI: same | Indirect |
| Questionnaire archive | `ReasonRequiredDialog` (`QuestionnairePage.tsx` L190-199) | UI: same | None |

**Backend enforcement:** `ArchiveJobProfileUseCase`, `RequestJobProfileChangesUseCase`, `ArchiveQuestionnaireUseCase` all enforce `MIN_REASON_LENGTH = 10`. The brief said "≥20 char min"; **backend is at 10**, not 20. **D-304 (Low–Medium).**

---

## 11. AI Advisory Marker

- `AIRecommendationPanel.tsx`:
  - Always renders status badge with tone `ai-suggestion` and label `aiAssist.advisory_label`.
  - Renders `data-testid="ai-disclaimer"` paragraph with `aiAssist.disclaimer` i18n key.
  - Accept/Reject buttons `disabled={disabled = true}` by default — no auto-acceptance possible.
- `AIRecommendationPanel.test.tsx` validates the disclaimer + badge.
- Visually distinct: `border-l-ai-suggestion` left accent, `bg-ai-suggestion-bg` background.

**Verdict: PASS — AI surface is clearly advisory; no auto-approve path exists.**

---

## 12. i18n Parity (90 new keys × 4 locales)

- `shared/i18n/__tests__/i18nParity.test.ts` enforces 3 invariants: full union, no orphans, equal counts.
- Locale files at `shared/i18n/locales/{en-US,ru-RU,uz-Cyrl-UZ,uz-Latn-UZ}.json`.
- **Verdict:** Parity is GREEN by construction — the test gates every PR.

---

## 13. No Salary Leakage

Grep `salary|Salary|SALARY` over:
- `backend/src/main/java/uz/hrlab/grading/jobprofile/**` — **0 matches**
- `backend/src/main/java/uz/hrlab/grading/jobanalysis/**` — **0 matches**
- `frontend/src/features/job-profiles/**` — **0 matches**
- `frontend/src/features/job-analysis/**` — **0 matches**

**Verdict: PASS — Phase 3 surface has zero salary fields, references, or test fixtures.**

---

## 14. Defects Found (Phase 3)

### D-301 — ABAC denial-audit row not integration-tested on Phase 3 use cases

- **Severity:** Medium
- **Affected component:** `access.application.AbacGate.recordDenial` × any Phase 3 write use case
- **Description:**
  - **Given** a Department-Manager user U6 attempting to update a JobProfile outside their dept scope,
  - **When** `UpdateJobProfileUseCase.update` is called and `AbacGate.enforceCanWriteInDepartment` rejects,
  - **Then** there is no test asserting an `ACCESS_DENIED_BY_ABAC` row landed in `system_audit_log` for any Phase 3 mutation.
- **Suggested fix:** add `@Tag("integration")` test `Phase3AbacDenialAuditTest` that exercises one Phase 3 use case with a denying ABAC context and asserts the row.
- **Owner:** backend-engineer

### D-302 — Primary-locale validation has no backend test

- **Severity:** Medium
- **Affected component:** `SubmitJobProfileForReviewUseCase.requirePrimaryLocaleFields`
- **Description:**
  - **Given** a job profile with `purposeI18n = { "en-US": "Purpose" }` (no ru-RU),
  - **When** `POST /api/v1/job-profiles/{id}/submit` is called,
  - **Then** backend should throw `PRIMARY_LOCALE_MISSING` → 409, but **no test** exists that asserts this. The 6-field whitelist (purpose, mainDuties, responsibilityArea, kpi, education, experience) is hardcoded and untested — a refactor that omits one field ships silently.
- **Suggested fix:** parameterized unit test on `SubmitJobProfileForReviewUseCase` per required field, plus one happy-path test where all 6 have ru-RU.
- **Owner:** backend-engineer

### D-303 — Audit events have no before/after JSON payload

- **Severity:** Medium
- **Affected component:** all 10 Phase 3 mutation use cases
- **Description:**
  - **Given** a `JOB_PROFILE_UPDATED` event,
  - **When** queried from `system_audit_log`,
  - **Then** the row carries `action`, `entity_type`, `entity_id`, `actor_user_id` but **NOT** `before_json` / `after_json` — these are NULL because no use case calls `.beforeJson()` / `.afterJson()` on the AuditEvent builder. Master plan §17 implies the hash-chain row should capture the diff for audit-trail completeness. For an UPDATE this is the difference between "something changed" and "X changed from a to b".
- **Suggested fix:** decide explicitly (a) defer payload capture to MVP 2 and document, or (b) implement a JsonNode canonicalizer + per-use-case before/after capture (filtering salary fields, though Phase 3 has none).
- **Owner:** backend-engineer + security-engineer

### D-304 — Reason min length is 10, not 20 (backend) — mismatch with brief

- **Severity:** Low
- **Affected component:** `ArchiveJobProfileUseCase` (L30), `RequestJobProfileChangesUseCase` (L35), `ArchiveQuestionnaireUseCase` (L26)
- **Description:**
  - **Given** the Phase 3 brief states "reason required dialog for archive + request-changes (≥20 char min)",
  - **When** backend code sets `MIN_REASON_LENGTH = 10`,
  - **Then** the policy is 10 chars, not 20 — a UI that requires 20 will pass backend but the backend itself enforces a weaker contract. If the master-plan / PRD specifies 20, this is a spec drift; if 10 is the right value, the brief should be updated.
- **Suggested fix:** align with PRD §E6 / master plan §14 explicit minimum, then add a test for boundary (length=N-1 rejected, length=N accepted).
- **Owner:** hr-product-owner (decide value) + backend-engineer (align + test)

### D-305 — `CreateJobProfileRevisionUseCase` end-to-end use-case test missing

- **Severity:** Medium
- **Affected component:** `CreateJobProfileRevisionUseCase`
- **Description:**
  - **Given** an APPROVED profile P1 (rev 1),
  - **When** `revisionUseCase.createRevision(P1.id)` is called,
  - **Then** there is no test that asserts (a) P1 is unchanged in DB after the call, (b) a new row P2 exists with `revisionNumber=2`, `previousRevisionId=P1.id`, `status=DRAFT`, content equal to P1's content, (c) an audit row `JOB_PROFILE_REVISION_CREATED` is written. The current `Phase3JobProfileIntegrationTest` only proves repo-level constraints — not the use case wiring.
- **Suggested fix:** `@SpringBootTest` integration test that calls `revisionUseCase.createRevision` with a seeded APPROVED profile and a `TenantContext` mocked via `TestSecurityContext`.
- **Owner:** backend-engineer

### D-306 — Frontend QuestionnairePage archive button uses wrong permission

- **Severity:** Medium
- **Affected component:** `frontend/src/features/job-analysis/pages/QuestionnairePage.tsx` line 130
- **Description:**
  - **Given** the questionnaire archive button,
  - **When** rendered for a user with `JOB_ANALYSIS_EDIT` but not `JOB_PROFILE_EDIT`,
  - **Then** the button is hidden by `<PermissionGate permission={PERMISSIONS.JOB_PROFILE_EDIT}>` — the WRONG permission. Should be `JOB_ANALYSIS_EDIT`. This is also a hint that `PERMISSIONS.JOB_ANALYSIS_EDIT` / `JOB_ANALYSIS_READ` constants are not even defined in `shared/types/permissions.ts` (see D-307).
- **Suggested fix:** change permission to `PERMISSIONS.JOB_ANALYSIS_EDIT`. Add `JOB_ANALYSIS_READ`/`JOB_ANALYSIS_EDIT` constants to PERMISSIONS first.
- **Owner:** frontend-engineer

### D-307 — Frontend PERMISSIONS map missing JOB_ANALYSIS_* constants

- **Severity:** Medium (root cause of D-306)
- **Affected component:** `frontend/src/shared/types/permissions.ts` (L26-28 has only JOB_PROFILE_*)
- **Description:**
  - **Given** backend ships `JOB_ANALYSIS_READ` and `JOB_ANALYSIS_EDIT` permission codes (PermissionCodes.java L39-40, Liquibase seed 007),
  - **When** the frontend gates `<PermissionGate>` on a job-analysis screen,
  - **Then** there is no `PERMISSIONS.JOB_ANALYSIS_READ` / `PERMISSIONS.JOB_ANALYSIS_EDIT` available — developers fall back to closest-named permission (D-306), which is a silent BOLA-class risk: a user without `JOB_ANALYSIS_EDIT` but with `JOB_PROFILE_EDIT` (e.g. a Department Manager who has JOB_PROFILE_EDIT but not JOB_ANALYSIS_EDIT per the matrix) will see the questionnaire archive button.
- **Suggested fix:** add `JOB_ANALYSIS_READ: 'JOB_ANALYSIS_READ'` and `JOB_ANALYSIS_EDIT: 'JOB_ANALYSIS_EDIT'` to `PERMISSIONS`; refactor `QuestionnairePage` + `QuestionnaireController` callers to use them; add a per-permission negative test (e.g. PermissionGate hides button for `JOB_PROFILE_EDIT`-only role).
- **Owner:** frontend-engineer

### D-308 — Frontend QuestionnaireStatus includes `IN_PROGRESS` but backend does not

- **Severity:** Medium
- **Affected component:** `frontend/src/features/job-analysis/types.ts` L3 vs `backend/src/main/java/uz/hrlab/grading/jobanalysis/domain/QuestionnaireStatus.java`
- **Description:**
  - **Given** backend defines `QuestionnaireStatus = { DRAFT, COMPLETED, ARCHIVED }`,
  - **When** frontend declares `QuestionnaireStatus = 'DRAFT' | 'IN_PROGRESS' | 'COMPLETED' | 'ARCHIVED'`,
  - **Then** frontend logic that branches on `IN_PROGRESS` (e.g. `QuestionnairePage.tsx` test fixtures, `STATUS_LABEL` map) will be a dead branch in production — and any code emitting `IN_PROGRESS` from the API mock will be schema-incoherent. The QuestionnairePage test fixtures use `status: 'IN_PROGRESS'` for two of the 4 cases — meaning these tests do not match production status semantics.
- **Suggested fix:** decide: either add `IN_PROGRESS` to backend enum + Liquibase CHECK + transition policy, OR remove it from frontend types + tests + MSW fixtures.
- **Owner:** hr-product-owner (decide) + backend-engineer + frontend-engineer

### D-309 — No HTTP-layer cross-tenant probe test for Phase 3 endpoints

- **Severity:** Medium
- **Affected component:** all 13 Phase 3 endpoints
- **Description:**
  - **Given** Phase 2 added per-endpoint `*ControllerSecurityTest` for tenant_id-in-body, anon, wrong-perm,
  - **When** Phase 3 follows the same pattern for permission checks,
  - **Then** Phase 3 security tests do NOT add a "wrong tenant" case (e.g. authenticated as Tenant A, GET `/api/v1/job-profiles/{tenant-B-uuid}` → 404). The current tests cover authority gates well but skip cross-tenant existence-leak protection at HTTP layer.
- **Suggested fix:** extend `JobProfileControllerSecurityTest` and `JobAnalysisControllerSecurityTest` with a "cross-tenant returns 404 not 403" assertion (mock `findQuery.findById(...)` to throw `TenantAccessDeniedException` and assert 404 body shape).
- **Owner:** backend-engineer + QA

### D-310 — JobAnalysis repository-level tenant isolation test missing

- **Severity:** Medium
- **Affected component:** `JobAnalysisQuestionnaireRepository`, `JobAnalysisAnswerRepository`
- **Description:**
  - **Given** `Phase3JobProfileIntegrationTest` proves Tenant A cannot read Tenant B's JobProfile at repo layer,
  - **When** the same probe is attempted for Questionnaire / Answer,
  - **Then** no test exists. The repositories extend `TenantAwareRepository` (verified by ArchUnit Rule 1) so the contract is enforced — but there is no positive proof.
- **Suggested fix:** add `Phase3JobAnalysisIntegrationTest` with questionnaire + answer cross-tenant probe and uniqueness/storage shape tests.
- **Owner:** backend-engineer

### D-311 — Auto-save debounce not asserted for Job Profile (30s)

- **Severity:** Low
- **Affected component:** `JobProfileEditorPage.tsx` L122-131
- **Description:**
  - **Given** the editor auto-saves every 30s when `dirtyRef.current = true`,
  - **When** a developer changes the interval to 30ms (typo),
  - **Then** no test catches it. (The Questionnaire 500ms debounce IS tested in `QuestionnairePage.test.tsx`.) The JobProfile interval is exposed as `AUTO_SAVE_DEBOUNCE_MS` but never asserted.
- **Suggested fix:** add a `vi.useFakeTimers()` test on JobProfileEditorPage that types, advances 29s, asserts NO save; advances 1s, asserts save called.
- **Owner:** frontend-engineer

### D-312 — `noTenantIdLeak.test.ts` does not cover Phase 3 fetchers

- **Severity:** Medium
- **Affected component:** `frontend/src/shared/api/__tests__/noTenantIdLeak.test.ts`
- **Description:**
  - **Given** the test asserts no `tenant_id` leaks for Phase 2 fetchers (projectApi/organizationApi/positionApi),
  - **When** Phase 3 ships `jobProfileApi` and `jobAnalysisApi` with 15 fetchers,
  - **Then** none of them are asserted. A Phase 3 fetcher that mistakenly sends `?tenantId=...` would pass CI silently.
- **Suggested fix:** add `it()` blocks for every Phase 3 fetcher inside the existing `describe('no tenant_id leak in outbound API requests')`.
- **Owner:** frontend-engineer

### D-313 — End-to-end audit-row write assertion still missing (carryover PC-4)

- **Severity:** Medium
- **Affected component:** Phase 2 and Phase 3 lifecycle audit events
- **Description:**
  - **Given** PC-4 in Phase 2 review required "one end-to-end audit row assertion per Phase 2 resource",
  - **When** Phase 3 doubles down on audit events (11 new),
  - **Then** there is still no integration test that POSTs to a controller and queries `system_audit_log` to assert the row landed with the right `action`, `entity_type`, `entity_id`, non-null `hash_current`, and `hash_prev → hash_current` continuity. `Phase3AuditActionsTest` is a static catalogue check only.
- **Suggested fix:** one `Phase3AuditLifecycleTest` that runs CreateJobProfile → SubmitForReview → Approve → CreateRevision and asserts 4 audit rows with correct fields and hash continuity.
- **Owner:** backend-engineer + QA

### D-314 — Position-scoped GET `/job-profile` returns 404 vs empty: behavior undocumented

- **Severity:** Low
- **Affected component:** `PositionJobProfileController.getActive`
- **Description:**
  - **Given** a position with NO active profile,
  - **When** `GET /api/v1/positions/{positionId}/job-profile` is called,
  - **Then** `findQuery.findActiveByPositionId(positionId)` — behavior is not documented (empty/null/404?). The frontend `JobProfileEditorPage` treats `profile === null` as empty-state and renders a Create CTA. Need explicit contract.
- **Suggested fix:** document in OpenAPI (200 with empty body, or 404). Add controller test.
- **Owner:** backend-engineer + hr-product-owner

### D-315 — Cross-FK constraint between `job_profiles.project_id` and `positions.project_id` not enforced

- **Severity:** Medium
- **Affected component:** Liquibase tenant-schema `005-create-job-profiles.yaml`
- **Description:**
  - **Given** the comment on `005-create-job-profiles.yaml` says "composite FK (project_id, position_id) → positions(project_id, id) ... cf. F-204 pattern",
  - **When** the actual SQL is inspected (L121-123),
  - **Then** the FK is simply `fk_job_profiles_position FOREIGN KEY (position_id) REFERENCES positions(id)` — NOT a composite FK on `(project_id, position_id)`. The defense-in-depth pattern documented in the comment is NOT implemented. A position from project A could (in theory, via direct SQL) be referenced from a job profile carrying project B's `project_id`. Application code prevents this; the DB does not.
- **Suggested fix:** add unique constraint `positions(project_id, id)` (if missing) then change FK to `FOREIGN KEY (project_id, position_id) REFERENCES positions(project_id, id)`. Same review needed for `job_analysis_questionnaires.position_id`.
- **Owner:** db / backend-engineer + security-engineer

---

## 15. Missing Tests for Phase 4 Entry

| Pack | Missing test | File |
|------|--------------|------|
| HTTP cross-tenant | TIP-class 404 on Phase 3 endpoints (D-309) | extend `*ControllerSecurityTest` |
| Integration | JobAnalysis repo tenant probe (D-310) | new `Phase3JobAnalysisIntegrationTest` |
| Integration | ABAC denial audit row (D-301) | new `Phase3AbacDenialAuditTest` |
| Integration | end-to-end audit row + hash chain on Phase 3 lifecycles (D-313) | new `Phase3AuditLifecycleTest` |
| Use case | CreateJobProfileRevisionUseCase happy-path + immutability of source (D-305) | new `CreateJobProfileRevisionUseCaseTest` |
| Use case | SubmitForReview primary-locale validation per field (D-302) | new `SubmitJobProfileForReviewUseCaseTest` |
| JSONB | round-trip for all 12 long-text columns | new `JobProfileJsonbRoundTripTest` |
| Frontend | JobProfile auto-save 30s debounce (D-311) | extend `JobProfileEditorPage.test.tsx` |
| Frontend | Phase 3 fetchers in noTenantIdLeak (D-312) | extend `noTenantIdLeak.test.ts` |
| Frontend | per-permission negative test for QuestionnairePage archive (D-306/307) | new `QuestionnairePage.permissions.test.tsx` |
| Frontend | UNDER_REVIEW read-only field state | extend `JobProfileEditorPage.test.tsx` |
| Frontend | Reason dialog min-length boundary | extend `JobProfileActionsBar.test.tsx` |

**Total: 12 test files / suites required before Phase 4 entry.**

---

## 16. Test Execution Result

- **Frontend:** Vitest ran on the review machine. Result: **23 test files PASS; 88 tests PASS / 0 fail** — duration 141s. Three thread-worker timeouts occurred at vitest pool level (vitest 3 / Node 22 on Windows / heavy environment-setup), but every test ultimately ran and passed. The brief claimed 95/95; the actual collected pass count is 88. Discrepancy is **either a transient worker-pool issue on this machine** (3 timeouts) **or a counting difference** (e.g. excluded `.test.ts` files in some configs). **Action: confirm on CI with surefire-equivalent JUnit report.**
- **Backend:** Testcontainers gating remains in place. The brief claimed 180 tests (144 pass + 36 Docker-skipped). At file count: 38 test files under `src/test/java/uz/hrlab/grading/` (Phase 3 added 7: 2 jobprofile domain policy tests + 2 controller security tests + 2 phase3 integration tests + 1 audit catalogue test). The 180 total is plausible; **CI surefire report is the source of truth.** Review machine cannot execute Docker.

---

## 17. Regression Risks for Phase 4 (Methodology Builder)

1. **High** — D-303 (no before/after capture) and D-313 (no end-to-end audit row test) will compound as Phase 4 ships `METHODOLOGY_CREATED/EDITED/APPROVED/LOCKED` (4 more events). Master plan §14 makes methodology lock + version a release blocker — without before/after, "what changed" is invisible.
2. **High** — D-308 (status enum drift between front and back) is a class of bug that will repeat: methodology has `DRAFT/APPROVED/LOCKED/ARCHIVED` on backend; if frontend declares `IN_REVIEW` etc., the same drift will recur.
3. **Medium** — D-315 (missing composite FK on `(project_id, position_id)`) — when Phase 4 wires Methodology to Position (or to Project), the same defense-in-depth gap will appear. Fix the pattern now.
4. **Medium** — D-302 (primary-locale validation untested) — Phase 4 multiplies multilingual surface (factors + levels + names × 4 locales). Without a tested validator, broken submits will leak through.
5. **Medium** — D-307 (frontend PERMISSIONS missing JOB_ANALYSIS_*) — Phase 4 will add `METHODOLOGY_CREATE/EDIT/APPROVE/LOCK`. Establish the contract NOW: backend and frontend permission codes are 1:1 mirrors.
6. **Low** — D-311 (auto-save 30s untested) — methodology builder will have similar autosave; replicate the test pattern.

---

## 18. Release Gate Decision

> **DECISION: GO WITH CONDITIONS** for Phase 3.

Rationale:
- The state machine (7 transitions + 13 invalid + ARCHIVED terminal) is **fully unit-tested**.
- Immutability policy is **unit-tested** (APPROVED + ARCHIVED reject mutation; UNDER_REVIEW rejects edit).
- Revision chain at storage shape is **integration-tested** (partial unique index + history retrieval).
- ABAC write-path is **enforced** on all 10 mutation use cases by source review.
- All 11 new audit constants are **present and statically asserted**.
- Frontend status-locked UI uses **read-only divs** (not disabled inputs) — the master-plan pattern.
- AI panel is **clearly advisory**, no auto-approve path.
- i18n parity is **automated** (D-205 closed).
- **No salary leakage** anywhere in Phase 3.

Defects are about **coverage gaps and minor contract drifts**, not flawed primitives. None is Critical.

**Conditions that MUST be met before Phase 4 begins (blocking):**

- **PC3-1** Fix D-307 + D-306 (frontend PERMISSIONS map + wrong-permission gate on QuestionnairePage archive). One line each — Critical-class regression risk if Phase 4 copies the pattern.
- **PC3-2** Decide and align D-308 (QuestionnaireStatus IN_PROGRESS) — either backend adds the state or frontend removes it. Cannot ship a Phase 4 methodology workflow with the same enum-drift class of bug latent.
- **PC3-3** Add D-301 (ABAC denial audit row test) + D-313 (end-to-end audit row write test) — Phase 4 doubles audit events; the assertion pattern must exist before then.
- **PC3-4** Decide D-303 (audit before/after capture) — defer with explicit DOCUMENTATION + JIRA backlog, OR implement before Phase 4. Cannot leave silent.
- **PC3-5** Resolve D-304 (reason min length) — pick 10 or 20 in PRD, then enforce + test.
- **PC3-6** Fix D-315 (composite FK on job_profiles → positions) — small DDL change; defense-in-depth pattern.

**Non-blocking (track in sprint):**
- **PC3-7** Close D-302 (primary-locale validation tests), D-305 (revision use-case test), D-309 (cross-tenant HTTP test), D-310 (job-analysis tenant probe), D-311 (auto-save debounce), D-312 (Phase 3 fetchers in no-leak test), D-314 (empty-state contract) during Phase 4 W1.

If **PC3-1 … PC3-6** are met before Phase 4 PRs land, Phase 3 ships green. Otherwise the gate flips to **NO-GO**.

---

## 19. Top Action Items (prioritized)

### backend-engineer

1. **(Blocking, PC3-3)** Add `Phase3AbacDenialAuditTest` + `Phase3AuditLifecycleTest` — proves ABAC denial AND happy-path audit rows land with correct fields and hash continuity (D-301, D-313).
2. **(Blocking, PC3-4)** Make a documented decision on D-303 — either capture before/after now, or add an explicit comment in `AuditService` + backlog ticket.
3. **(Blocking, PC3-5)** Align reason min length to PRD value (D-304); add boundary tests.
4. **(Blocking, PC3-6)** Add composite FK `(project_id, position_id)` to `job_profiles` and `job_analysis_questionnaires` (D-315).
5. Add `Phase3JobAnalysisIntegrationTest` (D-310), `CreateJobProfileRevisionUseCaseTest` (D-305), and `SubmitJobProfileForReviewUseCaseTest` parametrised per required field (D-302).
6. Extend `*ControllerSecurityTest` with cross-tenant 404 assertion (D-309).
7. (Optional) Add `Phase3JsonbRoundTripTest` for the 12 long-text columns.

### frontend-engineer

1. **(Blocking, PC3-1)** Add `JOB_ANALYSIS_READ` + `JOB_ANALYSIS_EDIT` to `PERMISSIONS`; replace `JOB_PROFILE_EDIT` with `JOB_ANALYSIS_EDIT` on `QuestionnairePage.tsx` line 130 (D-306 + D-307). Add negative permission test.
2. **(Blocking, PC3-2 → if fix lands on frontend)** Remove `IN_PROGRESS` from `QuestionnaireStatus` + update fixtures + tests (D-308).
3. Extend `noTenantIdLeak.test.ts` with all Phase 3 fetchers (D-312).
4. Add JobProfileEditorPage 30s debounce test (D-311) and UNDER_REVIEW read-only-state test.
5. Add reason-dialog boundary test (D-304 alignment).

### db / devops

1. Surface CI surefire + Vitest JUnit reports so the "180 tests" and "95/95" numbers in the brief are verifiable (D-313-class).
2. Verify Liquibase 005/006/007 roll back cleanly on a fresh DB.

### security-engineer

1. Sign off on D-303 decision (audit before/after capture) — this is materially a threat-model gap (audit completeness).
2. Sign off on D-304 reason length (audit-trail evidentiary value).
3. Sign off on D-315 composite FK (defense-in-depth on cross-tenant writes).
4. Re-confirm the 404 vs 403 policy for D-309 cross-tenant probe additions.

### hr-product-owner

1. Decide D-308 (questionnaire IN_PROGRESS state) — does the methodology require an intermediate "in progress" state?
2. Decide D-304 reason min length (10 or 20).
3. Decide D-314 empty-state contract for `GET /api/v1/positions/{id}/job-profile`.

---

**End of Phase 3 QA Review.**

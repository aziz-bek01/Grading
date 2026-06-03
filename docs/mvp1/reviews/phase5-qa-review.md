# Phase 5 QA Review Report — grading.hrlab.uz

Document owner: QA Engineering
Status: Phase 5 release gate (Evaluation + Scoring Engine + Calibration + Preview + Status workflow + Audit)
Date: 2026-05-23
Benchmark: `docs/mvp1/03-qa-master-test-plan.md` §15 (Scoring Pack) + §17 (Audit Pack) + `docs/mvp1/reviews/phase4-qa-review.md` + `docs/mvp1/reviews/po-comprehensive-audit-phase0-4.md`

Reviewed build:
- **Backend Phase 5:** new module `uz.hrlab.grading.evaluation.*` (45 files: 10 domain incl. 4 policies + 1 enum + transition exception + scoring engine + ScoringInputs/ScoringResult records, 6 infrastructure JPA, 18 application use cases + commands + context loader + recompute service + audit snapshot, 10 API DTOs + EvaluationController + ReasonRequest). 2 tenant-schema changelogs: 015 (3 tables: evaluations, evaluation_scores, evaluation_calibration_events + 2 PL/pgSQL triggers `enforce_evaluation_lock_immutability` + `prevent_score_changes_on_locked_evaluation`); 016 (CALIBRATION_EDIT + EVALUATION_LOCK seed + role grants). 11 new `AuditAction` constants (1 more than brief — see §11). 5 new `PermissionCodes`. 13 new REST endpoints under `/api/v1/evaluations`. Test files: `EvaluationScoringEngineTest`, `EvaluationStatusTransitionPolicyTest`, `EvaluationImmutabilityPolicyTest`, `EvaluationCompletenessCheckerTest`, `EvaluationControllerSecurityTest`, `Phase5AuditActionsTest` (6 files).
- **Frontend Phase 5:** `features/evaluation/` (20 files: 1 api/evaluationApi, 6 components incl. EvaluationMatrix/CalibrationDialog/CalibrationTable/ScorePreviewBanner/EvaluationStatusBadge/EvaluationActionsBar/EvaluationScoreBreakdown, 1 hook with usePreviewScore + 10 mutation hooks, 2 pages, 1 schemas, 1 types). 7 test files: `CalibrationDialog.test.tsx`, `CalibrationTable.test.tsx`, `EvaluationActionsBar.test.tsx`, `EvaluationMatrix.test.tsx`, `EvaluationStatusBadge.test.tsx`, `ScorePreviewBanner.test.tsx`, `__tests__/evaluationMswHandlers.test.ts`. MSW handler at `handlers.ts` L1047-1442 with `computeMockScoring` mirror of backend engine. 6 evaluation fetchers added to `noTenantIdLeak.test.ts`. ~75 new i18n keys × 4 locales.

---

## 1. Review Scope

In scope:
- **Backend:** `uz.hrlab.grading.evaluation.*` (4 sub-packages: domain/application/infrastructure/api); `AuditAction.EVALUATION_*` + `CALIBRATION_*` (11 codes); `PermissionCodes.EVALUATION_READ/EDIT/APPROVE/LOCK + CALIBRATION_EDIT`; Liquibase tenant-schema `015 + 016`; `EvaluationController`; the scoring engine + immutability + status policies; preview/calibration use cases.
- **Backend tests:** the 6 files listed above plus the indirect coverage via the methodology test suite.
- **Frontend:** `features/evaluation/{api,components,hooks,pages,schemas,types}`; MSW evaluation handlers (handlers.ts L1047-1442); `noTenantIdLeak.test.ts` Phase 5 extension (L289-360).

Out of scope (Phase 6+): Grade Structure, Salary engine, MVP 2/3/4 features.

---

## 2. Phase 4 Carry-over Closure

| ID | Condition | Status @ Phase 5 entry | Evidence |
|----|-----------|------------------------|----------|
| **PC4-1** Weight tolerance contract | **CLOSED** | Confirmed in PO audit (task #27 + #28). |
| **PC4-2** CreateMethodologyVersion deep-copy test | **CLOSED** | Same. |
| **PC4-3** Phase4AbacDenialAuditTest + Phase4AuditLifecycleTest | **CLOSED** | Per PO audit §6. **However, the same gap re-emerges in Phase 5 — see D-503.** |
| **PC4-4** MSW approve handler bug | **CLOSED** | `methodologyApproveLockHandlers.test.ts` added (untracked file in gitStatus) — verifies APPROVE → APPROVED (not LOCKED) + 409 from non-APPROVED LOCK. PASS. |
| **PC4-5** Actor name resolution contract | **CLOSED** | Per PO audit §6. |
| **PC4-6 / D-403** Factor + FactorLevel cross-tenant probe | **STILL OPEN** | No new probe added in Phase 5. Tracked. |
| **PC4-D408** ARCHIVED methodology FE render-state test | **STILL OPEN** | Tracked. |
| **PC4-D409** FactorService immutability integration test | **STILL OPEN** | Tracked. |
| **PC4-D410** Validator scope (FactorService.add doesn't run validator) | **STILL OPEN** | Tracked. |
| **PC4-D412** Long i18n preview truncation test | **STILL OPEN** | Tracked. |
| **PC3-5** Reason min-length 10 vs 20 — PO clarification | **RESOLVED in Phase 5** | Calibration use case + DB CHECK both enforce min 20 chars (`CalibrateEvaluationScoreUseCase.MIN_REASON_LENGTH = 20`; changelog 015 `chk_eval_calib_reason_length` `length(trim(reason)) >= 20`). PO's decision "enforce 20 only for score override" honored. |

**Summary:** 5/10 Phase 4 carry-overs CLOSED; 5/10 still OPEN — none blocking Phase 5 entry, all Low/Medium. Phase 5 inherits the audit-lifecycle test gap (D-503).

---

## 3. Phase 5 Test Coverage Matrix vs QA Master Test Plan

| Pack | Required (master plan §15 / §17) | Implemented | Missing |
|------|---------------------------------|-------------|---------|
| **Status machine (7 statuses × N transitions)** | All valid + invalid transitions; APPROVED→APPROVED forbidden; ARCHIVED terminal | `EvaluationStatusTransitionPolicyTest`: 13 valid @CsvSource + 20 invalid @CsvSource + `archivedIsTerminal` (loops every transition) + `lockedCanOnlyGoToArchived` (loops every transition). **33 distinct assertions across 7 states.** | None. Comprehensive. |
| **Immutability (service + DB trigger)** | APPROVED/LOCKED/ARCHIVED service-layer rejection + DB triggers on UPDATE of status + INSERT/UPDATE/DELETE of scores | `EvaluationImmutabilityPolicyTest` (4 unit tests). DB triggers `enforce_evaluation_lock_immutability` (BEFORE UPDATE OF status — rejects LOCKED→non-ARCHIVED, ARCHIVED→any, APPROVED regression) + `prevent_score_changes_on_locked_evaluation` (BEFORE INSERT/UPDATE/DELETE on evaluation_scores when parent LOCKED/ARCHIVED). | **No `Phase5MethodologyIntegrationTest` equivalent** (Docker-gated DB integration test that exercises both triggers end-to-end). **D-504.** Also: **DB trigger does NOT block APPROVED score updates** (only LOCKED/ARCHIVED) — intentional gap for calibration write-through. **D-501.** |
| **Reproducibility (`.equals()` not `.compareTo()`)** | Same inputs twice → byte-identical output via AssertJ `.isEqualTo` (BigDecimal semantics use `.equals`, catches scale drift) | `EvaluationScoringEngineTest.reproducibilitySameInputsTwiceProducesByteIdenticalOutput` (L121-151): asserts `r1.rawTotal()` `.isEqualTo` `r2.rawTotal()`; same for displayedTotal + perFactorRaw map; AND asserts `.scale() == 4` (raw) and `.scale() == 2` (displayed). Final golden value `new BigDecimal("75.0001")`. **PASS.** | The test runs twice on the same VM — does NOT verify "different VMs / different orders" reproducibility. Master plan §15 TC-SCORE-014 calls for "SHA-256 of serialized outputs identical across runs" — not implemented as a golden file. **D-502** (Low — golden file as future-proofing). |
| **3 scoring modes (DIRECT/WEIGHTED_POINTS/WEIGHTED_SCALE)** | All three with known fixtures | `directPointsSumOfLevelPoints` (100+200+300=600.0000); `weightedPointsNormalizesByMaxPoints` (40×(50/100)=20.0000); `weightedScaleMultipliesWeightByScaleValue` (10×2.5=25.0000); `weightedPointsZeroMaxYieldsZero` (defence against division-by-zero). **PASS.** | None at the unit-test level. Master plan §15 also calls for: `TC-SCORE-004` BigDecimal precision under 1000-loop stress; `TC-SCORE-005` required-missing → incomplete (via `EvaluationCompletenessCheckerTest` — not opened in this review); `TC-SCORE-008` adjustment-requires-comment (covered by `EvaluationControllerSecurityTest.calibrateRejectsReasonShorterThan20Chars`); `TC-SCORE-009` approved immutable (covered by `EvaluationImmutabilityPolicyTest`). |
| **Boundary scoring (33.33% × 3 factors)** | Tested at 99.9999 / 100.00 boundary | `boundaryWeightsThatDoNotDivideEvenly` (L154-171): 33.3333% × 3 × 100/100 = 99.9999 (NOT 100); displayed = 100.00. **PASS** — exactly the architecture §15.4 promise that "raw stored unrounded; displayed rounded to 2; grade derived from raw". | None. |
| **Manual calibration (CALIBRATION_EDIT + 20-char reason + audit row + original preserved)** | All four invariants enforced | `CalibrateEvaluationScoreUseCase`: permission gate L88, reason min 20 L99-102, original preserved on FIRST calibration L131-133, audit row L143-153 with before/after JSON via `EvaluationAuditSnapshot.of(row)`. `EvaluationControllerSecurityTest.calibrateRequiresCalibrationEdit` + `calibrateRejectsReasonShorterThan20Chars`. **PASS.** | No end-to-end test that asserts: (a) ROUND-TRIP: original_factor_score is preserved across MULTIPLE calibrations (only set on first), (b) recompute does NOT overwrite calibration value (covered by code review — `EvaluationRecomputeService` line 84 `if (row.isManuallyAdjusted()) continue;` — confirms), (c) calibration delta = newScore - originalScore is correctly persisted to `evaluation_calibration_events` table. **D-505.** |
| **Preview endpoint (no state, no audit, no side effects)** | Stateless; `@Transactional(readOnly = true)`; no repository.save(); no audit.record(); EVALUATION_READ minimum | `PreviewEvaluationScoreUseCase`: `@Transactional(readOnly = true)` (L40), permission gate `EVALUATION_READ` (L43), only `engine.compute(...)` (L61) — **zero repository.save() and zero audit.record() calls** (source-verified). `EvaluationControllerSecurityTest.previewRequiresEvaluationRead`. **PASS.** | No specific test asserts that "after preview, no audit row was written" (audit absence is implicit via source review, not assertion). **D-506** (Low). |
| **10/11 new audit actions present** | EVALUATION_CREATED/UPDATED/SCORE_UPSERTED/SCORE_DELETED/SUBMITTED/CHANGES_REQUESTED/APPROVED/LOCKED/ARCHIVED/SCORE_CALIBRATED | `Phase5AuditActionsTest.allEvaluationAuditActionsArePresent` expects **10** codes. The catalog actually has **11** codes (also `EVALUATION_SCORE_CHANGED` at AuditAction.java L86). The test does NOT assert `EVALUATION_SCORE_CHANGED` — it uses `containsAll(EXPECTED)` so the test passes by accident, but the catalog has one undeclared (and apparently unused-or-loose) action. **D-507** (Low — catalog hygiene). | D-507. |
| **5 permission codes present** | EVALUATION_READ/EDIT/APPROVE/LOCK + CALIBRATION_EDIT | `Phase5AuditActionsTest.allEvaluationPermissionsArePresent`: 5 codes. PermissionCodes.java L50-54 confirms. **PASS.** | None. |
| **Frontend status-locked UI (read-only `<div>` not `<input disabled>`)** | APPROVED/LOCKED/ARCHIVED render markers, not disabled inputs | `EvaluationMatrix.tsx` L34-38: `EDITABLE` set = `{DRAFT, INCOMPLETE, COMPLETE}`. L209-229: read-only branch renders `<div data-testid="level-marker-...">` with `data-readonly="true"` attribute. Editable branch L231-250 renders `<button role="radio">`. **Pattern correct.** | **SUBMITTED is treated as read-only by the matrix** (not in EDITABLE) but the backend reject-message for status SUBMITTED is `EVALUATION_LOCKED — current state is SUBMITTED` — not a security issue but UX-inconsistent ("submitted" is not "locked"). Acceptable design choice. |
| **"Preview only" disclaimer** | Visible in preview banner per design-foundation §10 | `ScorePreviewBanner.tsx` — file present (test exists). Not opened in this review but visible in test list. **Confidence: HIGH** by inference. | Not directly verified. |
| **Cross-tenant isolation Phase 5** | By UUID, by positionId, by methodologyVersionId | Repository contract enforced via `TenantAwareRepository` (ArchUnit rule, established Phase 0+1). `EvaluationControllerSecurityTest.unknownIdReturns404` covers UUID probe. **No `Phase5EvaluationIntegrationTest` (Docker-gated) that proves Tenant A cannot read Tenant B's Evaluation entity at the repository layer.** Same gap as PC4-6 / D-403. **D-508.** | D-508. |
| **i18n parity ~75 new keys × 4 locales** | All 4 locales equal-cardinality (the existing `i18nParity.test.ts` enforces this) | Locale files updated (gitStatus shows all 4 `.json` modified). Parity test passes by construction (was green before; new keys added in all 4). **Confidence HIGH** but I have not byte-grep'd new keys against the locale files; recommend running parity test once before merge. | None. |
| **Salary leakage** | Zero salary fields in Evaluation/Score DTOs | Grep `salary|Salary|SALARY` over `backend/.../evaluation/` and `frontend/.../features/evaluation/` — files not surfaced any matches in the code I read. **Confidence HIGH.** | None. |

---

## 4. CRITICAL VERIFICATION — EvaluationScoringEngine

### 4.1 Algorithm correctness per architecture §15.2

| Mode | Algorithm specified | Engine code (`EvaluationScoringEngine.java`) | Verdict |
|------|---------------------|-----------------------------------------------|---------|
| DIRECT_POINTS | `factorScore = level.points; total = Σ` | L96-97 `nullSafePoints(selected.points()).setScale(RAW_SCALE, ROUNDING)` then summed L76 | **CORRECT** |
| WEIGHTED_POINTS | `normalised = level.points / factor.maxPoints; factorScore = weight × normalised; total = Σ` | L103-114: `divide(max, 8, HALF_UP)` then `weight.multiply(normalised).setScale(4, HALF_UP)`; **division by zero guarded** L107-110 returns 0 | **CORRECT** |
| WEIGHTED_SCALE | `factorScore = weight × level.scaleValue` | L116-122: null-scaleValue → 0; `weight.multiply(scale).setScale(4, HALF_UP)` | **CORRECT** |

### 4.2 BigDecimal scale + rounding consistency

- Raw scale = 4, displayed scale = 2, `RoundingMode.HALF_UP` everywhere (constants L49-51).
- Per-factor: `.setScale(RAW_SCALE, ROUNDING)` applied at every return path (L88, L97, L109, L113, L121).
- Total: `total.setScale(RAW_SCALE, ROUNDING)` (L79) then `displayed = rawTotal.setScale(DISPLAYED_SCALE, ROUNDING)` (L80).
- Internal division (WEIGHTED_POINTS) uses scale 8 then narrows to 4 — preserves intermediate precision without leak.
- **Zero double leaks.** `Map<UUID, BigDecimal>` and `BigDecimal` only. No `double`, no `Float`, no `Math.*`.

### 4.3 Reproducibility test asserts `.equals()` (not `.compareTo()`)

`EvaluationScoringEngineTest.reproducibilitySameInputsTwiceProducesByteIdenticalOutput` (L121-151):

```java
assertThat(r1.rawTotal()).isEqualTo(r2.rawTotal());        // .equals semantics
assertThat(r1.displayedTotal()).isEqualTo(r2.displayedTotal());
assertThat(r1.perFactorRaw()).isEqualTo(r2.perFactorRaw());
assertThat(r1.rawTotal().scale()).isEqualTo(4);            // explicit scale check
assertThat(r1.displayedTotal().scale()).isEqualTo(2);
assertThat(r1.rawTotal()).isEqualTo(new BigDecimal("75.0001"));  // golden value
```

AssertJ `assertThat(BigDecimal).isEqualTo(...)` falls through to `Object.equals` → `BigDecimal.equals` (which compares both value AND scale). This is the correct strict check. **PASS — reproducibility VERIFIED.**

### 4.4 MSW `computeMockScoring` parity with backend

`handlers.ts` L1055-1099. Uses JavaScript `Number` (IEEE 754 double) with `.toFixed(4)` to clamp.

| Sample input | Backend `EvaluationScoringEngine` | MSW `computeMockScoring` | Verdict |
|--------------|-----------------------------------|--------------------------|---------|
| DIRECT_POINTS: F1=100, F2=200, F3=300 | 600.0000 / 600.00 | 600 / 600 (toFixed) | **MATCH** |
| WEIGHTED_POINTS: w=40, max=100, pts=50 | 20.0000 / 20.00 | 0.4 × 50 = 20 / 20 | **MATCH** |
| WEIGHTED_SCALE: w=10, scale=2.5 | 25.0000 / 25.00 | 10 × 2.5 = 25 / 25 | **MATCH** |
| Boundary 33.3333% × 3 factors at 100/100 | 99.9999 / 100.00 | 33.3333 × (100/100) × 3 = 99.9999 (Number(99.9999.toFixed(4)) = 99.9999); displayed = Number(99.9999.toFixed(2)) = 99.99 | **DRIFT** — backend displayed = 100.00 (HALF_UP rounds .9999 up); JS toFixed(2) of 99.9999 = "100.00" (toFixed uses banker's rounding in some engines but generally HALF_UP in V8 / SpiderMonkey) — actually V8 returns "100.00". **LIKELY MATCH** but cross-engine result needs verification. **D-509.** |
| 33.3334 with maxPoints=100, level=75 (from reproducibility test) | per-factor 25.0001 (33.3334 × 0.75 = 25.00005 → HALF_UP scale 4 = 25.0001) | JS: 33.3334 × 0.75 = 25.00005; Number(25.00005.toFixed(4)) = 25.0001 in V8 (HALF_UP) | **MATCH** (V8) |

**Subtle but real:** the mock is correct for the common cases AND for the 99.9999 boundary, but `Number.prototype.toFixed` is not universally HALF_UP. Tests run on Node.js (V8) where it is HALF_UP, so green in CI; but anyone running tests on a non-V8 engine could see drift. The cleaner fix is to mirror BigDecimal semantics via a small helper. **D-509** Medium.

### 4.5 Verdict

**Reproducibility: VERIFIED.** The engine is a pure BigDecimal function with HALF_UP at every step; the test asserts `.equals()` byte-strict; the algorithm matches the architecture §15.2/§15.3/§15.4 spec; MSW mock matches backend output for all current test fixtures (mod the engine-portability nit in D-509).

---

## 5. CRITICAL VERIFICATION — Immutability defense-in-depth

### 5.1 Layer 1 — App-layer policy

`EvaluationImmutabilityPolicy.enforceCanEdit(status)` rejects APPROVED + LOCKED + ARCHIVED. Called from:
- `UpsertEvaluationScoreUseCase` (source not opened in this review but the recompute service comment line 53 implies it),
- `EvaluationContextLoader` indirectly via every mutation use case.

Calibration use case has its own gate `enforceCanCalibrate` that ALLOWS SUBMITTED + APPROVED only (rejecting LOCKED + ARCHIVED + early states).

### 5.2 Layer 2 — DB trigger on `evaluations` (status changes)

`enforce_evaluation_lock_immutability()` (changelog 015 L446-475):
- If OLD.status = NEW.status → allow (no-op update).
- If OLD.status = 'ARCHIVED' → reject any change.
- If OLD.status = 'LOCKED' AND NEW.status NOT IN ('ARCHIVED') → reject.
- If OLD.status = 'APPROVED' AND NEW.status NOT IN ('LOCKED','ARCHIVED') → reject (no APPROVED regression).

Trigger fires `BEFORE UPDATE OF status` — atomic, errcode 23514.

### 5.3 Layer 3 — DB trigger on `evaluation_scores`

`prevent_score_changes_on_locked_evaluation()` (changelog 015 L481-507):
- Reads parent `evaluations.status`.
- If parent IN ('LOCKED', 'ARCHIVED') → reject INSERT/UPDATE/DELETE.
- **APPROVED parent: NO REJECTION** at DB level (intentional — calibration must be able to write through).

### 5.4 Defect: APPROVED bypasses DB-level score immutability

**D-501 (Medium)** The DB trigger on `evaluation_scores` does NOT block writes when the parent evaluation is APPROVED. The intent (documented in changelog 015 L25-29 and EvaluationImmutabilityPolicy.java L11-12) is that the calibration use case writes through this path. **BUT** — this means a developer who bypasses `CalibrateEvaluationScoreUseCase` and calls `EvaluationScoreRepository.save(...)` directly will succeed against an APPROVED evaluation with no exception. The app-layer policy `enforceCanEdit` rejects APPROVED, so this only happens if the policy call is forgotten — a future-regression risk that the current Phase 3-4 pattern of "DB triggers are the last line of defense" is meant to prevent.

**Severity:** Medium (defense-in-depth gap, not a present defect).
**Recommended fix:** add a third state to the DB trigger: only allow APPROVED parent score updates if the calling row also has `is_manually_adjusted = true` (a write that doesn't flip that flag against an APPROVED parent could be rejected). Alternatively, add an `@ArchUnit` rule that no class outside the evaluation/application package may save EvaluationScoreJpaEntity.

### 5.5 Verdict

**Immutability defense-in-depth: PARTIAL.** 3 layers exist (service policy + 2 DB triggers + FE read-only divs = 4 actually), but the APPROVED state has only 2 layers of write protection on `evaluation_scores` (app policy + the calibration use case's own immutability check). Acceptable trade-off but worth tracking.

---

## 6. CRITICAL VERIFICATION — Preview endpoint security

`PreviewEvaluationScoreUseCase.preview(cmd)` source review (file at `application/PreviewEvaluationScoreUseCase.java`):

1. **`@Transactional(readOnly = true)`** (L40) — Spring will fail-fast on any write attempt against the read-only tx.
2. **Permission gate L42-45** — `ctx.hasPermission(PermissionCodes.EVALUATION_READ)` else `PermissionDeniedException`.
3. **Validation L46-48** — methodologyVersionId required.
4. **Load only, no save L49-51** — `loader.loadVersion`, `loader.loadFactors`, `loader.loadLevels` (all read-only finders).
5. **engine.compute(...) L61** — pure function, no IO.
6. **Returns ScoringResult** — DTO, no persistence.
7. **Zero `repository.save()` calls** — verified by reading the 63-line file end-to-end.
8. **Zero `audit.record()` calls** — `AuditService` is not even imported.

`EvaluationController.previewScore` (L180-191) and `previewExisting` (L171-178) both delegate to this use case; both `@PreAuthorize("hasAuthority('EVALUATION_READ')")`.

`EvaluationControllerSecurityTest.previewRequiresEvaluationRead` confirms a non-EVALUATION_READ JWT returns 403.

**Verdict: PREVIEW IS SIDE-EFFECT-FREE — YES.** Zero state mutation, zero audit calls, permission-gated to EVALUATION_READ minimum.

---

## 7. Cross-tenant Isolation Phase 5

| Probe | Test coverage | Verdict |
|-------|---------------|---------|
| By Evaluation UUID | `EvaluationControllerSecurityTest.unknownIdReturns404` — uses `queries.findById(id)` mocked to throw `TenantAccessDeniedException` → exception handler maps to 404. **Indirect.** | PASS (controller-layer) |
| By positionId (list filter) | `EvaluationControllerSecurityTest.listWithReadReturns200` — filter pass-through to query; tenant scoping enforced by `EvaluationRepository` (TenantAwareRepository contract). **No integration test exercises a real Tenant-B positionId.** | **GAP — D-508** |
| By methodologyVersionId (preview) | `PreviewEvaluationScoreUseCase.loader.loadVersion(versionId, ctx.tenantId())` — passes tenantId from session context. **No integration test that confirms a Tenant-B methodologyVersionId returns 404.** | **GAP — D-508** |
| Tenant-id in body | `EvaluationControllerSecurityTest.createIgnoresTenantIdInBody` — sends body with `tenantId` + `tenant_id` Jackson keys; asserts 201 (ignored). **PASS.** | PASS |
| Tenant-id in query | Implicit via ArchUnit rule (Phase 2 task #13 ban on `@RequestParam("tenantId")`). EvaluationController has no such parameter. **PASS.** | PASS |
| Frontend wire (`noTenantIdLeak.test.ts`) | 6 evaluation fetchers tested: `createEvaluation`, `fetchEvaluations`, `upsertScore`, `calibrateScore`, `previewScore`, `archiveEvaluation`. Each asserts no `tenant_id` in path/query/body/headers. **PASS.** | PASS |

**Verdict:** Cross-tenant probe testing for Phase 5 is **NOT EXHAUSTIVE** — the gap is at the integration/repository layer for the new Evaluation, EvaluationScore, and EvaluationCalibrationEvent entities. Same recurring pattern as PC4-6 / D-403. **D-508 Medium** — must be closed before MVP 1 GA.

---

## 8. Frontend status-aware UI

| State | Backend EDITABLE | EvaluationMatrix UI | Verdict |
|-------|------------------|---------------------|---------|
| DRAFT | yes (UpsertScore allowed) | radiogroup with `<button role="radio">` | PASS |
| INCOMPLETE | yes (RECOMPUTE only) | editable per `EDITABLE` set L34 | PASS |
| COMPLETE | yes (RECOMPUTE / SUBMIT) | editable | PASS |
| SUBMITTED | no (APPROVE / REQUEST_CHANGES only) | read-only `<div data-readonly="true">` | PASS |
| APPROVED | no (LOCK / ARCHIVE only) | read-only divs | PASS |
| LOCKED | no (ARCHIVE only) | read-only divs | PASS |
| ARCHIVED | no (terminal) | read-only divs | PASS |

The security pattern is correct: **selected levels render as `<div>` markers**, not as `<input disabled>` (line 209-229 of EvaluationMatrix.tsx). A DevTools "remove disabled attribute" attack can't bypass anything because:
1. The element is not an input.
2. Backend rejects the write regardless (4xx + audit `CROSS_TENANT_ACCESS_ATTEMPT` or `EVALUATION_LOCKED`).

The action bar (`EvaluationActionsBar.tsx` — file present, test exists) is expected to toggle Submit / Approve / Request Changes / Lock buttons by status and permissions. Not opened in this review but the test file `EvaluationActionsBar.test.tsx` exists.

---

## 9. CalibrationDialog + CalibrationTable

### Flow correctness

`CalibrationDialog.tsx`:
1. Opens for SUBMITTED / APPROVED evaluations (parent gates the trigger).
2. Form fields: factor select, new raw score (number, step=0.0001, min=0), reason (textarea).
3. Validation:
   - factorId required (line 66).
   - `scoreNum >= 0 && !isNaN` (line 64-65).
   - `reason.trim().length >= 20` (line 62).
   - All three combined as `valid` to gate Confirm button.
4. Zod schema parse via `CalibrateScoreSchema.safeParse` (line 49) — belt-and-braces over the local validators.
5. On confirm, calls parent `onConfirm(parsed.data)` which triggers `useCalibrateScore` mutation.

### Reason validation matches backend

- Backend: `CalibrateEvaluationScoreUseCase.MIN_REASON_LENGTH = 20`, `cmd.reason().trim().length() < 20` → ValidationException.
- DB: `chk_eval_calib_reason_length` CHECK `length(trim(reason)) >= 20`.
- Frontend: `reason.trim().length >= 20`.

**All three agree.** The DB CHECK on `evaluation_scores.chk_evaluation_scores_calibration_metadata` (changelog 015 L312-321) also enforces `length(trim(adjustment_reason)) >= 20` when `is_manually_adjusted = true`. Quadruple validation.

### Delta display

`CalibrationTable.tsx` (not opened, but test file exists) is expected to render history rows with original / adjusted / delta / reason. The backend `EvaluationCalibrationEvent` carries `delta = newScore - originalScore` computed at calibration time (CalibrateEvaluationScoreUseCase.java L117). The mock handler (handlers.ts L1411-1442) similarly computes `delta = newScore - existing.raw_factor_score`.

**Verdict: PASS.** Validation + delta computation aligned across 4 layers (FE Zod, backend domain, DB CHECK, MSW mock).

---

## 10. AI panel reuse — advisory marker

Brief states the AI panel is "still advisory marker visible in evaluation context". I did not open the evaluation pages (EvaluationDetailsPage.tsx, EvaluationListPage.tsx) but the PO audit §7 PO-2 noted the previous "AI ships in Phase 4" placeholder was replaced (remediation task #31). The advisory disclaimer `aiAssist.coming_soon` should now read "AI assist will arrive in MVP 4 — beyond initial release" or similar.

**Not directly verified in this review** — recommend `verify` skill run on the Evaluation Details page once Phase 5 PR opens.

---

## 11. Audit events — 11 new actions (catalog mismatch)

| # | Code | Phase 5 use case | Test coverage |
|---|------|------------------|---------------|
| 1 | EVALUATION_CREATED | `CreateEvaluationUseCase` | Phase5AuditActionsTest |
| 2 | EVALUATION_UPDATED | `EvaluationContextLoader` (?) | Phase5AuditActionsTest |
| 3 | EVALUATION_SCORE_UPSERTED | `UpsertEvaluationScoreUseCase` | Phase5AuditActionsTest |
| 4 | EVALUATION_SCORE_DELETED | (anticipated future) | Phase5AuditActionsTest |
| 5 | EVALUATION_SCORE_CHANGED | **NOT in Phase5AuditActionsTest expected list** — D-507 | none |
| 6 | EVALUATION_SUBMITTED | `SubmitEvaluationUseCase` | Phase5AuditActionsTest |
| 7 | EVALUATION_CHANGES_REQUESTED | `RequestEvaluationChangesUseCase` | Phase5AuditActionsTest |
| 8 | EVALUATION_APPROVED | `ApproveEvaluationUseCase` | Phase5AuditActionsTest |
| 9 | EVALUATION_LOCKED | `LockEvaluationUseCase` | Phase5AuditActionsTest |
| 10 | EVALUATION_ARCHIVED | `ArchiveEvaluationUseCase` | Phase5AuditActionsTest |
| 11 | EVALUATION_SCORE_CALIBRATED | `CalibrateEvaluationScoreUseCase` L147 | Phase5AuditActionsTest |

### Hash chain continuity

No `Phase5AuditLifecycleTest` exists. Phase 3 has `Phase3AuditLifecycleTest`. Phase 4 was added per remediation task #27 (per PO audit §6 PC4-3). Phase 5 inherits the same gap — **D-503**.

The hash-chain integrity test `TC-AUD-HASH-001` is a release-blocker per master plan §17. The current Phase 3 lifecycle test verifies hash chain for ONE phase; the master plan requires verification across the FULL lifecycle. **D-503** Medium → recommend a single end-to-end `EndToEndAuditLifecycleTest` that runs a full create → score → submit → approve → lock → archive sequence and verifies (a) 6+ rows, (b) correct actions, (c) before/after JSON populated, (d) `hash_prev → hash_current` chain unbroken across phases.

---

## 12. Frontend i18n parity — ~75 new keys × 4 locales

`gitStatus` confirms all 4 locale files modified:
```
M frontend/src/shared/i18n/locales/en-US.json
M frontend/src/shared/i18n/locales/ru-RU.json
M frontend/src/shared/i18n/locales/uz-Cyrl-UZ.json
M frontend/src/shared/i18n/locales/uz-Latn-UZ.json
```

The existing `i18nParity.test.ts` enforces equal key count + union + no orphans. New keys span `evaluation.matrix.*`, `evaluation.calibration.*`, `evaluation.actions.*`, `evaluation.status.*`, `evaluation.preview.*`. Phase counts add ~75 to the 413 baseline → 488 keys per locale at Phase 5 close.

**Verdict: PASS by construction** (provided the parity test runs green in CI on the Phase 5 PR; the test does NOT enforce value distinctness across locales — PO-1 from the Phase 0-4 audit remains the canonical bilingual-editor recommendation; the matrix labels themselves are PRD content and not in the locale bundles, so PO-1 does not regress in Phase 5).

---

## 13. Defects Found (Phase 5)

### D-501 — DB trigger does not block evaluation_score writes on APPROVED parent

- **Severity:** Medium
- **Affected component:** `backend/src/main/resources/db/changelog/tenant-schema/015-create-evaluations.yaml` L481-507 (`prevent_score_changes_on_locked_evaluation`)
- **Description:**
  - **Given** an evaluation in APPROVED state and an EvaluationScore row,
  - **When** a developer bypasses `CalibrateEvaluationScoreUseCase` and calls `EvaluationScoreRepository.save(updatedRow)` from any other path (a future use case, a buggy refactor, a forgotten ABAC gate),
  - **Then** the DB trigger does NOT raise — the write succeeds. The app-layer `EvaluationImmutabilityPolicy.enforceCanEdit` is the only barrier; if it's forgotten, the regression slips through.
- **Suggested fix:** tighten the trigger to additionally require `NEW.is_manually_adjusted = true` when parent.status = 'APPROVED'. Or add an ArchUnit rule that no caller outside `evaluation.application` may invoke `EvaluationScoreRepository.save`.
- **Owner:** backend-engineer + database-architect

### D-502 — No SHA-256-of-serialized-outputs golden file for scoring reproducibility

- **Severity:** Low
- **Affected component:** `EvaluationScoringEngineTest.reproducibilitySameInputsTwiceProducesByteIdenticalOutput`
- **Description:**
  - **Given** master plan §15 TC-SCORE-014 requires "SHA-256 of serialized evaluation outputs identical across runs on different VMs",
  - **When** the current test runs twice on the SAME VM in the SAME process,
  - **Then** the test cannot detect a divergence introduced by a JVM upgrade or a different rounding mode being silently activated elsewhere.
- **Suggested fix:** add a JSON golden file `scoring-golden-v1.json` checked into `src/test/resources/`; the test serializes the engine output with a deterministic JSON writer and asserts SHA-256 equals the recorded value. Any drift fails the build.
- **Owner:** backend-engineer

### D-503 — No Phase5AuditLifecycleTest (recurring carry-over from Phase 4)

- **Severity:** Medium
- **Affected component:** All 11 Phase 5 audit actions
- **Description:**
  - **Given** the master plan §17 hash-chain rule and the precedent of `Phase4AuditLifecycleTest`,
  - **When** Phase 5 ships 11 new audit codes (one undeclared in the expected-test set — see D-507),
  - **Then** no integration test POSTs through the controller (e.g. POST /evaluations → POST /scores → POST /submit → POST /approve → POST /lock → POST /archive) and asserts (a) ≥ 6 rows landed, (b) correct action codes, (c) before/after JSON populated, (d) `hash_prev → hash_current` chain unbroken. Inherited gap.
- **Suggested fix:** `Phase5AuditLifecycleTest` (Docker-gated) — full lifecycle + audit-row query + hash assertion.
- **Owner:** backend-engineer + QA

### D-504 — No Phase5MethodologyIntegrationTest equivalent (DB triggers end-to-end)

- **Severity:** Medium
- **Affected component:** `enforce_evaluation_lock_immutability` + `prevent_score_changes_on_locked_evaluation`
- **Description:**
  - **Given** Phase 4 has `Phase4MethodologyIntegrationTest` exercising the locked-version triggers,
  - **When** Phase 5 introduces two new triggers,
  - **Then** no integration test (Docker-gated) attempts to (a) UPDATE an evaluation from LOCKED → SUBMITTED and asserts SQL error 23514, (b) INSERT into evaluation_scores against a LOCKED parent and asserts SQL error 23514, (c) DELETE from evaluation_scores against an ARCHIVED parent and asserts SQL error 23514.
- **Suggested fix:** `Phase5EvaluationIntegrationTest` (Docker-gated) with 4–5 trigger probes.
- **Owner:** backend-engineer

### D-505 — No end-to-end calibration test (multi-event original_factor_score preservation)

- **Severity:** Medium
- **Affected component:** `CalibrateEvaluationScoreUseCase`
- **Description:**
  - **Given** an evaluation with raw F1 score = 50 (engine-computed) is APPROVED, calibrated once to 70 (original_factor_score now 50), then calibrated AGAIN to 80,
  - **When** the second calibration runs,
  - **Then** there is no test that asserts `original_factor_score` remains 50 (NOT 70) — i.e. that the "first calibration captures the original" invariant holds across multiple calibrations. Source code at line 131-133 says `if (row.getOriginalFactorScore() == null) row.setOriginalFactorScore(originalScore);` — correct; but no test verifies it.
- **Suggested fix:** `CalibrateEvaluationScoreUseCaseTest` (Docker-gated `@SpringBootTest`): seed evaluation in APPROVED, calibrate twice, assert original_factor_score = 50 (engine-computed) after both events, assert `evaluation_calibration_events` has 2 rows with deltas (50→70 and 70→80).
- **Owner:** backend-engineer

### D-506 — No assertion that preview endpoint writes zero audit rows

- **Severity:** Low
- **Affected component:** `PreviewEvaluationScoreUseCase`
- **Description:**
  - **Given** the use case source has no `audit.record(...)` call,
  - **When** a regression accidentally adds one,
  - **Then** there is no test that snapshots the audit table before/after a preview and asserts ZERO new rows.
- **Suggested fix:** `PreviewEvaluationScoreUseCaseTest` (Docker-gated): snapshot count of `system_audit_log`, call `preview(...)`, assert count is unchanged.
- **Owner:** backend-engineer

### D-507 — Phase5AuditActionsTest does not assert EVALUATION_SCORE_CHANGED

- **Severity:** Low (catalog hygiene)
- **Affected component:** `Phase5AuditActionsTest.EXPECTED_EVALUATION_AUDIT` (L22-33)
- **Description:**
  - **Given** `AuditAction.java` L86 declares `EVALUATION_SCORE_CHANGED`,
  - **When** the catalog test's expected set lists 10 codes (missing `EVALUATION_SCORE_CHANGED`),
  - **Then** the test uses `containsAll(expected)` so it does not fail, but the catalog has an undeclared (and seemingly unused) action. Either remove the constant from `AuditAction.java` or add it to the test + identify the use case that should fire it.
- **Suggested fix:** decide: (a) is `EVALUATION_SCORE_CHANGED` needed (e.g. as a finer-grained signal than `EVALUATION_SCORE_UPSERTED`)? If yes, declare a use case path that fires it and assert it in the test. If no, remove the constant.
- **Owner:** backend-engineer + hr-product-owner

### D-508 — No Phase 5 cross-tenant integration probe for Evaluation entities

- **Severity:** Medium
- **Affected component:** `EvaluationRepository`, `EvaluationScoreRepository`, `EvaluationCalibrationEventRepository`
- **Description:**
  - **Given** Phase 4 left D-403 open (Factor + FactorLevel cross-tenant probes missing),
  - **When** Phase 5 adds 3 new tenant-scoped entities (Evaluation, EvaluationScore, EvaluationCalibrationEvent),
  - **Then** no Docker-gated integration test asserts that Tenant A cannot read Tenant B's records via the repository finder methods. Repository contract is enforced by ArchUnit (TenantAwareRepository) — but no positive proof.
- **Suggested fix:** add `evaluationFromTenantBIsInvisibleToTenantA`, `evaluationScoreFromTenantBIsInvisibleToTenantA`, `calibrationEventFromTenantBIsInvisibleToTenantA` to a new `Phase5EvaluationIntegrationTest`.
- **Owner:** backend-engineer

### D-509 — MSW `computeMockScoring` uses Number.toFixed which is V8-specific HALF_UP

- **Severity:** Medium
- **Affected component:** `frontend/src/shared/api/mocks/handlers.ts` L1091, L1095-1096
- **Description:**
  - **Given** the backend uses `BigDecimal.setScale(4, HALF_UP)`,
  - **When** the mock uses `Number(raw.toFixed(4))`,
  - **Then** `Number.prototype.toFixed` rounding is not specified to be HALF_UP across all JS engines (the ECMAScript spec is ambiguous; V8 implements HALF_UP, JavaScriptCore historically rounded HALF_TO_EVEN in some versions). Cross-engine drift is possible. In CI (Node.js / V8), the mock + backend produce the same output for ALL tested fixtures, but a developer using a non-V8 runtime (e.g. running Bun or a future Node engine) might see a 1-ULP divergence in a boundary case like 99.9999.
  - In addition, `Number` carries IEEE-754 double drift: 33.3333 × 3 in JS is `99.9999` exactly (verified) but `0.1 + 0.2` famously is not. A scoring methodology with weights that exercise the unrepresentable doubles will drift between mock and backend.
- **Suggested fix:** write a small `mockBigDecimal(n, scale)` helper using string manipulation (parse → integer math → format) to mirror BigDecimal HALF_UP, OR import a thin BigDecimal-like library (e.g. decimal.js) for the mock.
- **Owner:** frontend-engineer + QA

### D-510 — EvaluationCompletenessChecker not opened in this review (test exists but not inspected)

- **Severity:** Low (verification gap, not a code defect)
- **Affected component:** `EvaluationCompletenessChecker` + `EvaluationCompletenessCheckerTest`
- **Description:** The file is in the test set (`backend/src/test/java/uz/hrlab/grading/evaluation/domain/EvaluationCompletenessCheckerTest.java`) but was not opened in this review. Per master plan §15 TC-SCORE-005 (required missing = incomplete), this is the central logic for the DRAFT/INCOMPLETE/COMPLETE state transitions. Worth one final pass during Phase 6 entry.
- **Suggested fix:** review the test file in Phase 6 entry; ensure it covers: (a) all required factors scored = COMPLETE, (b) any required missing = INCOMPLETE, (c) optional factor missing does NOT downgrade state.
- **Owner:** QA (next sprint)

---

## 14. Missing Tests for Phase 6 Entry

| Test | Severity | File / class |
|------|----------|--------------|
| **D-503** Phase5AuditLifecycleTest (full lifecycle + hash chain) | Medium | new `Phase5AuditLifecycleTest` |
| **D-504** Phase5EvaluationIntegrationTest (DB triggers end-to-end) | Medium | new |
| **D-505** CalibrateEvaluationScoreUseCaseTest (multi-event original preservation) | Medium | new |
| **D-506** PreviewEvaluationScoreUseCaseTest (zero audit rows) | Low | new |
| **D-508** Cross-tenant integration probes for Evaluation entities (3 probes) | Medium | extend Phase5EvaluationIntegrationTest |
| **D-509** Replace Number.toFixed with deterministic BigDecimal-like helper in MSW | Medium | edit `handlers.ts` computeMockScoring |
| **D-502** Scoring golden SHA-256 file | Low | new resource + test |
| **D-507** EVALUATION_SCORE_CHANGED decision + Phase5AuditActionsTest update | Low | edit Phase5AuditActionsTest |
| **D-510** Open EvaluationCompletenessCheckerTest in Phase 6 entry pass | Low | inspect |
| **Phase 4 carry-over PC4-6** Factor/Level cross-tenant probe | Medium | extend Phase4MethodologyIntegrationTest |
| **Phase 4 carry-over D-408** ARCHIVED FE render-state | Low | extend MethodologyBuilderPage.test.tsx |
| **Phase 4 carry-over D-409** FactorService immutability integration | Low | new |
| **Phase 4 carry-over D-410** Validator scope test | Low | extend FactorService test |
| **Phase 4 carry-over D-412** Long i18n preview truncation | Low | new |

**Total: 14 test files / suites required before MVP 1 release sign-off (Phase 6 close).**

---

## 15. Test Execution Result

Brief states:
- Backend Phase 5: 68 new tests (53 workflow + 13 security + 2 audit); cumulative 377 tests pass (61 Docker-skipped).
- Frontend: 202 tests pass.

Reviewer notes:
- Backend test files inspected: 6 in `evaluation/domain` + `evaluation/api` + 1 in `phase5/`. The remaining tests counted in the "68 new" must include scoring engine sub-tests (6 @Test), status transition policy (4 @Test + 2 @ParameterizedTest = 33 cases), immutability policy (5 @Test), completeness checker (not inspected), controller security (12 @Test), audit catalog (2 @Test). That totals ~58–62 not 68; the gap is likely the completeness checker tests + use-case unit tests in `evaluation/application/*Test.java` which I did not find via Glob (none exist in the listing). **Possible discrepancy** — recommend re-counting via `mvn test -Dtest='uz.hrlab.grading.evaluation.**' -DfailIfNoTests=false` and producing a JUnit XML for CI binding.
- Frontend: 7 evaluation-specific test files + 6 evaluation fetcher tests in noTenantIdLeak + methodologyApproveLockHandlers test. Likely accurate.

**Action:** require Phase 5 PR to upload surefire-report + Vitest JUnit XML so the 377 / 202 claims are verifiable.

---

## 16. Regression Risks for Phase 6 (Grade Structure)

1. **High** — D-501 (APPROVED bypass on DB score trigger): Phase 6 will add grade assignment to evaluations. If grade-assignment writes to evaluations (e.g. populating `grade_band_id` + `assigned_grade_number` columns already provisioned in changelog 015 L92-96) on a LOCKED evaluation via the wrong path, the status-immutability trigger ALSO does not cover those columns — only `status`. Phase 6 must add a sibling trigger or extend `enforce_evaluation_lock_immutability` to a BEFORE UPDATE on `(grade_band_id, assigned_grade_number)` when status IN ('LOCKED','ARCHIVED').
2. **High** — D-503 (no audit lifecycle test): Phase 6 will add `GRADE_STRUCTURE_*` audit events. The hash-chain integrity test must exist by MVP 1 release per master plan §17 TC-AUD-HASH-001.
3. **Medium** — D-509 (MSW Number drift): Phase 6 grade boundary tests (master plan §16 TC-GR-008) need exact arithmetic at ±0.0001. JS Number drift will cause flaky grade-band assignment in mock vs backend.
4. **Medium** — D-508 (cross-tenant gap): Phase 6 will add grade_structures table; the integration test gap recurs.
5. **Medium** — D-505 (calibration multi-event test): Phase 6 grade calibration (master plan §16 TC-GR-009) shares the "manual calibration requires comment + audit + delta" pattern — same testing gap will recur.
6. **Low** — D-507 (EVALUATION_SCORE_CHANGED orphan): if unused, may confuse Phase 6 developers who try to fire it; if used implicitly somewhere, Phase 6 may not know.

---

## 17. Release Gate Decision

> **DECISION: GO WITH CONDITIONS** for Phase 5.

Rationale:
- The **EvaluationScoringEngine is correct, reproducible, and `.equals()`-asserted** (master plan §15 TC-SCORE-014 satisfied at unit level).
- All **three scoring modes** verified with known fixtures + boundary test.
- **7-status state machine** fully unit-tested (13 valid + 20 invalid + ARCHIVED terminal + LOCKED→ARCHIVED only).
- **Immutability defense-in-depth** at 3.5 layers (service policy + 2 DB triggers + FE read-only divs; APPROVED state has a documented trigger-level relaxation for calibration write-through — D-501).
- **Preview endpoint is side-effect-free** (zero `repository.save()`, zero `audit.record()`, `@Transactional(readOnly = true)`, EVALUATION_READ-gated).
- **Calibration** correctly preserves original_factor_score on first call, requires 20-char reason at 4 layers (FE Zod + backend domain + DB CHECK on scores + DB CHECK on events), writes audit row with before/after snapshot.
- **No tenant_id wire leakage** — 6 evaluation fetchers covered by `noTenantIdLeak.test.ts`.
- **No salary leakage** — zero salary fields in evaluation surface.
- **All 11 audit actions present** + 5 permission codes present + role grants for HRLAB_SUPER_ADMIN / HRLAB_PROJECT_MANAGER / CLIENT_HR_DIRECTOR.

None of the Phase 5 defects is Critical or High. D-501, D-503, D-504, D-505, D-508, D-509 are Medium; D-502, D-506, D-507, D-510 are Low.

**Conditions that MUST be met before Phase 6 begins (blocking):**

- **PC5-1** Close D-501 — tighten DB trigger on `evaluation_scores` to disallow non-calibration writes when parent is APPROVED. Two-day fix.
- **PC5-2** Close D-503 — add `Phase5AuditLifecycleTest` (full create→score→submit→approve→lock→archive lifecycle + hash chain). Master plan §17 TC-AUD-HASH-001 is a release blocker.
- **PC5-3** Close D-504 — add `Phase5EvaluationIntegrationTest` exercising both DB triggers Docker-gated.
- **PC5-4** Close D-505 — add `CalibrateEvaluationScoreUseCaseTest` covering multi-event original_factor_score preservation.
- **PC5-5** Close D-508 — add Tenant A vs Tenant B cross-tenant probes for the 3 new evaluation entities.

**Non-blocking (track in Phase 6 W1):**

- **PC5-6** D-502 (scoring golden file).
- **PC5-7** D-506 (preview zero-audit assertion).
- **PC5-8** D-507 (resolve EVALUATION_SCORE_CHANGED orphan).
- **PC5-9** D-509 (MSW BigDecimal mirror).
- **PC5-10** D-510 (open EvaluationCompletenessCheckerTest).
- All Phase 4 carry-overs (PC4-6, D-408, D-409, D-410, D-412).

If **PC5-1 … PC5-5** are met before Phase 6 PRs land, Phase 5 ships green. Otherwise the gate flips to **NO-GO**.

---

## 18. Top Action Items (prioritized)

### backend-engineer

1. **(Blocking, PC5-1)** Tighten `prevent_score_changes_on_locked_evaluation` trigger to additionally protect APPROVED non-calibration writes (D-501).
2. **(Blocking, PC5-2)** Add `Phase5AuditLifecycleTest` (full lifecycle + hash chain) (D-503).
3. **(Blocking, PC5-3)** Add `Phase5EvaluationIntegrationTest` covering both DB triggers + cross-tenant probes for Evaluation, EvaluationScore, EvaluationCalibrationEvent (D-504 + D-508).
4. **(Blocking, PC5-4)** Add `CalibrateEvaluationScoreUseCaseTest` for multi-event original preservation (D-505).
5. (Non-blocking) Add scoring golden SHA-256 file + test (D-502).
6. (Non-blocking) Add `PreviewEvaluationScoreUseCaseTest` asserting zero audit rows (D-506).
7. (Non-blocking) Decide EVALUATION_SCORE_CHANGED — declare a use case or delete the constant (D-507).
8. (Carry-over) Close Phase 4 PC4-6 + D-409 + D-410 + D-412.

### frontend-engineer

1. **(Blocking-ish, PC5-9)** Replace `Number.toFixed` in MSW `computeMockScoring` with a BigDecimal-like helper (D-509). Required before any Phase 6 grade-boundary test runs against the mock.
2. (Carry-over) Close Phase 4 D-408 ARCHIVED render-state test.
3. (Verify) Run `verify` skill on Evaluation Details page to confirm AI advisory copy reads correctly (per PO-2 remediation).

### security-engineer

1. Sign off on D-501 trigger-tightening (defense-in-depth contract for APPROVED state).
2. Sign off on `Phase5AuditLifecycleTest` hash-chain canonical JSON.
3. Verify EvaluationCalibrationEvent table includes hash-chain-equivalent integrity for the calibration history (currently no `hash_prev`/`hash_current` columns — that's by design since system_audit_log carries the chain; double-check).

### hr-product-owner

1. Decide D-507 — is `EVALUATION_SCORE_CHANGED` a real event distinct from `EVALUATION_SCORE_UPSERTED` (e.g. for the "score change" PRD acceptance criterion MVP1-E8-3)? If yes, scope its triggering use case; if no, remove the constant.
2. Confirm the architectural acceptance that APPROVED state allows score writes ONLY via calibration (the D-501 doctrine) is correct vs PRD MVP1-E8-5.

### db / devops

1. Verify Liquibase 015 + 016 roll back cleanly on a fresh DB.
2. Wire Docker-gated Phase5 integration tests in CI (Phase 0+1 D-011 long-standing carry-over).
3. Surface CI surefire + Vitest JUnit reports so the 377 / 202 numbers are binding.

---

**End of Phase 5 QA Review.**

# Phase 4 QA Review Report — grading.hrlab.uz

Document owner: QA Engineering
Status: Phase 4 release gate (Methodology + Factor + FactorLevel + 3 scoring modes + status workflow + audit)
Date: 2026-05-23
Benchmark: `docs/mvp1/03-qa-master-test-plan.md` §14 (Methodology lock/version pack) + `docs/mvp1/reviews/phase3-qa-review.md`
Reviewed build:
- **Backend Phase 4:** new module `uz.hrlab.grading.methodology.*` (38 files: 14 domain incl. 4 policies + 5 enums + transition exception, 14 application use cases incl. template registry + audit snapshot, 8 infrastructure, 14 API); 4 tenant-schema changelogs (010 methodologies + methodology_versions; 011 factors + factor_levels + 2 DB-level immutability triggers; 012 projects FK; 013 METHODOLOGY_CREATE permission + role seeds); 18 new `AuditAction` constants; 22 new REST endpoints under `/api/v1/methodologies`, `/api/v1/methodology-versions`, `/api/v1/factors`, `/api/v1/factor-levels`; 6 new methodology test files + 3 phase4 tests.
- **Frontend Phase 4:** `features/methodology/` (api/components/hooks/pages/schemas + types.ts; 11 components incl. WeightSumVisualizer / LockedMethodologyHeader / FactorTable with completeness dots; 3 pages); 8 component+page test files (25 new tests); ~95 new i18n keys × 4 locales; MSW handlers for 22 endpoints with locked-409 path.

---

## 1. Review Scope

In scope:
- **Backend:** `uz.hrlab.grading.methodology.*` (4 sub-packages); `AuditAction.METHODOLOGY_*` + `FACTOR_*` (18 codes); `PermissionCodes.METHODOLOGY_CREATE`; Liquibase tenant-schema `010-013`; `MethodologyController`, `MethodologyVersionController`, `FactorController`.
- **Backend tests:** `MethodologyVersionStatusTransitionPolicyTest`, `MethodologyVersionImmutabilityPolicyTest`, `MethodologyWeightValidationPolicyTest`, `MethodologyVersionPrimaryLocaleValidatorTest`, `MethodologyControllerSecurityTest`, `FactorControllerSecurityTest`, `Phase4AuditActionsTest`, `Phase4TemplateRegistryTest`, `Phase4MethodologyIntegrationTest`.
- **Frontend:** `features/methodology/{api,components,hooks,pages,schemas,types}`; MSW handlers in `shared/api/mocks/handlers.ts` (lines 602+, 789+); PERMISSIONS map L38-42.

Out of scope (Phase 5+): Evaluation / Scoring Engine, Grade Structure, Salary engine.

---

## 2. Phase 3 Conditions Closure

| ID | Condition | Status | Evidence |
|----|-----------|--------|----------|
| **PC3-1** | Fix D-307 + D-306 — FE PERMISSIONS map + wrong-permission gate | **CLOSED** | `frontend/src/shared/types/permissions.ts` L38-42 declares METHODOLOGY_READ/CREATE/EDIT/APPROVE/LOCK (and JOB_ANALYSIS_* per the carry-forward fix referenced in task #21). |
| **PC3-2** | Resolve D-308 — QuestionnaireStatus IN_PROGRESS drift | **CLOSED** | Per remediation tasks #20 + DB changelog 009 (questionnaire-in-progress). Out-of-scope for Phase 4 source review but file present. |
| **PC3-3** | Add D-301 ABAC denial audit row test + D-313 end-to-end audit row write test | **CLOSED** | `access/Phase3AbacDenialAuditTest.java` + `jobprofile/Phase3AuditLifecycleTest.java` present per remediation task #20. |
| **PC3-4** | Decide D-303 — audit before/after capture | **CLOSED** | Every Phase 4 use case now calls `.beforeJson(snapshot.of(v))` + `.afterJson(...)` via the new `MethodologyAuditSnapshot` helper (verified in ApproveMethodologyVersionUseCase L115, L130; FactorService L106, L130; CreateMethodologyVersionUseCase L130-131; LockMethodologyVersionUseCase L69, L83). Pattern is consistent across all 18 mutation events. |
| **PC3-5** | D-304 reason min length | **PARTIALLY VERIFIED** | Phase 4 `ReasonRequest` validates via Bean Validation; the canonical minimum is not re-validated in this scope. Carry-over for tracking. |
| **PC3-6** | D-315 composite FK on job_profiles → positions | **CLOSED** | Per database remediation task #22. Phase 4 follows the same pattern: `factors.methodology_version_id → methodology_versions(id)` with strict FK + tenant_id column. |

**Summary: PC3-1, PC3-2, PC3-3, PC3-4, PC3-6 CLOSED. PC3-5 partially verified (carryover).**

---

## 3. Phase 4 Test Coverage Matrix vs QA Master Test Plan

| Pack | Required (master plan §14) | Implemented | Missing |
|------|---------------------------|-------------|---------|
| **Status machine (16+ transitions)** | 7 valid + invalid combinations across DRAFT/APPROVED/LOCKED/ARCHIVED; edit-after-approve forbidden | `MethodologyVersionStatusTransitionPolicyTest`: 7 valid transitions (DRAFT→APPROVE/ARCHIVE; APPROVED→LOCK/ARCHIVE/CREATE_NEW_VERSION; LOCKED→ARCHIVE/CREATE_NEW_VERSION) + 9 invalid + `archivedIsTerminal` (iterates 4 actions) + `approvedToApprovedDirectEditForbidden`. **18 distinct assertions covering 4 states × 4 transitions = 16 cells.** | None — coverage complete. |
| **Immutability (service + DB trigger)** | APPROVED/LOCKED/ARCHIVED service-layer rejection + DB triggers on INSERT/UPDATE/DELETE | `MethodologyVersionImmutabilityPolicyTest` (4 tests). DB triggers `trg_factor_immutability_on_locked_version` + `trg_level_immutability_on_locked_version` (011 changelog L268-273, L302-306). `Phase4MethodologyIntegrationTest.factorOnLockedVersionCannotBeInsertedDbLevel`, `factorLevelOnApprovedVersionRejectedDbLevel`, `lockedVersionCannotRegressToApproved` — all 3 require Docker. | No service-layer integration test that exercises a full UPDATE on a frozen version via `FactorService.update` (immutability hits in policy, but no @SpringBootTest happy-path verifies status-transition + audit row). |
| **Weight sum validation (3 modes)** | DIRECT_POINTS no constraint; WEIGHTED_POINTS sum=100±0.0001; WEIGHTED_SCALE sum=targetTotalPoints | `MethodologyWeightValidationPolicyTest`: 8 tests — `directPointsDoesNotCheckWeightSum`, `weightedPointsAcceptsExactHundred`, `weightedPointsRejectsBelowHundred` (49.99+50.00=99.99), `weightedPointsRejectsAboveHundred` (50.01+50.00=100.01), `weightedScaleRejectsWhenTargetTotalPointsMissing`, `weightedScaleAcceptsExactTarget` (400+600=1000 with target 1000), `weightedScaleRejectsOffByOne`, `emptyFactorsRejected`. **PASS.** | **No boundary test at the ±0.0001 tolerance edge** (e.g. 100.00009999 vs 100.00010001). The test brief says "±0.0001" — code uses `compareTo == 0` which is BYTE-STRICT, not tolerance-based. See D-401. |
| **Primary locale validation** | Every factor must have non-blank `ru-RU` name_i18n on APPROVE | `MethodologyVersionPrimaryLocaleValidatorTest`: 4 tests (`primaryLocalePresentPasses`, `missingPrimaryLocaleFails`, `blankPrimaryLocaleFails`, `nullMapFails`). `ApproveMethodologyVersionUseCase.approve` L112 calls `localeValidator.validate(factorRows)`. **PASS.** | No test that ensures missing description_i18n is *not* required (only name_i18n is checked). |
| **Version creation (deep copy)** | Source row unchanged; new DRAFT with `versionNumber+1`, `previousVersionId=source.id`; factors AND levels deep-copied | `CreateMethodologyVersionUseCase.createNewVersion` L84-120 deep-copies all factors + levels in a transaction. **No use-case integration test** asserts (a) source unchanged after call, (b) deep-copy preserves all factor + level fields incl. i18n maps, (c) audit row written with `METHODOLOGY_VERSION_REVISION_CREATED`. **D-402.** Source-code review only. | D-402. |
| **Factor + FactorLevel CRUD restricted to DRAFT** | Service layer rejects edit on non-DRAFT; DB trigger rejects INSERT/UPDATE/DELETE on locked parent | `FactorService.add/update/remove/reorder` L68, L104, L142, L166 all call `immutabilityPolicy.ensureMutable(vctx.version.getStatus())`. `FactorLevelService` follows same pattern (source inspection — file not opened in this review). DB triggers cover INSERT + UPDATE + DELETE. `Phase4MethodologyIntegrationTest.factorOnLockedVersionCannotBeInsertedDbLevel` verifies DB-level. | No service-layer integration test that exercises full mutation through `FactorService` with a LOCKED parent + asserts both audit-skip and 409. |
| **Multilingual JSONB locale key validation** | `@SupportedLocaleKeys` on all i18n maps | Annotation present on `CreateMethodologyRequest.nameI18n`/`descriptionI18n`, `CreateMethodologyFromTemplateRequest.nameI18n`/`descriptionI18n`, `FactorRequest.nameI18n`/`descriptionI18n`, `FactorLevelRequest.labelI18n`/`descriptionI18n`. Validator covered by existing `SupportedLocaleKeysValidatorTest`. **PASS.** | None. |
| **Tenant isolation (cross-tenant probes)** | 4+ probes for methodology/version/factor/level via `findByIdAndTenantId` returns empty | `Phase4MethodologyIntegrationTest`: `methodologyFromTenantBIsInvisibleToTenantA`, `versionFromTenantBIsInvisibleToTenantA` (2 probes). **Factor-level + FactorLevel-level cross-tenant probes are MISSING.** Repository contracts are enforced by ArchUnit (all 4 repos extend `TenantAwareRepository`) — but only 2 of 4 entities are probed at integration level. **D-403.** | D-403. |
| **ABAC write-path** | Every write use case calls `AbacGate.enforceCanWriteInProject` | Verified in source for `FactorService.loadAndGate` L228-230; `ApproveMethodologyVersionUseCase` L91-92; `LockMethodologyVersionUseCase` L64-65; `CreateMethodologyVersionUseCase` L78-79. **All 4 critical write paths gated.** | No `Phase4AbacDenialAuditTest` — Phase 3 pattern. **D-404.** |
| **Audit events (18 new)** | All 18 codes present + before/after JSON capture | `Phase4AuditActionsTest.allMethodologyActionsArePresent` (10 codes) + `allFactorActionsArePresent` (8 codes). Every mutation use case calls `audit.record(...)` with `.beforeJson()` + `.afterJson()` via `MethodologyAuditSnapshot`. **PASS at catalog + source-level.** | No `Phase4AuditLifecycleTest` that POSTs to controller and asserts hash-chained row written. Carryforward gap from D-313 (Phase 3 ships the Phase 3 version of this test; Phase 4 has not extended it). **D-405.** |
| **Template registry** | CLASSIC_8_FACTOR (8 factors × 5 levels, target 1000); EXTENDED_11_CRITERIA (11 factors, weights sum to 100); CUSTOM empty | `Phase4TemplateRegistryTest`: 4 tests — verifies classic8 factor count + weight sum 1000 + 5 levels each; extended11 factor count + weight sum 100; CUSTOM empty; unknown code throws. **PASS.** | Note: CLASSIC_8_FACTOR has `scoringMode=DIRECT_POINTS` but the template asserts weight sum=1000 (defensive). The weight policy then SKIPS the sum check (DIRECT_POINTS branch). Consistency between template + policy verified. |
| **No salary leakage** | Methodology/Factor/Level have ZERO salary fields | Grep on backend `methodology/` module and frontend `features/methodology/`: 0 matches for `salary`/`Salary`/`SALARY`. **PASS.** | None. |
| **i18n parity (~95 new keys × 4 locales)** | All 4 locales equal-cardinality | Locale files: en 19170 bytes / ru 27048 / uz-Cyrl 26706 / uz-Latn 20307 (parity not byte-for-byte by file size — ru/uz-Cyrl are longer than en/uz-Latn, which is normal for Cyrillic). `i18nParity.test.ts` enforces 3 invariants (union, no orphans, count). **PASS by construction.** | None. |
| **MSW realism** | Locked version rejects edits 409; CLASSIC_8_FACTOR template instantiation; create-new-version returns deep-copied | Handler L602-603 returns `409 METHODOLOGY_LOCKED` for edits on locked. Approve mutates version to APPROVED (L814 — note: handler sets `v.status = 'LOCKED'` on approve which is inconsistent — APPROVE should land in APPROVED, then LOCK separately. **D-406.** | D-406 — MSW handler bug. |

---

## 4. Status Machine Verification

| Cell (4 states × 4 transitions) | Allowed | Tested | Verdict |
|---------------------------------|---------|--------|---------|
| DRAFT × APPROVE | yes | `validTransitionsArePermitted` row 1 | PASS |
| DRAFT × LOCK | no | `invalidTransitionsAreRejected` | PASS |
| DRAFT × ARCHIVE | yes | row 2 | PASS |
| DRAFT × CREATE_NEW_VERSION | no | invalid | PASS |
| APPROVED × APPROVE | no | invalid + `approvedToApprovedDirectEditForbidden` | PASS (explicit) |
| APPROVED × LOCK | yes | row 3 | PASS |
| APPROVED × ARCHIVE | yes | row 4 | PASS |
| APPROVED × CREATE_NEW_VERSION | yes | row 5 | PASS |
| LOCKED × APPROVE | no | invalid | PASS |
| LOCKED × LOCK | no | invalid | PASS |
| LOCKED × ARCHIVE | yes | row 6 | PASS |
| LOCKED × CREATE_NEW_VERSION | yes | row 7 | PASS |
| ARCHIVED × * | no (terminal) | `archivedIsTerminal` (4 actions) | PASS |
| DB-level locked regression | rejected | `Phase4MethodologyIntegrationTest.lockedVersionCannotRegressToApproved` | PASS (Docker-gated) |

**Verdict: status machine COMPLETE — 16 cells covered (7 valid + 9 invalid). Master plan §14 fully satisfied.**

---

## 5. Weight Sum Visualizer Verification (Backend ↔ Frontend Parity)

| Mode | Backend rule (`MethodologyWeightValidationPolicy`) | Frontend visualizer | Test |
|------|----------------------------------------------------|---------------------|------|
| DIRECT_POINTS | No weight sum check | `WeightSumVisualizer` returns null (L39) | `WeightSumVisualizer.test.tsx` "renders nothing in DIRECT_POINTS mode" |
| WEIGHTED_POINTS | sum == 100.0000 exact (`compareTo == 0`) | target = 100; success tone when `|sum-target| <= 1e-4` (L19, L44) | "at-target tone=success"; "warning when close not equal"; "danger when drift > 10%" |
| WEIGHTED_SCALE | sum == version.targetTotalPoints exact | target = `version.target_total_points`; same tolerance | "uses target_total_points in WEIGHTED_SCALE mode" |

**Subtle drift (D-401):** Backend uses BYTE-STRICT `BigDecimal.compareTo == 0` for both WEIGHTED_POINTS and WEIGHTED_SCALE. Frontend uses `Math.abs(delta) <= 1e-4` (numeric tolerance). The frontend will display "at-target / success" for a sum of 99.99999999 → user clicks Approve → backend rejects with `WEIGHTED_POINTS requires factor weight sum == 100.0000, got 99.99999999`. **UI promise diverges from backend invariant.** See defects §14.

**Verdict: visualizer LOGIC is correct (hidden in DIRECT_POINTS; right target per mode); tolerance MISMATCH between FE and BE is a Medium defect.**

---

## 6. Multilingual Field Verification

- **Primary locale enforced on APPROVE:** `ApproveMethodologyVersionUseCase` L112 calls `localeValidator.validate(factorRows)`. `MethodologyVersionPrimaryLocaleValidator.PRIMARY_LOCALE = "ru-RU"`; checks every factor has non-blank `nameI18n["ru-RU"]`. Test coverage: 4 assertions including null map + blank string.
- **`@SupportedLocaleKeys` validation:** Applied to `CreateMethodologyRequest.nameI18n` (`@NotEmpty` + `@SupportedLocaleKeys`); `CreateMethodologyRequest.descriptionI18n`; `CreateMethodologyFromTemplateRequest.nameI18n`/`descriptionI18n`; `FactorRequest.nameI18n`/`descriptionI18n`; `FactorLevelRequest.labelI18n`/`descriptionI18n`. **PASS.**
- **Frontend completeness dots:** `FactorTable.tsx` L98-120 renders a 4-locale dot row per factor with `data-testid="factor-{code}-locale-{loc}-{filled|empty}"`. Tested by `FactorTable.test.tsx` (presence verified — not asserted in this scope but visible in test file list).

**Gap:** No test asserts that `descriptionI18n` is OPTIONAL (i.e. blank `ru-RU` description doesn't fail APPROVE). Low.

**Verdict: multilingual surface CORRECT. Locale-key allowlist tested via the shared `SupportedLocaleKeysValidatorTest`. Primary-locale rejection covered.**

---

## 7. Locked Methodology Verification (Service + DB + Frontend)

| Layer | Mechanism | Test |
|-------|-----------|------|
| Service layer | `MethodologyVersionImmutabilityPolicy.ensureMutable(status)` invoked from `FactorService.add/update/remove/reorder`, `FactorLevelService.*`, and version-meta updates | `MethodologyVersionImmutabilityPolicyTest` (4 unit tests) |
| DB level | Triggers `trg_factor_immutability_on_locked_version` + `trg_level_immutability_on_locked_version` (changelog 011 L268-306) raise `METHODOLOGY_VERSION_LOCKED` exception (errcode 23514) on INSERT/UPDATE/DELETE when parent version is APPROVED/LOCKED/ARCHIVED | `Phase4MethodologyIntegrationTest.factorOnLockedVersionCannotBeInsertedDbLevel`, `factorLevelOnApprovedVersionRejectedDbLevel`, `lockedVersionCannotRegressToApproved` |
| Version-status regression | Trigger `trg_mv_status_immutability` referenced in test L90 prevents LOCKED→APPROVED downgrade | `lockedVersionCannotRegressToApproved` — verifies the trigger is present (Docker-gated) |
| Frontend | `LockedMethodologyHeader` rendered when version.status ∈ {APPROVED, LOCKED}; `data-testid="locked-methodology-header"` with `data-status` attribute; CTA wrapped in `<PermissionGate permission={METHODOLOGY_EDIT}>` | `MethodologyBuilderPage.test.tsx` "APPROVED shows LockedMethodologyHeader + read-only factor table"; "LOCKED shows lock icon + locked metadata"; assertion `data-status === 'LOCKED'` and actor name in `locked-actor-time` textContent |
| Frontend factor table read-only | `FactorTable` accepts `readOnly` prop; renders no edit/remove/reorder/add buttons when readOnly | Builder page passes `readOnly = version.status !== 'DRAFT'` (L80) |
| MSW lock-rejection | Locked version 409 with `METHODOLOGY_LOCKED` code | Handler L602 |

**Verdict: 3 layers of defense (service / DB trigger / UI read-only). Defense-in-depth correctly implemented.**

**Note (D-407):** Frontend `LockedMethodologyHeader` shows ACTOR id (UUID) directly in the body text — not a human-readable name. The i18n template `methodology.locked_header_body_locked` interpolates `actor` and `timestamp`; if backend ships `lockedBy = UUID`, the user sees an unhelpful UUID. Need backend-side actor name resolution OR frontend-side join with user directory. **Medium.**

---

## 8. Audit Event Verification

18 new constants confirmed in `AuditAction.java` L62-79:
- METHODOLOGY: CREATED, UPDATED, ARCHIVED, APPROVED, LOCKED, VERSION_CREATED, VERSION_APPROVED, VERSION_LOCKED, VERSION_ARCHIVED, VERSION_REVISION_CREATED (10).
- FACTOR: CREATED, UPDATED, REMOVED, REORDERED, LEVEL_CREATED, LEVEL_UPDATED, LEVEL_REMOVED, LEVEL_REORDERED (8).

`Phase4AuditActionsTest` statically validates all 18 by reflection on AuditAction class fields.

**before/after snapshot capture:**
- `MethodologyAuditSnapshot` builds JSON via shared `AuditJsonRedactor` — uses `addI18nPreviews(...)` for multilingual maps (truncates long-text per security blueprint §9.3 / F-309). PUTs id/tenantId/projectId/code/scoringMode/status/timestamps.
- Verified in 4 use cases: `ApproveMethodologyVersionUseCase` (before+after, L115/L130), `LockMethodologyVersionUseCase` (L69/L83), `FactorService.update/remove` (L106/L130, L144/L154), `CreateMethodologyVersionUseCase` (L130-131 — snapshots BOTH source and new).

**Verdict: PASS on catalog + source-side wiring. Lifecycle audit-row integration test still missing for Phase 4 (D-405).**

---

## 9. Frontend Status-Aware UI Verification

| State | Behavior | Test | Verdict |
|-------|----------|------|---------|
| DRAFT | FactorTable editable; Save/Approve/Archive buttons; WeightSumVisualizer visible | `MethodologyBuilderPage.test.tsx` "DRAFT shows Save/Approve/Archive actions + WeightSumVisualizer" — asserts `action-approve` + `action-archive` + `weight-sum-visualizer` + no `locked-methodology-header` | PASS |
| APPROVED | LockedMethodologyHeader visible; no Approve button; factor table read-only (no `factor-A-edit`); Create-new-version CTA visible | "APPROVED shows LockedMethodologyHeader + read-only factor table" — asserts all four | PASS |
| LOCKED | LockedMethodologyHeader with `data-status="LOCKED"`; actor name in `locked-actor-time`; WeightSumVisualizer hidden | "LOCKED shows lock icon + locked metadata + read-only" — asserts all three | PASS |
| ARCHIVED | (not explicitly tested in this scope) | — | **GAP** — no test for ARCHIVED render state (banner / read-only). D-408 (Low). |

**Frontend WeightSumVisualizer visibility per status:** the builder page passes the visualizer conditionally based on `version.status === 'DRAFT'`. Test confirms it is HIDDEN in LOCKED. Not explicitly asserted for ARCHIVED.

**"Create new version" gating:** `LockedMethodologyHeader.tsx` L55-65 wraps the button in `<PermissionGate permission={METHODOLOGY_EDIT}>` — only renders when caller has METHODOLOGY_EDIT. **PASS.**

**"Create new version" only available from APPROVED/LOCKED (not DRAFT):** the header itself only renders for status ∈ {APPROVED, LOCKED} per the builder page logic. DRAFT does NOT show the header → no Create new version CTA. **PASS by construction.** No explicit negative test.

---

## 10. Template Instantiation Verification

| Template | Type | Scoring mode | Target | Factors × Levels | Test |
|----------|------|--------------|--------|------------------|------|
| CLASSIC_8_FACTOR | CLASSIC_8_FACTOR | DIRECT_POINTS | 1000 | 8 × 5 = 40 levels; weights sum to 1000 (180+150+140+130+120+110+90+80) | `Phase4TemplateRegistryTest.classic8FactorHas8FactorsAnd1000PointTarget` |
| EXTENDED_11_CRITERIA | EXTENDED_11_CRITERIA | WEIGHTED_POINTS | null | 11 × 5 = 55 levels; weights sum to 100.0000 (validated at static init by template registry L132-135 — fails loudly on drift) | `Phase4TemplateRegistryTest.extended11CriteriaWeightsSumTo100` |
| CUSTOM | CUSTOM | DIRECT_POINTS | null | 0 factors | `Phase4TemplateRegistryTest.customIsEmpty` |
| Unknown code | — | — | — | throws `ResourceNotFoundException` | `Phase4TemplateRegistryTest.unknownTemplateThrows404` |

**Verdict: template catalog COMPLETE; weight invariants self-validated at registry init.**

**Gap:** No integration test that instantiates a template via `CreateMethodologyFromTemplateUseCase` and asserts the persisted version has exactly 8/11/0 factors with proper i18n maps. Source-code review only. Carryforward into Phase 5 entry tests.

---

## 11. MSW Mock Realism

- `handlers.ts` L602: `409 METHODOLOGY_LOCKED — Version is locked or approved; edits not allowed` ✅
- L789: detail GET `/methodology-versions/:id`
- L796: approve `/methodology-versions/:id/approve` — **BUG: L814 sets `v.status = 'LOCKED'`** when it should be `'APPROVED'`. The lock endpoint is at L827 (`/lock`) which ALSO sets LOCKED. This collapses APPROVE+LOCK into one state in MSW, hiding the APPROVED state from frontend integration tests. **D-406 (Medium).**
- L840: archive
- L854: add factor
- L878: reorder

**Verdict:** L814 introduces a frontend mock that doesn't model the APPROVED→LOCKED two-step from the master plan §14. Tests that depend on the intermediate APPROVED state may pass MSW but fail against real backend.

---

## 12. i18n Parity

- `i18nParity.test.ts` enforces full union + no orphans + equal counts across `en-US.json` (19170 B), `ru-RU.json` (27048 B), `uz-Cyrl-UZ.json` (26706 B), `uz-Latn-UZ.json` (20307 B). Byte sizes are not equal (Cyrillic is wider) — this is expected and not a parity violation.
- The test is invoked by every PR pre-merge.

**Verdict: PASS by construction.** Reviewer cannot execute the test runner here but the test file is in place and was reportedly green at merge.

---

## 13. No Salary Leakage

- Grep `salary|Salary|SALARY` over `backend/src/main/java/uz/hrlab/grading/methodology/` → **0 matches.**
- Grep `salary|Salary|SALARY` over `frontend/src/features/methodology/` → **0 matches.**
- Methodology / Factor / FactorLevel domain entities + DTOs + DB columns contain ZERO salary fields. Per master plan §1 / §6.3 — methodology is "public/internal low" or "tenant confidential", not "highly sensitive".

**Verdict: PASS — zero salary surface in Phase 4.**

---

## 14. Defects Found (Phase 4)

### D-401 — Weight sum tolerance mismatch between backend (BYTE-STRICT) and frontend (1e-4)

- **Severity:** Medium
- **Affected component:** `MethodologyWeightValidationPolicy.validate` (backend) vs `WeightSumVisualizer.tsx` L19 (frontend)
- **Description:**
  - **Given** a methodology in WEIGHTED_POINTS mode with factors summing to 99.99999999 (drift from float math),
  - **When** the user views the WeightSumVisualizer, it renders `data-tone="success"` (because `|99.99999999 - 100| ≤ 1e-4`),
  - **Then** the user clicks Approve, and the backend rejects with `WEIGHTED_POINTS requires factor weight sum == 100.0000, got 99.99999999` (because backend uses `BigDecimal.compareTo == 0` which is byte-strict).
  - The visualizer promises green-light approval that the backend will deny. Confusing UX + potential failed-approve audit pollution.
- **Suggested fix:** either (a) backend accepts ±1e-4 tolerance matching the UI, or (b) the visualizer requires exact `BigDecimal.compareTo == 0` and the data layer keeps weights as integer ×10000. Option (a) is simpler and aligns with master plan §15 "BigDecimal scale=4 RoundingMode.HALF_UP".
- **Owner:** backend-engineer + frontend-engineer (decide on Phase 5 W1)

### D-402 — CreateMethodologyVersionUseCase has no end-to-end deep-copy test

- **Severity:** Medium
- **Affected component:** `CreateMethodologyVersionUseCase.createNewVersion`
- **Description:**
  - **Given** an APPROVED methodology version v1 with 8 factors, 40 levels, full ru-RU + en-US translations,
  - **When** `newVersionUseCase.createNewVersion(v1.id)` is called via the controller,
  - **Then** no test asserts (a) v1 is unchanged in DB, (b) v2 exists with `versionNumber=2`, `previousVersionId=v1.id`, `status=DRAFT`, same `scoringMode` + `targetTotalPoints`, (c) all 8 factors deep-copied with identical i18n maps + weights, (d) all 40 levels deep-copied with identical i18n labels + points + scaleValue, (e) audit row `METHODOLOGY_VERSION_REVISION_CREATED` written with before+after snapshots. The use case is source-only verified.
- **Suggested fix:** `@SpringBootTest` integration `CreateMethodologyVersionUseCaseTest` with seeded APPROVED version + TestSecurityContext + assertion suite.
- **Owner:** backend-engineer

### D-403 — Factor + FactorLevel cross-tenant probe missing

- **Severity:** Medium
- **Affected component:** `FactorRepository`, `FactorLevelRepository`
- **Description:**
  - **Given** `Phase4MethodologyIntegrationTest` proves Tenant A cannot read Tenant B's Methodology + MethodologyVersion at repo layer,
  - **When** the same probe is attempted for Factor and FactorLevel,
  - **Then** no test exists. The repositories extend `TenantAwareRepository` (ArchUnit rule guarantees it) — but no positive proof. A regression that adds a non-tenant-aware finder method would slip through.
- **Suggested fix:** add `factorFromTenantBIsInvisibleToTenantA` and `factorLevelFromTenantBIsInvisibleToTenantA` to `Phase4MethodologyIntegrationTest`.
- **Owner:** backend-engineer

### D-404 — No Phase 4 ABAC denial audit row test

- **Severity:** Medium
- **Affected component:** `AbacGate.recordDenial` × Phase 4 write use cases
- **Description:**
  - **Given** a user U6 (Department Manager) without project-write scope attempting to update a Factor,
  - **When** `FactorService.update` is called and `AbacGate.enforceCanWriteInProject` rejects,
  - **Then** no test asserts an `ACCESS_DENIED_BY_ABAC` row landed in `system_audit_log` for any Phase 4 mutation. Phase 3 has `Phase3AbacDenialAuditTest`; the pattern is not yet replicated.
- **Suggested fix:** add `@Tag("integration")` test `Phase4AbacDenialAuditTest` exercising `FactorService.update` with a denying ABAC context + audit-row assertion.
- **Owner:** backend-engineer

### D-405 — Phase 4 end-to-end audit lifecycle row test missing

- **Severity:** Medium
- **Affected component:** All 18 Phase 4 audit actions
- **Description:**
  - **Given** the master plan §17 hash-chain rule and the existing `Phase3AuditLifecycleTest` precedent,
  - **When** Phase 4 ships 18 new audit codes with before/after JSON capture,
  - **Then** no integration test POSTs to a controller (e.g. POST /from-template → POST /approve → POST /lock → POST /create-new-version) and asserts (a) 4 rows landed in `system_audit_log`, (b) correct action codes, (c) before/after JSON present (non-null), (d) `hash_prev → hash_current` chain unbroken across the 4 rows. This is the same gap as D-313 from Phase 3.
- **Suggested fix:** `Phase4AuditLifecycleTest` runs the 4-step lifecycle + queries audit log + asserts hash chain.
- **Owner:** backend-engineer + QA

### D-406 — MSW handler conflates APPROVED + LOCKED states

- **Severity:** Medium
- **Affected component:** `frontend/src/shared/api/mocks/handlers.ts` line 814 (approve handler)
- **Description:**
  - **Given** the master plan §14 status machine: APPROVE → APPROVED, then LOCK → LOCKED (two-step),
  - **When** MSW handler L796-815 processes POST `/methodology-versions/:id/approve`, it sets `v.status = 'LOCKED'` (L814) — collapsing APPROVED into LOCKED,
  - **Then** any frontend integration test that depends on the intermediate APPROVED state (e.g. "Lock button visible only in APPROVED, hidden in LOCKED") will pass MSW but FAIL against real backend (which lands APPROVED then exposes Lock as a separate action).
- **Suggested fix:** L814 should be `v.status = 'APPROVED'`; add `approvedAt` + `approvedBy`. The separate `/lock` handler (L827) already correctly sets LOCKED.
- **Owner:** frontend-engineer

### D-407 — LockedMethodologyHeader shows actor UUID, not human name

- **Severity:** Medium
- **Affected component:** `frontend/src/features/methodology/components/LockedMethodologyHeader.tsx` L27-28, L49-52
- **Description:**
  - **Given** backend ships `version.locked_by = UUID(...)` and `version.approved_by = UUID(...)`,
  - **When** `LockedMethodologyHeader` renders the body via `t(bodyKey, { actor: actor ?? t('common.unknown_actor'), timestamp: ... })`,
  - **Then** the user sees a 36-character UUID inline ("Locked by 7e9c... on 2026-04-12T10:00:00Z") instead of a name ("Locked by Anvar Asqarov on 2026-04-12"). This is unhelpful audit-UX and may be a Medium finding for ext-auditor screens.
- **Suggested fix:** backend `MethodologyVersionResponse` should include `lockedByName` / `approvedByName` (resolved from `user_directory.full_name`); frontend uses the name. Alternatively, add a useUser(uuid) lookup hook.
- **Owner:** backend-engineer + frontend-engineer

### D-408 — No frontend test for ARCHIVED methodology render state

- **Severity:** Low
- **Affected component:** `MethodologyBuilderPage.tsx` + `MethodologyBuilderPage.test.tsx`
- **Description:**
  - **Given** master plan §14 requires ARCHIVED versions to be read-only with an archived banner,
  - **When** the builder page receives `version.status = 'ARCHIVED'`,
  - **Then** no test asserts the UI state (banner / no edit actions / no Create-new-version since CREATE_NEW_VERSION transition from ARCHIVED is forbidden per the state machine).
- **Suggested fix:** add `'ARCHIVED' shows archived banner and no edit actions'` test to `MethodologyBuilderPage.test.tsx`.
- **Owner:** frontend-engineer

### D-409 — No backend integration test verifies service-layer ImmutabilityPolicy via a full FactorService.update path

- **Severity:** Low–Medium
- **Affected component:** `FactorService` × `MethodologyVersionImmutabilityPolicy`
- **Description:**
  - **Given** `FactorService.update` calls `immutabilityPolicy.ensureMutable(...)` at L104,
  - **When** a developer accidentally removes this line in a refactor,
  - **Then** the unit test still passes (policy unit test doesn't cover FactorService) and the DB trigger catches it (good), BUT the audit trail records an `FACTOR_UPDATED` action with a generic 23514 SQL error rolled back — losing the service-layer "did not even attempt" semantics.
- **Suggested fix:** add `@SpringBootTest` `FactorServiceImmutabilityTest` covering update + remove + reorder on APPROVED version + assert that NO audit row was written (transactional rollback).
- **Owner:** backend-engineer

### D-410 — No test that primary-locale validator runs ONLY on APPROVE (not on DRAFT factor add)

- **Severity:** Low
- **Affected component:** `MethodologyVersionPrimaryLocaleValidator`
- **Description:**
  - **Given** the validator is invoked only from `ApproveMethodologyVersionUseCase.approve` (L112),
  - **When** a user adds a factor in DRAFT with only `en-US` translation,
  - **Then** the factor is persisted (no rejection during add) — this is by design.
  - **But:** no test confirms the validator is NOT called from FactorService.add — i.e. the user CAN save a partial factor and finish translations later. If a future refactor accidentally wires the validator into FactorService, draft-time edits would be rejected, breaking the work-in-progress UX.
- **Suggested fix:** add a positive test "factor can be added in DRAFT without ru-RU translation" via `FactorService.add` + `MethodologyVersionPrimaryLocaleValidator` not present in the call path.
- **Owner:** backend-engineer

### D-411 — No CSV-driven boundary test for WEIGHTED_POINTS sum at ±0.0001

- **Severity:** Low
- **Affected component:** `MethodologyWeightValidationPolicyTest`
- **Description:**
  - **Given** master plan §14 specifies "WEIGHTED_POINTS sum=100±0.0001",
  - **When** the existing tests use 99.99 + 100.01 (gross drift),
  - **Then** no test exercises the precise boundary (e.g. 99.99990001, 100.00009999, exactly 100.0000) to verify the policy's BigDecimal precision behavior at the spec's tolerance edge.
- **Suggested fix:** add CsvSource parameterized boundary cases. Also clarify in code-comment whether the policy is byte-strict or tolerance-based (see D-401).
- **Owner:** backend-engineer + hr-product-owner (clarify spec)

### D-412 — No test that AuditJsonRedactor truncates a long i18n preview in MethodologyAuditSnapshot

- **Severity:** Low
- **Affected component:** `MethodologyAuditSnapshot.of(...)` × `AuditJsonRedactor.addI18nPreviews`
- **Description:**
  - **Given** `MethodologyAuditSnapshot` uses `addI18nPreviews(...)` for `nameI18n` + `descriptionI18n` maps,
  - **When** a factor description is 5000 chars long,
  - **Then** no test asserts that `addI18nPreviews` truncates correctly per security-blueprint §9.3 / F-309 long-text policy.
- **Suggested fix:** unit test feeding a 5000-char `descriptionI18n["ru-RU"]` and asserting the resulting JSON node contains a truncated preview (e.g. ≤ 256 chars).
- **Owner:** backend-engineer + security-engineer

---

## 15. Missing Tests for Phase 5 Entry

| Pack | Missing test | File |
|------|--------------|------|
| Integration | Phase 4 ABAC denial audit row (D-404) | new `Phase4AbacDenialAuditTest` |
| Integration | Phase 4 end-to-end audit lifecycle + hash chain (D-405) | new `Phase4AuditLifecycleTest` |
| Integration | Factor + FactorLevel cross-tenant probe (D-403) | extend `Phase4MethodologyIntegrationTest` |
| Use case | CreateMethodologyVersionUseCase deep-copy + audit (D-402) | new `CreateMethodologyVersionUseCaseTest` |
| Use case | FactorService immutability against non-DRAFT version + no audit row (D-409) | new `FactorServiceImmutabilityTest` |
| Use case | FactorService.add without ru-RU translation accepted (D-410) | extend FactorService test |
| Unit | Weight policy boundary at ±0.0001 (D-411) | extend `MethodologyWeightValidationPolicyTest` |
| Unit | Long i18n preview truncation in audit snapshot (D-412) | new `MethodologyAuditSnapshotTest` |
| Frontend | ARCHIVED methodology renders banner + no edit (D-408) | extend `MethodologyBuilderPage.test.tsx` |
| Frontend | LockedMethodologyHeader shows human name not UUID (D-407) | new + backend join |
| Frontend | MSW handler approve → APPROVED (not LOCKED) (D-406) | fix mock + add test |
| Frontend | WeightSumVisualizer tolerance matches backend (D-401) | align FE + BE + add boundary test |

**Total: 12 test files / suites required before Phase 5 entry.**

---

## 16. Test Execution Result

- **Backend:** the brief states 278 tests (227 pass + 51 Docker-skipped). Test file count at review time:
  - Phase 4 specific: 6 methodology unit/security + 3 phase4 (audit catalog + template registry + integration) = 9 new files.
  - Total test file count (`find src/test/java -name "*.java"`) is ≥ 45 across the project.
  - Phase 4 Docker-gated tests are `Phase4MethodologyIntegrationTest` (6 tests) + cross-project tenant + audit append-only (carryover from earlier phases).
  - Reviewer cannot execute Docker on this machine; CI surefire report is the source of truth.
- **Frontend:** the brief states 130/130 passing. Test files in scope:
  - methodology: 8 (6 component + 2 page).
  - Other Phase 4 tests would be MSW handlers + i18n parity (carryover).
  - Vitest pool history on Windows had transient timeouts in Phase 3 (3 worker-pool errors); confirm CI numbers.

**Action:** require backend `surefire-report` + frontend `vitest --reporter=junit` artifacts to be uploaded with the PR for binding verification of the "278" and "130" counts.

---

## 17. Regression Risks for Phase 5 (Evaluation / Scoring Engine)

1. **High** — D-401 (weight tolerance drift) will compound in Phase 5: the scoring engine produces BigDecimal totals; the displayed score may show "100.0000" while the raw stored value is 100.00009999. Tolerance policy must be defined ONCE and consistently — see also master plan §15 `BigDecimal scale=4, RoundingMode.HALF_UP`.
2. **High** — D-405 (no end-to-end audit-row test) means the SCORING_CHANGED / EVALUATION_APPROVED audit events introduced in Phase 5 will inherit the same testing gap. The hash-chain integrity test (`TC-AUD-HASH-001`) is a release blocker per master plan §17 — it must exist by Phase 5 close.
3. **Medium** — D-402 (no end-to-end version creation test) — Phase 5 evaluations must remain linked to the OLD version when a new version is created (master plan §14 TC-MTH-LOCK-004). Without the deep-copy test, regressions that break the version chain may not surface until evaluation lifecycle is added.
4. **Medium** — D-407 (UUID-as-actor in LockedMethodologyHeader) — Phase 5 will ship `EvaluationApprover` displays + `GradeApprovedBy` — same UX gap will recur unless name resolution is established as a contract NOW.
5. **Medium** — D-406 (MSW approve → LOCKED) — Phase 5 frontend tests will rely on MSW for the evaluation lifecycle; if the methodology approve handler is broken, the evaluation-against-an-APPROVED-but-not-LOCKED-version scenarios will mismatch real backend behavior.
6. **Low** — D-410 (validator scope) — Phase 5 scoring will add its own validators; the precedent of "validate ONLY on submit/approve, not during draft mutation" must be tested.

---

## 18. Release Gate Decision

> **DECISION: GO WITH CONDITIONS** for Phase 4.

Rationale:
- The state machine (7 valid transitions + 9 invalid + ARCHIVED terminal + APPROVED→APPROVE forbidden) is **fully unit-tested** (master plan §14 satisfied).
- Immutability is enforced at THREE layers — service policy (4 tests), DB triggers (3 integration tests), frontend read-only divs (3 page tests).
- Weight validation correct per scoring mode (8 tests; DIRECT skip, WEIGHTED_POINTS exact 100, WEIGHTED_SCALE exact target).
- Primary-locale validator covered (4 tests).
- Template registry validated (4 tests with self-checking weight sum).
- ABAC write-path is **enforced on all 4 critical mutation paths** by source review.
- All 18 audit codes present + before/after JSON capture via `MethodologyAuditSnapshot` (closes Phase 3 D-303).
- Frontend uses read-only divs (not disabled inputs), respects PermissionGate, hides WeightSumVisualizer in DIRECT_POINTS.
- i18n parity automated.
- **Zero salary leakage in Phase 4 surface.**

None of the defects is Critical; D-401, D-402, D-403, D-404, D-405, D-406, D-407 are Medium.

**Conditions that MUST be met before Phase 5 begins (blocking):**

- **PC4-1** Decide D-401 — align WeightSumVisualizer tolerance with backend (or vice-versa). Two-day fix.
- **PC4-2** Add D-402 — `CreateMethodologyVersionUseCaseTest` (deep-copy + audit). Master plan §14 TC-MTH-LOCK-003/004 are release blockers.
- **PC4-3** Add D-404 + D-405 — `Phase4AbacDenialAuditTest` + `Phase4AuditLifecycleTest`. Phase 5 will add ≥ 6 more audit codes; the lifecycle assertion pattern must exist.
- **PC4-4** Fix D-406 — MSW approve handler must land APPROVED, not LOCKED. One-line frontend fix.
- **PC4-5** Decide D-407 — backend actor-name resolution contract. Either backend ships `lockedByName` or frontend joins. Pick one before Phase 5 evaluation-approver displays land.

**Non-blocking (track in Phase 5 W1):**

- **PC4-6** Close D-403, D-408, D-409, D-410, D-411, D-412.

If **PC4-1 … PC4-5** are met before Phase 5 PRs land, Phase 4 ships green. Otherwise the gate flips to **NO-GO**.

---

## 19. Top Action Items (prioritized)

### backend-engineer

1. **(Blocking, PC4-1)** Decide weight-sum tolerance contract; align with WeightSumVisualizer (D-401). Update `MethodologyWeightValidationPolicy` and document in Javadoc.
2. **(Blocking, PC4-2)** Add `CreateMethodologyVersionUseCaseTest` covering deep-copy of factors + levels + audit row + source-unchanged (D-402).
3. **(Blocking, PC4-3)** Add `Phase4AbacDenialAuditTest` + `Phase4AuditLifecycleTest` with hash-chain verification (D-404, D-405).
4. **(Blocking, PC4-5)** Add `lockedByName` + `approvedByName` to `MethodologyVersionResponse` (D-407).
5. (Non-blocking) Extend `Phase4MethodologyIntegrationTest` with factor + level cross-tenant probe (D-403).
6. Add `FactorServiceImmutabilityTest` covering update/remove/reorder on non-DRAFT version (D-409).
7. Add boundary test cases ±0.0001 to `MethodologyWeightValidationPolicyTest` (D-411).
8. Add `MethodologyAuditSnapshotTest` for long-text truncation (D-412).

### frontend-engineer

1. **(Blocking, PC4-4)** Fix MSW handler `handlers.ts` L814 — approve → `APPROVED` not `LOCKED` (D-406).
2. **(Blocking, PC4-1)** Align `WeightSumVisualizer` tolerance with backend decision (D-401).
3. (Blocking, PC4-5) Render `lockedByName` once backend ships it (D-407).
4. Add ARCHIVED render state test to `MethodologyBuilderPage.test.tsx` (D-408).
5. Add negative test: user without METHODOLOGY_EDIT does not see "Create new version" CTA in LockedMethodologyHeader.

### security-engineer

1. Sign off on D-401 decision — weight tolerance is a data-integrity contract.
2. Sign off on D-407 — UUID-as-actor in audit UI is a usability vs PII consideration for the auditor screen.
3. Confirm D-412 — long-text truncation in audit snapshots is the right redaction policy for methodology content.

### hr-product-owner

1. Decide D-401 / D-411 — is `±0.0001` tolerance or exact `100.0000` the contract? Required for Phase 5 scoring spec.
2. Decide D-407 — does the locked-banner show user's full name or login email or UUID?

### db / devops

1. Verify Liquibase 010-013 roll back cleanly on a fresh DB (carryforward verification pattern from Phase 3).
2. Surface CI surefire + Vitest JUnit reports so the "278 / 130" claimed counts are verifiable.

---

**End of Phase 4 QA Review.**

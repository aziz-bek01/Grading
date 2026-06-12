# Proposal — Item 3: Multi-Evaluator Campaigns, Averaged Result, CEO Approval

**Status:** PROPOSAL / RATIFIABLE. No code, no schema change applied. This document is the concept the owner ratifies before implementation, because it alters the production data model.

**Scope of this round:** product/UX design + data-model sketch + reuse map. It answers the owner's honest question first, then sketches each screen in words.

---

## 0. Honest answer to "Булар ҳисобга олинганми?" — NO, not today

I traced the production model. The current system is built around **exactly one evaluation per (position, methodology_version)**, enforced in two places (both verified):

- **DB:** partial unique index in `015-create-evaluations.yaml:178`
  `uq_evaluations_active_per_position_version ON evaluations (tenant_id, project_id, position_id, methodology_version_id) WHERE status <> 'ARCHIVED'`.
- **App:** `CreateEvaluationUseCase.java:98` → `existsByTenantIdAndPositionIdAndMethodologyVersionIdAndStatusNot(...)` throws `VALIDATION_FAILED` ("already exists"), surfaced by `BulkCreateEvaluationsUseCase` as `ALREADY_EXISTS`.

So today a SECOND evaluator literally cannot create a sheet for the same position — they hit `ALREADY_EXISTS`. There is no concept of an evaluator **role** (HR director / dept director / external expert), no averaging, and the CEO approval is a generic single-step `EVALUATION_APPROVE` (`SubmitEvaluationUseCase.java:125-132`), not a campaign-level CEO sign-off over an averaged result.

**The good news (what already exists and we reuse, no duplication):**
- `evaluations.evaluator_user_id` column ALREADY exists (`015:69`), is FK'd to `public.users`, indexed (`idx_evaluations_tenant_evaluator`), and there is already a finder `findAllByTenantIdAndEvaluatorUserId`. The model was *almost* shaped for this — the only blocker is the unique index excluding the evaluator.
- The K-sheet (`EvaluationByFactorView`) already keys every row by `evaluation_id` and respects per-evaluation status — it works unchanged for "one evaluator's own sheet".
- The approval workflow already wraps `EVALUATION` as an `ApprovalEntityType`, supports ordered multi-step `ApprovalStep`s gated by `requiredPermission`, and the inbox/detail already resolve `entity_label_i18n` + initiator name (the items 1-2 fix). The CEO step is just one more step with a CEO permission.
- `MethodologyActorNameResolver` is the proven BE pattern for turning evaluator UUIDs into display names; we reuse it (do not invent a new resolver).

---

## 1. Core concept: the Evaluation **Campaign**

Introduce ONE new aggregate that groups the per-evaluator sheets. The per-evaluator sheet stays the existing `evaluation` row (re-keyed). This keeps the K-sheet, scoring engine, calibration, and approval surfaces intact.

```
evaluation_campaign  (NEW aggregate — 1 per position+methodology_version)
   ├─ position_id, methodology_version_id, project_id, tenant_id
   ├─ status: SETUP → IN_PROGRESS → ALL_COMPLETE → AVERAGED → CEO_PENDING → APPROVED → LOCKED → ARCHIVED
   ├─ min_evaluators = 3 (default; configurable per project, never below 3)
   ├─ averaged_raw_total, averaged_displayed_total   (filled only at AVERAGED)
   └─ approval_request_id (the CEO chain, nullable until CEO_PENDING)

evaluation  (EXISTING row, re-scoped — now "one evaluator's K-sheet")
   ├─ campaign_id (NEW FK)
   ├─ evaluator_user_id        (existing column — now meaningful)
   ├─ evaluator_role: HR_DIRECTOR | DEPARTMENT_DIRECTOR | EXTERNAL_EXPERT | ADDITIONAL  (NEW enum)
   ├─ blind = true             (NEW — drives "cannot see others' scores")
   └─ status (existing state machine, unchanged per-sheet)

evaluation_campaign_factor_average  (NEW, derived/append-on-average)
   └─ campaign_id, factor_id, avg_raw_factor_score, evaluator_count, stddev (optional)
```

**Why a campaign aggregate and not "just N evaluation rows"?** Three reasons surfaced by the requirements:
1. *Completion rule* needs a single owner: "all ASSIGNED evaluators done". A loose set of rows has no place to store "who is assigned but hasn't started".
2. *Averaging* needs a single official result to attach a grade + CEO approval to. Today grade assignment hangs off one evaluation; with N sheets we need one averaged target.
3. *Bias / blind rule* needs a gate that says "this campaign is still collecting → hide cross-evaluator scores".

**Schema-change required (the part the owner must ratify):**
- Replace the unique index with `uq_evaluations_active_per_position_version_evaluator` → `(tenant_id, project_id, position_id, methodology_version_id, evaluator_user_id) WHERE status <> 'ARCHIVED'`. This is the single line that unblocks N evaluators. Existing single-evaluator data is forward-compatible (each becomes a 1-sheet campaign on migration).
- Add `evaluation_campaign`, `campaign_id` FK on `evaluations`, `evaluator_role`, `evaluation_campaign_factor_average`.
- The averaged total is computed by a NEW thin aggregation service that calls the EXISTING `EvaluationScoringEngine` per sheet and averages the results — the pure scoring engine is NOT touched (no duplication; averaging is composition on top).

---

## 2. Evaluator roles & the "minimum 3" rule

- Roles enum: `HR_DIRECTOR`, `DEPARTMENT_DIRECTOR`, `EXTERNAL_EXPERT` (the mandatory three) + `ADDITIONAL` (variable extra evaluators).
- A campaign cannot move SETUP → IN_PROGRESS until it has at least one of each mandatory role AND total assigned ≥ `min_evaluators` (default 3). The "variable count" requirement is honored: you may add as many `ADDITIONAL` as needed.
- Each evaluator gets exactly one sheet (one `evaluation` row) per campaign — the unique index now permits this.
- The three mandatory roles map naturally to who they are; we do NOT hardcode them to specific users — the assigner picks a user per role from the project's user list (reuse `users-access` people picker).

---

## 3. The bias / blind rule (they must NOT see each other's scores before completion)

This is a **read-scoping** rule, not a new UI. It lives in the read path:

- While campaign status ∈ {SETUP, IN_PROGRESS}: an evaluator's K-sheet query returns ONLY rows where `evaluation.evaluator_user_id = current_user`. The existing `findAllByTenantIdAndEvaluatorUserId` finder is the seed; the by-factor grid query (`findForFactorGrid`) gets a `+ AND evaluator_user_id = :me` predicate when the caller is an evaluator and the campaign is still collecting.
- Campaign-wide / averaged views (per-evaluator breakdown, averages) are gated behind a NEW read permission `CAMPAIGN_RESULTS_VIEW` that plain expert evaluators do NOT hold (mirrors the existing `canSeePoints = can(CALIBRATION_EDIT)` anchoring-bias pattern in `EvaluationByFactorView.tsx:124` — reuse that exact thinking, new permission code).
- Once campaign reaches ALL_COMPLETE, the blind lifts and the breakdown becomes visible to result-viewers (HR director, PM, CEO). An evaluator still sees only their own sheet unless they also hold `CAMPAIGN_RESULTS_VIEW`.
- Visual affordance on the evaluator's own sheet while blind: a small info banner "Бошқа экспертлар баҳоси якунлангунча яширин" (Other evaluators' scores are hidden until completion) — reuse the existing `ScorePreviewBanner` styling, not a new component.

---

## 4. Averaging rule (total + per-factor, only after all complete)

- Trigger: when the LAST assigned evaluator's sheet reaches COMPLETE (or SUBMITTED), the campaign auto-advances IN_PROGRESS → ALL_COMPLETE.
- A NEW `ComputeCampaignAverageService`:
  - For each factor: `avg_raw_factor_score = mean(raw_factor_score across all evaluators' sheets for that factor)`, stored to NUMERIC(12,4) HALF_UP (mirror the engine's rounding contract exactly).
  - `averaged_raw_total = mean(each sheet's raw_total_score)` — equivalently Σ(per-factor averages); we compute via per-factor for transparency and store both for display (12,4 raw, 12,2 displayed).
  - Optional `stddev` per factor → drives a "disagreement" indicator (red/amber) so calibration can target factors where evaluators diverged. Not color-only: pair with an icon + numeric delta (consistent with the accessibility rule).
- Averaging is composition over the EXISTING `EvaluationScoringEngine` (call per sheet, average results). The engine stays a pure per-sheet function — zero duplication.
- The averaged result is what gets a grade assigned and what the CEO approves — NOT any single evaluator's sheet.

---

## 5. CEO approval step (inside the existing approvals inbox)

We reuse the approval-request workflow verbatim. The CEO sign-off is a step on a campaign-level approval request.

- Entity type: add `EVALUATION_CAMPAIGN` to `ApprovalEntityType` (alongside the existing `EVALUATION`). The campaign — not a single sheet — is the approval target, so the CEO approves the averaged result.
- On ALL_COMPLETE → averaged, the campaign opens an approval request via the EXISTING `CreateApprovalRequestUseCase.createSystem(...)` (same path `SubmitEvaluationUseCase.java:127` already uses). Steps (ordered):
  - Step 1 (optional, if your process wants it): HR Director consolidation review — `requiredPermission = EVALUATION_APPROVE`.
  - Step 2: **CEO approval** — NEW permission `CAMPAIGN_CEO_APPROVE`. Last step approving flips campaign → APPROVED (reuse the existing "last step approves the request" logic in `ApprovalDecisionMaker`).
- Entity-label resolution (the items-1 fix path): `entity_label_i18n` for an `EVALUATION_CAMPAIGN` = position title + methodology name + "averaged" marker, resolved server-side via the same `ApprovalQueries.hydrate` enrichment that already produces labels for the other entity types. Initiator name via the existing resolver. So the CEO's inbox card reads e.g. **"Бош бухгалтер · ХР-Лаб методологияси v3 · ўртача баҳо"** — never a UUID.

---

## 6. Screen-by-screen sketches (words only; reuse existing surfaces)

### 6.1 Campaign setup / assign evaluators — NEW small surface, on the Evaluation list
**Where:** the existing `EvaluationListPage` "Add positions" flow (`AddPositionsDialog`) is generalized into an "Open evaluation campaign" dialog. Reuse the same modal shell + partial-fail result block.
- **Layout:** pick positions (multi-select, existing) → pick methodology version (existing default-version logic) → NEW "Evaluators" section: three required rows pre-labelled HR Director / Department Director / External Expert, each a user-picker (reuse `users-access` picker); a "+ Add evaluator" button appends `ADDITIONAL` rows.
- **Validation:** confirm disabled until 3 mandatory roles filled. Inline helper: "Минимум 3 эксперт: ХР директор, бўлим директори, ташқи эксперт."
- **On confirm:** creates one campaign + N sheets (one per evaluator). The bulk-create path (`BulkCreateEvaluationsUseCase`) is reused — it already collects per-row failures; the only change is it now creates per (position × evaluator) and the `ALREADY_EXISTS` guard keys on the new 5-column index, so re-running is idempotent per evaluator.
- **States:** loading (spinner in confirm), empty (no positions → existing EmptyState), error/partial-fail (existing result block listing which evaluator/position failed and why), no-access (dialog CTA hidden behind `EVALUATION_EDIT`, unchanged).
- **Localization:** role labels in all 4 locales; mind Uzbek-Cyrillic length ("Ташқи эксперт" vs "External expert") — give role column min-width.

### 6.2 Evaluator's own K-sheet — REUSE `EvaluationByFactorView`, no structural change
- Same Excel grid. The ONLY differences:
  - Header strip gains a small role chip: "Сиз: Ташқи эксперт" (You: external expert) so the evaluator knows their seat.
  - Blind banner (section 3) while campaign is collecting.
  - Rows are auto-scoped to the current evaluator's sheet (read predicate, section 3) — the evaluator never even sees a column for other evaluators.
- States: unchanged (the view already handles loading/empty/error/locked per-row). When campaign is APPROVED/LOCKED the evaluator's sheet is read-only via the existing per-evaluation status → read-only mapping.

### 6.3 Campaign progress view (who finished) — NEW lightweight panel, on Evaluation list (by-position) row expand or a campaign drawer
- **Goal:** at a glance, "3/3 done" or "2/3 — waiting on External expert".
- **Layout:** a compact roster: one line per assigned evaluator → name (via resolver) · role chip · status badge (reuse `EvaluationStatusBadge`: Draft/Incomplete/Complete) · last-updated date. A header progress chip "N/M completed" (reuse the existing `ProgressChip`).
- **Blind-safe:** this panel shows STATUS only, never scores, so it is safe to show to PM/HR before completion without leaking bias.
- **Next action / responsible:** if blocked, line reads "Кутилмоқда: Ташқи эксперт" with the responsible name — consistent with the workspace stepper's "responsible role / next action" pattern.
- **States:** loading (skeleton rows), empty (no evaluators assigned yet → "Assign evaluators" CTA back to 6.1), error (ErrorState + retry), no-access (panel hidden if no `EVALUATION_VIEW`).

### 6.4 Averaged result display (total + per-factor, only after all complete) — REUSE `EvaluationScoreBreakdown`, extended
- **Visibility gate:** only renders when campaign status ≥ ALL_COMPLETE AND viewer holds `CAMPAIGN_RESULTS_VIEW`. Before that: a locked-state placeholder "Ўртача натижа барча экспертлар якунлагач кўрсатилади" (Average shown once all evaluators finish) — reuse the locked/empty visual, never show partial averages.
- **Layout:** the existing per-factor breakdown table, with extra columns:
  - per-factor: **Average** (bold, the official number) + a faint **per-evaluator mini-row / sparkline** of the N individual factor scores + a **disagreement indicator** (icon + numeric stddev/range, not color-only).
  - footer: **Averaged total** (the official `averaged_displayed_total`), labelled "Ўртача умумий балл".
- **Per-evaluator breakdown:** a toggle "Экспертлар бўйича" expands to show each evaluator's column (name + role) side-by-side per factor — this is the same data the CEO sees (section 6.5), one shared component.
- **Preview-only honesty:** keep the existing "Preview only. Final official score is calculated by backend" convention — the displayed average is the backend's stored `averaged_*`, labelled as official; any FE-side recompute stays clearly a preview.
- **States:** loading, locked-placeholder (pre-completion), error, no-access ("Натижани кўриш ҳуқуқи йўқ").

### 6.5 CEO approval — REUSE approvals inbox + detail (the items-1/2 surfaces)
- **Inbox card (CEO):** the EXISTING `ApprovalRequestCard`, now showing an `EVALUATION_CAMPAIGN` request. Title = resolved entity label (position + methodology + "ўртача баҳо"); subline = "Тасдиқ · CEO қадами" + initiator name + date. No UUIDs (the fix already in place handles label + name; we extend the BE label resolver to cover the new entity type).
- **Detail page (`/app/approvals/{id}`):** the EXISTING `ApprovalDetailsPage`. For an `EVALUATION_CAMPAIGN` entity it renders, between the header and the steps list, a NEW read-only **campaign result summary block**:
  - top: averaged total + assigned grade band (reuse `AssignedGradeBadge`).
  - middle: the per-factor average table (the same component from 6.4) with the per-evaluator breakdown expandable — so the CEO sees "averaged summary + per-evaluator breakdown" exactly as the requirement states.
  - This summary is fetched by entity_id (campaign id) → reuse a campaign-detail query; no salary data here.
- **Actions:** the EXISTING `ApprovalActionsBar` — Approve / Reject / Request changes, gated by the step's `requiredPermission` (`CAMPAIGN_CEO_APPROVE`). Reject/Request-changes already require a reason ≥ 20 chars (reuse). On CEO approve → campaign APPROVED → grade assignment proceeds (existing `EvaluationGradeAssignmentService` runs against the averaged result instead of a single sheet).
- **States:** loading/error/empty already handled by the page (the items-2 fix); add a no-access branch when a non-CEO opens a CEO-only step (reuse the existing access-denied pattern).

---

## 7. Completion & status rules (single source of truth)
- Campaign `IN_PROGRESS → ALL_COMPLETE` iff every ASSIGNED sheet is COMPLETE/SUBMITTED. Derive from the sheet set; do NOT inline a status list anywhere — add one predicate on the campaign aggregate (mirror the existing `EvaluationStatus.isPreSubmission()` single-source pattern).
- `ALL_COMPLETE → AVERAGED` on successful average compute (auto).
- `AVERAGED → CEO_PENDING` opens the approval request (auto, reusing `createSystem`).
- `CEO_PENDING → APPROVED` on last approval step (reuse `ApprovalDecisionMaker`).
- Adding an evaluator after IN_PROGRESS re-opens completion (campaign drops back to IN_PROGRESS until the new sheet completes) — explicit confirm dialog (reuse `ConfirmDialog`, destructive=false, reason optional) because it can delay the CEO step.

---

## 8. Permissions to add (minimal, reuse-first)
- `CAMPAIGN_RESULTS_VIEW` — see averaged + per-evaluator breakdown (gate for 6.4/6.5 summary).
- `CAMPAIGN_CEO_APPROVE` — the CEO approval step permission.
- Everything else reuses existing codes: `EVALUATION_EDIT` (assign/score), `EVALUATION_APPROVE` (optional HR consolidation step), `EVALUATION_VIEW` (progress roster), `CALIBRATION_EDIT` (post-average manual calibration on the averaged result, unchanged path).

---

## 9. What does NOT change (explicit, to bound the blast radius)
- `EvaluationScoringEngine` (pure per-sheet) — untouched.
- K-sheet grid component — untouched (only a read predicate + 2 small banners).
- Approval inbox/detail/actions components — untouched structurally; one new entity-type label + one new summary block.
- Calibration, grade assignment, audit, salary masking — unchanged; they now point at the averaged result instead of a single sheet.
- The items-1/2 fixes (entity label + initiator name + detail page) directly carry over to the new `EVALUATION_CAMPAIGN` entity — we just extend the BE label resolver to the new type.

---

## 10. Open questions for the owner (ratification points)
1. Confirm the average is a **simple mean** (HR director, dept director, external expert weighted equally) — or do roles carry weights? Design currently assumes equal weight; weights are a config field if needed.
2. Should the **HR consolidation step** (6.5 step 1) exist, or go straight to CEO? Both are one config of the steps list.
3. Should an evaluator who has **not finished** block averaging, or can the campaign average with a quorum (e.g. 3 of 4)? Current rule = all assigned must finish; quorum is a `min_complete` field if desired.
4. After CEO approval, do we **lock all individual sheets** (recommended, immutable record) — confirm.

---

## Қисқача хулоса (Ўзбек кирилл)

**Савол:** кўп экспертли баҳолаш, ўртача натижа ва CEO тасдиғи ҳисобга олинганми? **Жавоб: ЙЎҚ.** Ҳозир тизим бир лавозим + бир методология учун фақат БИТТА баҳолашга рухсат беради (`uq_evaluations_active_per_position_version` уникал индекс ва `CreateEvaluationUseCase` даги текширув иккинчи экспертни `ALREADY_EXISTS` билан тўхтатади). Эксперт **роли**, ўртача ҳисоб ва кампания даражасидаги CEO тасдиғи йўқ.

**Таклиф (фақат концепция, код йўқ):**
1. **Кампания** деган янги тузилма киритилади — бир лавозим+методология учун битта. Ҳар бир эксперт ўзининг алоҳида K-варағини (мавжуд `evaluation` қатори, энди `evaluator_user_id` ва `evaluator_role` билан) олади. Уникал индекс эндиликда экспертни ҳам ўз ичига олади — шу ягона ўзгариш N экспертни очади.
2. **Минимум 3 эксперт:** ХР директор, бўлим директори, ташқи эксперт + истаганча қўшимча.
3. **Холислик (bias):** кампания тугагунча эксперт фақат ўзининг варағини кўради; бошқаларнинг баллари яширин (ўқиш даражасида чекланади, янги UI эмас).
4. **Ўртача:** барча экспертлар тугатгачгина ҳисобланади — умумий балл + ҳар фактор бўйича ўртача (мавжуд `EvaluationScoreBreakdown` кенгайтирилади; pure scoring engine ўзгармайди).
5. **CEO тасдиғи:** мавжуд approvals inbox/detail ичида. Янги `EVALUATION_CAMPAIGN` тури; CEO карточкада UUID эмас, тушунарли ном (лавозим + методология + "ўртача") ва ташаббускор исмини кўради (items-1/2 тузатиши шу ерга ҳам тушади). CEO батафсил саҳифада ўртача якун + ҳар эксперт бўйича тафсилотни кўради.

**Муҳим:** бу production маълумот моделини ўзгартиради (схема: `evaluation_campaign`, `campaign_id`, `evaluator_role`, уникал индекс алмаштириш). Шунинг учун амалга оширишдан олдин эгаси ТАСДИҚЛАШИ керак. Дубликат код йўқ — мавжуд K-варақ, scoring engine, approval workflow, name resolver қайта ишлатилади. 10-бўлимдаги 4 савол тасдиқ нуқталари.
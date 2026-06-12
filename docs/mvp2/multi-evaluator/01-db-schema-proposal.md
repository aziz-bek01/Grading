# PROPOSAL — Multi-Evaluator Model (Item 3) — RATIFY BEFORE IMPLEMENTATION

> Scope: design-only. No code, no migration applied. Alters the production data model, so it requires the owner's explicit go-ahead. Goal: **minimal change, reversible, backward-compatible with existing single-evaluation prod rows.**

---

## 0. Honest answer to "Булар ҳисобга олинганми?"

**No — the current system does NOT support multiple evaluators per position.** Verified against the live schema and code:

- `evaluations` (changelog `015-create-evaluations.yaml`) has exactly one `evaluator_user_id UUID NOT NULL` per row.
- A **partial unique index** `uq_evaluations_active_per_position_version ON evaluations (tenant_id, project_id, position_id, methodology_version_id) WHERE status <> 'ARCHIVED'` enforces **one active evaluation per (position, methodology_version)** — full stop, regardless of evaluator.
- `CreateEvaluationUseCase` re-checks the same rule in app code (`existsByTenantId...AndStatusNot(ARCHIVED)` → `"An active evaluation already exists..."`), and `BulkCreateEvaluationsUseCase` surfaces it as `ALREADY_EXISTS`.
- `SubmitEvaluationUseCase` opens a **single-step** approval request (`EVALUATION_APPROVE`) at submit time; `ApproveEvaluationUseCase` flips `SUBMITTED → APPROVED` and assigns the grade from `rawTotalScore`.

So today: 1 position + 1 methodology version = 1 evaluation = 1 evaluator = 1 score, approved by 1 step. The "minimum 3 evaluators, average their scores, then CEO approves" workflow is not modeled at all. Below is how to add it with the smallest reversible footprint.

---

## 1. Core design decision: introduce a "panel" aggregate above per-evaluator evaluations

The cheapest correct model keeps the existing `evaluations`/`evaluation_scores` tables almost untouched and **reuses each existing row as one evaluator's independent evaluation**. We add a thin parent that groups the evaluators and stores the averaged result.

```
evaluation_panels (NEW)            -- one per (position, methodology_version) — the "case"
  └─ panel_assignments (NEW)       -- one per (panel, evaluator) + role — min 3
  └─ evaluations (EXISTING)        -- one per evaluator (gets nullable panel_id FK)
        └─ evaluation_scores (EXISTING, unchanged) -- per (evaluation, factor)
  └─ panel_factor_averages (NEW)   -- materialized per-factor average (computed on completion)
  approval_requests (EXISTING)     -- opened on the PANEL when all evaluators complete -> CEO
```

Why a panel parent instead of widening `evaluations`:
- The uniqueness that must now hold is **"one active panel per (position, methodology_version)"** — exactly the constraint that lives on `evaluations` today. Move it up to `evaluation_panels` and **relax** it on `evaluations` to **"one active evaluation per (panel, evaluator)"**. This is a clean ownership transfer, not a behavioral guess.
- Each evaluator's `evaluations` row keeps its own state machine, scoring engine output, completeness check, calibration history, RLS, and triggers **with zero changes**. No duplication of the scoring engine (`EvaluationScoringEngine` stays the single pure function — Item 4).
- The averaged result is a derived value with its own approval lifecycle; it does not belong on any single evaluator's row.

---

## 2. New tables

### 2.1 `evaluation_panels` (new) — the unit that gets averaged + CEO-approved

| column | type | notes |
|---|---|---|
| `id` | UUID PK | `gen_random_uuid()` |
| `tenant_id` | UUID NOT NULL | FK `public.tenants`, RLS column (defense in depth) |
| `project_id` | UUID NOT NULL | FK `projects` |
| `position_id` | UUID NOT NULL | FK `positions` |
| `methodology_version_id` | UUID NOT NULL | FK `methodology_versions` |
| `status` | VARCHAR(32) NOT NULL | `COLLECTING / AWAITING_EVALUATIONS / AVERAGED / SUBMITTED / APPROVED / LOCKED / ARCHIVED` |
| `min_evaluators` | INT NOT NULL DEFAULT 3 | CHECK `>= 1`; product rule default 3 |
| `raw_total_score` | NUMERIC(12,4) | averaged total; null until `AVERAGED` |
| `displayed_total_score` | NUMERIC(12,2) | rounded average for UI |
| `grade_band_id` | UUID | assigned from averaged `raw_total_score` on CEO approval |
| `assigned_grade_number` | INT | |
| `averaged_at` / `averaged_by` | TIMESTAMPTZ / UUID | when the average was computed |
| `submitted_at` / `submitted_by` | TIMESTAMPTZ / UUID | when sent to CEO |
| `approved_at` / `approved_by` | TIMESTAMPTZ / UUID | CEO decision |
| `locked_at`/`locked_by`/`archived_at`/`archived_by` | | mirror `evaluations` audit columns |
| `version`, `created_at`, `created_by`, `updated_at`, `updated_by` | | standard |

Constraints / indexes:
- `chk_panel_status` CHECK status IN (...)
- `chk_panel_raw_total_non_negative` CHECK `raw_total_score IS NULL OR raw_total_score >= 0`
- **`uq_panels_active_per_position_version`** UNIQUE INDEX `(tenant_id, project_id, position_id, methodology_version_id) WHERE status <> 'ARCHIVED'` ← **this is the old `evaluations` constraint, moved up one level**.
- `idx_panels_tenant_project_status (tenant_id, project_id, status)`
- `idx_panels_tenant_position (tenant_id, position_id)`
- `idx_panels_mv (tenant_id, methodology_version_id)`
- Grade assignment reuses `EvaluationGradeAssignmentService` (Item 4 — no new grade logic), reading the panel's averaged `raw_total_score` instead of an evaluation's.

### 2.2 `panel_assignments` (new) — who must evaluate + their role

| column | type | notes |
|---|---|---|
| `id` | UUID PK | |
| `tenant_id` | UUID NOT NULL | RLS column |
| `panel_id` | UUID NOT NULL | FK `evaluation_panels` ON DELETE CASCADE |
| `evaluator_user_id` | UUID NOT NULL | FK `public.users` |
| `evaluator_role` | VARCHAR(40) NOT NULL | `HR_DIRECTOR / DEPARTMENT_DIRECTOR / EXTERNAL_EXPERT / ADDITIONAL` |
| `evaluation_id` | UUID NULL | FK `evaluations` — the per-evaluator row once started; null until they begin |
| `assignment_status` | VARCHAR(24) NOT NULL | `ASSIGNED / IN_PROGRESS / COMPLETED / WITHDRAWN` |
| `version`, audit columns | | standard |

Constraints / indexes:
- **`uq_panel_assignment_per_evaluator`** UNIQUE `(tenant_id, panel_id, evaluator_user_id) WHERE assignment_status <> 'WITHDRAWN'` — an evaluator is assigned to a panel once.
- `chk_panel_assignment_role` CHECK role IN (...)
- `chk_panel_assignment_status` CHECK status IN (...)
- `idx_panel_assignments_panel (tenant_id, panel_id)`
- `idx_panel_assignments_evaluator (tenant_id, evaluator_user_id, assignment_status)` — "my assigned evaluations" inbox.
- **Minimum-3 rule is enforced at the APPLICATION layer, not by a DB constraint** (DB cannot count cross-row at insert time without a deferred trigger; a trigger here would be brittle and hard to reverse). Rule: a panel cannot transition `COLLECTING → AWAITING_EVALUATIONS` (i.e. cannot lock the roster and start evaluating) until `count(active assignments) >= min_evaluators`. `min_evaluators` is stored so the rule is data-driven and a client could later raise it above 3.
- **Role completeness** (HR director + department director + external expert present) is also an app-layer roster validation, configurable per project later; default = these 3 roles required.

### 2.3 `panel_factor_averages` (new, MATERIALIZED) — per-factor average for display + reproducibility

| column | type | notes |
|---|---|---|
| `id` | UUID PK | |
| `tenant_id` | UUID NOT NULL | RLS column |
| `panel_id` | UUID NOT NULL | FK `evaluation_panels` ON DELETE CASCADE |
| `factor_id` | UUID NOT NULL | FK `factors` |
| `avg_raw_factor_score` | NUMERIC(12,4) NOT NULL | mean of each evaluator's `raw_factor_score` for this factor |
| `evaluator_count` | INT NOT NULL | how many evaluators contributed (denominator) |
| audit columns | | |

- `uq_panel_factor_average (tenant_id, panel_id, factor_id)`
- `idx_panel_factor_averages_panel (tenant_id, panel_id)`

---

## 3. Averaging: materialized result row, NOT computed-on-read

**Decision: materialize.** Store `evaluation_panels.raw_total_score` + `panel_factor_averages` rows, computed once at the completion event.

Rationale (this is a deliberate, defensible call, not a default):
- **Reproducibility (architecture §15 / golden-file principle):** the averaged number that the CEO approved and that drives grade assignment must be frozen at approval time. If a withdrawn evaluator or a late calibration changed the set of contributing rows afterward, a computed-on-read view would silently shift the approved score. Materializing pins it.
- **Immutability symmetry:** individual `evaluations` become immutable once `APPROVED/LOCKED` (existing trigger). The panel average is the analogue at the panel level and gets the same freeze.
- **Display performance:** the K-sheet and panel detail read one row, no per-request aggregation across N evaluators × M factors.

Computation contract (reuses existing pure pieces — Item 4):
1. Each evaluator's per-factor `raw_factor_score` already exists in `evaluation_scores` (produced by the single `EvaluationScoringEngine`). **We do not re-run scoring; we average already-computed engine output.**
2. `avg_raw_factor_score = mean(evaluator raw_factor_score for that factor)`, `BigDecimal`, `RoundingMode.HALF_UP`, scale 4 — identical rounding policy to the engine (`RAW_SCALE=4`).
3. `panel.raw_total_score = Σ avg_raw_factor_score`, re-rounded to scale 4; `displayed_total_score` = scale 2. (Average-of-per-factor-then-sum == average-of-totals when every evaluator scored every factor; using per-factor keeps it correct even if denominators differ and gives the per-factor display the product asks for.)
4. A new tiny pure domain service `PanelAveragingService.average(List<EvaluatorFactorScores>)` mirrors `EvaluationScoringEngine`'s purity (no clock/IO) so it is golden-file testable. It is the **only** place averaging math lives.

---

## 4. Completion semantics ("when ALL assigned evaluators complete")

State machine on `evaluation_panels` (deliberately mirrors the evaluation FSM vocabulary so reviewers recognize it):

```
COLLECTING            roster being built; assignments added/removed; NOT yet started
  → (roster valid: count >= min_evaluators AND required roles present)
AWAITING_EVALUATIONS  roster locked; each evaluator scores their own evaluation independently
  → (every active assignment.assignment_status = COMPLETED)
AVERAGED              PanelAveragingService run; raw/displayed totals + panel_factor_averages written
  → (panel owner submits)
SUBMITTED             approval_request opened on the PANEL, single CEO step
  → (CEO approves)             → APPROVED  (grade assigned from averaged score)
  → (CEO requests changes)     → AWAITING_EVALUATIONS (reopen; see §5 reversibility)
APPROVED → LOCKED → ARCHIVED   (terminal chain, same as evaluations)
```

"All evaluators complete" = **every non-WITHDRAWN `panel_assignment` has `assignment_status = COMPLETED`**, where COMPLETED means that evaluator's own `evaluations` row reached `COMPLETE` (existing `EvaluationCompletenessChecker` decides COMPLETE vs INCOMPLETE per evaluator — **no new completeness logic**). When the last assignment flips to COMPLETED, the app transitions the panel `AWAITING_EVALUATIONS → AVERAGED` and runs averaging.

Important reuse: each evaluator still individually goes DRAFT→…→COMPLETE on their own `evaluations` row. We do **not** add a parallel completeness engine. The panel only watches the aggregate of assignment statuses.

---

## 5. CEO approval — reuse the existing approval-request workflow (no new engine)

- On panel `AVERAGED → SUBMITTED`, call the existing `CreateApprovalRequestUseCase.createSystem(...)` exactly as `SubmitEvaluationUseCase` does today, but with:
  - `entityType = EVALUATION_PANEL` (new enum value + new value in the `chk_approval_requests_entity_type` CHECK — a small, additive, reversible CHECK change),
  - `entityId = panel.id`,
  - a **single step** requiring a new permission `EVALUATION_PANEL_APPROVE` (the "CEO approves" gate), seeded only to the CEO-equivalent role(s).
- The approval FSM, steps, decisions, audit, RLS, and the inbox are 100% reused. `ApprovalDecisionMaker` is untouched.
- A thin `ApprovePanelUseCase` (analogue of `ApproveEvaluationUseCase`) listens for / is invoked on the panel approval and does panel `SUBMITTED → APPROVED` + `EvaluationGradeAssignmentService` on the averaged score. (Wiring approval-decision → panel-status can reuse the same pattern the evaluation approve path uses; if today that coupling is manual, keep it manual for the panel to avoid inventing an event bus.)
- **Multi-step option, free of charge:** because `approval_steps` already supports `step_order 1..N`, a future "department director sign-off then CEO" chain needs only extra step rows — no schema change. MVP = single CEO step.
- `CHANGES_REQUESTED` from the CEO reopens the panel to `AWAITING_EVALUATIONS`; per-evaluator rows are reverted from COMPLETE only if a re-score is needed (app decision), keeping individual immutability intact for untouched evaluators.

---

## 6. SAFE migration path — what happens to current uniqueness + existing prod rows

The riskiest part. Phased, each step reversible. New changelogs slot in after `033` (next is `034…`), consistent with `db.changelog-tenant.yaml` ordering and re-applied per tenant by `TenantSchemaProvisioner` (`mode-shared` context).

**Step A — additive create (no impact on existing rows):**
- `034-create-evaluation-panels.yaml`: create `evaluation_panels`, `panel_assignments`, `panel_factor_averages` with FKs/checks/indexes (incl. `uq_panels_active_per_position_version`).
- `035-...`: add **nullable** `panel_id UUID` + nullable `evaluator_role VARCHAR(40)` to `evaluations`, with FK `fk_evaluations_panel` ON DELETE RESTRICT. Nullable = existing rows untouched, no rewrite, no lock-heavy backfill required to be online. Add `idx_evaluations_panel (tenant_id, panel_id)`.

**Step B — backfill existing single evaluations into panels (data migration, idempotent):**
- For every existing active (`status <> ARCHIVED`) `evaluations` row: create one `evaluation_panels` row with the **same** `(tenant_id, project_id, position_id, methodology_version_id)`, `min_evaluators = 1` (so legacy single-evaluator cases stay valid and never get blocked by the min-3 roster rule), and `status` mapped from the evaluation status:
  - evaluation `DRAFT/INCOMPLETE` → panel `COLLECTING`
  - `COMPLETE` → panel `AWAITING_EVALUATIONS`
  - `SUBMITTED` → panel `SUBMITTED`
  - `APPROVED` → panel `APPROVED` (copy raw/displayed totals + grade fields up to the panel; write `panel_factor_averages` = that evaluation's own per-factor scores, `evaluator_count = 1`)
  - `LOCKED/ARCHIVED` → same.
- Set the evaluation's `panel_id` to the new panel; create one `panel_assignments` row (`evaluator_role = ADDITIONAL` for legacy, `assignment_status` matching the evaluation state, `evaluation_id` = the row).
- Backfill runs as a SQL changeset that is **safe to re-run** (guard with `WHERE panel_id IS NULL`). Written so it works whether one or many tenants exist (it keys on `tenant_id` from the rows themselves).

**Step C — transfer the uniqueness constraint (the one ordering-sensitive moment):**
- In the SAME changeset, AFTER backfill: `DROP INDEX uq_evaluations_active_per_position_version;` then create the relaxed evaluation uniqueness **`uq_evaluations_active_per_panel_evaluator` UNIQUE `(tenant_id, panel_id, evaluator_user_id) WHERE status <> 'ARCHIVED'`**. The position/version uniqueness now lives only on `evaluation_panels`. Order matters: panels (with their unique index) must be populated before the old index is dropped, so there is never a window where duplicates can be created.
- Rollback of this changeset re-creates the old `evaluations` index and drops the new one (reverse order). Because legacy panels are 1:1 with their evaluation, re-creating the old index cannot fail on legacy data.

**Step D — app-layer cutover (no schema):**
- `CreateEvaluationUseCase`'s "already exists" guard moves up: the **panel** create enforces one-active-panel; evaluation create now enforces one-active-evaluation-per-(panel, evaluator). Old prod rows already satisfy this (one evaluator each). `BulkCreate`'s `ALREADY_EXISTS` semantics are preserved at the panel level.

**Step E — make `panel_id` NOT NULL (deferred, optional, separate release):**
- Only after Step B is confirmed complete on prod (validation query: `SELECT count(*) FROM evaluations WHERE panel_id IS NULL AND status <> 'ARCHIVED'` = 0). Then `036-...` does the safe 4-step (add nullable → backfill → validate → set not null) — here only "validate + set not null" remain. Keep this in its own changelog so it is independently reversible and never blocks Step A–D.

This sequencing means: **at every committed step the DB is in a consistent, queryable state, and any step can be rolled back via its own Liquibase `rollback` block.**

---

## 7. Backward compatibility for existing single-evaluation data

- Legacy evaluations keep working unchanged through their own FSM, scores, triggers, calibration, and grade assignment. They simply gain a 1-evaluator panel wrapper.
- `min_evaluators = 1` on legacy panels means the min-3 roster rule never retroactively invalidates historical or in-flight single-evaluator work. New panels created via the new flow default `min_evaluators = 3`.
- Existing read endpoints (`EvaluationQueries.list`, K-sheet `listByFactor`, `findById`) continue to return per-evaluator rows — the K-sheet remains a per-evaluator scoring grid. The **panel** gets its own read surface (panel detail = roster + per-evaluator completion + averaged totals + per-factor averages). No breaking change to `EvaluationResponse` / `EvaluationByFactorRow`; add a new `PanelResponse` (snake_case wire, `PageResponse{items}` for any list — consistent with existing contract).
- Approval inbox: existing evaluation approval requests stay valid; new panel requests appear with `entity_type = EVALUATION_PANEL`. (Note for Item 1/2 owners: the inbox label resolver must learn `EVALUATION_PANEL` → position title + methodology name, same resolution the evaluation label uses.)

---

## 8. RLS, indexes, audit, triggers

- **RLS:** all three new tables (`evaluation_panels`, `panel_assignments`, `panel_factor_averages`) carry `tenant_id NOT NULL` and get the standard `ENABLE + FORCE ROW LEVEL SECURITY` + `tenant_isolation_*` policy `USING/WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid)` in a new `037-rls-panels.yaml`, in lock-step with the NULLIF/blank-safe pattern of `030/031`. No table without `tenant_id`. No cross-tenant mixing of any panel, assignment, average, or averaged result.
- **Indexes:** every index is `tenant_id`-prefixed (listed per table above). Inbox/my-assignments query covered by `idx_panel_assignments_evaluator`. Completion check (`all assignments COMPLETED`) covered by `idx_panel_assignments_panel`.
- **Immutability triggers:** add a panel-status trigger mirroring `trg_evaluation_status_immutability` (APPROVED cannot regress to SUBMITTED; LOCKED→ARCHIVED only; ARCHIVED terminal). Add a guard so `panel_factor_averages` cannot be modified once the panel is `APPROVED/LOCKED/ARCHIVED` (analogue of `trg_evaluation_score_lock`).
- **Audit:** new audit actions `PANEL_CREATED`, `PANEL_ASSIGNMENT_ADDED/REMOVED`, `PANEL_AVERAGED`, `PANEL_SUBMITTED`, `PANEL_APPROVED` via the existing `AuditService` (append-only `tenant_audit_logs`). No salary/sensitive fields involved.
- **Permissions seed:** `038-seed-panel-permissions.yaml` adds `EVALUATION_PANEL_MANAGE` (build roster / submit) and `EVALUATION_PANEL_APPROVE` (CEO), mapped only to appropriate roles (CEO role gets approve; HR director / PM get manage). Mirrors `016-seed-evaluation-permissions.yaml`.

---

## 9. Reversibility summary

| Change | Rollback |
|---|---|
| Create 3 tables (034) | `dropTable` (their own rollback blocks) |
| Add nullable `panel_id`/`evaluator_role` to evaluations (035) | `dropColumn` |
| Backfill + swap unique index (035/Step C) | re-create old `uq_evaluations_active_per_position_version`, drop new index, leave panels in place (harmless) |
| RLS / triggers / permissions (037/038) | drop policies, drop triggers/functions, delete permission rows (mirrors 016/030 rollbacks) |
| `panel_id` NOT NULL (036, optional) | `dropNotNullConstraint` |

Because the new tables are additive and the only mutation to an existing table is **nullable columns + one index swap performed after data is in place**, a full rollback returns the schema to today's behavior with no data loss (legacy evaluation rows are never deleted or rewritten).

---

## 10. Open product questions for the owner to ratify

1. **Averaging method:** plain arithmetic mean of evaluator totals (proposed), or weighted by role (e.g. external expert weighted differently)? Schema supports weights later via an optional `weight NUMERIC(9,4)` on `panel_assignments` — say the word and I add it now while it is cheap.
2. **Min-3 enforcement point:** block starting evaluation until 3 assigned (proposed) vs allow start but block submit-to-CEO until 3 completed. Proposed = block start (clearer).
3. **Required roles:** is the trio (HR director, department/direction director, external expert) mandatory for every panel, or only a recommended default that a project can relax? Proposed default = mandatory trio, project-overridable later.
4. **Calibration after averaging:** if the CEO requests changes, do all evaluators re-score, or may a PM calibrate the averaged per-factor values directly? Proposed = reopen to evaluators (preserves "each evaluator scores independently"); direct calibration on averages is out of scope for v1.
5. **Single-step now, chain later:** MVP = one CEO step. Confirm no intermediate sign-off (e.g. project manager) is required before the CEO in v1.

---

## Қисқа хулоса (ўзбекча, кирилл)

**Ҳозирги ҳолат:** тизим ҳар бир (лавозим, методология версияси) учун фақат БИТТА баҳолашга рухсат беради — `evaluations` жадвалидаги `uq_evaluations_active_per_position_version` уникал индекси ва `CreateEvaluationUseCase`нинг "ALREADY_EXISTS" текшируви буни мажбур қилади. Демак, кўп баҳоловчи (камида 3 та: HR директор, бўлим директори, ташқи эксперт) ва уларнинг ўртача баҳоси — **ҳисобга олинмаган**.

**Таклиф (минимал, қайтарилувчан):**
1. Янги **`evaluation_panels`** жадвали — (лавозим, методология версияси) учун "панель" бирлиги. Эски уникал қоида шу ерга кўчади ("бир актив панель").
2. **`panel_assignments`** — ҳар бир баҳоловчи + унинг роли (минимум 3 — қоида ДАСТУР қатламида, чунки DB кросс-қатор санашни ишончли қила олмайди).
3. Мавжуд **`evaluations`** жадвали деярли ўзгармайди: ҳар бир баҳоловчи ўз мустақил баҳолашини олиб боради; унга фақат `panel_id` (NULL бўлиши мумкин) қўшилади ва уникал қоида "панель + баҳоловчи"га юмшатилади.
4. **`panel_factor_averages`** + панелдаги `raw_total_score` — ўртача натижа **сақланади** (computed эмас), чунки тасдиқланган балл қайта ишлаб чиқарилувчи ва ўзгармас бўлиши шарт (§15). Ўртачани ҳисоблаш учун янги соф `PanelAveragingService` — мавжуд `EvaluationScoringEngine` дубликат қилинмайди (4-банд).
5. **Тугаш қоидаси:** барча тайинланган баҳоловчилар ўз баҳолашини COMPLETE қилганда панель `AWAITING_EVALUATIONS → AVERAGED` га ўтади ва ўртача ҳисобланади.
6. **CEO тасдиғи:** мавжуд approval-request оқими қайта ишлатилади — `entity_type = EVALUATION_PANEL`, битта қадам, янги `EVALUATION_PANEL_APPROVE` рухсати фақат CEO ролига берилади. `ApprovalDecisionMaker` ўзгармайди.
7. **Хавфсиз миграция:** аввал янги жадваллар (additive) → `evaluations`га NULL устунлар → мавжуд қаторларни панелга backfill (`min_evaluators=1`, идемпотент) → ШУНДАН КЕЙИН эски индексни алмаштириш → ихтиёрий равишда кейин `panel_id`ни NOT NULL қилиш. Ҳар қадам алоҳида rollback билан қайтарилади; эски prod қаторлари ўчирилмайди/қайта ёзилмайди.
8. **RLS/индекс/аудит:** учала янги жадвалда `tenant_id NOT NULL` + FORCE RLS, барча индекслар `tenant_id` билан бошланади, immutability триггерлари `evaluations`никига ўхшаш.

**Қарор:** бу ишлаб чиқариш маълумот моделини ўзгартиргани учун — фақат ТАКЛИФ. Эга 10-бўлимдаги 5 та савол бўйича тасдиқ берсин, кейин амалга оширамиз.

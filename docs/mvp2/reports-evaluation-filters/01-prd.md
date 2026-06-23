# PRD — EVALUATION_SUMMARY report filters (methodology, date, evaluator)

Status: DRAFT for owner sign-off
Author: hr-product-owner
Date: 2026-06-23
Scope: Reports module ("Hisobotlar"), `ReportType.EVALUATION_SUMMARY` (and any
future evaluation-bearing report types — none exist today besides this one).

## 1. Current behaviour (evidence)

### 1.1 Backend — request → generation pipeline

- `RequestReportRequest` (`backend/src/main/java/uz/hrlab/grading/reporting/api/RequestReportRequest.java:9-14`)
  accepts exactly 4 fields: `reportType`, `format`, `projectId`, `filterParams`
  (`filterParams` is a bare `String`, NOT a structured object).
- `ReportController.request` (`.../reporting/api/ReportController.java:65-71`) —
  `POST /api/v1/reports/request`, gated `@PreAuthorize("hasAuthority('REPORT_CREATE')")`.
  `tenantId` is never accepted from the client (controller javadoc line 38-39) —
  sourced from `TenantContext`.
- `RequestReportUseCase.request` (`.../reporting/application/RequestReportUseCase.java:47-95`)
  persists the `ReportJpaEntity` row with `filterParams` stored verbatim as TEXT
  and dispatches the async worker `ReportGenerationJob.generate` after commit.
- `ReportGenerationJob.generate` (`.../reporting/infrastructure/ReportGenerationJob.java:106-119`)
  builds `ReportGenerationContext` carrying `filterParams()` through unchanged
  (`ReportGenerationContext.java:24,50,73`).
- `AbstractReportTemplate.render` (`.../template/AbstractReportTemplate.java:29-37`)
  dispatches to `loadData(ctx)` then a per-format renderer.
- **`EvaluationSummaryReport.loadData`** (`.../template/impl/EvaluationSummaryReport.java:52-55`)
  calls `data.loadEvaluations(ctx.tenantId(), ctx.projectId(), ctx.locale())` —
  **`ctx.filterParams()` is never read.** The opaque string is plumbed all the
  way to the template and then silently dropped. **This is the gap the owner's
  request closes.**
- `ReportDataPort.loadEvaluations` (`.../template/ReportDataPort.java:38-44`)
  signature is `(UUID tenantId, UUID projectId, String locale)` — **no filter
  parameters of any kind exist on the port today.**
- `DefaultReportDataPort.loadEvaluations` (`.../infrastructure/DefaultReportDataPort.java:305-428`)
  loads **all** evaluations of the project via
  `evaluations.findAllByTenantIdAndProjectId(tenantId, projectId, PageRequest.of(0, MAX_EVALUATIONS=1000))`
  (line 311-312) — single page, hard cap 1000, no status/date/evaluator/methodology
  narrowing whatsoever.
- Report types today (`ReportType.java:11-16`): `GRADE_DISTRIBUTION`,
  `POSITION_CATALOG`, `EVALUATION_SUMMARY`, `METHODOLOGY_SPEC`, `AUDIT_SUMMARY`,
  `EXECUTIVE_SUMMARY`. Only `EVALUATION_SUMMARY` covers evaluation data.
- Async lifecycle: `REQUESTED → QUEUED → GENERATING → GENERATED/FAILED` (worker
  javadoc `ReportGenerationJob.java:33`), with bounded retry → `DEAD_LETTER`.
  Formats: PDF / DOCX / XLSX (`ReportFormat`), rendered per
  `AbstractReportTemplate.render` (line 29-37).
- Salary: `EvaluationSummaryReport` javadoc (lines 33-36) explicitly states
  salary fields are NOT part of this report; `containsSalaryData` is
  hardcoded `false` at request time (`RequestReportUseCase.java:64`).
- DB: `reports.filter_params` is `TEXT` (`028-create-reports.yaml:29`) — already
  schema-flexible enough to hold a JSON-encoded structured filter; **no
  migration required** to introduce structured filters.
- Permissions: `REPORT_READ`, `REPORT_CREATE`, `REPORT_EXPORT`
  (`PermissionCodes.java:107-109`); audit actions `REPORT_REQUESTED`,
  `REPORT_GENERATING`, `REPORT_GENERATED`, `REPORT_FAILED`, `REPORT_DEAD_LETTER`,
  `REPORT_DOWNLOADED`, `REPORT_EXPIRED`, `REPORT_CANCELLED`, `REPORT_EXPORTED`
  (`AuditAction.java:284,365-376`) — already cover the request lifecycle; no
  new audit action needed for "filtered" requests (the filter values should be
  captured in the existing `REPORT_REQUESTED` audit `reason`/structured field,
  see §4).

### 1.2 Frontend — request dialog → API client

- `ReportRequestDialog.tsx` (lines 1-13, 145-155) renders ONE free-form
  `<textarea>` bound to `filterParams` — there is **no structured filter UI** at
  all today; the operator would have to know/type a JSON or key=value string
  with no validation, no pickers, no labels.
- `reportSchemas.ts` (`RequestReportSchema`, lines 15-28) validates
  `filterParams` only as `z.string().optional().nullable()` — no shape
  validation.
- `reportApi.ts` `requestReport` (lines 73-85) sends
  `{ report_type, format, project_id, filter_params }` in snake_case (per the
  cited fix commit `a45db24 "send report-request body in snake_case"`) —
  confirms the wire contract is snake_case end-to-end and `filter_params`
  is sent as a single string (currently raw passthrough of the textarea).
- `types.ts` defines `ReportRequestPayload` (consumed above) — filterParams
  stays `string | null`.

### 1.3 Existing reusable filter/predicate infrastructure (evaluation/panel side)

- `EvaluationRepository.findInDepartments` (`.../infrastructure/EvaluationRepository.java:258-277`)
  — JPQL with **exactly** the null-safe optional-predicate pattern needed:
  `(:projectId IS NULL OR e.projectId = :projectId)`,
  `(:evaluatorUserId IS NULL OR e.evaluatorUserId = :evaluatorUserId)`,
  `(:status IS NULL OR e.status = :status)`. This is the canonical pattern to
  extend, NOT reinvent.
- `EvaluationRepository.findForFactorGrid` family (lines 193-211, 222-242,
  293-314, 323-346) — same null-safe-predicate idiom, additionally scoped by
  `methodologyVersionId` (exact-match, not null-safe there because the K-sheet
  is always single-version) and `departmentId`.
- `EvaluationQueries.list` (`.../application/EvaluationQueries.java:128-165`)
  already accepts `projectId`, `positionId`, `evaluatorUserId`, `status` as
  query parameters and already branches on department scope
  (`DepartmentScopeFilter`) — the evaluator and methodology dimensions are
  proven query primitives in this codebase, just not yet exposed through the
  report port.
- `EvaluationJpaEntity` date columns available for a date filter:
  `submittedAt`, `approvedAt`, `lockedAt`, `archivedAt` (plus inherited
  `createdAt`/`updatedAt`) — confirmed via
  `backend/src/main/java/uz/hrlab/grading/evaluation/infrastructure/EvaluationJpaEntity.java:70,76,82,88`.
- `DefaultReportDataPort.loadAuditEvents` (`.../infrastructure/DefaultReportDataPort.java:269-303`)
  already accepts an `OffsetDateTime from, OffsetDateTime to` range — the
  precedent for a date-range parameter shape on `ReportDataPort` already
  exists in the SAME port interface (AUDIT_SUMMARY report). Mirror it, don't
  invent a new date-range shape.
- `ActorNameResolver` (`.../access/application/ActorNameResolver.java`) and its
  thin per-module delegate `MethodologyActorNameResolver`
  (`.../methodology/application/MethodologyActorNameResolver.java`) are THE
  single source of truth for user-id → display-name resolution, explicitly
  documented "Item 4 — NO DUPLICATION" (ActorNameResolver.java:19-24).
  `DefaultReportDataPort` currently does its OWN ad-hoc batch actor resolution
  (`resolveActorDisplays`, lines 548-558) duplicating `ActorNameResolver`
  rather than delegating to it — flagged below as a **pre-existing**
  duplication the new evaluator-filter work should NOT compound further (reuse
  `ActorNameResolver.resolveAll`, do not add a third ad-hoc resolver).
- Methodology label resolution: `resolveMethodologyName`
  (`DefaultReportDataPort.java:248-256`) — the existing, single place that
  turns a `methodologyVersionId` into "Name (vN)"; reuse for filter-chip /
  report-header labelling, don't re-derive.

### 1.4 Existing reusable frontend pickers

- **Methodology selector**: no dedicated `<MethodologySelector>` component
  exists as a standalone reusable widget; the established pattern is a native
  `<select>` bound directly to `useMethodologies(projectId)` /
  `useMethodologyVersions(methodologyId)` hooks
  (`frontend/src/features/methodology/hooks/*.ts:55,108`), as used in
  `OpenPanelDialog.tsx` (lines 582-596) which lists
  `activeMethodologies.map(m => <option value={m.active_version_id}>...)`.
  **Reuse anchor = the hooks, not a specific JSX component** (there is no
  duplicated component to avoid; the duplication risk is re-fetching
  methodologies with a hand-rolled query instead of `useMethodologies`).
- **Date range picker**: `AuditFilterBar.tsx` (lines 91-114) is the existing,
  shipped from/to date-range pattern — two native `<input type="date">`
  controlled inputs, `value.slice(0,10)`, `onChange` emitting
  `{ from?: string, to?: string }` into a query-object the parent owns. This
  is the literal reuse target for the EVALUATION_SUMMARY date filter — same
  shape, same control, same `data-testid` convention
  (`audit-filter-from`/`audit-filter-to`).
- **Evaluator / people picker**: `EvaluatorPicker.tsx`
  (`frontend/src/features/evaluation/components/panel/EvaluatorPicker.tsx`)
  wraps `useUsers()` (`@/features/users-access/hooks/useUsers`) into a
  tenant-scoped `<select>` of ACTIVE users. It is currently **single-select**
  (`value: string`, `onChange(userId, userName)`). The report filter needs
  **multi-select** — see Reuse Map §3 for the exact extension strategy (do not
  fork a second component; add a `mode="multi"` variant exactly like
  `RolePicker` already supports `mode="multi"` for `AddMembershipDialog.tsx`,
  see `RolePicker` usage at `AddMembershipDialog.tsx:147-152`).

## 2. Personas

- **HR Director / Company Admin** (client-side) — runs the EVALUATION_SUMMARY
  report at the end of a methodology cycle to audit/export scores for a given
  methodology version, period, or assessor.
- **Evaluation Committee Member** — has `REPORT_READ`, not necessarily
  `REPORT_CREATE`; may only need filtered self-scoped data (their own
  evaluator-filtered view), subject to existing department-scope and
  panel-bias rules.
- **HRLab Consultant / Project Manager** — runs cross-evaluator quality checks
  ("did Evaluator X score consistently across the period?").
- **External Auditor** (`REPORT_READ` + `REPORT_EXPORT`, no salary) — pulls a
  date-bounded evidence trail for a specific methodology version.

## 3. Business value

Today the operator cannot scope an EVALUATION_SUMMARY export without manually
post-filtering a 1000-row dump (or typing opaque text into a textarea with NO
effect on the actual report content, since `filterParams` is currently
ignored by `loadEvaluations`). Structured filters turn the report into a
real audit/export tool — required for MVP 2 consulting-delivery value
(client checkpoint reviews, methodology rollout sign-off, evaluator QA) and
removes a UI control (the free-text filterParams box) that currently lies to
the user about having any effect.

## 4. Acceptance criteria

### 4.1 Methodology filter

**Decision needed (see §6-D1).** Recommended default: **multi-select at
methodology-VERSION granularity**, with the version picker pre-populated and
grouped by parent methodology name (reuses `useMethodologies` +
`useMethodologyVersions`, mirrors the existing `OpenPanelDialog` UX which
already lists `{name} v{number}` per version). Selecting a parent methodology
with no version chosen = "all versions of that methodology" (server-side
expansion).

- US-1: As an HR Director, I want to filter the EVALUATION_SUMMARY report to
  one or more methodology versions, so that a report reflects only the
  scoring round I am auditing (e.g. excludes a superseded pilot version).
  - AC-1.1: Given no methodology filter is selected, When the report is
    requested, Then all methodology versions used in the project's
    evaluations appear (current/legacy behaviour preserved — empty filter =
    no narrowing).
  - AC-1.2: Given one or more `methodology_version_ids` are selected, When the
    report is generated, Then only evaluations whose
    `methodology_version_id IN (selected)` appear, and the per-row factor
    columns reflect ONLY the factor set of the selected version(s) (XLSX
    column union across selected versions, consistent with current
    multi-version handling at `DefaultReportDataPort.java:318-346`).
  - AC-1.3: Given a selected methodology version has been ARCHIVED at its
    container level, When the report is generated, Then it is still included
    (reports are an audit/history tool — unlike `listMine`'s ACTIVE-only
    inbox filter, EVALUATION_SUMMARY must NOT silently drop archived-container
    history; this mirrors the existing distinction documented at
    `EvaluationRepository.java:51-55` between `listMine` and the manager
    list).
  - AC-1.4: Given the caller selects a methodology that has zero matching
    evaluations in the project, When the report is generated, Then the report
    renders with zero rows and the existing `empty.evaluations` label
    (`EvaluationSummaryReport.java:82`), not an error.

### 4.2 Date filter

**Decision needed (see §6-D2).** Recommended default: filter on
**`submitted_at`** (the date the evaluator submitted their sheet) as the
primary semantic, because it is the closest analogue to "when was this
evaluation done" and is populated earlier in the lifecycle than `approved_at`
(many evaluations report-worthy before approval, e.g. mid-cycle reviews).
`approved_at` is a `D2/Open Decision` alternative — see §6.

- US-2: As an Evaluation Committee Member, I want to filter the report to a
  submitted-date range, so that I can produce a snapshot for a specific
  reporting period (e.g. Q2).
  - AC-2.1: Given `date_from` and `date_to` are both empty, When the report is
    requested, Then no date narrowing is applied (all evaluations regardless
    of submission date appear) — mirrors `loadAuditEvents`'s existing
    `from`/`to` optional-range contract (`DefaultReportDataPort.java:269-275`).
  - AC-2.2: Given `date_from = 2026-04-01` and `date_to = 2026-06-30`, When
    the report is generated, Then only evaluations with
    `submitted_at >= date_from 00:00:00Z` AND
    `submitted_at <= date_to 23:59:59.999999Z` appear — bounds INCLUSIVE on
    both ends, using `OffsetDateTime` / UTC normalization consistent with the
    existing `TIMESTAMPTZ` convention (CLAUDE.md tech-stack rule: "Timestamps:
    TIMESTAMPTZ/OffsetDateTime"). Day-only `date` inputs from the picker are
    expanded server-side to a full-day inclusive range in UTC (or
    tenant-configured timezone if one exists — confirm none exists today; if
    not, UTC).
  - AC-2.3: Given an evaluation has never been submitted
    (`submitted_at IS NULL`, e.g. still DRAFT/INCOMPLETE), When a date filter
    is active, Then that evaluation is EXCLUDED (a null submission date never
    matches a bounded range) — but remains included when no date filter is
    applied (AC-2.1).
  - AC-2.4: Given `date_from > date_to`, When the report is requested, Then
    the request is rejected with a 400 validation error
    (`REPORT_FILTER_INVALID_DATE_RANGE`) — fail fast, do not silently swap.

### 4.3 Evaluator filter

**Decision needed (see §6-D3).** Recommended default: multi-select of
evaluator USER ids; "matches" = the evaluation's OWN
`evaluator_user_id IN (selected)` — i.e. filter at the **per-evaluator-sheet**
granularity, the same granularity `EvaluationSummaryReport`'s `EvaluationRow`
already operates at (one row = one evaluation = one evaluator's sheet for one
position, per the panel model where `panel_assignments.evaluator_user_id`
maps 1:1 to one `Evaluation` per seat — confirmed via
`EvaluationRepository.findAllByTenantIdAndPanelId` / panel-seat-per-evaluation
contract, `PanelAssignmentRepository.java:33-35`). This does NOT require a
"panel-level" aggregate filter; the report does not currently render
panel-averaged rows at all (it renders raw per-evaluation rows), so evaluator
filtering is a direct column predicate, no new join.

- US-3: As an HRLab Consultant, I want to filter the EVALUATION_SUMMARY report
  to one or more evaluators, so that I can review or export only the sheets a
  specific assessor (or assessor group) produced.
  - AC-3.1: Given no evaluator is selected, When the report is requested,
    Then all evaluators' sheets appear (empty = no narrowing).
  - AC-3.2: Given one or more evaluator user ids are selected, When the
    report is generated, Then only `EvaluationRow`s whose source evaluation's
    `evaluator_user_id IN (selected)` appear.
  - AC-3.3: Given the requester does NOT hold an oversight/result-view
    permission and panel bias rules would otherwise blind them to peer sheets
    (`PanelBiasGuard`), When they attempt to select another evaluator's id in
    the filter picker, Then [Open Decision §6-D4] — either (a) the picker is
    restricted server-side to evaluators the requester is authorized to view
    results for, or (b) `REPORT_CREATE`/`REPORT_EXPORT` is treated as an
    oversight-level permission that always bypasses panel-bias (current
    `EvaluationSummaryReport` javadoc already states the report "is exposed to
    evaluators" with no special bias gating — recommend explicit decision
    before backend-engineer scopes the predicate).
  - AC-3.4: Given a selected evaluator id does not belong to an ACTIVE
    membership in the active tenant (stale/foreign id), When the report is
    generated, Then it contributes zero rows (no error, no cross-tenant leak)
    — consistent with `ActorNameResolver`'s fail-soft contract.

### 4.4 Combined semantics

- AC-4.1: Given two or more filters are set (methodology + date + evaluator),
  When the report is generated, Then all are combined with AND — a row must
  satisfy every active filter, mirroring the existing
  `(:param IS NULL OR predicate)` chained-AND idiom already used in
  `EvaluationRepository.findInDepartments`.
- AC-4.2: Given all three filters are empty/unset, When the report is
  generated, Then behaviour is IDENTICAL to today's unfiltered
  `loadEvaluations` (no regression).
- AC-4.3: The report header/meta block must echo the active filters in
  human-readable, localized form (e.g. "Methodology: Classic 8-Factor (v3);
  Period: 01.04.2026–30.06.2026; Evaluators: Aliyev A., Karimova D.") so a
  downloaded PDF/DOCX/XLSX is self-describing without the request UI — extend
  the existing `PdfBuilder.metaLine` / `DocxBuilder.metaLine` calls already
  used for `meta.project` / `meta.methodology`
  (`EvaluationSummaryReport.java:74-80, 108-117`).

### 4.5 Permission / tenant / salary / localization NFRs

- NFR-1 (permission): filter VALUES never widen what the requester is allowed
  to see. `REPORT_CREATE` is required to request; the use case re-checks
  permission server-side (existing pattern,
  `RequestReportUseCase.java:53-55`) — unchanged. No new permission code is
  needed for filtering itself; AC-3.3 may require a NEW fine-grained rule
  (decision pending, §6-D4), not a new top-level permission.
  Filters are NEVER a backdoor to bypass `DepartmentScopeFilter` or
  `PanelBiasGuard` — the data port must apply the SAME scope/bias gates the
  live evaluation queries apply, before or in addition to the new filter
  predicates (see §6-D4).
- NFR-2 (tenant isolation): every new predicate is tenant_id-scoped via the
  existing `(UUID tenantId, ...)` finder signatures; the methodology-version
  ids, evaluator user ids, and date bounds arriving from the client are NEVER
  trusted as tenant-scoped by themselves — they are passed into queries that
  ALREADY pin `tenant_id` (e.g. extending `findInDepartments`-style queries),
  so a cross-tenant id in the filter set simply yields zero matching rows,
  never an error that reveals existence.
- NFR-3 (salary masking): EVALUATION_SUMMARY today carries zero salary fields
  by design (`EvaluationSummaryReport.java:33-36`); filters must NOT be
  allowed to indirectly leak salary by, e.g., letting `filterParams` become a
  generic free-text passthrough to a different, salary-bearing query path.
  The structured filter DTO (methodology version ids, date range, evaluator
  ids) is a closed shape — no arbitrary filter expressions — eliminating that
  injection class entirely.
- NFR-4 (localization): filter labels (methodology name, evaluator display
  name, date range) rendered into the report meta block AND into the
  request-form UI must go through the existing i18n paths
  (`ReportLabels`, `i18n()` helper in `DefaultReportDataPort.java:262-267`,
  and `react-i18next` `t()` on the frontend) — no hardcoded Russian/English
  strings. 4-locale coverage (`ru-RU`, `uz-Cyrl-UZ`, `uz-Latn-UZ`, `en-US`) is
  required for every new label key (filter section headers, "all
  methodologies" / "all evaluators" placeholder text, validation messages).
- NFR-5 (audit): the existing `REPORT_REQUESTED` audit event
  (`RequestReportUseCase.java:69-77`) must carry the applied filter summary in
  its `reason` field (or a new structured field) so a forensic reviewer can
  see WHAT was filtered without needing salary/PII access — extend the
  existing `reason` string composition (`"type=" + type + " format=" + format`)
  to append filter cardinality, e.g.
  `"... filters={methodologyVersions:2,evaluators:1,dateRange:true}"` — do NOT
  log raw evaluator names/UUIDs in plaintext audit reason if that violates
  existing audit PII conventions (confirm with security-engineer; default to
  counts, not raw ids, in the human-readable reason).

## 5. Edge cases

- EC-1: Project has no evaluations at all → empty report regardless of
  filters (existing `empty.evaluations` path, unaffected).
- EC-2: Selected methodology version belongs to a DIFFERENT project than
  `projectId` → zero rows (version ids are not validated against the
  project's own version set today by `loadEvaluations`; the new predicate
  must implicitly exclude foreign-project versions because the base query is
  already `tenantId + projectId` scoped — confirm no version-id leak is
  possible by deriving "available versions" for the picker FROM the
  project's actual evaluations, not from a tenant-wide methodology list).
- EC-3: Evaluator filter selects a user with zero evaluations in this project
  → zero rows, not an error.
- EC-4: Date range with only `date_from` set (open-ended) → narrows to
  `submitted_at >= date_from`, no upper bound (mirrors `loadAuditEvents`
  optional `to`).
- EC-5: 1000-row `MAX_EVALUATIONS` cap (`DefaultReportDataPort.java:78`)
  still applies AFTER filtering, or BEFORE? **Must apply BEFORE is wrong** —
  filtering should happen in the SQL/JPQL predicate (not in-memory after the
  page load), so the 1000-row cap is the cap of the FILTERED result set, not
  the unfiltered one (otherwise a large unfiltered project could silently
  drop matching filtered rows beyond row 1000 of the UNFILTERED set). This is
  a correctness requirement for backend-engineer, not a product decision.

## 6. Open product decisions (owner must confirm; recommended defaults stated)

- **D1 — Methodology filter granularity.** Recommended: multi-select at
  VERSION granularity (selecting a parent methodology with no version = "all
  its versions"). Alternative: methodology-container-only (coarser, simpler
  UI, loses precision for clients who keep multiple active versions).
- **D2 — Which date field.** Recommended: `submitted_at`. Alternatives:
  `approved_at` (only captures approved/locked evaluations — would silently
  exclude in-progress work, probably wrong for most use cases),
  or a toggle letting the user CHOOSE which date dimension to filter on
  (more flexible, more UI complexity — defer to MVP 3 if requested).
- **D3 — Evaluator filter UX**: recommend extending `EvaluatorPicker` with a
  `mode="multi"` prop (mirrors `RolePicker`'s existing multi-mode) rather than
  building a new component. Confirm acceptable.
- **D4 — Evaluator filter + panel-bias interaction (AC-3.3).** Recommend:
  `REPORT_CREATE`/`REPORT_EXPORT` holders are, by definition in this product,
  HR Director / Consultant / PM-level roles who already hold
  `CAMPAIGN_RESULTS_VIEW`-equivalent oversight in practice — recommend the
  evaluator filter is available WITHOUT additional bias gating for any caller
  who holds `REPORT_CREATE`, with a note to security-engineer to confirm no
  role currently holds `REPORT_CREATE` without also holding result-oversight.
  If that assumption is false, the picker must be scoped server-side to
  evaluators within the caller's visible panel set.
- **D5 — Audit reason granularity** (counts vs. raw ids) — recommended counts
  only, pending security-engineer confirmation per NFR-5.

## 7. Out of scope (this change)

- Filtering any OTHER report type (GRADE_DISTRIBUTION, POSITION_CATALOG,
  METHODOLOGY_SPEC, AUDIT_SUMMARY already has its own date filter,
  EXECUTIVE_SUMMARY) — scoped to EVALUATION_SUMMARY only.
- Panel-AVERAGED row filtering / a new "panel result" report type — out of
  scope; current report is per-evaluation-sheet granularity.
- Salary-bearing evaluation/compensation reports (MVP 3).
- Saved/named filter presets, scheduled reports, or filter sharing across
  users.
- Changing the async job/staging contract — no new job type, no new object
  storage path shape.

## 8. Dependencies

- `useMethodologies` / `useMethodologyVersions` hooks (frontend/methodology
  feature) — already implemented, no change needed.
- `useUsers()` hook (frontend/users-access feature) — already implemented.
- `ActorNameResolver` — already implemented; reuse for evaluator-label
  resolution server-side (replace ad-hoc resolver in
  `DefaultReportDataPort.resolveActorDisplays`, do not duplicate further).

## 9. Definition of Ready

- This PRD's Open Decisions (§6) are resolved by the owner.
- `database-architect` confirms `filter_params TEXT` is sufficient (no schema
  change) OR specifies the alternative (e.g. typed columns) if structured
  querying/reporting on past filter usage is desired later.
- `security-engineer` confirms D4/D5.

## 10. Definition of Done

- All ACs in §4 pass automated tests (unit + integration).
- No new permission/audit gaps versus NFR-1/NFR-5.
- 4-locale label coverage verified.
- QA sign-off using the test pack derived from §4.

# Reuse map + agent dispatch plan — EVALUATION_SUMMARY report filters

Companion to `01-prd.md`. Read that first for ACs/decisions.

## 1. Reuse map (hard rule: NO new query/predicate/picker duplication)

| Filter | Reuse (EXTEND, do not duplicate) | What is genuinely net-new |
|---|---|---|
| Methodology | `EvaluationRepository`'s null-safe optional-predicate idiom (`findInDepartments`, lines 258-277: `(:param IS NULL OR e.field = :param)`). Extend the SAME idiom with `e.methodologyVersionId IN (:methodologyVersionIds)` (collection variant, null/empty = no filter). Methodology NAME resolution reuses `DefaultReportDataPort.resolveMethodologyName` (lines 248-256) — already the single place that turns a version id into "Name (vN)"; call it for the new meta-line, don't re-derive. Frontend: reuse `useMethodologies(projectId)` + `useMethodologyVersions(methodologyId)` hooks (`frontend/src/features/methodology/hooks/*.ts:55,108`) — same data source `OpenPanelDialog.tsx` already uses for its version `<select>`. | A new **multi-select** version of the methodology picker UI (the existing usage in `OpenPanelDialog` is single-select). New JPQL predicate variant for `IN (:list)` instead of `=  :id` (1-line addition to the existing query family, same file). |
| Date | `ReportDataPort.loadAuditEvents(tenantId, projectId, from, to, limit)` (`ReportDataPort.java:34-36`) — the date-range `OffsetDateTime from/to` PARAMETER SHAPE already exists on this exact interface for AUDIT_SUMMARY. Mirror it on `loadEvaluations`'s new signature — do not invent a different shape (e.g. ISO strings, epoch millis). Frontend: reuse `AuditFilterBar.tsx`'s two `<input type="date">` controls (lines 91-114) verbatim as the UI pattern (same `data-testid` convention, same `.slice(0,10)` normalization) — either literally extract a shared `<DateRangeFields>` sub-component from `AuditFilterBar`, or copy the same 24-line JSX block; PREFER extraction (see Net-new). | A small extracted `DateRangeFields` component (props: `from`, `to`, `onChange`) factored OUT of `AuditFilterBar` so report-request-dialog and audit-filter-bar share ONE implementation instead of two copies of the same JSX (this IS the de-duplication the owner's rule demands — today there is exactly one copy; adding a second free-form copy in the report dialog would be the violation). Backend: extend `EvaluationJpaEntity.submittedAt` predicate into the new JPQL (`(:dateFrom IS NULL OR e.submittedAt >= :dateFrom) AND (:dateTo IS NULL OR e.submittedAt <= :dateTo)`). |
| Evaluator | `EvaluationRepository.findInDepartments`'s existing `(:evaluatorUserId IS NULL OR e.evaluatorUserId = :evaluatorUserId)` predicate (line 263) — extend to `IN (:evaluatorUserIds)` collection form, same idiom as the methodology change. Display-name resolution: `ActorNameResolver.resolveAll(tenantId, userIds)` (`access/application/ActorNameResolver.java:86-114`) — the documented "Item 4 — NO DUPLICATION" single source of truth. `DefaultReportDataPort.resolveActorDisplays` (lines 548-558) is a **pre-existing** duplicate of this exact logic for the audit/executive reports; route the NEW evaluator-filter label resolution through `ActorNameResolver` directly (inject it into `DefaultReportDataPort`) rather than adding a THIRD copy, and flag the existing duplicate for a follow-up cleanup ticket (out of scope to fix now, but do not grow it). Frontend: reuse `EvaluatorPicker.tsx` (`features/evaluation/components/panel/EvaluatorPicker.tsx`) — wraps `useUsers()`. | Add a `mode="multi"` prop to `EvaluatorPicker` (mirrors `RolePicker`'s existing `mode="multi"` used by `AddMembershipDialog.tsx:147-152`) — same component, new prop, returns `string[]` instead of `string`. Backend: `IN (:list)` JPQL variant (same pattern as methodology). |
| Combined / request DTO | `RequestReportRequest.filterParams` (currently a raw `String`) is the ONE existing extension point — replace its semantics (not its column: `reports.filter_params` stays `TEXT`, store the structured filter as a JSON string) rather than adding new top-level request fields, so the wire contract (`POST /reports/request`) and the `reports` table both stay unchanged in shape. `ReportGenerationContext.filterParams()` (already plumbed end-to-end, `AbstractReportTemplate` → `loadData(ctx)`) is the ONE place the worker hands the string to the template — extend `EvaluationSummaryReport.loadData` to parse it and pass structured filters into a NEW `loadEvaluations` overload, do not add a parallel context field. | A small `EvaluationReportFilter` record (Java) + matching Zod schema / TS type (frontend) — the structured shape serialized into `filter_params`. This is the one new DTO; everything else is parameter-shape extension of existing methods. |

### Explicit "do NOT duplicate" call-outs

1. Do not write a new `EvaluationReportRepository` or a new "report-specific"
   evaluation query class. `EvaluationRepository` (infrastructure) and
   `EvaluationQueries` (application) already own every predicate this needs;
   `DefaultReportDataPort.loadEvaluations` should call into `EvaluationRepository`
   with an extended finder, exactly as it already does at line 311-312.
2. Do not build a second date-range picker component. Extract
   `DateRangeFields` from `AuditFilterBar` first (or, if time-boxed, the
   report dialog imports/reuses `AuditFilterBar`'s pattern 1:1 with identical
   prop names so a follow-up refactor can merge them trivially).
3. Do not build a second evaluator multi-select. Extend `EvaluatorPicker`
   with `mode="multi"`, not a new `EvaluatorMultiPicker`.
4. Do not introduce a second actor/name resolver in the reporting module.
   `ActorNameResolver.resolveAll` already exists and is documented as the
   single source of truth; inject it where `DefaultReportDataPort` currently
   hand-rolls `resolveActorDisplays`.
5. Do not add a new top-level "structured filter" REST field
   (`methodologyVersionIds`, `evaluatorUserIds`, `dateFrom`, `dateTo` as
   separate JSON body fields) alongside the existing `filterParams` string —
   keep ONE filter carrier (`filter_params`, JSON-encoded) so the wire
   contract for `/reports/request` does not fork into "legacy free-text" vs.
   "new structured" parallel fields. The frontend `filterParams` textarea is
   retired/replaced by the structured picker UI; the wire field name is
   unchanged.

## 2. Agent dispatch plan

### database-architect — NOT required for a new column/index

`reports.filter_params` is already `TEXT` (`028-create-reports.yaml:29`) and
can hold the JSON-encoded structured filter as-is. No new migration is
needed for this feature. **Optional, deferred**: if product later wants to
query/report on "what filters are commonly used" analytically, that would
need a follow-up migration (e.g. typed `filter_methodology_version_ids UUID[]`
column) — explicitly OUT OF SCOPE now; flag to database-architect only if the
owner asks for filter-usage analytics later.

### backend-engineer

1. Define `EvaluationReportFilter` (record: `List<UUID> methodologyVersionIds`,
   `OffsetDateTime dateFrom`, `OffsetDateTime dateTo`, `List<UUID> evaluatorUserIds`)
   in `reporting/application/template` or a new small `reporting/domain`
   record file; parse it from `ReportGenerationContext.filterParams()` (JSON)
   inside `EvaluationSummaryReport.loadData` (`.../template/impl/EvaluationSummaryReport.java:52-55`).
2. Extend `ReportDataPort.loadEvaluations` signature to accept the filter
   record (`ReportDataPort.java:38-44`) and update the single implementation
   `DefaultReportDataPort.loadEvaluations` (`.../infrastructure/DefaultReportDataPort.java:305-428`)
   to pass it into an EXTENDED `EvaluationRepository` finder (new `IN (:list)` +
   date-range JPQL variant of `findInDepartments`,
   `EvaluationRepository.java:258-277`) — apply filters in the SQL/JPQL `WHERE`,
   not in-memory after `findAllByTenantIdAndProjectId` (correctness requirement,
   PRD §5 EC-5).
3. Replace `DefaultReportDataPort.resolveActorDisplays` (lines 548-558) call
   sites relevant to the new evaluator-filter meta-line with
   `ActorNameResolver.resolveAll` (inject `ActorNameResolver` bean) — do not
   add a third resolver.
4. Add request-time validation (`date_from <= date_to`, methodology version
   ids belong to the same tenant/project) in `RequestReportUseCase.request`
   or a small validator, returning `REPORT_FILTER_INVALID_DATE_RANGE` /
   `REPORT_FILTER_INVALID_METHODOLOGY` per PRD AC-2.4.
5. Extend the `REPORT_REQUESTED` audit `reason` composition
   (`RequestReportUseCase.java:76`) with filter cardinality counts, per
   PRD NFR-5 / D5 (pending security-engineer sign-off on raw-id vs. count-only).
6. Update `EvaluationSummaryReport` PDF/DOCX meta-lines
   (`renderPdf` lines 73-80, `renderDocx` lines 108-117) to add the new
   "Period" / "Evaluators" localized meta lines (PRD AC-4.3), via
   `ReportLabels.label`.

### frontend-engineer

1. Replace the `ReportRequestDialog.tsx` free-text `filterParams` `<textarea>`
   (lines 145-155) with a conditional filter panel shown when
   `reportType === 'EVALUATION_SUMMARY'`, composed of:
   - methodology multi-select fed by `useMethodologies(projectId)` /
     `useMethodologyVersions(...)` (mirror `OpenPanelDialog.tsx:582-596`
     pattern, made multi-select).
   - date-range fields reusing/extracting `DateRangeFields` from
     `AuditFilterBar.tsx:91-114`.
   - evaluator multi-select via `EvaluatorPicker` + new `mode="multi"` prop.
2. Update `reportSchemas.ts` (`RequestReportSchema`, lines 15-28) to validate
   the new structured filter shape (Zod object) when `reportType` is
   `EVALUATION_SUMMARY`, and `reportApi.ts`'s `requestReport` (lines 73-85) to
   JSON-stringify the structured object into `filter_params` (snake_case
   payload keys for the object's own fields:
   `methodology_version_ids`, `date_from`, `date_to`, `evaluator_user_ids` —
   consistent with the existing snake_case convention, commit `a45db24`).
3. Add 4-locale i18n keys for the new filter section, placeholders ("all
   methodologies" / "all evaluators"), and validation messages.
4. Update `ReportRequestDialog.test.tsx` and add a new test file for the
   EVALUATION_SUMMARY filter panel (component-level), and extend
   `normalizeReport.test.ts` if the response echo of applied filters changes.

### integration-engineer — NOT required

The async job/staging contract (`ReportGenerationJob`, object storage path
shape, `ReportGenerationContext`) is unchanged in structure — only the
`filterParams` STRING's internal JSON shape changes, which is parsed inside
the existing template (`EvaluationSummaryReport.loadData`), not at the
job/worker boundary. No new job type, no new storage path. Skip this agent
for this feature.

### security-engineer (parallel, not a build agent but flagged here for the owner)

Confirm D4 (evaluator filter + panel-bias interaction) and D5 (audit reason:
counts vs. raw ids) from PRD §6 before backend-engineer finalizes the
predicate and audit composition.

### qa-engineer

1. Backend integration tests: empty-filter regression (AC-4.2), each filter
   in isolation (AC-1.x/2.x/3.x), combined AND semantics (AC-4.1), date
   inclusive-bounds + null-submitted-at exclusion (AC-2.2/2.3), invalid range
   rejection (AC-2.4), cross-tenant/cross-project id in filter → zero rows not
   error (NFR-2, EC-2).
2. Frontend component tests: multi-select methodology/evaluator pickers,
   date-range reuse parity with `AuditFilterBar`, snake_case payload shape
   assertion (mirror the regression class that produced commit `a45db24`).
3. Localization smoke test: all 4 locales render the new filter section
   without missing-key fallback.
4. Security test: a `REPORT_CREATE`-only caller without oversight role
   attempting to filter by an evaluator outside their panel-bias visibility —
   exercise whichever D4 resolution the owner picks.

## 3. Summary of net-new artifacts (minimal, by design)

- `EvaluationReportFilter` Java record (+ TS counterpart type).
- One extended JPQL finder on `EvaluationRepository` (IN-list + date-range
  variant of the existing `findInDepartments` pattern).
- One new `ReportDataPort.loadEvaluations` parameter (filter object) +
  matching `DefaultReportDataPort` implementation change.
- One `mode="multi"` prop addition to `EvaluatorPicker`.
- One extracted `DateRangeFields` component (de-duplicating, not duplicating).
- New filter-panel JSX inside `ReportRequestDialog` (conditional on report
  type) + Zod schema extension.
- New i18n keys (4 locales).
- No new tables/columns/permissions/audit-actions/job-types.

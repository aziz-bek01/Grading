package uz.hrlab.grading.integration.reportdata;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only port templates use to fetch tenant-scoped data. The default
 * implementation lives in {@code infrastructure} and bridges to existing
 * repositories ({@code PositionRepository}, {@code MethodologyVersionRepository},
 * {@code EvaluationRepository}, etc.).
 *
 * <p>All queries are tenant + project scoped — the worker passes both ids in.
 * Human-facing strings (status, grade, factor, department, project, tenant
 * names) are resolved to the caller's {@code locale} INSIDE the port so the
 * rendering side never leaks machine values (UUIDs, enum names, factor codes).
 */
public interface ReportDataPort {

    /** Position catalog rows for the project (tenant-scoped, localized to {@code locale}). */
    List<PositionRow> positions(UUID tenantId, UUID projectId, String locale);

    /** Aggregated count of positions per assigned grade code (localized to {@code locale}). */
    List<GradeCountRow> gradeDistribution(UUID tenantId, UUID projectId, String locale);

    /** Methodology spec for the project's active methodology version. */
    MethodologySpec methodologySpec(UUID tenantId, UUID projectId, String locale);

    /**
     * Last audit events for the tenant — bounded by {@code limit} and optional
     * time window. Returned newest-first. Used by AUDIT_SUMMARY report.
     */
    List<AuditEventRow> loadAuditEvents(UUID tenantId, UUID projectId,
                                        OffsetDateTime from, OffsetDateTime to,
                                        int limit);

    /**
     * Per-position evaluation rows with factor scores attached. Each row carries
     * the canonical (position, methodology version, evaluator, status) plus a
     * pre-rendered factor-code → score map keyed by factor code. Used by
     * EVALUATION_SUMMARY report.
     *
     * <p>The {@code filter} narrows the ALREADY-tenant+project-scoped data set
     * (methodology version / submitted-date range / evaluator), AND-combined,
     * applied in the JPQL WHERE. {@link EvaluationReportFilter#none()} (or an
     * empty filter) preserves the legacy unfiltered behaviour. The filter NEVER
     * widens visibility — it only narrows the already-authorized project rows.
     */
    EvaluationMatrix loadEvaluations(UUID tenantId, UUID projectId, String locale,
                                     EvaluationReportFilter filter);

    /**
     * Aggregated KPIs for the project — pre-computed numbers for one-page
     * executive summary. Used by EXECUTIVE_SUMMARY report.
     *
     * <p>The {@code filter} narrows ONLY the evaluation-scoped KPIs
     * ({@code evaluatedCount}, the APPROVED/LOCKED count, the distinct
     * assigned-grade set) via the SAME tenant+project-scoped finder
     * {@code loadEvaluations} uses ({@code EvaluationRepository
     * .findForEvaluationReport}). The non-evaluation KPIs (position count,
     * audit-event count, recent approvals, project meta) stay project-wide —
     * they are not evaluation-scoped. {@link EvaluationReportFilter#none()} (or
     * an empty filter) preserves the legacy unfiltered behaviour byte-for-byte.
     */
    ExecutiveKpi loadExecutiveKpi(UUID tenantId, UUID projectId, String locale,
                                  EvaluationReportFilter filter);

    /**
     * Localized display NAME of a project (tenant-scoped). Falls back to the
     * project code, then to {@code "—"} — NEVER the raw UUID in a human report.
     */
    String projectName(UUID tenantId, UUID projectId, String locale);

    /**
     * Display NAME of a tenant (control-plane). Falls back to slug, then to
     * {@code "—"} — NEVER the raw UUID in a human report.
     */
    String tenantName(UUID tenantId, String locale);

    record PositionRow(
            String code,
            String title,
            String departmentName,
            String jobFamily,
            String jobLevel,
            String status) { }

    record GradeCountRow(
            String gradeCode,
            String gradeName,
            int positionCount) { }

    record MethodologySpec(
            String methodologyName,
            String versionLabel,
            String status,
            List<FactorRow> factors) { }

    record FactorRow(
            String code,
            String title,
            int weight,
            int maxPoints,
            String scoringMode,
            List<Map<String, String>> levels) { }

    /**
     * One row in the audit summary report. Strings only — the rendering side is
     * format-agnostic and may not have access to formatter beans.
     */
    record AuditEventRow(
            OffsetDateTime timestamp,
            String action,
            String actorDisplay,
            String entityType,
            String entityId,
            String reason,
            String correlationId) { }

    /**
     * A factor column reference: stable {@code code} (used as the XLSX data-row
     * lookup key and for cross-format ordering) plus the localized human
     * {@code name} shown to the reader in the column header.
     */
    record FactorRef(String code, String name) { }

    /**
     * Evaluation matrix carrying per-row scores keyed by factor code.
     * {@code factors} provides the canonical column order — rendering keeps the
     * same column order across PDF/DOCX/XLSX so cross-referencing works. Each
     * {@link FactorRef} pairs the lookup code with its localized display name.
     *
     * <p>{@code activeFilters} is the human-readable, localized echo of the
     * applied EVALUATION_SUMMARY filters (PRD AC-4.3) so a downloaded PDF/DOCX is
     * self-describing — name resolution happens INSIDE the port (single source of
     * truth) so the rendering side never sees raw UUIDs. Each value is blank when
     * its dimension was not filtered.
     */
    record EvaluationMatrix(
            String projectName,
            String methodologyName,
            int totalPositions,
            int approvedCount,
            List<FactorRef> factors,
            List<EvaluationRow> rows,
            FilterEcho activeFilters,
            List<PanelAverageRow> panelAverages) {

        public EvaluationMatrix(String projectName, String methodologyName, int totalPositions,
                                int approvedCount, List<FactorRef> factors, List<EvaluationRow> rows) {
            this(projectName, methodologyName, totalPositions, approvedCount, factors, rows,
                    FilterEcho.empty(), List.of());
        }

        public EvaluationMatrix(String projectName, String methodologyName, int totalPositions,
                                int approvedCount, List<FactorRef> factors, List<EvaluationRow> rows,
                                FilterEcho activeFilters) {
            this(projectName, methodologyName, totalPositions, approvedCount, factors, rows,
                    activeFilters, List.of());
        }
    }

    /**
     * Localized, name-resolved echo of the applied EVALUATION_SUMMARY filters for
     * the report meta block. All three fields are already human strings (no raw
     * UUIDs / ISO timestamps); a blank string means "this dimension not filtered"
     * and the renderer omits the corresponding meta line.
     */
    record FilterEcho(String period, String evaluators, String methodologies) {

        public static FilterEcho empty() {
            return new FilterEcho("", "", "");
        }

        public boolean hasPeriod() { return period != null && !period.isBlank(); }
        public boolean hasEvaluators() { return evaluators != null && !evaluators.isBlank(); }
        public boolean hasMethodologies() { return methodologies != null && !methodologies.isBlank(); }
    }

    /**
     * One evaluation row. The condensed PDF/DOCX surfaces read only the original
     * fields ({@code positionCode}, {@code positionTitle}, {@code departmentName},
     * {@code status}, scores, total, grade) and are UNCHANGED. The grading XLSX
     * additionally reads the MVP2 per-evaluator enrichment:
     *
     * <ul>
     *   <li>{@code divisionName} — the position's OWN (leaf) department name, set
     *       ONLY when it differs from {@code departmentName} (the top-level
     *       ancestor); blank otherwise ("added if exists").</li>
     *   <li>{@code evaluatorName} — resolved evaluator full name (batch, no N+1),
     *       blank when unresolvable.</li>
     *   <li>{@code evaluatorRole} — PRE-localized evaluator role label.</li>
     *   <li>{@code evaluationDate} — {@code submittedAt} as {@code yyyy-MM-dd}
     *       (UTC calendar date), blank for DRAFT / unsubmitted.</li>
     *   <li>{@code methodologyVersionLabel} — localized methodology name for the
     *       row's own methodology version ("Name (vN)").</li>
     *   <li>{@code panelId} — render-side grouping key (NOT a rendered column);
     *       null for standalone single evaluations.</li>
     * </ul>
     *
     * <p>{@code departmentName} now carries the TOP-LEVEL ancestor department name
     * for the grading XLSX; the condensed PDF/DOCX (which only ever showed a
     * single department column) reads the same field and is visually unchanged for
     * top-level positions, and for nested ones simply shows the root department —
     * consistent with the XLSX "Departament" column.
     */
    record EvaluationRow(
            String positionCode,
            String positionTitle,
            String departmentName,
            String status,
            Map<String, String> scoresByFactorCode,
            String totalScore,
            String gradeCode,
            String gradeName,
            String divisionName,
            String evaluatorName,
            String evaluatorRole,
            String evaluationDate,
            String methodologyVersionLabel,
            UUID panelId) {

        /** Legacy 8-arg shape (condensed PDF/DOCX fixtures) — enrichment blank/null. */
        public EvaluationRow(String positionCode, String positionTitle, String departmentName,
                             String status, Map<String, String> scoresByFactorCode,
                             String totalScore, String gradeCode, String gradeName) {
            this(positionCode, positionTitle, departmentName, status, scoresByFactorCode,
                    totalScore, gradeCode, gradeName, "", "", "", "", "", null);
        }
    }

    /**
     * One panel AVERAGE row (the green highlighted row rendered AFTER a panel's
     * evaluator rows in the grading XLSX). Present ONLY for panels that reached
     * AVERAGED+ (i.e. that have materialized {@code panel_factor_averages} rows).
     * All human strings are pre-localized in the port. {@code avgScoresByFactorCode}
     * is keyed by the SAME stable factor code the evaluator rows use, so it slots
     * into the union-of-factors column order. {@code gradeCode}/{@code gradeName}
     * are blank unless the panel is APPROVED/LOCKED (an {@code assignedGradeNumber}
     * is set).
     */
    record PanelAverageRow(
            UUID panelId,
            String status,
            String methodologyVersionLabel,
            String evaluationDate,
            Map<String, String> avgScoresByFactorCode,
            String totalScore,
            String gradeCode,
            String gradeName) { }

    /**
     * KPI snapshot for the executive summary. Numbers are pre-computed; the
     * template does no further aggregation.
     *
     * <p>{@code activeFilters} is the localized, name-resolved echo of the
     * applied evaluation filters (REUSES the EVALUATION_SUMMARY {@link
     * FilterEcho} + {@code DefaultReportDataPort.buildFilterEcho}). It is blank
     * ({@link FilterEcho#empty()}) when the report was requested unfiltered, so
     * the renderer omits the corresponding meta lines.
     */
    record ExecutiveKpi(
            String projectName,
            String projectStatus,
            String periodFrom,
            String periodTo,
            int positionCount,
            int evaluatedCount,
            int approvedEvaluationCount,
            int gradeCount,
            int auditEventCount,
            List<RecentApprovalRow> recentApprovals,
            FilterEcho activeFilters) {

        public ExecutiveKpi(String projectName, String projectStatus, String periodFrom,
                            String periodTo, int positionCount, int evaluatedCount,
                            int approvedEvaluationCount, int gradeCount, int auditEventCount,
                            List<RecentApprovalRow> recentApprovals) {
            this(projectName, projectStatus, periodFrom, periodTo, positionCount, evaluatedCount,
                    approvedEvaluationCount, gradeCount, auditEventCount, recentApprovals,
                    FilterEcho.empty());
        }
    }

    record RecentApprovalRow(
            OffsetDateTime timestamp,
            String action,
            String entityType,
            String entityId,
            String actorDisplay) { }
}

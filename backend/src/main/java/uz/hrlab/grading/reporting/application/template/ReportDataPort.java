package uz.hrlab.grading.reporting.application.template;

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
 */
public interface ReportDataPort {

    /** Position catalog rows for the project (tenant-scoped). */
    List<PositionRow> positions(UUID tenantId, UUID projectId);

    /** Aggregated count of positions per assigned grade code. */
    List<GradeCountRow> gradeDistribution(UUID tenantId, UUID projectId);

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
     */
    EvaluationMatrix loadEvaluations(UUID tenantId, UUID projectId);

    /**
     * Aggregated KPIs for the project — pre-computed numbers for one-page
     * executive summary. Used by EXECUTIVE_SUMMARY report.
     */
    ExecutiveKpi loadExecutiveKpi(UUID tenantId, UUID projectId);

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
     * Evaluation matrix carrying per-row scores keyed by factor code.
     * {@code factorCodes} provides the canonical column order — rendering keeps
     * the same column order across PDF/DOCX/XLSX so cross-referencing works.
     */
    record EvaluationMatrix(
            String projectName,
            String methodologyName,
            int totalPositions,
            int approvedCount,
            List<String> factorCodes,
            List<EvaluationRow> rows) { }

    record EvaluationRow(
            String positionCode,
            String positionTitle,
            String status,
            Map<String, String> scoresByFactorCode,
            String totalScore,
            String gradeCode) { }

    /**
     * KPI snapshot for the executive summary. Numbers are pre-computed; the
     * template does no further aggregation.
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
            List<RecentApprovalRow> recentApprovals) { }

    record RecentApprovalRow(
            OffsetDateTime timestamp,
            String action,
            String entityType,
            String entityId,
            String actorDisplay) { }
}

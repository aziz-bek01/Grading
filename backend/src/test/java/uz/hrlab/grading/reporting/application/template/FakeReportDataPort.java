package uz.hrlab.grading.reporting.application.template;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Deterministic fixture used by template tests. Returns LOCALIZED, leak-free
 * data (human names, no raw UUIDs / enum names) — mirroring what the real
 * {@link uz.hrlab.grading.reporting.infrastructure.DefaultReportDataPort}
 * produces after the report-content fixes.
 */
final class FakeReportDataPort implements ReportDataPort {

    @Override
    public List<PositionRow> positions(UUID tenantId, UUID projectId, String locale) {
        return List.of(
                new PositionRow("POS-001", "Software engineer", "IT department",
                        "Engineering", "L3", "Active"),
                new PositionRow("POS-002", "Senior software engineer", "IT department",
                        "Engineering", "L4", "Active"),
                new PositionRow("POS-003", "Head of HR", "HR department",
                        "People & Culture", "L5", "Active"));
    }

    @Override
    public List<GradeCountRow> gradeDistribution(UUID tenantId, UUID projectId, String locale) {
        return List.of(
                new GradeCountRow("G3", "Operational", 12),
                new GradeCountRow("G4", "Senior", 7),
                new GradeCountRow("G5", "Lead", 3));
    }

    @Override
    public MethodologySpec methodologySpec(UUID tenantId, UUID projectId, String locale) {
        return new MethodologySpec("Classic 8-factor (v2)", "v2", "Approved", List.of(
                new FactorRow("KNOWLEDGE", "Knowledge", 20, 100, "Weighted points",
                        List.of(Map.of("code", "L1", "label", "Basic",
                                        "points", "10", "scaleValue", "1"),
                                Map.of("code", "L2", "label", "Skilled",
                                        "points", "30", "scaleValue", "2"))),
                new FactorRow("PROBLEM_SOLVING", "Problem solving", 15, 80, "Direct points",
                        List.of(Map.of("code", "L1", "label", "Reactive",
                                        "points", "10", "scaleValue", "1")))));
    }

    @Override
    public List<AuditEventRow> loadAuditEvents(UUID tenantId, UUID projectId,
                                               OffsetDateTime from, OffsetDateTime to,
                                               int limit) {
        OffsetDateTime t = OffsetDateTime.parse("2026-05-20T10:00:00Z");
        return List.of(
                new AuditEventRow(t.plusMinutes(15), "EVALUATION_APPROVED",
                        "Alice Director", "Evaluation",
                        "00000000-0000-0000-0000-000000000001",
                        "approved by HR director", "corr-1"),
                new AuditEventRow(t.plusMinutes(30), "GRADE_ASSIGNED",
                        "System", "Evaluation",
                        "00000000-0000-0000-0000-000000000001",
                        "auto-assignment", "corr-1"),
                new AuditEventRow(t.plusHours(2), "METHODOLOGY_VERSION_LOCKED",
                        "Bob Methodologist", "MethodologyVersion",
                        "00000000-0000-0000-0000-000000000002",
                        "lock requested by methodologist", "corr-2"));
    }

    @Override
    public EvaluationMatrix loadEvaluations(UUID tenantId, UUID projectId, String locale,
                                            EvaluationReportFilter filter) {
        List<FactorRef> factors = List.of(
                new FactorRef("KNOWLEDGE", "Knowledge"),
                new FactorRef("PROBLEM_SOLVING", "Problem solving"),
                new FactorRef("ACCOUNTABILITY", "Accountability"));
        // Mirror the real port: when a filter is supplied, echo a name-resolved
        // FilterEcho so the template's meta lines can be asserted.
        FilterEcho echo = (filter == null || filter.isEmpty())
                ? FilterEcho.empty()
                : new FilterEcho("2026-04-01 – 2026-06-30", "Aliyev A.", "Classic 8-factor (v2)");
        return new EvaluationMatrix(
                "Fixture project",
                "Classic 8-factor (v2)",
                3,
                2,
                factors,
                List.of(
                        new EvaluationRow("POS-001", "Software engineer", "IT department", "Approved",
                                Map.of("KNOWLEDGE", "60", "PROBLEM_SOLVING", "40",
                                        "ACCOUNTABILITY", "30"),
                                "130", "G3", "Operational"),
                        new EvaluationRow("POS-002", "Senior software engineer", "IT department", "Approved",
                                Map.of("KNOWLEDGE", "80", "PROBLEM_SOLVING", "50",
                                        "ACCOUNTABILITY", "40"),
                                "170", "G4", "Senior"),
                        new EvaluationRow("POS-003", "Head of HR", "HR department", "Draft",
                                Map.of("KNOWLEDGE", "70"),
                                "70", "", "")),
                echo);
    }

    @Override
    public ExecutiveKpi loadExecutiveKpi(UUID tenantId, UUID projectId, String locale,
                                         EvaluationReportFilter filter) {
        OffsetDateTime t = OffsetDateTime.parse("2026-05-20T10:00:00Z");
        // Mirror the real port: the evaluation-scoped KPIs reflect the filter,
        // while positionCount / auditEventCount stay project-wide; and a non-empty
        // filter echoes a name-resolved FilterEcho so the meta lines can be asserted.
        boolean filtered = filter != null && !filter.isEmpty();
        int evaluatedCount = filtered ? 12 : 30;
        int approvedEvaluationCount = filtered ? 9 : 21;
        int gradeCount = filtered ? 4 : 6;
        FilterEcho echo = filtered
                ? new FilterEcho("2026-04-01 – 2026-06-30", "Aliyev A.", "Classic 8-factor (v2)")
                : FilterEcho.empty();
        return new ExecutiveKpi(
                "Fixture project",
                "Active",
                "2026-01-01",
                "2026-12-31",
                42, evaluatedCount, approvedEvaluationCount, gradeCount, 187,
                List.of(
                        new RecentApprovalRow(t.plusMinutes(15), "EVALUATION_APPROVED",
                                "Evaluation",
                                "00000000-0000-0000-0000-000000000001", "Alice Director"),
                        new RecentApprovalRow(t.plusMinutes(20), "EVALUATION_APPROVED",
                                "Evaluation",
                                "00000000-0000-0000-0000-000000000002", "Alice Director"),
                        new RecentApprovalRow(t.plusMinutes(25), "METHODOLOGY_VERSION_LOCKED",
                                "MethodologyVersion",
                                "00000000-0000-0000-0000-000000000003", "Bob Methodologist"),
                        new RecentApprovalRow(t.plusMinutes(35), "GRADE_ASSIGNED",
                                "Evaluation",
                                "00000000-0000-0000-0000-000000000004", "System"),
                        new RecentApprovalRow(t.plusMinutes(40), "EVALUATION_APPROVED",
                                "Evaluation",
                                "00000000-0000-0000-0000-000000000005", "Carol Approver")),
                echo);
    }

    @Override
    public String projectName(UUID tenantId, UUID projectId, String locale) {
        return "Fixture project";
    }

    @Override
    public String tenantName(UUID tenantId, String locale) {
        return "Fixture company-client";
    }
}

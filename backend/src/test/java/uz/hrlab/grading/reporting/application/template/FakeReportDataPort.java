package uz.hrlab.grading.reporting.application.template;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Deterministic fixture used by template tests. */
final class FakeReportDataPort implements ReportDataPort {

    @Override
    public List<PositionRow> positions(UUID tenantId, UUID projectId) {
        return List.of(
                new PositionRow("POS-001", "Software engineer", "IT",
                        "Engineering", "L3", "ACTIVE"),
                new PositionRow("POS-002", "Senior software engineer", "IT",
                        "Engineering", "L4", "ACTIVE"),
                new PositionRow("POS-003", "Head of HR", "HR",
                        "People & Culture", "L5", "ACTIVE"));
    }

    @Override
    public List<GradeCountRow> gradeDistribution(UUID tenantId, UUID projectId) {
        return List.of(
                new GradeCountRow("G3", "Operational", 12),
                new GradeCountRow("G4", "Senior", 7),
                new GradeCountRow("G5", "Lead", 3));
    }

    @Override
    public MethodologySpec methodologySpec(UUID tenantId, UUID projectId, String locale) {
        return new MethodologySpec("Classic 8-factor", "v1.2", "APPROVED", List.of(
                new FactorRow("KNOWLEDGE", "Knowledge", 20, 100, "WEIGHTED_POINTS",
                        List.of(Map.of("code", "L1", "title", "Basic",
                                        "points", "10", "scaleValue", "1"),
                                Map.of("code", "L2", "title", "Skilled",
                                        "points", "30", "scaleValue", "2"))),
                new FactorRow("PROBLEM_SOLVING", "Problem solving", 15, 80, "DIRECT_POINTS",
                        List.of(Map.of("code", "L1", "title", "Reactive",
                                        "points", "10", "scaleValue", "1")))));
    }

    @Override
    public List<AuditEventRow> loadAuditEvents(UUID tenantId, UUID projectId,
                                               OffsetDateTime from, OffsetDateTime to,
                                               int limit) {
        OffsetDateTime t = OffsetDateTime.parse("2026-05-20T10:00:00Z");
        return List.of(
                new AuditEventRow(t.plusMinutes(15), "EVALUATION_APPROVED",
                        "user-alice", "Evaluation",
                        "00000000-0000-0000-0000-000000000001",
                        "approved by HR director", "corr-1"),
                new AuditEventRow(t.plusMinutes(30), "GRADE_ASSIGNED",
                        "system", "Evaluation",
                        "00000000-0000-0000-0000-000000000001",
                        "auto-assignment", "corr-1"),
                new AuditEventRow(t.plusHours(2), "METHODOLOGY_VERSION_LOCKED",
                        "user-bob", "MethodologyVersion",
                        "00000000-0000-0000-0000-000000000002",
                        "lock requested by methodologist", "corr-2"));
    }

    @Override
    public EvaluationMatrix loadEvaluations(UUID tenantId, UUID projectId) {
        List<String> factorCodes = List.of("KNOWLEDGE", "PROBLEM_SOLVING", "ACCOUNTABILITY");
        return new EvaluationMatrix(
                "Fixture project",
                "Classic 8-factor v1.2",
                3,
                2,
                factorCodes,
                List.of(
                        new EvaluationRow("POS-001", "Software engineer", "APPROVED",
                                Map.of("KNOWLEDGE", "60", "PROBLEM_SOLVING", "40",
                                        "ACCOUNTABILITY", "30"),
                                "130", "G3"),
                        new EvaluationRow("POS-002", "Senior software engineer", "APPROVED",
                                Map.of("KNOWLEDGE", "80", "PROBLEM_SOLVING", "50",
                                        "ACCOUNTABILITY", "40"),
                                "170", "G4"),
                        new EvaluationRow("POS-003", "Head of HR", "DRAFT",
                                Map.of("KNOWLEDGE", "70"),
                                "70", "")));
    }

    @Override
    public ExecutiveKpi loadExecutiveKpi(UUID tenantId, UUID projectId) {
        OffsetDateTime t = OffsetDateTime.parse("2026-05-20T10:00:00Z");
        return new ExecutiveKpi(
                "Fixture project",
                "ACTIVE",
                "2026-01-01",
                "2026-12-31",
                42, 30, 21, 6, 187,
                List.of(
                        new RecentApprovalRow(t.plusMinutes(15), "EVALUATION_APPROVED",
                                "Evaluation",
                                "00000000-0000-0000-0000-000000000001", "user-alice"),
                        new RecentApprovalRow(t.plusMinutes(20), "EVALUATION_APPROVED",
                                "Evaluation",
                                "00000000-0000-0000-0000-000000000002", "user-alice"),
                        new RecentApprovalRow(t.plusMinutes(25), "METHODOLOGY_VERSION_LOCKED",
                                "MethodologyVersion",
                                "00000000-0000-0000-0000-000000000003", "user-bob"),
                        new RecentApprovalRow(t.plusMinutes(35), "GRADE_ASSIGNED",
                                "Evaluation",
                                "00000000-0000-0000-0000-000000000004", "system"),
                        new RecentApprovalRow(t.plusMinutes(40), "EVALUATION_APPROVED",
                                "Evaluation",
                                "00000000-0000-0000-0000-000000000005", "user-carol")));
    }
}

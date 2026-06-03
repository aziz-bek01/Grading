package uz.hrlab.grading.reporting.infrastructure;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogJpaEntity;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogRepository;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorRepository;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.project.infrastructure.ProjectJpaEntity;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.reporting.application.template.ReportDataPort;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Default port implementation. The position query is fully wired to the
 * tenant-aware {@link PositionRepository}; audit summary, evaluation matrix and
 * executive KPI now read from the corresponding module repositories.
 *
 * <p>Grade-distribution and methodology specs still return empty placeholders —
 * they rely on cross-aggregate joins that ship in MVP 3.
 */
@Component
public class DefaultReportDataPort implements ReportDataPort {

    private static final int MAX_POSITIONS = 1_000;
    private static final int MAX_EVALUATIONS = 1_000;

    private final PositionRepository positions;
    private final ProjectRepository projects;
    private final EvaluationRepository evaluations;
    private final EvaluationScoreRepository evaluationScores;
    private final FactorRepository factors;
    private final SystemAuditLogRepository auditLog;

    public DefaultReportDataPort(PositionRepository positions,
                                 ProjectRepository projects,
                                 EvaluationRepository evaluations,
                                 EvaluationScoreRepository evaluationScores,
                                 FactorRepository factors,
                                 SystemAuditLogRepository auditLog) {
        this.positions = positions;
        this.projects = projects;
        this.evaluations = evaluations;
        this.evaluationScores = evaluationScores;
        this.factors = factors;
        this.auditLog = auditLog;
    }

    @Override
    public List<PositionRow> positions(UUID tenantId, UUID projectId) {
        var page = positions.search(tenantId, projectId, null, null, null,
                PageRequest.of(0, MAX_POSITIONS));
        List<PositionRow> rows = new ArrayList<>(page.getNumberOfElements());
        for (PositionJpaEntity p : page.getContent()) {
            rows.add(new PositionRow(
                    p.getCode(),
                    titleFor(p),
                    "",                       // departmentName — joined in MVP 3
                    nz(p.getJobFamily()),
                    nz(p.getJobLevel()),
                    p.getStatus() == null ? "" : p.getStatus().name()));
        }
        return rows;
    }

    @Override
    public List<GradeCountRow> gradeDistribution(UUID tenantId, UUID projectId) {
        // Real cross-aggregate query lands in MVP 3 — the template handles
        // empty data and the test suite asserts the report still renders.
        return List.of();
    }

    @Override
    public MethodologySpec methodologySpec(UUID tenantId, UUID projectId, String locale) {
        return new MethodologySpec(
                "(active methodology — wired in MVP 3)",
                "(version)",
                "(status)",
                List.<FactorRow>of());
    }

    @Override
    public List<AuditEventRow> loadAuditEvents(UUID tenantId, UUID projectId,
                                               OffsetDateTime from, OffsetDateTime to,
                                               int limit) {
        if (tenantId == null || limit <= 0) return List.of();
        var page = auditLog.search(tenantId, null, null, null, null,
                from, to, PageRequest.of(0, Math.min(limit, 200)));
        List<AuditEventRow> rows = new ArrayList<>(page.getNumberOfElements());
        for (SystemAuditLogJpaEntity e : page.getContent()) {
            // Project filter applied in-memory: most tenants will not have
            // millions of events per tenant and search() is page-bounded.
            if (projectId != null && e.getProjectId() != null
                    && !projectId.equals(e.getProjectId())) {
                continue;
            }
            rows.add(new AuditEventRow(
                    e.getCreatedAt(),
                    nz(e.getAction()),
                    e.getActorUserId() == null ? "" : e.getActorUserId().toString(),
                    nz(e.getEntityType()),
                    e.getEntityId() == null ? "" : e.getEntityId().toString(),
                    nz(e.getReason()),
                    nz(e.getCorrelationId())));
        }
        return rows;
    }

    @Override
    public EvaluationMatrix loadEvaluations(UUID tenantId, UUID projectId) {
        String projectName = projects.findByIdAndTenantId(projectId, tenantId)
                .map(DefaultReportDataPort::titleForProject)
                .orElse(projectId == null ? "" : projectId.toString());

        var page = evaluations.findAllByTenantIdAndProjectId(tenantId, projectId,
                PageRequest.of(0, MAX_EVALUATIONS));

        // Collect distinct methodology version ids; build the column order
        // (factor codes) by walking through the factors of each version in
        // sortOrder. Factors that recur across versions deduplicate by code.
        Set<UUID> methodologyVersionIds = new LinkedHashSet<>();
        for (EvaluationJpaEntity e : page.getContent()) {
            methodologyVersionIds.add(e.getMethodologyVersionId());
        }
        LinkedHashMap<UUID, String> factorCodeById = new LinkedHashMap<>();
        List<String> factorColumnOrder = new ArrayList<>();
        String methodologyLabel = "";
        for (UUID mvId : methodologyVersionIds) {
            List<FactorJpaEntity> fs =
                    factors.findAllByTenantIdAndMethodologyVersionIdOrderBySortOrderAsc(
                            tenantId, mvId);
            for (FactorJpaEntity f : fs) {
                if (!factorCodeById.containsKey(f.getId())) {
                    factorCodeById.put(f.getId(), f.getCode());
                    if (!factorColumnOrder.contains(f.getCode())) {
                        factorColumnOrder.add(f.getCode());
                    }
                }
            }
            if (methodologyLabel.isEmpty() && !fs.isEmpty()) {
                methodologyLabel = "methodology_version=" + mvId;
            }
        }

        int approved = 0;
        List<EvaluationRow> rows = new ArrayList<>(page.getNumberOfElements());
        for (EvaluationJpaEntity ev : page.getContent()) {
            if (ev.getStatus() == EvaluationStatus.APPROVED
                    || ev.getStatus() == EvaluationStatus.LOCKED) {
                approved++;
            }
            Map<String, String> scoresByCode = new LinkedHashMap<>();
            List<EvaluationScoreJpaEntity> scores =
                    evaluationScores.findAllByTenantIdAndEvaluationId(tenantId, ev.getId());
            for (EvaluationScoreJpaEntity s : scores) {
                String code = factorCodeById.get(s.getFactorId());
                if (code == null) continue;
                BigDecimal v = s.getRawFactorScore();
                scoresByCode.put(code, v == null ? "" : v.toPlainString());
            }

            // Position label — look up code+title (best-effort, tenant-scoped).
            PositionRow label = positionLabel(tenantId, projectId, ev.getPositionId());
            String gradeCode = ev.getAssignedGradeNumber() == null
                    ? "" : "G" + ev.getAssignedGradeNumber();
            rows.add(new EvaluationRow(
                    label.code(),
                    label.title(),
                    ev.getStatus() == null ? "" : ev.getStatus().name(),
                    scoresByCode,
                    ev.getDisplayedTotalScore() == null
                            ? "0" : ev.getDisplayedTotalScore().toPlainString(),
                    gradeCode));
        }

        return new EvaluationMatrix(
                projectName,
                methodologyLabel,
                page.getNumberOfElements(),
                approved,
                factorColumnOrder,
                rows);
    }

    @Override
    public ExecutiveKpi loadExecutiveKpi(UUID tenantId, UUID projectId) {
        String projectName;
        String projectStatus = "";
        String periodFrom = "";
        String periodTo = "";
        Optional<ProjectJpaEntity> project = projects.findByIdAndTenantId(projectId, tenantId);
        if (project.isPresent()) {
            ProjectJpaEntity p = project.get();
            projectName = titleForProject(p);
            projectStatus = p.getStatus() == null ? "" : p.getStatus().name();
            periodFrom = p.getStartDate() == null ? "" : p.getStartDate().toString();
            periodTo = p.getEndDate() == null ? "" : p.getEndDate().toString();
        } else {
            projectName = projectId == null ? "" : projectId.toString();
        }

        int positionCount = (int) positions.search(tenantId, projectId, null, null, null,
                PageRequest.of(0, 1)).getTotalElements();

        var evalPage = evaluations.findAllByTenantIdAndProjectId(tenantId, projectId,
                PageRequest.of(0, MAX_EVALUATIONS));
        int evaluatedCount = (int) evalPage.getTotalElements();
        int approved = 0;
        Set<Integer> distinctGrades = new LinkedHashSet<>();
        for (EvaluationJpaEntity ev : evalPage.getContent()) {
            if (ev.getStatus() == EvaluationStatus.APPROVED
                    || ev.getStatus() == EvaluationStatus.LOCKED) {
                approved++;
            }
            if (ev.getAssignedGradeNumber() != null) {
                distinctGrades.add(ev.getAssignedGradeNumber());
            }
        }

        var auditPage = auditLog.search(tenantId, null, null, null, null, null, null,
                PageRequest.of(0, 200));
        int auditCount = (int) auditPage.getTotalElements();

        List<RecentApprovalRow> recent = new ArrayList<>();
        for (SystemAuditLogJpaEntity e : auditPage.getContent()) {
            String action = nz(e.getAction());
            if (!action.endsWith("_APPROVED") && !action.endsWith("_LOCKED")
                    && !action.equals("GRADE_ASSIGNED")) {
                continue;
            }
            if (projectId != null && e.getProjectId() != null
                    && !projectId.equals(e.getProjectId())) {
                continue;
            }
            recent.add(new RecentApprovalRow(
                    e.getCreatedAt(),
                    action,
                    nz(e.getEntityType()),
                    e.getEntityId() == null ? "" : e.getEntityId().toString(),
                    e.getActorUserId() == null ? "" : e.getActorUserId().toString()));
            if (recent.size() >= 5) break;
        }
        recent.sort(Comparator.comparing(RecentApprovalRow::timestamp,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return new ExecutiveKpi(projectName, projectStatus, periodFrom, periodTo,
                positionCount, evaluatedCount, approved, distinctGrades.size(),
                auditCount, recent);
    }

    private PositionRow positionLabel(UUID tenantId, UUID projectId, UUID positionId) {
        if (positionId == null) return new PositionRow("", "", "", "", "", "");
        var hit = positions.findByIdAndTenantId(positionId, tenantId);
        if (hit.isEmpty()) return new PositionRow(positionId.toString(), "", "", "", "", "");
        PositionJpaEntity p = hit.get();
        return new PositionRow(p.getCode(), titleFor(p), "", nz(p.getJobFamily()),
                nz(p.getJobLevel()), p.getStatus() == null ? "" : p.getStatus().name());
    }

    private static String titleFor(PositionJpaEntity p) {
        Map<String, String> i18n = p.getTitleI18n();
        if (i18n == null || i18n.isEmpty()) return p.getCode();
        return i18n.getOrDefault("ru-RU",
                i18n.values().stream().findFirst().orElse(p.getCode()));
    }

    private static String titleForProject(ProjectJpaEntity p) {
        Map<String, String> i18n = p.getNameI18n();
        if (i18n == null || i18n.isEmpty()) return p.getCode();
        return i18n.getOrDefault("ru-RU",
                i18n.values().stream().findFirst().orElse(p.getCode()));
    }

    private static String nz(String s) { return s == null ? "" : s; }

    // Suppress IDE warning — kept for test seam compatibility with the older
    // single-arg constructor signature.
    @SuppressWarnings("unused")
    private static HashMap<String, String> emptyMap() { return new HashMap<>(); }
}

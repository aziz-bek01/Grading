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
import uz.hrlab.grading.gradestructure.domain.GradeStructureStatus;
import uz.hrlab.grading.gradestructure.infrastructure.GradeJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
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
import java.util.TreeMap;
import java.util.UUID;

/**
 * Default port implementation. The position query is fully wired to the
 * tenant-aware {@link PositionRepository}; audit summary, evaluation matrix and
 * executive KPI read from the corresponding module repositories.
 *
 * <p>Batch-2 wiring: {@link #gradeDistribution} now aggregates approved/locked
 * evaluations per assigned grade (tenant + project scoped) and resolves grade
 * display names from the project's active grade structure; {@link
 * #methodologySpec} resolves the project's active methodology version and reads
 * its factors + levels (tenant-scoped). Both fall back to a correct
 * empty/placeholder result when the project has no grade structure / methodology
 * yet — the template still renders.
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
    private final FactorLevelRepository factorLevels;
    private final MethodologyVersionRepository methodologyVersions;
    private final GradeStructureRepository gradeStructures;
    private final GradeRepository grades;
    private final SystemAuditLogRepository auditLog;

    public DefaultReportDataPort(PositionRepository positions,
                                 ProjectRepository projects,
                                 EvaluationRepository evaluations,
                                 EvaluationScoreRepository evaluationScores,
                                 FactorRepository factors,
                                 FactorLevelRepository factorLevels,
                                 MethodologyVersionRepository methodologyVersions,
                                 GradeStructureRepository gradeStructures,
                                 GradeRepository grades,
                                 SystemAuditLogRepository auditLog) {
        this.positions = positions;
        this.projects = projects;
        this.evaluations = evaluations;
        this.evaluationScores = evaluationScores;
        this.factors = factors;
        this.factorLevels = factorLevels;
        this.methodologyVersions = methodologyVersions;
        this.gradeStructures = gradeStructures;
        this.grades = grades;
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
        if (tenantId == null || projectId == null) return List.of();

        // Resolve the active grade structure (LOCKED preferred, else APPROVED) so
        // grade numbers carry a human display name. Tenant + project scoped.
        Map<Integer, String> gradeNames = resolveGradeNames(tenantId, projectId);

        // Count assigned grades across the project's evaluations (tenant-scoped).
        var page = evaluations.findAllByTenantIdAndProjectId(tenantId, projectId,
                PageRequest.of(0, MAX_EVALUATIONS));
        Map<Integer, Integer> countByGrade = new TreeMap<>();
        for (EvaluationJpaEntity ev : page.getContent()) {
            Integer g = ev.getAssignedGradeNumber();
            if (g == null) continue;
            countByGrade.merge(g, 1, Integer::sum);
        }

        List<GradeCountRow> rows = new ArrayList<>(countByGrade.size());
        for (Map.Entry<Integer, Integer> e : countByGrade.entrySet()) {
            String code = "G" + e.getKey();
            rows.add(new GradeCountRow(
                    code,
                    gradeNames.getOrDefault(e.getKey(), code),
                    e.getValue()));
        }
        return rows;
    }

    /** Active grade structure grade-number → display name (tenant + project scoped). */
    private Map<Integer, String> resolveGradeNames(UUID tenantId, UUID projectId) {
        GradeStructureJpaEntity structure = activeGradeStructure(tenantId, projectId);
        if (structure == null) return Map.of();
        Map<Integer, String> names = new HashMap<>();
        for (GradeJpaEntity g :
                grades.findAllByTenantIdAndGradeStructureIdOrderBySortOrderAsc(
                        tenantId, structure.getId())) {
            names.put(g.getGradeNumber(), gradeName(g));
        }
        return names;
    }

    private GradeStructureJpaEntity activeGradeStructure(UUID tenantId, UUID projectId) {
        var locked = gradeStructures
                .findAllByTenantIdAndProjectIdAndStatusOrderByVersionNumberDesc(
                        tenantId, projectId, GradeStructureStatus.LOCKED);
        if (!locked.isEmpty()) return locked.get(0);
        var approved = gradeStructures
                .findAllByTenantIdAndProjectIdAndStatusOrderByVersionNumberDesc(
                        tenantId, projectId, GradeStructureStatus.APPROVED);
        return approved.isEmpty() ? null : approved.get(0);
    }

    @Override
    public MethodologySpec methodologySpec(UUID tenantId, UUID projectId, String locale) {
        if (tenantId == null || projectId == null) {
            return new MethodologySpec("", "", "", List.of());
        }
        ProjectJpaEntity project = projects.findByIdAndTenantId(projectId, tenantId).orElse(null);
        if (project == null || project.getMethodologyVersionId() == null) {
            return new MethodologySpec("", "", "", List.of());
        }
        UUID versionId = project.getMethodologyVersionId();
        MethodologyVersionJpaEntity version =
                methodologyVersions.findByIdAndTenantId(versionId, tenantId).orElse(null);
        if (version == null) {
            return new MethodologySpec("", "", "", List.of());
        }

        String scoringMode = version.getScoringMode() == null
                ? "" : version.getScoringMode().name();
        String versionLabel = "v" + version.getVersionNumber();
        String status = version.getStatus() == null ? "" : version.getStatus().name();

        List<FactorRow> factorRows = new ArrayList<>();
        for (FactorJpaEntity f :
                factors.findAllByTenantIdAndMethodologyVersionIdOrderBySortOrderAsc(
                        tenantId, versionId)) {
            List<Map<String, String>> levels = new ArrayList<>();
            for (FactorLevelJpaEntity lvl :
                    factorLevels.findAllByTenantIdAndFactorIdOrderByLevelOrderAsc(
                            tenantId, f.getId())) {
                Map<String, String> lm = new LinkedHashMap<>();
                lm.put("code", nz(lvl.getCode()));
                lm.put("order", String.valueOf(lvl.getLevelOrder()));
                lm.put("points", lvl.getPoints() == null ? "" : lvl.getPoints().toPlainString());
                lm.put("label", i18n(lvl.getLabelI18n(), locale, nz(lvl.getCode())));
                levels.add(lm);
            }
            factorRows.add(new FactorRow(
                    nz(f.getCode()),
                    i18n(f.getNameI18n(), locale, nz(f.getCode())),
                    f.getWeight() == null ? 0 : f.getWeight().intValue(),
                    f.getMaxPoints() == null ? 0 : f.getMaxPoints().intValue(),
                    scoringMode,
                    levels));
        }
        // Methodology display name not modelled on the version row; use version label.
        return new MethodologySpec("methodology " + versionLabel, versionLabel, status, factorRows);
    }

    private static String gradeName(GradeJpaEntity g) {
        Map<String, String> i18n = g.getNameI18n();
        if (i18n == null || i18n.isEmpty()) return "G" + g.getGradeNumber();
        return i18n.getOrDefault("ru-RU",
                i18n.values().stream().findFirst().orElse("G" + g.getGradeNumber()));
    }

    private static String i18n(Map<String, String> map, String locale, String fallback) {
        if (map == null || map.isEmpty()) return fallback;
        if (locale != null && map.containsKey(locale)) return map.get(locale);
        return map.getOrDefault("ru-RU", map.values().stream().findFirst().orElse(fallback));
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

        // PERF (P1) — batch-load every score for the whole page of evaluations in
        // ONE tenant-scoped query (was one findAllByTenantIdAndEvaluationId per
        // evaluation → up to MAX_EVALUATIONS round-trips). Grouped by evaluation
        // id in memory; ordering within an evaluation is irrelevant because the
        // row map is keyed by factor code, matching the per-evaluation loop below.
        Set<UUID> evaluationIds = new LinkedHashSet<>();
        for (EvaluationJpaEntity ev : page.getContent()) {
            evaluationIds.add(ev.getId());
        }
        Map<UUID, List<EvaluationScoreJpaEntity>> scoresByEval = new HashMap<>();
        if (!evaluationIds.isEmpty()) {
            for (EvaluationScoreJpaEntity s :
                    evaluationScores.findAllByTenantIdAndEvaluationIdIn(tenantId, evaluationIds)) {
                scoresByEval.computeIfAbsent(s.getEvaluationId(), k -> new ArrayList<>()).add(s);
            }
        }

        // PERF (P1) — batch-load every referenced position in ONE tenant-scoped
        // query (was one findByIdAndTenantId per evaluation row → N+1). Built into
        // a map so positionLabel() resolves from memory; tenant_id stays pinned.
        Set<UUID> positionIds = new LinkedHashSet<>();
        for (EvaluationJpaEntity ev : page.getContent()) {
            if (ev.getPositionId() != null) positionIds.add(ev.getPositionId());
        }
        Map<UUID, PositionJpaEntity> positionById = new HashMap<>();
        if (!positionIds.isEmpty()) {
            for (PositionJpaEntity p : positions.findAllByTenantIdAndIdIn(tenantId, positionIds)) {
                positionById.put(p.getId(), p);
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
            for (EvaluationScoreJpaEntity s :
                    scoresByEval.getOrDefault(ev.getId(), List.of())) {
                String code = factorCodeById.get(s.getFactorId());
                if (code == null) continue;
                BigDecimal v = s.getRawFactorScore();
                scoresByCode.put(code, v == null ? "" : v.toPlainString());
            }

            // Position label — resolve from the batch-loaded map (tenant-scoped).
            PositionRow label = positionLabel(positionById, ev.getPositionId());
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

    /**
     * Resolve a position's label row from the page-level batch map (PERF P1 — no
     * per-row DB hit). Output is byte-identical to the prior per-row lookup:
     * null id ⇒ empty row; an id with no batch-loaded (tenant-scoped) position ⇒
     * the id string as code with empty rest; otherwise the full code+title row.
     */
    private static PositionRow positionLabel(Map<UUID, PositionJpaEntity> positionById,
                                             UUID positionId) {
        if (positionId == null) return new PositionRow("", "", "", "", "", "");
        PositionJpaEntity p = positionById.get(positionId);
        if (p == null) return new PositionRow(positionId.toString(), "", "", "", "", "");
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

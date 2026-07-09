package uz.hrlab.grading.reporting.infrastructure;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import uz.hrlab.grading.access.application.ActorNameResolver;
import uz.hrlab.grading.access.infrastructure.UserJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserRepository;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogJpaEntity;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogRepository;
import uz.hrlab.grading.common.i18n.I18nText;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationReportSpecifications;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelFactorAverageJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelFactorAverageRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;
import uz.hrlab.grading.gradestructure.domain.GradeStructureStatus;
import uz.hrlab.grading.gradestructure.infrastructure.GradeJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
import uz.hrlab.grading.organization.infrastructure.DepartmentHierarchyResolver;
import uz.hrlab.grading.organization.infrastructure.DepartmentJpaEntity;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.project.infrastructure.ProjectJpaEntity;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.reporting.application.template.EvaluationReportFilter;
import uz.hrlab.grading.reporting.application.template.ReportDataPort;
import uz.hrlab.grading.reporting.application.template.ReportLabels;
import uz.hrlab.grading.tenancy.infrastructure.TenantRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
 * Default port implementation. Every human-facing string is resolved to the
 * caller's locale INSIDE this port, so report templates never receive (and
 * therefore never leak) machine values — raw UUIDs, Java enum names, factor
 * codes, or synthetic grade keys. Cross-module name resolution is batch-loaded
 * and tenant-scoped:
 *
 * <ul>
 *   <li>methodology name ← {@link MethodologyRepository} (version → methodology
 *       {@code name_i18n} + version number);</li>
 *   <li>department name ← {@link DepartmentRepository} (batch id → localized
 *       name);</li>
 *   <li>actor display ← {@link UserRepository} (batch id → full name / email,
 *       fallback to first-8-chars+ellipsis — never a full UUID);</li>
 *   <li>project / tenant name ← {@link ProjectRepository} / {@link
 *       TenantRepository};</li>
 *   <li>grade name ← active grade structure (LOCKED preferred, else APPROVED),
 *       fallback {@code "G"+number}.</li>
 * </ul>
 *
 * <p>Statuses and scoring modes are localized via {@link ReportLabels}. All
 * methods fall back to a correct empty/placeholder result when the project has
 * no grade structure / methodology yet — the template still renders.
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
    private final MethodologyRepository methodologies;
    private final GradeStructureRepository gradeStructures;
    private final GradeRepository grades;
    private final DepartmentRepository departments;
    private final UserRepository users;
    private final TenantRepository tenants;
    private final SystemAuditLogRepository auditLog;
    private final ActorNameResolver actorNames;
    private final PanelRepository panels;
    private final PanelFactorAverageRepository panelFactorAverages;
    private final DepartmentHierarchyResolver departmentHierarchy;

    public DefaultReportDataPort(PositionRepository positions,
                                 ProjectRepository projects,
                                 EvaluationRepository evaluations,
                                 EvaluationScoreRepository evaluationScores,
                                 FactorRepository factors,
                                 FactorLevelRepository factorLevels,
                                 MethodologyVersionRepository methodologyVersions,
                                 MethodologyRepository methodologies,
                                 GradeStructureRepository gradeStructures,
                                 GradeRepository grades,
                                 DepartmentRepository departments,
                                 UserRepository users,
                                 TenantRepository tenants,
                                 SystemAuditLogRepository auditLog,
                                 ActorNameResolver actorNames,
                                 PanelRepository panels,
                                 PanelFactorAverageRepository panelFactorAverages,
                                 DepartmentHierarchyResolver departmentHierarchy) {
        this.positions = positions;
        this.projects = projects;
        this.evaluations = evaluations;
        this.evaluationScores = evaluationScores;
        this.factors = factors;
        this.factorLevels = factorLevels;
        this.methodologyVersions = methodologyVersions;
        this.methodologies = methodologies;
        this.gradeStructures = gradeStructures;
        this.grades = grades;
        this.departments = departments;
        this.users = users;
        this.tenants = tenants;
        this.auditLog = auditLog;
        this.actorNames = actorNames;
        this.panels = panels;
        this.panelFactorAverages = panelFactorAverages;
        this.departmentHierarchy = departmentHierarchy;
    }

    @Override
    public List<PositionRow> positions(UUID tenantId, UUID projectId, String locale) {
        var page = positions.search(tenantId, projectId, null, null, null,
                PageRequest.of(0, MAX_POSITIONS));
        Map<UUID, String> deptNames = resolveDepartmentNames(tenantId,
                collectDepartmentIds(page.getContent()), locale);
        List<PositionRow> rows = new ArrayList<>(page.getNumberOfElements());
        for (PositionJpaEntity p : page.getContent()) {
            rows.add(new PositionRow(
                    p.getCode(),
                    titleFor(p, locale),
                    deptNames.getOrDefault(p.getDepartmentId(), ""),
                    nz(p.getJobFamily()),
                    nz(p.getJobLevel()),
                    p.getStatus() == null ? "" : ReportLabels.localizeStatus(p.getStatus().name(), locale)));
        }
        return rows;
    }

    @Override
    public List<GradeCountRow> gradeDistribution(UUID tenantId, UUID projectId, String locale) {
        if (tenantId == null || projectId == null) return List.of();

        // Resolve the active grade structure (LOCKED preferred, else APPROVED) so
        // grade numbers carry a human display name. Tenant + project scoped.
        Map<Integer, String> gradeNames = resolveGradeNames(tenantId, projectId, locale);

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

    /** Active grade structure grade-number → localized display name (tenant + project scoped). */
    private Map<Integer, String> resolveGradeNames(UUID tenantId, UUID projectId, String locale) {
        GradeStructureJpaEntity structure = activeGradeStructure(tenantId, projectId);
        if (structure == null) return Map.of();
        Map<Integer, String> names = new HashMap<>();
        for (GradeJpaEntity g :
                grades.findAllByTenantIdAndGradeStructureIdOrderBySortOrderAsc(
                        tenantId, structure.getId())) {
            names.put(g.getGradeNumber(), gradeName(g, locale));
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
                ? "" : ReportLabels.localizeScoringMode(version.getScoringMode().name(), locale);
        String versionLabel = "v" + version.getVersionNumber();
        String status = version.getStatus() == null
                ? "" : ReportLabels.localizeStatus(version.getStatus().name(), locale);
        String methodologyName = resolveMethodologyName(tenantId, version, locale);

        List<FactorJpaEntity> factorList =
                factors.findAllByTenantIdAndMethodologyVersionIdOrderBySortOrderAsc(
                        tenantId, versionId);
        // BE-26 — batch every factor's levels in ONE tenant-scoped query (was one
        // findAllByTenantIdAndFactorId per factor → N+1). Global level_order ASC +
        // group-by-factorId preserves each factor's per-level order byte-for-byte.
        Set<UUID> factorIds = new LinkedHashSet<>();
        for (FactorJpaEntity f : factorList) factorIds.add(f.getId());
        Map<UUID, List<FactorLevelJpaEntity>> levelsByFactor = new HashMap<>();
        if (!factorIds.isEmpty()) {
            for (FactorLevelJpaEntity lvl :
                    factorLevels.findAllByTenantIdAndFactorIdInOrderByLevelOrderAsc(
                            tenantId, factorIds)) {
                levelsByFactor.computeIfAbsent(lvl.getFactorId(), k -> new ArrayList<>()).add(lvl);
            }
        }
        List<FactorRow> factorRows = new ArrayList<>();
        for (FactorJpaEntity f : factorList) {
            List<Map<String, String>> levels = new ArrayList<>();
            for (FactorLevelJpaEntity lvl :
                    levelsByFactor.getOrDefault(f.getId(), List.of())) {
                Map<String, String> lm = new LinkedHashMap<>();
                lm.put("code", nz(lvl.getCode()));
                lm.put("order", String.valueOf(lvl.getLevelOrder()));
                lm.put("points", lvl.getPoints() == null ? "" : lvl.getPoints().toPlainString());
                lm.put("label", I18nText.pick(lvl.getLabelI18n(), locale, nz(lvl.getCode())));
                levels.add(lm);
            }
            factorRows.add(new FactorRow(
                    nz(f.getCode()),
                    I18nText.pick(f.getNameI18n(), locale, nz(f.getCode())),
                    f.getWeight() == null ? 0 : f.getWeight().intValue(),
                    f.getMaxPoints() == null ? 0 : f.getMaxPoints().intValue(),
                    scoringMode,
                    levels));
        }
        return new MethodologySpec(methodologyName, versionLabel, status, factorRows);
    }

    /** Real methodology display name + version number (was "methodology vN"). */
    private String resolveMethodologyName(UUID tenantId, MethodologyVersionJpaEntity version, String locale) {
        String fallback = "v" + version.getVersionNumber();
        if (version.getMethodologyId() == null) return fallback;
        MethodologyJpaEntity m = methodologies
                .findByIdAndTenantId(version.getMethodologyId(), tenantId).orElse(null);
        if (m == null) return fallback;
        String name = I18nText.pick(m.getNameI18n(), locale, nz(m.getCode()));
        return name + " (v" + version.getVersionNumber() + ")";
    }

    private static String gradeName(GradeJpaEntity g, String locale) {
        return I18nText.pick(g.getNameI18n(), locale, "G" + g.getGradeNumber());
    }

    @Override
    public List<AuditEventRow> loadAuditEvents(UUID tenantId, UUID projectId,
                                               OffsetDateTime from, OffsetDateTime to,
                                               int limit) {
        if (tenantId == null || limit <= 0) return List.of();
        var page = auditLog.search(tenantId, null, null, null, null,
                from, to, PageRequest.of(0, Math.min(limit, 200)));

        List<SystemAuditLogJpaEntity> events = new ArrayList<>();
        Set<UUID> actorIds = new LinkedHashSet<>();
        for (SystemAuditLogJpaEntity e : page.getContent()) {
            // Project filter applied in-memory: most tenants will not have
            // millions of events per tenant and search() is page-bounded.
            if (projectId != null && e.getProjectId() != null
                    && !projectId.equals(e.getProjectId())) {
                continue;
            }
            events.add(e);
            if (e.getActorUserId() != null) actorIds.add(e.getActorUserId());
        }
        Map<UUID, String> actorNames = resolveActorDisplays(actorIds);

        List<AuditEventRow> rows = new ArrayList<>(events.size());
        for (SystemAuditLogJpaEntity e : events) {
            rows.add(new AuditEventRow(
                    e.getCreatedAt(),
                    nz(e.getAction()),
                    actorDisplay(actorNames, e.getActorUserId()),
                    nz(e.getEntityType()),
                    e.getEntityId() == null ? "" : e.getEntityId().toString(),
                    nz(e.getReason()),
                    nz(e.getCorrelationId())));
        }
        return rows;
    }

    @Override
    public EvaluationMatrix loadEvaluations(UUID tenantId, UUID projectId, String locale,
                                            EvaluationReportFilter filter) {
        EvaluationReportFilter activeFilter = filter == null ? EvaluationReportFilter.none() : filter;
        String projectName = projects.findByIdAndTenantId(projectId, tenantId)
                .map(p -> titleForProject(p, locale))
                .orElse("—");

        // Apply every filter dimension in the DB WHERE (NOT in-memory), so the page
        // cap caps the FILTERED set (PRD §5 EC-5). Built as a dynamic Specification
        // (a predicate per PRESENT dimension) so an absent dimension binds no NULL —
        // PostgreSQL rejects untyped NULL binds in the `(:param IS NULL OR ...)` idiom.
        var page = evaluations.findAll(
                EvaluationReportSpecifications.forReport(
                        tenantId, projectId,
                        activeFilter.methodologyVersionIds(),
                        activeFilter.evaluatorUserIds(),
                        activeFilter.dateFrom(), activeFilter.dateTo()),
                PageRequest.of(0, MAX_EVALUATIONS));

        // Collect distinct methodology version ids; build the column order
        // (FactorRef: code + localized name) by walking through the factors of
        // each version in sortOrder. Factors that recur across versions
        // deduplicate by code. The first version drives the methodology label.
        Set<UUID> methodologyVersionIds = new LinkedHashSet<>();
        for (EvaluationJpaEntity e : page.getContent()) {
            methodologyVersionIds.add(e.getMethodologyVersionId());
        }
        LinkedHashMap<UUID, String> factorCodeById = new LinkedHashMap<>();
        LinkedHashMap<String, FactorRef> factorByCode = new LinkedHashMap<>();
        // Per-version localized methodology label ("Name (vN)") — resolved ONCE per
        // distinct version id (reuse anchor: resolveMethodologyName), so the grading
        // XLSX methodology column never triggers an N+1 across rows. The first
        // version still drives the matrix-level methodologyName (PDF/DOCX meta).
        Map<UUID, String> methodologyLabelByVersion = new HashMap<>();
        String methodologyName = "—";
        boolean methodologyResolved = false;
        for (UUID mvId : methodologyVersionIds) {
            List<FactorJpaEntity> fs =
                    factors.findAllByTenantIdAndMethodologyVersionIdOrderBySortOrderAsc(
                            tenantId, mvId);
            for (FactorJpaEntity f : fs) {
                if (!factorCodeById.containsKey(f.getId())) {
                    factorCodeById.put(f.getId(), f.getCode());
                    factorByCode.putIfAbsent(f.getCode(),
                            new FactorRef(f.getCode(), I18nText.pick(f.getNameI18n(), locale, nz(f.getCode()))));
                }
            }
            MethodologyVersionJpaEntity version =
                    methodologyVersions.findByIdAndTenantId(mvId, tenantId).orElse(null);
            String label = version == null ? "" : resolveMethodologyName(tenantId, version, locale);
            methodologyLabelByVersion.put(mvId, label);
            if (!methodologyResolved && version != null) {
                methodologyName = label;
                methodologyResolved = true;
            }
        }
        List<FactorRef> factorColumns = new ArrayList<>(factorByCode.values());

        // PERF (P1) — batch-load every score for the whole page of evaluations in
        // ONE tenant-scoped query. Grouped by evaluation id in memory.
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

        // PERF (P1) — batch-load every referenced position in ONE tenant-scoped query.
        Set<UUID> positionIds = new LinkedHashSet<>();
        for (EvaluationJpaEntity ev : page.getContent()) {
            if (ev.getPositionId() != null) positionIds.add(ev.getPositionId());
        }
        Map<UUID, PositionJpaEntity> positionById = new HashMap<>();
        Set<UUID> departmentIds = new LinkedHashSet<>();
        if (!positionIds.isEmpty()) {
            for (PositionJpaEntity p : positions.findAllByTenantIdAndIdIn(tenantId, positionIds)) {
                positionById.put(p.getId(), p);
                if (p.getDepartmentId() != null) departmentIds.add(p.getDepartmentId());
            }
        }
        // Department tree (Departament = top-level ancestor; Bo'limi = own leaf dept
        // when it has a parent). Shared with the panel list surface via
        // DepartmentHierarchyResolver (BE-032): one tenant-scoped
        // findAllByTenantIdAndIdIn per level (org trees are shallow), no per-row N+1.
        Map<UUID, DepartmentJpaEntity> deptById =
                departmentHierarchy.loadClosure(tenantId, departmentIds);
        Map<UUID, String> deptNames = localizeDepartments(deptById, locale);

        // Grade names — resolve once for the project (active grade structure).
        Map<Integer, String> gradeNames = resolveGradeNames(tenantId, projectId, locale);

        // PERF (P1) — batch-resolve EVERY evaluator full name in ONE membership-gated
        // ActorNameResolver.resolveAll call (single source of truth — no N+1, no
        // cross-tenant leak). Ids come from already-tenant-loaded evaluation rows.
        Set<UUID> evaluatorUserIds = new LinkedHashSet<>();
        for (EvaluationJpaEntity ev : page.getContent()) {
            if (ev.getEvaluatorUserId() != null) evaluatorUserIds.add(ev.getEvaluatorUserId());
        }
        Map<UUID, String> evaluatorNames = actorNames.resolveAll(tenantId, evaluatorUserIds);

        // Within-panel evaluator order: EvaluatorRole enum ordinal, then resolved
        // evaluator name ASC (REQ — grading layout). Done HERE (we still hold the
        // enum + resolved name) so the render-side grouping keeps this order inside
        // each panel group. A stable sort keeps a deterministic tail order for rows
        // with no role / equal names. Standalone rows are unaffected by grouping.
        List<EvaluationJpaEntity> sortedRows = new ArrayList<>(page.getContent());
        sortedRows.sort(Comparator
                .comparingInt((EvaluationJpaEntity ev) ->
                        ev.getEvaluatorRole() == null
                                ? Integer.MAX_VALUE : ev.getEvaluatorRole().ordinal())
                .thenComparing(ev -> nz(evaluatorNames.get(ev.getEvaluatorUserId())),
                        Comparator.nullsLast(Comparator.naturalOrder())));

        int approved = 0;
        List<EvaluationRow> rows = new ArrayList<>(page.getNumberOfElements());
        for (EvaluationJpaEntity ev : sortedRows) {
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

            PositionJpaEntity p = ev.getPositionId() == null
                    ? null : positionById.get(ev.getPositionId());
            String posCode = p == null
                    ? (ev.getPositionId() == null ? "" : truncate(ev.getPositionId())) : p.getCode();
            String posTitle = p == null ? "" : titleFor(p, locale);

            // Departament (top-level ancestor) + Bo'limi (own leaf dept IF nested).
            UUID leafDeptId = p == null ? null : p.getDepartmentId();
            DepartmentHierarchyResolver.DepartmentSplit split =
                    departmentHierarchy.split(leafDeptId, deptById);
            String topDeptName = split.topLevelId() == null
                    ? "" : deptNames.getOrDefault(split.topLevelId(), "");
            // "added if exists, blank if not": a division only when the leaf differs
            // from the top-level ancestor (i.e. the position's department has a parent).
            String divisionName = split.divisionId() == null
                    ? "" : deptNames.getOrDefault(split.divisionId(), "");

            Integer gradeNum = ev.getAssignedGradeNumber();
            String gradeCode = gradeNum == null ? "" : "G" + gradeNum;
            String gradeName = gradeNum == null
                    ? "" : gradeNames.getOrDefault(gradeNum, "G" + gradeNum);

            String evaluatorName = ev.getEvaluatorUserId() == null
                    ? "" : nz(evaluatorNames.get(ev.getEvaluatorUserId()));
            String evaluatorRole = ev.getEvaluatorRole() == null
                    ? "" : ReportLabels.localizeEvaluatorRole(ev.getEvaluatorRole().name(), locale);
            String evaluationDate = formatUtcDate(ev.getSubmittedAt());
            String methodologyVersionLabel =
                    nz(methodologyLabelByVersion.get(ev.getMethodologyVersionId()));

            rows.add(new EvaluationRow(
                    posCode,
                    posTitle,
                    topDeptName,
                    ev.getStatus() == null ? "" : ReportLabels.localizeStatus(ev.getStatus().name(), locale),
                    scoresByCode,
                    ev.getDisplayedTotalScore() == null
                            ? "0" : ev.getDisplayedTotalScore().toPlainString(),
                    gradeCode,
                    gradeName,
                    divisionName,
                    evaluatorName,
                    evaluatorRole,
                    evaluationDate,
                    methodologyVersionLabel,
                    ev.getPanelId()));
        }

        List<PanelAverageRow> panelAverages = loadPanelAverages(
                tenantId, page.getContent(), factorCodeById, methodologyLabelByVersion,
                gradeNames, locale);

        return new EvaluationMatrix(
                projectName,
                methodologyName,
                page.getNumberOfElements(),
                approved,
                factorColumns,
                rows,
                buildFilterEcho(tenantId, activeFilter, locale),
                panelAverages);
    }

    /**
     * Build one {@link PanelAverageRow} per distinct non-null panel id that has
     * materialized {@code panel_factor_averages} rows (panel reached AVERAGED+).
     * Panels still collecting (AWAITING_EVALUATIONS, no averages) produce NO row.
     *
     * <p>Batch, tenant-scoped, no N+1 (BE-19): the per-factor averages for the WHOLE
     * page of panels load via {@code findAllByTenantIdAndPanelIdIn} in ONE round-trip
     * (grouped by panel id in memory), and the panel entities via
     * {@code findAllByTenantIdAndIdIn} in ONE more — the SAME batch pattern
     * {@code loadEvaluations} already uses ~15 lines above (scores/positions IdIn),
     * replacing the previous {@code findAllByTenantIdAndPanelId + findByIdAndTenantId}
     * per-panel pair. Factor ids map to the SAME stable factor codes the evaluator
     * rows use ({@code factorCodeById}). Output order (panel-id insertion order) and
     * the "no materialized average ⇒ no row" / "foreign panel ⇒ no row" semantics are
     * byte-identical to the per-panel loop.
     */
    private List<PanelAverageRow> loadPanelAverages(
            UUID tenantId, List<EvaluationJpaEntity> pageRows,
            Map<UUID, String> factorCodeById, Map<UUID, String> methodologyLabelByVersion,
            Map<Integer, String> gradeNames, String locale) {

        Set<UUID> panelIds = new LinkedHashSet<>();
        for (EvaluationJpaEntity ev : pageRows) {
            if (ev.getPanelId() != null) panelIds.add(ev.getPanelId());
        }
        if (panelIds.isEmpty()) return List.of();

        // BE-19 — ONE query for every panel's per-factor averages, grouped by panel.
        Map<UUID, List<PanelFactorAverageJpaEntity>> avgsByPanel = new HashMap<>();
        for (PanelFactorAverageJpaEntity a :
                panelFactorAverages.findAllByTenantIdAndPanelIdIn(tenantId, panelIds)) {
            avgsByPanel.computeIfAbsent(a.getPanelId(), k -> new ArrayList<>()).add(a);
        }
        // BE-19 — ONE query for every distinct panel entity (tenant-scoped).
        Map<UUID, EvaluationPanelJpaEntity> panelById = new HashMap<>();
        for (EvaluationPanelJpaEntity p : panels.findAllByTenantIdAndIdIn(tenantId, panelIds)) {
            panelById.put(p.getId(), p);
        }

        List<PanelAverageRow> out = new ArrayList<>();
        for (UUID panelId : panelIds) {
            List<PanelFactorAverageJpaEntity> avgs =
                    avgsByPanel.getOrDefault(panelId, List.of());
            // No materialized averages ⇒ panel has not reached AVERAGED ⇒ NO row.
            if (avgs.isEmpty()) continue;

            EvaluationPanelJpaEntity panel = panelById.get(panelId);
            if (panel == null) continue; // tenant-scoped: a foreign id yields nothing

            Map<String, String> avgByCode = new LinkedHashMap<>();
            for (PanelFactorAverageJpaEntity a : avgs) {
                String code = factorCodeById.get(a.getFactorId());
                if (code == null) continue;
                BigDecimal v = a.getAvgRawFactorScore();
                avgByCode.put(code, v == null ? "" : v.toPlainString());
            }

            Integer gradeNum = panel.getAssignedGradeNumber();
            String gradeCode = gradeNum == null ? "" : "G" + gradeNum;
            String gradeName = gradeNum == null
                    ? "" : gradeNames.getOrDefault(gradeNum, "G" + gradeNum);

            out.add(new PanelAverageRow(
                    panelId,
                    panel.getStatus() == null
                            ? "" : ReportLabels.localizeStatus(panel.getStatus().name(), locale),
                    nz(methodologyLabelByVersion.get(panel.getMethodologyVersionId())),
                    formatUtcDate(panel.getAveragedAt()),
                    avgByCode,
                    panel.getDisplayedTotalScore() == null
                            ? "" : panel.getDisplayedTotalScore().toPlainString(),
                    gradeCode,
                    gradeName));
        }
        return out;
    }

    /**
     * Build the localized, name-resolved echo of the applied filters for the
     * report meta block (PRD AC-4.3). Name resolution stays INSIDE the port:
     * methodology version → "Name (vN)" via {@link #resolveMethodologyName}
     * (reuse anchor), evaluator user id → display name via
     * {@link ActorNameResolver#resolveAll} (single source of truth — no third
     * resolver). Dates are rendered as inclusive calendar days.
     */
    private FilterEcho buildFilterEcho(UUID tenantId, EvaluationReportFilter f, String locale) {
        if (f == null || f.isEmpty()) return FilterEcho.empty();

        String period = "";
        if (f.dateFrom() != null || f.dateTo() != null) {
            String from = f.dateFrom() == null ? "…" : f.dateFrom().toLocalDate().toString();
            String to = f.dateTo() == null ? "…" : f.dateTo().toLocalDate().toString();
            period = from + " – " + to;
        }

        String methodologies = "";
        if (!f.methodologyVersionIds().isEmpty()) {
            List<String> names = new ArrayList<>();
            for (UUID mvId : new LinkedHashSet<>(f.methodologyVersionIds())) {
                MethodologyVersionJpaEntity version =
                        methodologyVersions.findByIdAndTenantId(mvId, tenantId).orElse(null);
                // Tenant-scoped: a foreign id resolves to null and is shown as a
                // privacy-safe truncation (never a full UUID), consistent with the
                // zero-rows outcome from the filtered query.
                names.add(version == null
                        ? truncate(mvId) : resolveMethodologyName(tenantId, version, locale));
            }
            methodologies = String.join(", ", names);
        }

        String evaluators = "";
        if (!f.evaluatorUserIds().isEmpty()) {
            // Reuse ActorNameResolver (Item 4 — NO DUPLICATION). TODO(reports):
            // consolidate the pre-existing ad-hoc resolveActorDisplays() onto this
            // resolver for the audit/executive reports too.
            Map<UUID, String> resolved = actorNames.resolveAll(tenantId, f.evaluatorUserIds());
            List<String> names = new ArrayList<>();
            for (UUID uid : new LinkedHashSet<>(f.evaluatorUserIds())) {
                String name = resolved.get(uid);
                names.add(name != null && !name.isBlank() ? name : truncate(uid));
            }
            evaluators = String.join(", ", names);
        }

        return new FilterEcho(period, evaluators, methodologies);
    }

    @Override
    public ExecutiveKpi loadExecutiveKpi(UUID tenantId, UUID projectId, String locale,
                                         EvaluationReportFilter filter) {
        EvaluationReportFilter activeFilter = filter == null ? EvaluationReportFilter.none() : filter;
        String projectName;
        String projectStatus = "";
        String periodFrom = "";
        String periodTo = "";
        Optional<ProjectJpaEntity> project = projects.findByIdAndTenantId(projectId, tenantId);
        if (project.isPresent()) {
            ProjectJpaEntity p = project.get();
            projectName = titleForProject(p, locale);
            projectStatus = p.getStatus() == null
                    ? "" : ReportLabels.localizeStatus(p.getStatus().name(), locale);
            periodFrom = p.getStartDate() == null ? "" : p.getStartDate().toString();
            periodTo = p.getEndDate() == null ? "" : p.getEndDate().toString();
        } else {
            projectName = "—";
        }

        // positionCount is NOT evaluation-scoped — stays project-wide regardless
        // of the filter.
        int positionCount = (int) positions.search(tenantId, projectId, null, null, null,
                PageRequest.of(0, 1)).getTotalElements();

        // Evaluation-scoped KPIs (evaluatedCount, APPROVED/LOCKED count, distinct
        // assigned-grade set). When a filter is active, derive them from the
        // FILTERED evaluation set via the EXISTING finder loadEvaluations uses
        // (findForEvaluationReport) — no new query. Empty filter keeps the legacy
        // unfiltered findAllByTenantIdAndProjectId path byte-for-byte (no regression).
        var evalPage = activeFilter.isEmpty()
                ? evaluations.findAllByTenantIdAndProjectId(tenantId, projectId,
                        PageRequest.of(0, MAX_EVALUATIONS))
                : evaluations.findAll(
                        EvaluationReportSpecifications.forReport(
                                tenantId, projectId,
                                activeFilter.methodologyVersionIds(),
                                activeFilter.evaluatorUserIds(),
                                activeFilter.dateFrom(), activeFilter.dateTo()),
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

        // TODO(reports-p2): scope the executive audit-event count to the project
        // instead of the whole tenant (currently tenant-wide totalElements).
        var auditPage = auditLog.search(tenantId, null, null, null, null, null, null,
                PageRequest.of(0, 200));
        int auditCount = (int) auditPage.getTotalElements();

        List<SystemAuditLogJpaEntity> recentEvents = new ArrayList<>();
        Set<UUID> actorIds = new LinkedHashSet<>();
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
            recentEvents.add(e);
            if (e.getActorUserId() != null) actorIds.add(e.getActorUserId());
            if (recentEvents.size() >= 5) break;
        }
        Map<UUID, String> actorNames = resolveActorDisplays(actorIds);

        List<RecentApprovalRow> recent = new ArrayList<>(recentEvents.size());
        for (SystemAuditLogJpaEntity e : recentEvents) {
            recent.add(new RecentApprovalRow(
                    e.getCreatedAt(),
                    nz(e.getAction()),
                    nz(e.getEntityType()),
                    e.getEntityId() == null ? "" : e.getEntityId().toString(),
                    actorDisplay(actorNames, e.getActorUserId())));
        }
        recent.sort(Comparator.comparing(RecentApprovalRow::timestamp,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return new ExecutiveKpi(projectName, projectStatus, periodFrom, periodTo,
                positionCount, evaluatedCount, approved, distinctGrades.size(),
                auditCount, recent,
                buildFilterEcho(tenantId, activeFilter, locale));
    }

    @Override
    public String projectName(UUID tenantId, UUID projectId, String locale) {
        if (tenantId == null || projectId == null) return "—";
        return projects.findByIdAndTenantId(projectId, tenantId)
                .map(p -> titleForProject(p, locale))
                .orElse("—");
    }

    @Override
    public String tenantName(UUID tenantId, String locale) {
        if (tenantId == null) return "—";
        return tenants.findById(tenantId)
                .map(t -> t.getDisplayName() == null || t.getDisplayName().isBlank()
                        ? nzOrDash(t.getSlug()) : t.getDisplayName())
                .orElse("—");
    }

    // ── cross-module name resolution (batch, tenant-scoped) ─────────────────

    private static Set<UUID> collectDepartmentIds(List<PositionJpaEntity> ps) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (PositionJpaEntity p : ps) {
            if (p.getDepartmentId() != null) ids.add(p.getDepartmentId());
        }
        return ids;
    }

    /** Batch-resolve department id → localized name (tenant-scoped, no N+1). */
    private Map<UUID, String> resolveDepartmentNames(UUID tenantId, Set<UUID> ids, String locale) {
        if (tenantId == null || ids == null || ids.isEmpty()) return Map.of();
        Map<UUID, String> names = new HashMap<>();
        for (DepartmentJpaEntity d : departments.findAllByTenantIdAndIdIn(tenantId, ids)) {
            names.put(d.getId(), I18nText.pick(d.getNameI18n(), locale, nz(d.getCode())));
        }
        return names;
    }

    /** Localize every loaded department (closure) id → localized name. */
    private static Map<UUID, String> localizeDepartments(
            Map<UUID, DepartmentJpaEntity> byId, String locale) {
        Map<UUID, String> names = new HashMap<>(byId.size());
        for (DepartmentJpaEntity d : byId.values()) {
            names.put(d.getId(), I18nText.pick(d.getNameI18n(), locale, nz(d.getCode())));
        }
        return names;
    }

    private static final DateTimeFormatter UTC_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * Format a timestamp as a UTC calendar date {@code yyyy-MM-dd} (no time) for
     * the grading XLSX evaluation-date column. Null ⇒ blank (DRAFT / unsubmitted).
     */
    private static String formatUtcDate(OffsetDateTime ts) {
        if (ts == null) return "";
        return ts.withOffsetSameInstant(ZoneOffset.UTC).toLocalDate().format(UTC_DATE);
    }

    /**
     * Batch-resolve actor user id → display (full name, else email). Users are
     * control-plane (no tenant_id), so {@code findAllById} is permitted here.
     */
    private Map<UUID, String> resolveActorDisplays(Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        Map<UUID, String> names = new HashMap<>();
        for (UserJpaEntity u : users.findAllById(ids)) {
            String display = u.getFullName() != null && !u.getFullName().isBlank()
                    ? u.getFullName()
                    : nz(u.getEmail());
            names.put(u.getId(), display);
        }
        return names;
    }

    /**
     * Actor display column — resolved name, else a privacy-safe first-8-chars
     * truncation of the id (NEVER a full UUID in a human report column).
     */
    private static String actorDisplay(Map<UUID, String> names, UUID actorUserId) {
        if (actorUserId == null) return "";
        String resolved = names.get(actorUserId);
        if (resolved != null && !resolved.isBlank()) return resolved;
        return truncate(actorUserId);
    }

    private static String truncate(UUID id) {
        String s = id.toString();
        return (s.length() <= 8 ? s : s.substring(0, 8)) + "…";
    }

    private static String titleFor(PositionJpaEntity p, String locale) {
        return I18nText.pick(p.getTitleI18n(), locale, p.getCode());
    }

    private static String titleForProject(ProjectJpaEntity p, String locale) {
        return I18nText.pick(p.getNameI18n(), locale, p.getCode());
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String nzOrDash(String s) { return s == null || s.isBlank() ? "—" : s; }
}

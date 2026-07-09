package uz.hrlab.grading.evaluation.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.ActorNameResolver;
import uz.hrlab.grading.access.application.DepartmentScopeFilter;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.access.domain.DepartmentScopePolicy;
import uz.hrlab.grading.access.infrastructure.UserDepartmentScopeRepository;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.evaluation.api.PanelAssignmentResponse;
import uz.hrlab.grading.evaluation.api.PanelDetailResponse;
import uz.hrlab.grading.evaluation.api.PanelResponse;
import uz.hrlab.grading.evaluation.api.PanelResultResponse;
import uz.hrlab.grading.evaluation.api.RosterSuggestionResponse;
import uz.hrlab.grading.organization.infrastructure.DepartmentJpaEntity;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.domain.PanelAssignmentStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelAssignmentJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelAssignmentRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelFactorAverageJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelFactorAverageRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorRepository;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * BE-13 — panel read surface.
 *
 * <ul>
 *   <li>List + detail (roster + progress N/M) require {@code EVALUATION_READ} —
 *       blind-safe (STATUS only, never scores; no live running average while
 *       collecting — REQ-ISO-5).</li>
 *   <li>Averaged result ({@link #getResult}) requires
 *       {@code CAMPAIGN_RESULTS_VIEW} AND panel status &gt;= AVERAGED; before
 *       that it is forbidden/empty.</li>
 * </ul>
 *
 * <p>Every load is tenant-scoped {@code findByIdAndTenantId}; names reuse the
 * shared {@link ActorNameResolver} (no new resolver).
 */
@Service
public class PanelQueries {

    /** Advisory suggestion cap — never return an unbounded candidate list (BE-5). */
    public static final int MAX_SUGGESTIONS = 50;

    private final PanelRepository panels;
    private final PanelAssignmentRepository assignments;
    private final PanelFactorAverageRepository averages;
    private final EvaluationRepository evaluations;
    private final EvaluationScoreRepository scores;
    private final PositionRepository positions;
    private final FactorRepository factors;
    private final ActorNameResolver actorNames;
    private final AbacGate abacGate;
    private final DepartmentRepository departments;
    private final UserDepartmentScopeRepository departmentScopes;
    private final DepartmentScopeFilter departmentScopeFilter;

    public PanelQueries(PanelRepository panels,
                        PanelAssignmentRepository assignments,
                        PanelFactorAverageRepository averages,
                        EvaluationRepository evaluations,
                        EvaluationScoreRepository scores,
                        PositionRepository positions,
                        FactorRepository factors,
                        ActorNameResolver actorNames,
                        AbacGate abacGate,
                        DepartmentRepository departments,
                        UserDepartmentScopeRepository departmentScopes,
                        DepartmentScopeFilter departmentScopeFilter) {
        this.panels = panels;
        this.assignments = assignments;
        this.averages = averages;
        this.evaluations = evaluations;
        this.scores = scores;
        this.positions = positions;
        this.factors = factors;
        this.actorNames = actorNames;
        this.abacGate = abacGate;
        this.departments = departments;
        this.departmentScopes = departmentScopes;
        this.departmentScopeFilter = departmentScopeFilter;
    }

    /**
     * BE-5 — ADVISORY roster suggestion: department-director candidates for a
     * department. Resolves the department subtree (reusing
     * {@code DepartmentRepository.findSubtreeIds}) and returns distinct users who
     * hold an ACTIVE scope intersecting that subtree AND carry the dept-director
     * role ({@link DepartmentScopePolicy#DEPARTMENT_DIRECTOR_ROLE_CODE} — single
     * source of truth) on an ACTIVE membership.
     *
     * <p>Gates: {@code EVALUATION_PANEL_MANAGE} (the panel-setup permission) PLUS
     * the ABAC department READ gate, so a department-scoped caller cannot probe
     * directors of a department outside their own subtree (out-of-subtree → 404,
     * no existence reveal). The result is ADVISORY only — the server re-validates
     * membership on the actual assign.
     */
    @Transactional(readOnly = true)
    public RosterSuggestionResponse suggestDepartmentDirector(UUID projectId, UUID departmentId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.EVALUATION_PANEL_MANAGE)) {
            throw new PermissionDeniedException();
        }
        if (projectId == null || departmentId == null) {
            throw new ValidationException("project_id and department_id are required");
        }
        UUID tenant = ctx.tenantId();
        // ABAC read gate on the target department — denies an out-of-subtree probe
        // with a 404 + ACCESS_DENIED_BY_ABAC audit (no existence reveal).
        abacGate.enforceCanReadDepartment(ctx, departmentId, projectId, null);

        // Expand to subtree so a scope on a parent department also surfaces its
        // descendants' directors. Reuses the org-module CTE — no reimplementation.
        List<UUID> subtree = departments.findSubtreeIds(List.of(departmentId), tenant);
        if (subtree.isEmpty()) {
            subtree = List.of(departmentId);
        }
        List<RosterSuggestionResponse.Candidate> candidates = departmentScopes
                .findDeptScopedCandidates(tenant, subtree,
                        DepartmentScopePolicy.DEPARTMENT_DIRECTOR_ROLE_CODE).stream()
                .limit(MAX_SUGGESTIONS)
                .map(c -> new RosterSuggestionResponse.Candidate(c.getUserId(), c.getFullName()))
                .toList();
        return new RosterSuggestionResponse(departmentId, candidates);
    }

    /**
     * REQ-CEO — optional {@code statuses} filter for the org-wide view. When
     * {@code statuses} is non-empty AND no projectId/positionId is given (the CEO's
     * tenant-wide "what's awaiting me" pull), the page is sourced from
     * {@link PanelRepository#findAllByTenantIdAndStatusIn}. In EVERY other case the
     * behavior is byte-identical to before (statuses null/empty, or scoped by
     * project/position). The downstream batched mapping is UNCHANGED and shared —
     * status filtering only swaps which repository query produces the source page.
     *
     * <p>Combining a status filter with projectId/positionId is intentionally NOT
     * supported here: the project/position-scoped paths already narrow the page to a
     * handful of panels, so an in-memory status post-filter would be the simpler add
     * but would silently break paging totals. The CEO org view is statuses-only;
     * project/position-scoped callers keep the existing unfiltered paths.
     *
     * <p>EPIC-001 — OBJECT-LEVEL ABAC. This org-wide list exposes each panel's
     * {@code raw/displayed_total_score} + {@code assigned_grade_number}, so it MUST
     * apply the same department dimension {@link #getPanelDetail} enforces per-row
     * (there via {@code AbacGate.enforceCanReadPosition} →
     * {@code DepartmentScopePolicy}). The decision point is the SHARED
     * {@link DepartmentScopeFilter} — the exact mechanism {@code FindPositionQuery}
     * and {@code EvaluationQueries} already use for their list reads (no second
     * filtering approach):
     * <ul>
     *   <li>{@link Optional#empty()} — tenant-wide bypass (CEO / HR Director /
     *       Company Admin / HRLab staff): UNFILTERED. The CEO Panel Overview stays
     *       org-wide across every department (regression-locked below).</li>
     *   <li>present, non-empty — a department-scoped caller (DEPARTMENT_MANAGER /
     *       EVALUATION_COMMITTEE_MEMBER): confine to panels whose position lives in
     *       the assigned subtree.</li>
     *   <li>present, EMPTY — department-scoped but assigned nothing: ZERO rows
     *       (fail-closed).</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public Page<PanelResponse> list(UUID projectId, UUID positionId,
                                    Collection<EvaluationPanelStatus> statuses, Pageable pageable) {
        TenantContext ctx = requireRead();
        UUID tenant = ctx.tenantId();

        // EPIC-001 — resolve the department allow-list ONCE via the shared filter.
        // Empty ⇒ unfiltered (bypass / CEO); present ⇒ confine; present-empty ⇒
        // fail-closed. Same contract as FindPositionQuery / EvaluationQueries.
        Optional<Set<UUID>> scope = departmentScopeFilter.allowedDepartmentIds(ctx);

        boolean filterByStatus = statuses != null && !statuses.isEmpty()
                && projectId == null && positionId == null;
        Page<EvaluationPanelJpaEntity> page;
        if (scope.isPresent()) {
            if (scope.get().isEmpty()) {
                return Page.<PanelResponse>empty(pageable); // scoped but no assignment → no rows
            }
            // ONE department-scoped finder reproduces the SAME optional projectId /
            // positionId / status-pull branch semantics as the unfiltered path
            // below, plus the department confinement. Non-status branches pass the
            // full status set (a no-op filter) so there is no duplicated per-branch
            // scoped query — mirrors EvaluationQueries.list's single findInDepartments.
            Collection<EvaluationPanelStatus> effectiveStatuses = filterByStatus
                    ? statuses : EnumSet.allOf(EvaluationPanelStatus.class);
            page = panels.findInDepartments(
                    tenant, projectId, positionId, effectiveStatuses, scope.get(), pageable);
        } else if (filterByStatus) {
            page = panels.findAllByTenantIdAndStatusIn(tenant, statuses, pageable);
        } else if (projectId != null && positionId != null) {
            page = panels.findAllByTenantIdAndProjectIdAndPositionId(
                    tenant, projectId, positionId, pageable);
        } else if (positionId != null) {
            page = panels.findAllByTenantIdAndPositionId(tenant, positionId, pageable);
        } else if (projectId != null) {
            page = panels.findAllByTenantIdAndProjectId(tenant, projectId, pageable);
        } else {
            page = panels.findAllByTenantId(tenant, pageable);
        }
        if (page.isEmpty()) {
            return page.map(p -> null);
        }

        // PERF — batch the per-row lookups the old toSummary() did one panel at a
        // time (was 1 + 2N queries for an N-panel page → a position read + a roster
        // read PER panel). Now: ONE position read + ONE roster read for the whole
        // page, grouped in memory. Output is byte-identical to the per-row path.
        Set<UUID> panelIds = new HashSet<>();
        Set<UUID> positionIds = new HashSet<>();
        page.getContent().forEach(p -> {
            panelIds.add(p.getId());
            positionIds.add(p.getPositionId());
        });

        Map<UUID, Map<String, String>> titleByPosition = new HashMap<>();
        // departmentId per position, for the Departament/Bo'limi split below.
        Map<UUID, UUID> deptByPosition = new HashMap<>();
        Set<UUID> leafDeptIds = new HashSet<>();
        for (PositionJpaEntity p : positions.findAllByTenantIdAndIdIn(tenant, positionIds)) {
            titleByPosition.put(p.getId(), p.getTitleI18n());
            if (p.getDepartmentId() != null) {
                deptByPosition.put(p.getId(), p.getDepartmentId());
                leafDeptIds.add(p.getDepartmentId());
            }
        }

        // Departament = top-level ancestor department; Bo'limi = the position's OWN
        // leaf department when it is nested (differs from the ancestor). Reuses the
        // same batched ancestor-walk pattern the evaluation report uses
        // (DefaultReportDataPort.loadDepartmentClosure/topLevelAncestor): ONE
        // tenant-scoped findAllByTenantIdAndIdIn per tree level (org trees are
        // shallow) — no per-panel N+1, no cross-tenant leak (tenant pinned).
        Map<UUID, DepartmentJpaEntity> deptById = loadDepartmentClosure(tenant, leafDeptIds);

        // Group ACTIVE roster seats by panel; precompute (active, completed) counts.
        Map<UUID, int[]> rosterCounts = new HashMap<>(); // panelId -> [active, completed]
        for (PanelAssignmentJpaEntity a : assignments.findAllByTenantIdAndPanelIdIn(tenant, panelIds)) {
            if (!a.getAssignmentStatus().isActive()) {
                continue;
            }
            int[] c = rosterCounts.computeIfAbsent(a.getPanelId(), k -> new int[2]);
            c[0]++;
            if (a.getAssignmentStatus() == PanelAssignmentStatus.COMPLETED) {
                c[1]++;
            }
        }

        // P0-B display-integrity — for a panel that is NO LONGER collecting
        // (AVERAGED / SUBMITTED / APPROVED / LOCKED), the "experts" count the CEO
        // sees MUST reflect the sheets that CONTRIBUTED to the average, not the
        // currently-active assignment seats. The materialized
        // panel_factor_averages.evaluator_count is exactly that denominator (the
        // COMPLETED sheets that fed the mean). Batch-loaded for the whole page in
        // ONE tenant-scoped query. Collecting panels keep the live roster count.
        Map<UUID, Integer> contributingByPanel = new HashMap<>();
        averages.findEvaluatorCountsByPanelIds(tenant, panelIds)
                .forEach(v -> contributingByPanel.put(v.getPanelId(), v.getEvaluatorCount()));

        return page.map(p -> {
            int[] c = rosterCounts.getOrDefault(p.getId(), EMPTY_COUNTS);
            int active = c[0];
            int completed = c[1];
            // Non-collecting panel with a materialized average → show the
            // contributing denominator (preserves the api field name/shape).
            if (!p.getStatus().isCollecting()) {
                Integer contributing = contributingByPanel.get(p.getId());
                if (contributing != null) {
                    active = contributing;
                    completed = contributing;
                }
            }
            // Departament (top-level ancestor) + Bo'limi (own leaf IF nested).
            UUID leafDeptId = deptByPosition.get(p.getPositionId());
            UUID rootDeptId = topLevelAncestor(leafDeptId, deptById);
            DepartmentJpaEntity rootDept = rootDeptId == null ? null : deptById.get(rootDeptId);
            Map<String, String> departmentLabel = rootDept == null ? null : rootDept.getNameI18n();
            // Division only when the leaf differs from the ancestor (has a parent).
            Map<String, String> divisionLabel = null;
            if (leafDeptId != null && rootDeptId != null && !leafDeptId.equals(rootDeptId)) {
                DepartmentJpaEntity leafDept = deptById.get(leafDeptId);
                divisionLabel = leafDept == null ? null : leafDept.getNameI18n();
            }
            return PanelResponse.from(p.toDomain(),
                    titleByPosition.get(p.getPositionId()),
                    departmentLabel, divisionLabel, active, completed);
        });
    }

    private static final int[] EMPTY_COUNTS = new int[2];

    @Transactional(readOnly = true)
    public PanelDetailResponse getPanelDetail(UUID panelId) {
        TenantContext ctx = requireRead();
        UUID tenant = ctx.tenantId();
        EvaluationPanelJpaEntity panel = panels.findByIdAndTenantId(panelId, tenant)
                .orElseThrow(TenantAccessDeniedException::new);
        // ABAC read gate via the panel's position (inherits department).
        PositionJpaEntity position = positions
                .findByIdAndTenantId(panel.getPositionId(), tenant)
                .orElseThrow(TenantAccessDeniedException::new);
        abacGate.enforceCanReadPosition(ctx, position.getId(), panel.getProjectId(),
                position.getDepartmentId(), panel.getStatus());

        List<PanelAssignmentJpaEntity> roster = assignments
                .findAllByTenantIdAndPanelId(tenant, panelId).stream()
                .filter(a -> a.getAssignmentStatus().isActive())
                .toList();
        Set<UUID> userIds = new HashSet<>();
        roster.forEach(a -> userIds.add(a.getEvaluatorUserId()));
        Map<UUID, String> names = actorNames.resolveAll(tenant, userIds);

        List<PanelAssignmentResponse> rosterDto = new ArrayList<>(roster.size());
        int completed = 0;
        for (PanelAssignmentJpaEntity a : roster) {
            if (a.getAssignmentStatus() == PanelAssignmentStatus.COMPLETED) {
                completed++;
            }
            rosterDto.add(PanelAssignmentResponse.from(a.toDomain(),
                    names.getOrDefault(a.getEvaluatorUserId(), null)));
        }

        // P0-B display-integrity (mirror of the list surface) — once the panel is
        // no longer collecting, the summary "experts" count must reflect the sheets
        // that contributed to the average (the materialized denominator), not the
        // current active-seat count. The detail roster itself still lists every
        // active seat; only the SUMMARY counts are corrected so the CEO header
        // ("N из M") is honest. Collecting panels keep the live roster count.
        int summaryEvaluators = roster.size();
        int summaryCompleted = completed;
        if (!panel.getStatus().isCollecting()) {
            Integer contributing = averages.findAllByTenantIdAndPanelId(tenant, panelId).stream()
                    .map(PanelFactorAverageJpaEntity::getEvaluatorCount)
                    .max(Integer::compareTo)
                    .orElse(null);
            if (contributing != null) {
                summaryEvaluators = contributing;
                summaryCompleted = contributing;
            }
        }
        PanelResponse summary = PanelResponse.from(
                panel.toDomain(), position.getTitleI18n(), summaryEvaluators, summaryCompleted);
        return new PanelDetailResponse(summary, rosterDto, summaryCompleted, summaryEvaluators);
    }

    /**
     * Averaged result — GATED. Requires {@code CAMPAIGN_RESULTS_VIEW} and a panel
     * that has reached AVERAGED (no running average while collecting). Returns the
     * per-factor averages + per-evaluator breakdown for result-viewers.
     */
    @Transactional(readOnly = true)
    public PanelResultResponse getResult(UUID panelId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.CAMPAIGN_RESULTS_VIEW)) {
            throw new PermissionDeniedException();
        }
        UUID tenant = ctx.tenantId();
        EvaluationPanelJpaEntity panel = panels.findByIdAndTenantId(panelId, tenant)
                .orElseThrow(TenantAccessDeniedException::new);
        PositionJpaEntity position = positions
                .findByIdAndTenantId(panel.getPositionId(), tenant)
                .orElseThrow(TenantAccessDeniedException::new);
        abacGate.enforceCanReadPosition(ctx, position.getId(), panel.getProjectId(),
                position.getDepartmentId(), panel.getStatus());

        // No result before AVERAGED (REQ-ISO-5) — fail closed.
        if (panel.getStatus() == EvaluationPanelStatus.COLLECTING
                || panel.getStatus() == EvaluationPanelStatus.AWAITING_EVALUATIONS) {
            throw new TenantAccessDeniedException();
        }

        List<PanelFactorAverageJpaEntity> avgRows = averages
                .findAllByTenantIdAndPanelId(tenant, panelId);

        // Per-evaluator breakdown: every done-scoring contributing sheet (COMPLETE,
        // SUBMITTED, APPROVED or LOCKED). SUBMITTED must be included — a panel
        // evaluator who hit the per-sheet "Submit" otherwise vanishes from the
        // breakdown the CEO reviews, so the result would not match the averaged
        // denominator. Single source: contributesToPanelResult().
        List<EvaluationJpaEntity> sheets = evaluations
                .findAllByTenantIdAndPanelId(tenant, panelId).stream()
                .filter(e -> e.getStatus().contributesToPanelResult())
                .toList();
        // factor_id -> list of (evaluator, role, score)
        Map<UUID, List<PanelResultResponse.PerEvaluator>> perFactor = new HashMap<>();
        Set<UUID> evalUserIds = new HashSet<>();
        Set<UUID> sheetIds = new HashSet<>();
        sheets.forEach(s -> {
            evalUserIds.add(s.getEvaluatorUserId());
            sheetIds.add(s.getId());
        });
        Map<UUID, String> names = actorNames.resolveAll(tenant, evalUserIds);

        // PERF — batch every contributing sheet's scores in ONE tenant-scoped query
        // (was one findAllByTenantIdAndEvaluationId PER sheet → N round-trips that
        // grow with the evaluator count). Grouped by evaluation id in memory, then
        // attributed to the owning sheet for the per-factor breakdown.
        Map<UUID, EvaluationJpaEntity> sheetById = new HashMap<>();
        sheets.forEach(s -> sheetById.put(s.getId(), s));
        for (EvaluationScoreJpaEntity s : scores.findAllByTenantIdAndEvaluationIdIn(tenant, sheetIds)) {
            EvaluationJpaEntity sheet = sheetById.get(s.getEvaluationId());
            if (sheet == null) {
                continue;
            }
            perFactor.computeIfAbsent(s.getFactorId(), k -> new ArrayList<>())
                    .add(new PanelResultResponse.PerEvaluator(
                            sheet.getEvaluatorUserId(),
                            names.getOrDefault(sheet.getEvaluatorUserId(), null),
                            sheet.getEvaluatorRole(),
                            s.getRawFactorScore()));
        }

        String locale = ctx.locale() == null ? "ru-RU" : ctx.locale();
        List<PanelResultResponse.FactorAverage> factorAverages = new ArrayList<>(avgRows.size());
        for (PanelFactorAverageJpaEntity row : avgRows) {
            FactorJpaEntity factor = factors
                    .findByIdAndTenantId(row.getFactorId(), tenant).orElse(null);
            List<PanelResultResponse.PerEvaluator> contributors =
                    perFactor.getOrDefault(row.getFactorId(), List.of());
            factorAverages.add(new PanelResultResponse.FactorAverage(
                    row.getFactorId(),
                    factor == null ? null : factor.getNameI18n(),
                    row.getAvgRawFactorScore(),
                    row.getEvaluatorCount(),
                    contributors,
                    disagreementRange(contributors)));
        }
        return new PanelResultResponse(
                panelId, panel.getDisplayedTotalScore(), panel.getRawTotalScore(),
                avgRows.isEmpty() ? 0 : avgRows.get(0).getEvaluatorCount(),
                factorAverages, panel.getGradeBandId(), panel.getAssignedGradeNumber());
    }

    /** max - min of the per-evaluator raw scores for a factor (null when < 2). */
    private static BigDecimal disagreementRange(List<PanelResultResponse.PerEvaluator> rows) {
        if (rows == null || rows.size() < 2) {
            return null;
        }
        BigDecimal min = null;
        BigDecimal max = null;
        for (PanelResultResponse.PerEvaluator r : rows) {
            BigDecimal v = r.rawFactorScore() == null ? BigDecimal.ZERO : r.rawFactorScore();
            if (min == null || v.compareTo(min) < 0) min = v;
            if (max == null || v.compareTo(max) > 0) max = v;
        }
        return max.subtract(min);
    }

    private TenantContext requireRead() {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.EVALUATION_READ)) {
            throw new PermissionDeniedException();
        }
        return ctx;
    }

    /**
     * Load the department CLOSURE (leaf departments + every ancestor up to the
     * root) so the panel list can split Departament (top-level ancestor) from
     * Bo'limi (own leaf, when nested). Reuses the same batched, tenant-scoped
     * approach as {@code DefaultReportDataPort.loadDepartmentClosure}: each level
     * resolves the as-yet-unseen parent ids in ONE {@code findAllByTenantIdAndIdIn}
     * (org trees are shallow → a handful of round-trips, no per-row N+1). A safety
     * bound makes a corrupt cyclic {@code parent_id} chain terminate. {@code tenant}
     * is pinned in every query, so a cross-tenant parent contributes nothing.
     */
    private Map<UUID, DepartmentJpaEntity> loadDepartmentClosure(UUID tenant, Set<UUID> leafIds) {
        if (tenant == null || leafIds == null || leafIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, DepartmentJpaEntity> byId = new HashMap<>();
        Set<UUID> frontier = new LinkedHashSet<>(leafIds);
        int safety = 0;
        while (!frontier.isEmpty() && safety++ < 64) {
            List<DepartmentJpaEntity> loaded = departments.findAllByTenantIdAndIdIn(tenant, frontier);
            for (DepartmentJpaEntity d : loaded) {
                byId.put(d.getId(), d);
            }
            Set<UUID> nextParents = new LinkedHashSet<>();
            for (DepartmentJpaEntity d : loaded) {
                UUID parent = d.getParentId();
                if (parent != null && !byId.containsKey(parent)) {
                    nextParents.add(parent);
                }
            }
            frontier = nextParents;
        }
        return byId;
    }

    /**
     * Walk {@code parentId} up from {@code leafId} to the root within the loaded
     * closure. Returns the top-level ancestor id (== {@code leafId} when the leaf
     * has no parent). Null leaf or an ancestor missing from the closure stops the
     * walk at the last resolved node; a {@code visited} guard makes a cyclic chain
     * terminate. Mirrors {@code DefaultReportDataPort.topLevelAncestor}.
     */
    private static UUID topLevelAncestor(UUID leafId, Map<UUID, DepartmentJpaEntity> byId) {
        if (leafId == null) {
            return null;
        }
        UUID current = leafId;
        Set<UUID> visited = new LinkedHashSet<>();
        while (current != null && visited.add(current)) {
            DepartmentJpaEntity d = byId.get(current);
            if (d == null) {
                return current; // last resolved node is the best-known root
            }
            UUID parent = d.getParentId();
            if (parent == null) {
                return current;
            }
            current = parent;
        }
        return current;
    }
}

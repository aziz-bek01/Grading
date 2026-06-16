package uz.hrlab.grading.evaluation.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.ActorNameResolver;
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
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
                        UserDepartmentScopeRepository departmentScopes) {
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

    @Transactional(readOnly = true)
    public Page<PanelResponse> list(UUID projectId, UUID positionId, Pageable pageable) {
        TenantContext ctx = requireRead();
        UUID tenant = ctx.tenantId();
        Page<EvaluationPanelJpaEntity> page;
        if (projectId != null && positionId != null) {
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
        positions.findAllByTenantIdAndIdIn(tenant, positionIds)
                .forEach(p -> titleByPosition.put(p.getId(), p.getTitleI18n()));

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

        return page.map(p -> {
            int[] c = rosterCounts.getOrDefault(p.getId(), EMPTY_COUNTS);
            return PanelResponse.from(p.toDomain(),
                    titleByPosition.get(p.getPositionId()), c[0], c[1]);
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
        PanelResponse summary = PanelResponse.from(
                panel.toDomain(), position.getTitleI18n(), roster.size(), completed);
        return new PanelDetailResponse(summary, rosterDto, completed, roster.size());
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

        // Per-evaluator breakdown: COMPLETED/LOCKED contributing sheets' per-factor scores.
        List<EvaluationJpaEntity> sheets = evaluations
                .findAllByTenantIdAndPanelId(tenant, panelId).stream()
                .filter(e -> e.getStatus() == EvaluationStatus.COMPLETE
                        || e.getStatus() == EvaluationStatus.LOCKED
                        || e.getStatus() == EvaluationStatus.APPROVED)
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
}

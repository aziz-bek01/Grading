package uz.hrlab.grading.evaluation.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.DepartmentScopeFilter;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.evaluation.api.EvaluationByFactorRow;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationCalibrationEventJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationCalibrationEventRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorRepository;
import uz.hrlab.grading.organization.infrastructure.DepartmentJpaEntity;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Read-side queries. Permission EVALUATION_READ on every method.
 *
 * <p>E4-S2 SECURITY NOTE — list reads are department-aware. An evaluation has
 * no department of its own; it inherits the department of its Position. A
 * caller in a department-scoped role ({@code DEPARTMENT_MANAGER} / {@code
 * EVALUATION_COMMITTEE_MEMBER}) only SEES evaluations whose position lives in
 * their assigned department subtree; other departments are INVISIBLE. A
 * department-scoped caller with no assignment sees ZERO evaluations
 * (fail-closed). Tenant-wide / bypass roles are unaffected. Role classification
 * is owned by {@code DepartmentScopePolicy}; this class delegates to
 * {@link DepartmentScopeFilter} and never hardcodes role codes.
 */
@Service
public class EvaluationQueries {

    private final EvaluationRepository evaluations;
    private final EvaluationScoreRepository scores;
    private final EvaluationCalibrationEventRepository calibrationEvents;
    private final FactorRepository factors;
    private final PositionRepository positions;
    private final DepartmentRepository departments;
    private final DepartmentScopeFilter departmentScopeFilter;
    private final AbacGate abacGate;

    public EvaluationQueries(EvaluationRepository evaluations,
                             EvaluationScoreRepository scores,
                             EvaluationCalibrationEventRepository calibrationEvents,
                             FactorRepository factors,
                             PositionRepository positions,
                             DepartmentRepository departments,
                             DepartmentScopeFilter departmentScopeFilter,
                             AbacGate abacGate) {
        this.evaluations = evaluations;
        this.scores = scores;
        this.calibrationEvents = calibrationEvents;
        this.factors = factors;
        this.positions = positions;
        this.departments = departments;
        this.departmentScopeFilter = departmentScopeFilter;
        this.abacGate = abacGate;
    }

    @Transactional(readOnly = true)
    public EvaluationJpaEntity findById(UUID id) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.EVALUATION_READ)) {
            throw new PermissionDeniedException();
        }
        EvaluationJpaEntity evaluation = evaluations.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        // C-2 — single-id reads must respect department scope (intra-tenant IDOR
        // fix), mirroring FindPositionQuery.findById. An evaluation inherits its
        // department from its Position; out-of-subtree scoped callers → 404.
        enforceReadScope(ctx, evaluation);
        return evaluation;
    }

    @Transactional(readOnly = true)
    public Page<EvaluationJpaEntity> list(UUID projectId, UUID positionId,
                                          UUID evaluatorUserId, EvaluationStatus status,
                                          Pageable pageable) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.EVALUATION_READ)) {
            throw new PermissionDeniedException();
        }
        UUID tenant = ctx.tenantId();

        // E4-S2 — department-scope filter. Present ⇒ confine evaluations to
        // positions in the assigned subtree; empty present set ⇒ fail-closed.
        Optional<Set<UUID>> scope = departmentScopeFilter.allowedDepartmentIds(ctx);
        if (scope.isPresent()) {
            if (scope.get().isEmpty()) {
                return Page.empty(pageable); // scoped but no assignment → no rows
            }
            return evaluations.findInDepartments(
                    tenant, projectId, positionId, evaluatorUserId, status,
                    scope.get(), pageable);
        }

        // Unfiltered (bypass / non-scoped) — preserve the existing branch order.
        if (positionId != null) {
            return evaluations.findAllByTenantIdAndPositionId(tenant, positionId, pageable);
        }
        if (evaluatorUserId != null) {
            return evaluations.findAllByTenantIdAndEvaluatorUserId(tenant, evaluatorUserId, pageable);
        }
        if (projectId != null && status != null) {
            return evaluations.findAllByTenantIdAndProjectIdAndStatus(
                    tenant, projectId, status, pageable);
        }
        if (projectId != null) {
            return evaluations.findAllByTenantIdAndProjectId(tenant, projectId, pageable);
        }
        return evaluations.findAllByTenantId(tenant, pageable);
    }

    @Transactional(readOnly = true)
    public List<EvaluationScoreJpaEntity> findScoresByEvaluationId(UUID evaluationId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.EVALUATION_READ)) {
            throw new PermissionDeniedException();
        }
        // Tenant guard — ensure evaluation belongs to the active tenant first.
        EvaluationJpaEntity evaluation = evaluations.findByIdAndTenantId(evaluationId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        // C-2 — department-scope read gate (intra-tenant IDOR fix).
        enforceReadScope(ctx, evaluation);
        return scores.findAllByTenantIdAndEvaluationId(ctx.tenantId(), evaluationId);
    }

    /**
     * Excel K-sheet UX: list evaluations in {@code projectId} together with
     * the score (if any) for {@code factorId}. Tenant-scoped via context;
     * {@code factorId} is validated server-side to belong to the same tenant
     * so users cannot probe foreign factors.
     *
     * <p>One row per evaluation. Position metadata + filled/total counts are
     * loaded once and joined in memory — query stays narrow (no jsonb in
     * SQL projection, no N+1).
     */
    @Transactional(readOnly = true)
    public Page<EvaluationByFactorRow> listByFactor(UUID projectId, UUID factorId,
                                                    EvaluationStatus status,
                                                    UUID departmentId,
                                                    Pageable pageable) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.EVALUATION_READ)) {
            throw new PermissionDeniedException();
        }
        if (projectId == null || factorId == null) {
            throw new ValidationException("projectId and factorId are required for groupBy=factor");
        }
        UUID tenant = ctx.tenantId();

        // F-OBLA: validate factorId belongs to active tenant — block cross-tenant probing.
        FactorJpaEntity factor = factors.findByIdAndTenantId(factorId, tenant)
                .orElseThrow(TenantAccessDeniedException::new);

        // E4-S2 — department-scope filter on the K-sheet grid.
        Optional<Set<UUID>> scope = departmentScopeFilter.allowedDepartmentIds(ctx);
        Page<EvaluationJpaEntity> page;
        if (scope.isPresent()) {
            if (scope.get().isEmpty()) {
                return Page.<EvaluationByFactorRow>empty(pageable); // scoped but no assignment
            }
            page = evaluations.findForFactorGridInDepartments(
                    tenant, projectId, status, departmentId, scope.get(), pageable);
        } else {
            page = evaluations.findForFactorGrid(tenant, projectId, status, departmentId, pageable);
        }
        if (page.isEmpty()) {
            return page.map(e -> null);
        }

        // Total factors for the methodology version — denominator of "filled / N".
        // Cache per-version since all evaluations sharing a version answer the same N.
        Map<UUID, Integer> totalsByVersion = new HashMap<>();
        // Resolve position metadata + per-position locale title once per page.
        Set<UUID> positionIds = new HashSet<>();
        Set<UUID> evalIds = new HashSet<>();
        page.getContent().forEach(e -> {
            positionIds.add(e.getPositionId());
            evalIds.add(e.getId());
        });

        Map<UUID, PositionJpaEntity> positionById = new HashMap<>();
        for (UUID pid : positionIds) {
            positions.findByIdAndTenantId(pid, tenant).ifPresent(p -> positionById.put(pid, p));
        }
        Set<UUID> departmentIds = new HashSet<>();
        positionById.values().forEach(p -> departmentIds.add(p.getDepartmentId()));
        Map<UUID, DepartmentJpaEntity> departmentById = new HashMap<>();
        for (UUID did : departmentIds) {
            departments.findByIdAndTenantId(did, tenant).ifPresent(d -> departmentById.put(did, d));
        }

        // Pre-fetch all per-evaluation factor scores for this single factor in one pass.
        // Repository method finds (tenant, evaluation, factor) — we loop per evaluation
        // (small page, MAX 200) which is acceptable; aggregating is unnecessary here.
        Map<UUID, EvaluationScoreJpaEntity> scoreByEval = new HashMap<>();
        Map<UUID, Integer> filledByEval = new HashMap<>();
        for (UUID eid : evalIds) {
            scores.findByTenantIdAndEvaluationIdAndFactorId(tenant, eid, factorId)
                    .ifPresent(s -> scoreByEval.put(eid, s));
            filledByEval.put(eid, scores.findAllByTenantIdAndEvaluationId(tenant, eid).size());
        }

        String locale = ctx.locale() == null ? "ru-RU" : ctx.locale();

        return page.map(e -> {
            PositionJpaEntity p = positionById.get(e.getPositionId());
            UUID deptId = p == null ? null : p.getDepartmentId();
            DepartmentJpaEntity d = deptId == null ? null : departmentById.get(deptId);
            EvaluationScoreJpaEntity s = scoreByEval.get(e.getId());

            // Cache total per methodology version (same N for all rows of one version).
            int total = totalsByVersion.computeIfAbsent(e.getMethodologyVersionId(),
                    vid -> factors
                            .findAllByTenantIdAndMethodologyVersionIdOrderBySortOrderAsc(tenant, vid)
                            .size());

            return new EvaluationByFactorRow(
                    e.getId(),
                    e.getPositionId(),
                    p == null ? null : p.getCode(),
                    p == null ? null : pickLocalized(p.getTitleI18n(), locale),
                    d == null ? null : pickLocalized(d.getNameI18n(), locale),
                    deptId,
                    e.getStatus(),
                    filledByEval.getOrDefault(e.getId(), 0),
                    total,
                    s == null ? null : s.getFactorLevelId(),
                    s == null ? null : s.getRawFactorScore(),
                    s == null ? null : s.getCommentText());
        });
    }

    /** Lightweight i18n fallback: requested locale → ru-RU → first available value. */
    private static String pickLocalized(Map<String, String> i18n, String locale) {
        if (i18n == null || i18n.isEmpty()) return null;
        String v = i18n.get(locale);
        if (v != null) return v;
        v = i18n.get("ru-RU");
        if (v != null) return v;
        return i18n.values().iterator().next();
    }

    @Transactional(readOnly = true)
    public List<EvaluationCalibrationEventJpaEntity> findCalibrationHistory(UUID evaluationId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.EVALUATION_READ)) {
            throw new PermissionDeniedException();
        }
        EvaluationJpaEntity evaluation = evaluations.findByIdAndTenantId(evaluationId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        // C-2 — department-scope read gate (intra-tenant IDOR fix).
        enforceReadScope(ctx, evaluation);
        return calibrationEvents
                .findAllByTenantIdAndEvaluationIdOrderByDecidedAtDesc(ctx.tenantId(), evaluationId);
    }

    /**
     * C-2 — read-side department scope gate for single-id evaluation reads.
     * An evaluation has no department of its own; it inherits the department of
     * its Position. Resolves the position (tenant-scoped) and delegates to the
     * shared {@link AbacGate#enforceCanReadPosition} — the SAME read path
     * {@code FindPositionQuery.findById} uses, exercising
     * {@code ProjectMembershipPolicy} + {@code DepartmentScopePolicy}. A scoped
     * caller outside the subtree is denied with a 404 (no reveal) and an
     * {@code ACCESS_DENIED_BY_ABAC} audit row; bypass / in-scope callers pass.
     */
    private void enforceReadScope(TenantContext ctx, EvaluationJpaEntity evaluation) {
        PositionJpaEntity position = positions
                .findByIdAndTenantId(evaluation.getPositionId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        abacGate.enforceCanReadPosition(ctx, position.getId(), evaluation.getProjectId(),
                position.getDepartmentId(), evaluation.getStatus());
    }
}

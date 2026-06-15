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
import uz.hrlab.grading.evaluation.api.MyEvaluationRow;
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
    private final PanelBiasGuard panelBiasGuard;

    public EvaluationQueries(EvaluationRepository evaluations,
                             EvaluationScoreRepository scores,
                             EvaluationCalibrationEventRepository calibrationEvents,
                             FactorRepository factors,
                             PositionRepository positions,
                             DepartmentRepository departments,
                             DepartmentScopeFilter departmentScopeFilter,
                             AbacGate abacGate,
                             PanelBiasGuard panelBiasGuard) {
        this.evaluations = evaluations;
        this.scores = scores;
        this.calibrationEvents = calibrationEvents;
        this.factors = factors;
        this.positions = positions;
        this.departments = departments;
        this.departmentScopeFilter = departmentScopeFilter;
        this.abacGate = abacGate;
        this.panelBiasGuard = panelBiasGuard;
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
        // BE-11 — bias-isolation: a peer cannot read another evaluator's sheet
        // while the panel is collecting (deny-by-default; 404 + denial audit).
        panelBiasGuard.enforceCanReadSheet(ctx, evaluation);
        return evaluation;
    }

    /**
     * BE-3 — frozen methodology basis snapshot of an evaluation, for reporting.
     * The snapshot is the factors/levels (id/code/weight/max_points/required/
     * sort_order + per-level id/code/points/scale_value) as they stood when the
     * evaluation first became scorable; it is what reports must use rather than
     * the (possibly later-edited) live methodology version. Returns {@code null}
     * when not yet captured. Same EVALUATION_READ + department-scope gating as
     * {@link #findById}.
     */
    @Transactional(readOnly = true)
    public com.fasterxml.jackson.databind.JsonNode findMethodologyBasisSnapshot(UUID id) {
        return findById(id).getMethodologyBasisSnapshot();
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

    /**
     * Evaluator self "my evaluations" inbox — the sheets the caller themselves
     * must score, AFTER roster lock. Self-scoped: {@code tenant_id = ctx.tenant
     * AND evaluator_user_id = ctx.userId}; ONLY the caller's own rows are returned.
     *
     * <p>This deliberately BYPASSES the department-scope fail-closed filter that
     * {@link #list} applies: an {@code EVALUATION_COMMITTEE_MEMBER} with no
     * {@code user_department_scopes} row would otherwise see ZERO of their OWN
     * assigned sheets (the core bug). Explicit ownership of the Evaluation
     * (its {@code evaluator_user_id}) IS the authorization here — and because the
     * finder pins {@code evaluator_user_id = ctx.userId()} in the predicate, no
     * cross-user / cross-tenant row can ever surface, so PanelBiasGuard's
     * own-sheet-only semantics are preserved by construction (only own rows exist
     * in the result). Permission: {@code EVALUATION_READ} — the minimal scoring
     * permission a committee member holds (seed 004).
     *
     * <p>Progress counts reuse the batched {@code countByTenantIdAndEvaluationIdIn}
     * finder (no per-row N+1); the per-version factor total denominator is cached
     * per methodology version; position titles are batch-loaded tenant-scoped.
     */
    @Transactional(readOnly = true)
    public List<MyEvaluationRow> listMine() {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.EVALUATION_READ)) {
            throw new PermissionDeniedException();
        }
        UUID tenant = ctx.tenantId();
        UUID me = ctx.userId();
        if (me == null) {
            // No authenticated user id ⇒ nothing is "mine" (fail-closed, but the
            // security layer already rejects anon with 401 before reaching here).
            return List.of();
        }

        List<EvaluationJpaEntity> mine = evaluations
                .findAllByTenantIdAndEvaluatorUserIdOrderByUpdatedAtDesc(tenant, me);
        if (mine.isEmpty()) {
            return List.of();
        }

        Set<UUID> positionIds = new HashSet<>();
        Set<UUID> evalIds = new HashSet<>();
        mine.forEach(e -> {
            positionIds.add(e.getPositionId());
            evalIds.add(e.getId());
        });

        // Batch-load positions (ONE tenant-scoped query — no N+1).
        Map<UUID, PositionJpaEntity> positionById = new HashMap<>();
        positions.findAllByTenantIdAndIdIn(tenant, positionIds)
                .forEach(p -> positionById.put(p.getId(), p));

        // Batched filled-score count per evaluation (reuse the recently-added
        // grouped finder — NOT one count query per row).
        Map<UUID, Integer> filledByEval = new HashMap<>();
        scores.countByTenantIdAndEvaluationIdIn(tenant, evalIds)
                .forEach(c -> filledByEval.put(c.getEvaluationId(), (int) c.getCount()));

        // Total factors per methodology version (the "filled / N" denominator),
        // cached so all sheets sharing a version answer the same N once.
        Map<UUID, Integer> totalsByVersion = new HashMap<>();

        List<MyEvaluationRow> out = new java.util.ArrayList<>(mine.size());
        for (EvaluationJpaEntity e : mine) {
            PositionJpaEntity p = positionById.get(e.getPositionId());
            int total = totalsByVersion.computeIfAbsent(e.getMethodologyVersionId(),
                    vid -> factors
                            .findAllByTenantIdAndMethodologyVersionIdOrderBySortOrderAsc(tenant, vid)
                            .size());
            out.add(new MyEvaluationRow(
                    e.getId(),
                    e.getProjectId(),
                    e.getPanelId(),
                    e.getPositionId(),
                    p == null ? null : p.getCode(),
                    p == null ? null : p.getTitleI18n(),
                    e.getStatus(),
                    filledByEval.getOrDefault(e.getId(), 0),
                    total));
        }
        return out;
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
        // BE-11 — bias-isolation: peer score read blocked while collecting
        // (REQ-ISO-2/-4; 404/empty + denial audit on a foreign sheet).
        panelBiasGuard.enforceCanReadSheet(ctx, evaluation);
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

        // METHODOLOGY-VERSION SCOPING (defect fix): the K-sheet is ONE methodology
        // version. A factor belongs to exactly one version, so we derive the
        // version from the requested factorId server-side and confine the grid to
        // evaluations of that SAME version. This excludes evaluations of other
        // methodologies in the project (e.g. a 12-factor HR-Lab evaluation no
        // longer appears under an 8-factor methodology's factor tab) and prevents
        // scoring a foreign-version evaluation against this factor.
        UUID methodologyVersionId = factor.getMethodologyVersionId();

        // BE-11 — bias-isolation: a caller WITHOUT CAMPAIGN_RESULTS_VIEW is
        // confined to their OWN evaluations on the grid so a peer never even sees
        // another evaluator's row of a still-collecting panel (REQ-ISO-2). The
        // confinement also drives the count (no leak). CAMPAIGN_RESULTS_VIEW
        // holders see the full grid. EVALUATION_READ alone does NOT lift it.
        boolean confineToOwn = panelBiasGuard.shouldConfineGridToOwn(ctx);
        UUID ownEvaluator = confineToOwn ? ctx.userId() : null;

        // E4-S2 — department-scope filter on the K-sheet grid.
        Optional<Set<UUID>> scope = departmentScopeFilter.allowedDepartmentIds(ctx);
        Page<EvaluationJpaEntity> page;
        if (scope.isPresent()) {
            if (scope.get().isEmpty()) {
                return Page.<EvaluationByFactorRow>empty(pageable); // scoped but no assignment
            }
            page = confineToOwn
                    ? evaluations.findForFactorGridInDepartmentsOwnOnly(
                            tenant, projectId, methodologyVersionId, status, departmentId,
                            scope.get(), ownEvaluator, pageable)
                    : evaluations.findForFactorGridInDepartments(
                            tenant, projectId, methodologyVersionId, status, departmentId,
                            scope.get(), pageable);
        } else {
            page = confineToOwn
                    ? evaluations.findForFactorGridOwnOnly(
                            tenant, projectId, methodologyVersionId, status, departmentId,
                            ownEvaluator, pageable)
                    : evaluations.findForFactorGrid(
                            tenant, projectId, methodologyVersionId, status, departmentId, pageable);
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

        // PERF (P1) — batch-load positions for the whole page in ONE tenant-scoped
        // query (was one findByIdAndTenantId per row → N+1).
        Map<UUID, PositionJpaEntity> positionById = new HashMap<>();
        positions.findAllByTenantIdAndIdIn(tenant, positionIds)
                .forEach(p -> positionById.put(p.getId(), p));
        Set<UUID> departmentIds = new HashSet<>();
        positionById.values().forEach(p -> departmentIds.add(p.getDepartmentId()));
        // PERF (P1) — batch-load departments in ONE tenant-scoped query.
        Map<UUID, DepartmentJpaEntity> departmentById = new HashMap<>();
        departments.findAllByTenantIdAndIdIn(tenant, departmentIds)
                .forEach(d -> departmentById.put(d.getId(), d));
        // BE-11 — resolve the PARENT department for each immediate department so
        // the K-sheet can show "department · unit" (unit = parent org node).
        // Tenant-scoped batch lookup; root departments (no parent) yield no unit.
        Set<UUID> parentIds = new HashSet<>();
        departmentById.values().forEach(d -> {
            if (d.getParentId() != null) {
                parentIds.add(d.getParentId());
            }
        });
        Map<UUID, DepartmentJpaEntity> parentById = new HashMap<>();
        departments.findAllByTenantIdAndIdIn(tenant, parentIds)
                .forEach(d -> parentById.put(d.getId(), d));

        // PERF (P1) — pre-fetch ALL scores for the page of evaluations in ONE
        // tenant-scoped query (was TWO queries per evaluation → ~2N round-trips).
        // From the single result we derive (a) the per-evaluation score for THIS
        // factor and (b) the filled-score count, both grouped in memory. The
        // grouped count query is also available, but the loaded rows already give
        // us the count for free, so we keep a single round-trip.
        Map<UUID, EvaluationScoreJpaEntity> scoreByEval = new HashMap<>();
        Map<UUID, Integer> filledByEval = new HashMap<>();
        for (EvaluationScoreJpaEntity s : scores.findAllByTenantIdAndEvaluationIdIn(tenant, evalIds)) {
            filledByEval.merge(s.getEvaluationId(), 1, Integer::sum);
            if (factorId.equals(s.getFactorId())) {
                scoreByEval.put(s.getEvaluationId(), s);
            }
        }

        String locale = ctx.locale() == null ? "ru-RU" : ctx.locale();

        return page.map(e -> {
            PositionJpaEntity p = positionById.get(e.getPositionId());
            UUID deptId = p == null ? null : p.getDepartmentId();
            DepartmentJpaEntity d = deptId == null ? null : departmentById.get(deptId);
            // BE-11 — unit = parent department name (broader org node), tenant-scoped.
            DepartmentJpaEntity parent = (d == null || d.getParentId() == null)
                    ? null : parentById.get(d.getParentId());
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
                    parent == null ? null : pickLocalized(parent.getNameI18n(), locale),
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
        // BE-11 — bias-isolation: calibration history is part of the per-evaluator
        // sheet — blocked for peers while collecting.
        panelBiasGuard.enforceCanReadSheet(ctx, evaluation);
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

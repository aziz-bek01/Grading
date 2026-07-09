package uz.hrlab.grading.evaluation.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.DepartmentScopeFilter;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.common.i18n.I18nText;
import uz.hrlab.grading.evaluation.api.EvaluationByFactorRow;
import uz.hrlab.grading.evaluation.api.EvaluationResponse;
import uz.hrlab.grading.evaluation.api.MyEvaluationRow;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationCalibrationEventJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationCalibrationEventRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationGridView;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRowView;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreRepository;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.infrastructure.FactorJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
import uz.hrlab.grading.organization.infrastructure.DepartmentJpaEntity;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;
import uz.hrlab.grading.tenancy.domain.Locale;

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
    private final MethodologyVersionRepository methodologyVersions;
    private final MethodologyRepository methodologies;
    private final DepartmentScopeFilter departmentScopeFilter;
    private final AbacGate abacGate;
    private final PanelBiasGuard panelBiasGuard;

    public EvaluationQueries(EvaluationRepository evaluations,
                             EvaluationScoreRepository scores,
                             EvaluationCalibrationEventRepository calibrationEvents,
                             FactorRepository factors,
                             PositionRepository positions,
                             DepartmentRepository departments,
                             MethodologyVersionRepository methodologyVersions,
                             MethodologyRepository methodologies,
                             DepartmentScopeFilter departmentScopeFilter,
                             AbacGate abacGate,
                             PanelBiasGuard panelBiasGuard) {
        this.evaluations = evaluations;
        this.scores = scores;
        this.calibrationEvents = calibrationEvents;
        this.factors = factors;
        this.positions = positions;
        this.departments = departments;
        this.methodologyVersions = methodologyVersions;
        this.methodologies = methodologies;
        this.departmentScopeFilter = departmentScopeFilter;
        this.abacGate = abacGate;
        this.panelBiasGuard = panelBiasGuard;
    }

    @Transactional(readOnly = true)
    public EvaluationJpaEntity findById(UUID id) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.EVALUATION_READ);
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

    /**
     * Project-level evaluation list. Returns the stable {@link EvaluationResponse}
     * wire rows directly (built from the {@link EvaluationRowView} projection, so
     * DB-23 the wide {@code methodology_basis_snapshot} JSONB is never selected on
     * the list surface) and each row carries the FE-27 server-resolved
     * {@code methodology_version_label} so the client never fires a per-methodology
     * request to label historical versions.
     *
     * <p>The department-scope routing and the unfiltered branch ORDER are preserved
     * byte-for-byte from before; only the returned columns (projection) and the
     * enrichment (version label) changed.
     */
    @Transactional(readOnly = true)
    public Page<EvaluationResponse> list(UUID projectId, UUID positionId,
                                         UUID evaluatorUserId, EvaluationStatus status,
                                         Pageable pageable) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.EVALUATION_READ);
        UUID tenant = ctx.tenantId();

        // E4-S2 — department-scope filter. Present ⇒ confine evaluations to
        // positions in the assigned subtree; empty present set ⇒ fail-closed.
        Optional<Set<UUID>> scope = departmentScopeFilter.allowedDepartmentIds(ctx);
        Page<EvaluationRowView> page;
        if (scope.isPresent()) {
            if (scope.get().isEmpty()) {
                return Page.empty(pageable); // scoped but no assignment → no rows
            }
            page = evaluations.findInDepartments(
                    tenant, projectId, positionId, evaluatorUserId, status,
                    scope.get(), pageable);
        } else if (positionId != null) {
            // Unfiltered (bypass / non-scoped) — preserve the existing branch order.
            page = evaluations.findRowsByTenantIdAndPositionId(tenant, positionId, pageable);
        } else if (evaluatorUserId != null) {
            page = evaluations.findRowsByTenantIdAndEvaluatorUserId(tenant, evaluatorUserId, pageable);
        } else if (projectId != null && status != null) {
            page = evaluations.findRowsByTenantIdAndProjectIdAndStatus(
                    tenant, projectId, status, pageable);
        } else if (projectId != null) {
            page = evaluations.findRowsByTenantIdAndProjectId(tenant, projectId, pageable);
        } else {
            page = evaluations.findRowsByTenantId(tenant, pageable);
        }

        // FE-27 — resolve the localized "Name (vN)" label ONCE per distinct
        // methodology version on the page (batched versions → methodology
        // containers), reusing the SAME lookup listMine already performs. No N+1.
        String locale = ctx.locale() == null ? Locale.RU_RU : ctx.locale();
        Map<UUID, String> labelByVersion = resolveVersionLabels(tenant, page.getContent(), locale);
        return page.map(v -> EvaluationResponse.fromRow(
                v, labelByVersion.get(v.getMethodologyVersionId())));
    }

    /**
     * FE-27 helper — distinct methodology-version ids on the page → localized
     * "Name (vN)" label. Two tenant-scoped batch queries (versions, then their
     * methodology containers), mirroring {@code listMine}'s enrichment (no
     * duplication of a third resolver). A foreign/stale version id resolves to no
     * label (the client falls back to a short id, as it did before).
     */
    private Map<UUID, String> resolveVersionLabels(
            UUID tenant, List<EvaluationRowView> rows, String locale) {
        Set<UUID> versionIds = new HashSet<>();
        for (EvaluationRowView v : rows) {
            if (v.getMethodologyVersionId() != null) {
                versionIds.add(v.getMethodologyVersionId());
            }
        }
        if (versionIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, MethodologyVersionJpaEntity> versionById = new HashMap<>();
        methodologyVersions.findAllByTenantIdAndIdIn(tenant, versionIds)
                .forEach(ver -> versionById.put(ver.getId(), ver));

        Set<UUID> methodologyIds = new HashSet<>();
        versionById.values().forEach(ver -> {
            if (ver.getMethodologyId() != null) {
                methodologyIds.add(ver.getMethodologyId());
            }
        });
        Map<UUID, MethodologyJpaEntity> methodologyById = new HashMap<>();
        if (!methodologyIds.isEmpty()) {
            methodologies.findAllByTenantIdAndIdIn(tenant, methodologyIds)
                    .forEach(m -> methodologyById.put(m.getId(), m));
        }

        Map<UUID, String> labels = new HashMap<>();
        for (UUID versionId : versionIds) {
            MethodologyVersionJpaEntity ver = versionById.get(versionId);
            if (ver == null) {
                continue; // foreign/stale id → no label
            }
            MethodologyJpaEntity m = ver.getMethodologyId() == null
                    ? null : methodologyById.get(ver.getMethodologyId());
            String name = null;
            if (m != null) {
                name = I18nText.pick(m.getNameI18n(), locale);
                if (name == null || name.isBlank()) {
                    name = m.getCode();
                }
            }
            labels.put(versionId, (name == null || name.isBlank())
                    ? "v" + ver.getVersionNumber()
                    : name + " (v" + ver.getVersionNumber() + ")");
        }
        return labels;
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
     *
     * <p>ACTIVE-METHODOLOGY FILTER (PART A): sheets whose methodology version
     * belongs to an ARCHIVED methodology container are EXCLUDED here — the inbox
     * must not deep-link a scorer into a retired methodology. This filter applies
     * to the SELF inbox ONLY; the project-level evaluation list ({@link #list})
     * used by managers must NOT inherit it.
     *
     * <p>METHODOLOGY ENRICHMENT (PART A): each row also carries its
     * methodology_version_id, the owning methodology_id, the container's localized
     * name, and the container's active version number (highest version-number
     * among its APPROVED / LOCKED versions — same derivation as
     * {@code MethodologyResponse.active_version_number}). All three lookups are
     * batched (distinct version ids → versions → methodology ids → containers +
     * all-versions-per-container) so there is no per-row N+1.
     */
    @Transactional(readOnly = true)
    public List<MyEvaluationRow> listMine() {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.EVALUATION_READ);
        UUID tenant = ctx.tenantId();
        UUID me = ctx.userId();
        if (me == null) {
            // No authenticated user id ⇒ nothing is "mine" (fail-closed, but the
            // security layer already rejects anon with 401 before reaching here).
            return List.of();
        }

        // PART A — active-methodology-only self inbox (ARCHIVED-container sheets
        // dropped). This finder is for listMine ONLY (see #list for managers).
        List<EvaluationJpaEntity> mine = evaluations
                .findActiveMethodologyEvaluationsByEvaluator(tenant, me);
        if (mine.isEmpty()) {
            return List.of();
        }

        Set<UUID> positionIds = new HashSet<>();
        Set<UUID> evalIds = new HashSet<>();
        Set<UUID> versionIds = new HashSet<>();
        mine.forEach(e -> {
            positionIds.add(e.getPositionId());
            evalIds.add(e.getId());
            versionIds.add(e.getMethodologyVersionId());
        });

        // Batch-load positions (ONE tenant-scoped query — no N+1).
        Map<UUID, PositionJpaEntity> positionById = new HashMap<>();
        positions.findAllByTenantIdAndIdIn(tenant, positionIds)
                .forEach(p -> positionById.put(p.getId(), p));

        // PART A — batch-load the distinct methodology versions to derive each
        // sheet's owning methodologyId (ONE tenant-scoped query — no N+1).
        Map<UUID, UUID> methodologyIdByVersion = new HashMap<>();
        methodologyVersions.findAllByTenantIdAndIdIn(tenant, versionIds)
                .forEach(v -> methodologyIdByVersion.put(v.getId(), v.getMethodologyId()));

        // PART A — batch-load the distinct methodology containers (localized name).
        Set<UUID> methodologyIds = new HashSet<>(methodologyIdByVersion.values());
        Map<UUID, MethodologyJpaEntity> methodologyById = new HashMap<>();
        methodologies.findAllByTenantIdAndIdIn(tenant, methodologyIds)
                .forEach(m -> methodologyById.put(m.getId(), m));

        // PART A — derive the active version number per methodology = highest
        // version-number among its APPROVED / LOCKED versions (the SAME rule
        // MethodologyResponse.fromList uses for active_version_number). One batched
        // version query covers every methodology on the page (no N+1); the rows
        // are version-number DESC so the first APPROVED/LOCKED is the active one.
        Map<UUID, Integer> activeVersionNumberByMethodology = new HashMap<>();
        if (!methodologyIds.isEmpty()) {
            methodologyVersions
                    .findAllByTenantIdAndMethodologyIdInOrderByVersionNumberDesc(tenant, methodologyIds)
                    .forEach(v -> {
                        if ((v.getStatus() == MethodologyVersionStatus.APPROVED
                                || v.getStatus() == MethodologyVersionStatus.LOCKED)
                                && !activeVersionNumberByMethodology.containsKey(v.getMethodologyId())) {
                            activeVersionNumberByMethodology.put(
                                    v.getMethodologyId(), v.getVersionNumber());
                        }
                    });
        }

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
            // BE-25 — count the version's factors via a scalar COUNT instead of
            // loading every factor row just to .size() them (byte-identical total).
            int total = totalsByVersion.computeIfAbsent(e.getMethodologyVersionId(),
                    vid -> (int) factors.countByTenantIdAndMethodologyVersionId(tenant, vid));
            UUID methodologyId = methodologyIdByVersion.get(e.getMethodologyVersionId());
            MethodologyJpaEntity m = methodologyId == null ? null : methodologyById.get(methodologyId);
            out.add(new MyEvaluationRow(
                    e.getId(),
                    e.getProjectId(),
                    e.getPanelId(),
                    e.getPositionId(),
                    p == null ? null : p.getCode(),
                    p == null ? null : p.getTitleI18n(),
                    e.getStatus(),
                    filledByEval.getOrDefault(e.getId(), 0),
                    total,
                    e.getMethodologyVersionId(),
                    methodologyId,
                    m == null ? null : m.getNameI18n(),
                    methodologyId == null ? null
                            : activeVersionNumberByMethodology.get(methodologyId)));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<EvaluationScoreJpaEntity> findScoresByEvaluationId(UUID evaluationId) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.EVALUATION_READ);
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
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.EVALUATION_READ);
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

        // BE-11 / Defect-2 / Defect-3 — bias-isolation: a caller WITHOUT an HRLab
        // oversight role is confined to their OWN evaluations on the grid so a peer
        // never even sees another evaluator's row of a still-collecting panel
        // (REQ-ISO-2). The confinement also drives the count (no leak).
        //
        // Defect-3 ("a member is blind, a pure overseer can see"): an oversight-role
        // holder who is ALSO a member/evaluator in THIS grid's scope (he has at least
        // one of his OWN evaluations in this (tenant, project, methodologyVersionId))
        // is a PEER → he MUST be confined to own (blind to other members), exactly
        // like a non-oversight committee member. Only a PURE overseer (oversight role
        // but NO own evaluation in scope) keeps the full-grid calibration bypass.
        // Neither EVALUATION_READ nor CAMPAIGN_RESULTS_VIEW lifts the per-sheet blind.
        boolean viewerIsMemberInScope = ctx.userId() != null
                && evaluations.existsByTenantIdAndProjectIdAndMethodologyVersionIdAndEvaluatorUserId(
                        tenant, projectId, methodologyVersionId, ctx.userId());
        boolean confineToOwn = panelBiasGuard.shouldConfineGridToOwn(ctx) || viewerIsMemberInScope;
        UUID ownEvaluator = confineToOwn ? ctx.userId() : null;

        // E4-S2 — department-scope filter on the K-sheet grid.
        Optional<Set<UUID>> scope = departmentScopeFilter.allowedDepartmentIds(ctx);
        // DB-23 — grid projection (no methodology_basis_snapshot JSONB on grid rows).
        Page<EvaluationGridView> page;
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

        String locale = ctx.locale() == null ? Locale.RU_RU : ctx.locale();

        return page.map(e -> {
            PositionJpaEntity p = positionById.get(e.getPositionId());
            UUID deptId = p == null ? null : p.getDepartmentId();
            DepartmentJpaEntity d = deptId == null ? null : departmentById.get(deptId);
            // BE-11 — unit = parent department name (broader org node), tenant-scoped.
            DepartmentJpaEntity parent = (d == null || d.getParentId() == null)
                    ? null : parentById.get(d.getParentId());
            EvaluationScoreJpaEntity s = scoreByEval.get(e.getId());

            // Cache total per methodology version (same N for all rows of one version).
            // BE-25 — scalar COUNT instead of loading every factor row to .size() it.
            int total = totalsByVersion.computeIfAbsent(e.getMethodologyVersionId(),
                    vid -> (int) factors.countByTenantIdAndMethodologyVersionId(tenant, vid));

            return new EvaluationByFactorRow(
                    e.getId(),
                    e.getPositionId(),
                    p == null ? null : p.getCode(),
                    p == null ? null : I18nText.pick(p.getTitleI18n(), locale),
                    d == null ? null : I18nText.pick(d.getNameI18n(), locale),
                    parent == null ? null : I18nText.pick(parent.getNameI18n(), locale),
                    deptId,
                    e.getStatus(),
                    filledByEval.getOrDefault(e.getId(), 0),
                    total,
                    s == null ? null : s.getFactorLevelId(),
                    s == null ? null : s.getRawFactorScore(),
                    s == null ? null : s.getCommentText());
        });
    }


    @Transactional(readOnly = true)
    public List<EvaluationCalibrationEventJpaEntity> findCalibrationHistory(UUID evaluationId) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.EVALUATION_READ);
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
     *
     * <p>SELF-OWNERSHIP CARVE-OUT (mirrors {@link #listMine()}): when the caller
     * IS the evaluation's own {@code evaluator_user_id}, the DEPARTMENT-SCOPE
     * dimension is short-circuited. An explicit panel assignment + ownership IS
     * the authorization, so a committee member with an EMPTY department scope can
     * still open and score their OWN sheet (the P0 fix). This relaxes ONLY the
     * department-scope dimension and ONLY for the owner — the tenant filter
     * (already matched via {@code findByIdAndTenantId}) and the owner-only
     * {@code PanelBiasGuard} are untouched, and NON-owners (managers) still need
     * the full subtree scope. For a non-owner the path is unchanged.
     */
    private void enforceReadScope(TenantContext ctx, EvaluationJpaEntity evaluation) {
        if (isOwnSheet(ctx, evaluation)) {
            return; // owner short-circuit: relax department scope only, for the owner only
        }
        PositionJpaEntity position = positions
                .findByIdAndTenantId(evaluation.getPositionId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        abacGate.enforceCanReadPosition(ctx, position.getId(), evaluation.getProjectId(),
                position.getDepartmentId(), evaluation.getStatus());
    }

    /**
     * True when the active caller owns this evaluation sheet (they are its
     * {@code evaluator_user_id}). The evaluation has already been tenant-matched
     * by the caller, so this never widens tenant scope — it only identifies the
     * owner so the department-scope check can be relaxed for them.
     */
    private static boolean isOwnSheet(TenantContext ctx, EvaluationJpaEntity evaluation) {
        return ctx.userId() != null
                && ctx.userId().equals(evaluation.getEvaluatorUserId());
    }
}

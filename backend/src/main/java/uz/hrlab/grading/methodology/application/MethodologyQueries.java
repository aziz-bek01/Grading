package uz.hrlab.grading.methodology.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.methodology.domain.Factor;
import uz.hrlab.grading.methodology.domain.FactorLevel;
import uz.hrlab.grading.methodology.domain.Methodology;
import uz.hrlab.grading.methodology.domain.MethodologyStatus;
import uz.hrlab.grading.methodology.domain.MethodologyVersion;
import uz.hrlab.grading.methodology.infrastructure.FactorJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Read-only queries for methodology / version / factor / level. */
@Service
public class MethodologyQueries {

    private final MethodologyRepository methodologies;
    private final MethodologyVersionRepository versions;
    private final FactorRepository factors;
    private final FactorLevelRepository levels;
    private final EvaluationRepository evaluations;
    private final AbacGate abacGate;

    public MethodologyQueries(MethodologyRepository methodologies,
                              MethodologyVersionRepository versions,
                              FactorRepository factors,
                              FactorLevelRepository levels,
                              EvaluationRepository evaluations,
                              AbacGate abacGate) {
        this.methodologies = methodologies;
        this.versions = versions;
        this.factors = factors;
        this.levels = levels;
        this.evaluations = evaluations;
        this.abacGate = abacGate;
    }

    @Transactional(readOnly = true)
    public Methodology findMethodologyById(UUID id) {
        // Defect-2: the by-factor scoring view (frontend EvaluationByFactorView)
        // populates its methodology selector from the methodology LIST/DETAIL
        // endpoints. A committee scorer (department director) holds
        // EVALUATION_READ, not METHODOLOGY_READ — so the structural READ gate is
        // broadened to either (same rationale as Defect-1: methodology
        // names/versions/factors are non-salary, non-sensitive structure needed
        // to score). Tenant + project ABAC below are UNCHANGED.
        TenantContext ctx = requireMethodologyStructureReadPerm();
        MethodologyJpaEntity m = methodologies.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (m.getProjectId() != null) {
            // Read ABAC: gate via abacGate.enforceCanListInProject covers consultant scope
            abacGate.enforceCanListInProject(ctx, m.getProjectId());
        }
        return m.toDomain();
    }

    /**
     * Latest-version-id pointer for the single methodology detail view (slice B4).
     * The list endpoint already enriches each row with {@code latest_version_id}
     * (via {@link #versionsByMethodologyIds}); the single-arg detail path
     * ({@code GET /api/v1/methodologies/{id}}) historically returned null, which
     * broke the FE create-from-scratch deep-link into the new v1 editor. Returns
     * the highest version-number row's id, or {@code null} if the methodology has
     * no versions yet. Tenant-scoped + permission-guarded like every other read.
     */
    @Transactional(readOnly = true)
    public UUID findLatestVersionId(UUID methodologyId) {
        // Defect-2: detail-path companion to findMethodologyById — same broadened
        // structural READ so the scorer's selector can deep-link the version.
        TenantContext ctx = requireMethodologyStructureReadPerm();
        return versions.findFirstByTenantIdAndMethodologyIdOrderByVersionNumberDesc(
                        ctx.tenantId(), methodologyId)
                .map(MethodologyVersionJpaEntity::getId)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Page<MethodologyJpaEntity> findByProject(UUID projectId, Pageable pageable) {
        // Defect-2: backs GET /methodologies?projectId=... — the scoring view's
        // methodology selector source. Broadened to EVALUATION_READ; project ABAC
        // (enforceCanListInProject) is UNCHANGED, so an evaluator still only sees
        // methodologies in projects they are a member of.
        TenantContext ctx = requireMethodologyStructureReadPerm();
        if (projectId != null) {
            abacGate.enforceCanListInProject(ctx, projectId);
            return methodologies.findAllByTenantIdAndProjectId(
                    ctx.tenantId(), projectId, pageable);
        }
        // Global / null-project listing: paged tenant-scoped — null-project filter
        return methodologies.findAllByTenantId(ctx.tenantId(), pageable);
    }

    /**
     * PART B — evaluator-scoped methodology list: the ACTIVE methodologies in
     * {@code projectId} under which the caller holds at least one of their OWN,
     * non-ARCHIVED evaluations. Backs {@code GET /api/v1/methodologies/my}.
     *
     * <p>Gate: {@code EVALUATION_READ} (the scoring permission a committee member
     * holds) — the structural READ gate, broadened like the rest of the scoring
     * reads. The result is self-scoped by construction: only methodologies the
     * caller has an own evaluation under can appear, so this deliberately does NOT
     * route through the department-scope fail-closed filter (mirrors
     * {@code EvaluationQueries.listMine} — explicit ownership IS the authorization).
     *
     * <p>Steps (all batched — no N+1): distinct methodology-version ids from the
     * caller's own non-ARCHIVED evaluations in the project → owning methodology
     * ids → ACTIVE containers among them in that project. Tenant + project +
     * evaluator are pinned in every predicate; ARCHIVED sheets and ARCHIVED
     * containers are excluded.
     */
    @Transactional(readOnly = true)
    public List<MethodologyJpaEntity> findMyMethodologiesInProject(UUID projectId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.EVALUATION_READ)) {
            throw new PermissionDeniedException();
        }
        if (projectId == null) {
            throw new ValidationException("projectId is required");
        }
        UUID tenant = ctx.tenantId();
        UUID me = ctx.userId();
        if (me == null) {
            return List.of();
        }

        // Distinct version ids the caller has an own, non-ARCHIVED evaluation under.
        List<UUID> versionIds = evaluations
                .findDistinctMethodologyVersionIdsByEvaluatorInProject(
                        tenant, projectId, me, EvaluationStatus.ARCHIVED);
        if (versionIds.isEmpty()) {
            return List.of();
        }

        // Map versions → methodology ids (ONE tenant-scoped query — no N+1).
        Set<UUID> methodologyIds = versions.findAllByTenantIdAndIdIn(tenant, versionIds).stream()
                .map(MethodologyVersionJpaEntity::getMethodologyId)
                .collect(Collectors.toSet());
        if (methodologyIds.isEmpty()) {
            return List.of();
        }

        // ACTIVE containers among those ids, in this project (ARCHIVED filtered out).
        return methodologies.findAllByTenantIdAndProjectIdAndStatusAndIdIn(
                tenant, projectId, MethodologyStatus.ACTIVE, methodologyIds);
    }

    /**
     * Batched version lookup for the list view: returns, per methodology id, all
     * its versions ordered version-number-descending. One query for the whole
     * page (no N+1). Tenant-scoped + permission-guarded like every other read.
     * The caller (list DTO factory) derives latest = first row, active = first
     * APPROVED/LOCKED row. ABAC project scoping is already enforced by
     * {@link #findByProject} which produced the ids.
     */
    @Transactional(readOnly = true)
    public Map<UUID, List<MethodologyVersionJpaEntity>> versionsByMethodologyIds(
            Collection<UUID> methodologyIds) {
        // Defect-2: list-path enrichment (latest/active version pointers) invoked
        // by GET /methodologies — same broadened structural READ as findByProject,
        // which already enforced the project ABAC that produced these ids.
        TenantContext ctx = requireMethodologyStructureReadPerm();
        if (methodologyIds == null || methodologyIds.isEmpty()) {
            return Map.of();
        }
        return versions.findAllByTenantIdAndMethodologyIdInOrderByVersionNumberDesc(
                        ctx.tenantId(), methodologyIds).stream()
                .collect(Collectors.groupingBy(MethodologyVersionJpaEntity::getMethodologyId));
    }

    /**
     * PART D — count of IN-PROGRESS (not-yet-submitted) evaluations across ALL
     * versions of {@code methodologyId}. Backs the deactivate/archive
     * confirmation dialog so the user knows how many scoring sheets are still
     * open. "In progress" = the pre-submission statuses (DRAFT / INCOMPLETE /
     * COMPLETE — {@code EvaluationStatus.isPreSubmission}); SUBMITTED / APPROVED /
     * LOCKED / ARCHIVED are excluded.
     *
     * <p>Gate: {@code METHODOLOGY_EDIT} (the same permission Archive/Restore
     * require — this count is part of that admin flow). Methodology loaded by
     * id + tenant (BOLA → 404); version ids resolved tenant-scoped.
     */
    @Transactional(readOnly = true)
    public long countInProgressEvaluations(UUID methodologyId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.METHODOLOGY_EDIT)) {
            throw new PermissionDeniedException();
        }
        UUID tenant = ctx.tenantId();
        MethodologyJpaEntity m = methodologies.findByIdAndTenantId(methodologyId, tenant)
                .orElseThrow(TenantAccessDeniedException::new);
        if (m.getProjectId() != null) {
            abacGate.enforceCanListInProject(ctx, m.getProjectId());
        }
        List<UUID> versionIds = versions
                .findAllByTenantIdAndMethodologyIdOrderByVersionNumberDesc(tenant, methodologyId)
                .stream()
                .map(MethodologyVersionJpaEntity::getId)
                .toList();
        if (versionIds.isEmpty()) {
            return 0L;
        }
        List<EvaluationStatus> inProgress = List.of(
                EvaluationStatus.DRAFT, EvaluationStatus.INCOMPLETE, EvaluationStatus.COMPLETE);
        return evaluations.countByTenantIdAndMethodologyVersionIdInAndStatusIn(
                tenant, versionIds, inProgress);
    }

    @Transactional(readOnly = true)
    public MethodologyVersion findVersionById(UUID id) {
        // Defect-1: the scoring sheet (fetchMethodologyVersion) needs this read to
        // render the form. An assigned evaluator holds EVALUATION_READ, not
        // METHODOLOGY_READ — so the structural READ permission is broadened to
        // either. Tenant + project ABAC below are UNCHANGED (an evaluator must
        // still be a member of the version's project).
        TenantContext ctx = requireMethodologyStructureReadPerm();
        MethodologyVersionJpaEntity v = versions.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        MethodologyJpaEntity m = methodologies.findByIdAndTenantId(v.getMethodologyId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (m.getProjectId() != null) {
            abacGate.enforceCanListInProject(ctx, m.getProjectId());
        }
        return v.toDomain();
    }

    @Transactional(readOnly = true)
    public List<MethodologyVersionJpaEntity> listVersions(UUID methodologyId) {
        // Defect-2: backs GET /methodologies/{id}/versions — the scoring entry
        // page (EvaluationListPage) fans out this call per methodology to map
        // version_id → methodology. Broadened to EVALUATION_READ; tenant + project
        // ABAC below are UNCHANGED.
        TenantContext ctx = requireMethodologyStructureReadPerm();
        MethodologyJpaEntity m = methodologies.findByIdAndTenantId(methodologyId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (m.getProjectId() != null) {
            abacGate.enforceCanListInProject(ctx, m.getProjectId());
        }
        return versions.findAllByTenantIdAndMethodologyIdOrderByVersionNumberDesc(
                ctx.tenantId(), methodologyId);
    }

    @Transactional(readOnly = true)
    public Factor findFactorById(UUID id) {
        TenantContext ctx = requireReadPerm();
        FactorJpaEntity f = factors.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        return f.toDomain();
    }

    @Transactional(readOnly = true)
    public List<FactorJpaEntity> listFactorsByVersion(UUID versionId) {
        // Defect-1: scoring-sheet hydration step (factors of the version). Same
        // broadened structural READ as findVersionById; ABAC unchanged.
        TenantContext ctx = requireMethodologyStructureReadPerm();
        MethodologyVersionJpaEntity v = versions.findByIdAndTenantId(versionId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        MethodologyJpaEntity m = methodologies.findByIdAndTenantId(v.getMethodologyId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (m.getProjectId() != null) {
            abacGate.enforceCanListInProject(ctx, m.getProjectId());
        }
        return factors.findAllByTenantIdAndMethodologyVersionIdOrderBySortOrderAsc(
                ctx.tenantId(), versionId);
    }

    @Transactional(readOnly = true)
    public List<FactorLevel> listLevelsByFactor(UUID factorId) {
        // Defect-1: scoring-sheet hydration step (levels of each factor). Same
        // broadened structural READ as findVersionById; tenant scoping unchanged.
        TenantContext ctx = requireMethodologyStructureReadPerm();
        FactorJpaEntity f = factors.findByIdAndTenantId(factorId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        return levels.findAllByTenantIdAndFactorIdOrderByLevelOrderAsc(
                        ctx.tenantId(), f.getId()).stream()
                .map(l -> l.toDomain())
                .toList();
    }

    private TenantContext requireReadPerm() {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.METHODOLOGY_READ)) {
            throw new PermissionDeniedException();
        }
        return ctx;
    }

    /**
     * Service-layer gate for the methodology STRUCTURE reads the scoring flow
     * needs. Either {@code METHODOLOGY_READ} (methodology authors) OR
     * {@code EVALUATION_READ} (assigned evaluators who do NOT hold
     * METHODOLOGY_READ) is sufficient — reading methodology names / versions /
     * factor / level structure is non-salary, non-sensitive. A caller with
     * NEITHER is denied here (defence in depth: the controller
     * {@code @PreAuthorize} mirrors this OR, but the service is the authoritative
     * gate, matching the project's "enforce in service, not only controller"
     * rule). Tenant + project ABAC remain enforced by the callers.
     *
     * <p>Covers two related broadenings:
     * <ul>
     *   <li>Defect-1 — the scoring SHEET hydration (single version + its factors +
     *       each factor's levels): {@link #findVersionById},
     *       {@link #listFactorsByVersion}, {@link #listLevelsByFactor}.</li>
     *   <li>Defect-2 — the by-factor scoring VIEW's methodology selector source
     *       (list + detail + versions): {@link #findByProject},
     *       {@link #versionsByMethodologyIds}, {@link #findMethodologyById},
     *       {@link #findLatestVersionId}, {@link #listVersions}.</li>
     * </ul>
     */
    private TenantContext requireMethodologyStructureReadPerm() {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.METHODOLOGY_READ)
                && !ctx.hasPermission(PermissionCodes.EVALUATION_READ)) {
            throw new PermissionDeniedException();
        }
        return ctx;
    }
}

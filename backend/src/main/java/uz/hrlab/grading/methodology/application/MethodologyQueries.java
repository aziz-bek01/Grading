package uz.hrlab.grading.methodology.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.methodology.domain.Factor;
import uz.hrlab.grading.methodology.domain.FactorLevel;
import uz.hrlab.grading.methodology.domain.Methodology;
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
import java.util.UUID;
import java.util.stream.Collectors;

/** Read-only queries for methodology / version / factor / level. */
@Service
public class MethodologyQueries {

    private final MethodologyRepository methodologies;
    private final MethodologyVersionRepository versions;
    private final FactorRepository factors;
    private final FactorLevelRepository levels;
    private final AbacGate abacGate;

    public MethodologyQueries(MethodologyRepository methodologies,
                              MethodologyVersionRepository versions,
                              FactorRepository factors,
                              FactorLevelRepository levels,
                              AbacGate abacGate) {
        this.methodologies = methodologies;
        this.versions = versions;
        this.factors = factors;
        this.levels = levels;
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

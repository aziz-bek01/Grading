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
        TenantContext ctx = requireReadPerm();
        MethodologyJpaEntity m = methodologies.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (m.getProjectId() != null) {
            // Read ABAC: gate via abacGate.enforceCanListInProject covers consultant scope
            abacGate.enforceCanListInProject(ctx, m.getProjectId());
        }
        return m.toDomain();
    }

    @Transactional(readOnly = true)
    public Page<MethodologyJpaEntity> findByProject(UUID projectId, Pageable pageable) {
        TenantContext ctx = requireReadPerm();
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
        TenantContext ctx = requireReadPerm();
        if (methodologyIds == null || methodologyIds.isEmpty()) {
            return Map.of();
        }
        return versions.findAllByTenantIdAndMethodologyIdInOrderByVersionNumberDesc(
                        ctx.tenantId(), methodologyIds).stream()
                .collect(Collectors.groupingBy(MethodologyVersionJpaEntity::getMethodologyId));
    }

    @Transactional(readOnly = true)
    public MethodologyVersion findVersionById(UUID id) {
        TenantContext ctx = requireReadPerm();
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
        TenantContext ctx = requireReadPerm();
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
        TenantContext ctx = requireReadPerm();
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
        TenantContext ctx = requireReadPerm();
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
}

package uz.hrlab.grading.position.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.DepartmentScopeFilter;
import uz.hrlab.grading.access.application.RoleCodes;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.position.domain.Position;
import uz.hrlab.grading.position.domain.PositionStatus;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Position read queries.
 *
 * <p>E4-S2 SECURITY NOTE — list reads are now department-aware. A caller in a
 * department-scoped role ({@code DEPARTMENT_MANAGER} / {@code
 * EVALUATION_COMMITTEE_MEMBER}) only SEES positions whose department is within
 * their assigned subtree ({@code TenantContext.departmentScope()}); departments
 * they were not assigned are INVISIBLE. A department-scoped caller with NO
 * assigned department sees ZERO positions (fail-closed). Tenant-wide / bypass
 * roles (HRLab staff, Client Company Admin, Client HR Director, …) are
 * unaffected and continue to see every department's positions in their tenant.
 * The classification is owned by {@code DepartmentScopePolicy}; this query never
 * hardcodes role codes — it delegates to {@link DepartmentScopeFilter}.
 */
@Service
public class FindPositionQuery {

    private static final int MAX_PAGE_SIZE = 200;

    private final PositionRepository positions;
    private final AbacGate abacGate;
    private final DepartmentScopeFilter departmentScopeFilter;

    public FindPositionQuery(PositionRepository positions, AbacGate abacGate,
                             DepartmentScopeFilter departmentScopeFilter) {
        this.positions = positions;
        this.abacGate = abacGate;
        this.departmentScopeFilter = departmentScopeFilter;
    }

    @Transactional(readOnly = true)
    public Position findById(UUID id) {
        TenantContext ctx = TenantContextHolder.requireActive();
        PositionJpaEntity entity = positions.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        abacGate.enforceCanReadPosition(ctx, entity.getId(), entity.getProjectId(),
                entity.getDepartmentId(), entity.getStatus());
        return entity.toDomain();
    }

    @Transactional(readOnly = true)
    public Page<Position> list(UUID projectId, UUID departmentId, PositionStatus status,
                               String jobFamily, int page, int size) {
        TenantContext ctx = TenantContextHolder.requireActive();
        // ABAC gate at listing scope: project membership + consultant assignment
        abacGate.enforceCanListInProject(ctx, projectId);

        // Viewer / Auditor → force ACTIVE-only at the query layer
        // (defense-in-depth on top of ApprovedEntityFilterPolicy).
        PositionStatus effectiveStatus = status;
        if (effectiveStatus == null
                && (ctx.hasRole(RoleCodes.VIEWER) || ctx.hasRole(RoleCodes.EXTERNAL_AUDITOR))
                && !ctx.hasRole(RoleCodes.HRLAB_SUPER_ADMIN)
                && !ctx.hasRole(RoleCodes.HRLAB_PROJECT_MANAGER)) {
            effectiveStatus = PositionStatus.ACTIVE;
        }

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0), safeSize, Sort.by("code").ascending());

        // E4-S2 — department-scope filter. Empty Optional ⇒ unfiltered (bypass
        // / non-scoped). Present ⇒ confine to the assigned subtree; an empty
        // present set ⇒ fail-closed (zero rows, never widen).
        Optional<Set<UUID>> scope = departmentScopeFilter.allowedDepartmentIds(ctx);
        Page<PositionJpaEntity> raw;
        if (scope.isEmpty()) {
            raw = positions.search(
                    ctx.tenantId(), projectId, departmentId, effectiveStatus, jobFamily, pageable);
        } else if (scope.get().isEmpty()) {
            return Page.empty(pageable); // department-scoped but no assignment → no rows
        } else {
            raw = positions.searchInDepartments(
                    ctx.tenantId(), projectId, departmentId, effectiveStatus, jobFamily,
                    scope.get(), pageable);
        }
        return raw.map(PositionJpaEntity::toDomain);
    }
}

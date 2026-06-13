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
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;
import uz.hrlab.grading.position.domain.Position;
import uz.hrlab.grading.position.domain.PositionStatus;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.LinkedHashSet;
import java.util.List;
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
    private final DepartmentRepository departments;
    private final AbacGate abacGate;
    private final DepartmentScopeFilter departmentScopeFilter;

    public FindPositionQuery(PositionRepository positions, DepartmentRepository departments,
                             AbacGate abacGate, DepartmentScopeFilter departmentScopeFilter) {
        this.positions = positions;
        this.departments = departments;
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

    /** Backward-compatible entrypoint — exact-department behaviour (no subtree expansion). */
    @Transactional(readOnly = true)
    public Page<Position> list(UUID projectId, UUID departmentId, PositionStatus status,
                               String jobFamily, int page, int size) {
        return list(projectId, departmentId, false, status, jobFamily, page, size);
    }

    /**
     * Position list read.
     *
     * <p>T4 FE — {@code includeSubtree} (Defect-1): when {@code true} AND a
     * {@code departmentId} is supplied, the requested department is expanded to
     * its subtree via the EXISTING
     * {@code DepartmentRepository.findSubtreeIds(rootIds, tenantId)} (the one
     * recursive closure — no new CTE) and the list is queried with the EXISTING
     * {@code PositionRepository.searchInDepartments(...)} (no new finder). When
     * {@code false}/absent, behaviour is unchanged (exact {@code department_id}).
     *
     * <p>The subtree expansion combines with the ABAC department-scope: a
     * department-scoped caller only ever sees the INTERSECTION of the requested
     * subtree and their assigned scope (fail-closed — an empty intersection
     * yields zero rows, never a widen).
     */
    @Transactional(readOnly = true)
    public Page<Position> list(UUID projectId, UUID departmentId, boolean includeSubtree,
                               PositionStatus status, String jobFamily, int page, int size) {
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

        // T4 — subtree expansion of the REQUESTED department (one closure call).
        Set<UUID> subtreeIds = null;
        if (includeSubtree && departmentId != null) {
            subtreeIds = new LinkedHashSet<>(
                    departments.findSubtreeIds(List.of(departmentId), ctx.tenantId()));
            if (subtreeIds.isEmpty()) {
                return Page.empty(pageable); // requested dept not in tenant → no rows
            }
        }

        Page<PositionJpaEntity> raw;
        if (scope.isEmpty()) {
            if (subtreeIds != null) {
                // Unscoped caller, subtree expansion: query the whole subtree set
                // (departmentId=null so the finder does not re-pin to one dept).
                raw = positions.searchInDepartments(
                        ctx.tenantId(), projectId, null, effectiveStatus, jobFamily,
                        subtreeIds, pageable);
            } else {
                raw = positions.search(ctx.tenantId(), projectId, departmentId,
                        effectiveStatus, jobFamily, pageable);
            }
        } else if (scope.get().isEmpty()) {
            return Page.empty(pageable); // department-scoped but no assignment → no rows
        } else {
            Set<UUID> effectiveScope = scope.get();
            if (subtreeIds != null) {
                // Intersect the requested subtree with the caller's allowed scope.
                Set<UUID> intersection = new LinkedHashSet<>(subtreeIds);
                intersection.retainAll(effectiveScope);
                if (intersection.isEmpty()) {
                    return Page.empty(pageable); // requested subtree outside scope
                }
                effectiveScope = intersection;
            }
            // When the subtree is in play the IN-set already confines the result,
            // so departmentId is passed only for the non-subtree (exact) path.
            UUID pinDepartment = subtreeIds != null ? null : departmentId;
            raw = positions.searchInDepartments(
                    ctx.tenantId(), projectId, pinDepartment, effectiveStatus, jobFamily,
                    effectiveScope, pageable);
        }
        return raw.map(PositionJpaEntity::toDomain);
    }
}

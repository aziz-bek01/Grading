package uz.hrlab.grading.organization.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.organization.domain.Department;
import uz.hrlab.grading.organization.domain.DepartmentTreeBuilder;
import uz.hrlab.grading.organization.domain.DepartmentTreeNode;
import uz.hrlab.grading.organization.infrastructure.DepartmentJpaEntity;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FindDepartmentQuery {

    private final DepartmentRepository departments;
    private final PositionRepository positions;
    private final AbacGate abacGate;
    private final DepartmentTreeBuilder treeBuilder = new DepartmentTreeBuilder();

    public FindDepartmentQuery(DepartmentRepository departments, PositionRepository positions,
                               AbacGate abacGate) {
        this.departments = departments;
        this.positions = positions;
        this.abacGate = abacGate;
    }

    @Transactional(readOnly = true)
    public Department findById(UUID id) {
        TenantContext ctx = TenantContextHolder.requireActive();
        DepartmentJpaEntity entity = departments.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        abacGate.enforceCanReadDepartment(ctx, entity.getId(), entity.getProjectId(),
                entity.getStatus());
        return entity.toDomain();
    }

    @Transactional(readOnly = true)
    public List<DepartmentTreeNode> tree(UUID projectId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        abacGate.enforceCanListInProject(ctx, projectId);
        List<Department> flat = departments
                .findByTenantIdAndProjectId(ctx.tenantId(), projectId)
                .stream()
                .map(DepartmentJpaEntity::toDomain)
                .toList();
        // For Department Manager: filter to their department subtree.
        List<Department> visible = filterByDepartmentScope(ctx, flat);
        return treeBuilder.build(visible);
    }

    /**
     * Defect-1 BE — server-authoritative position counts per department. Reuses
     * the SAME tenant+project department load + {@link #filterByDepartmentScope}
     * as {@link #tree(UUID)} (a department-scoped caller only sees counts for
     * in-scope departments), so role codes are never re-listed here.
     *
     * <p>Direct counts come from the ONE grouped query
     * {@code PositionRepository.countActiveByDepartment} (no N+1, never a capped
     * page). The subtree roll-up is computed in Java over the already-loaded flat
     * department list — a parent→children map is built once and each department's
     * subtree is summed bottom-up (self + descendants). No recursive CTE and no
     * per-department {@code findSubtreeIds} call (org trees are shallow; the
     * in-memory roll-up is the cheap, deterministic path).
     */
    @Transactional(readOnly = true)
    public List<DepartmentPositionCount> positionCounts(UUID projectId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        abacGate.enforceCanListInProject(ctx, projectId);
        List<Department> flat = departments
                .findByTenantIdAndProjectId(ctx.tenantId(), projectId)
                .stream()
                .map(DepartmentJpaEntity::toDomain)
                .toList();
        // Same ABAC visibility as the tree read — counts only for in-scope depts.
        List<Department> visible = filterByDepartmentScope(ctx, flat);

        // Direct counts: one grouped query keyed by department_id (absent ⇒ 0).
        Map<UUID, Long> directByDept = new HashMap<>();
        for (PositionRepository.DeptCountProjection row :
                positions.countActiveByDepartment(ctx.tenantId(), projectId)) {
            directByDept.put(row.getDepartmentId(), row.getCount());
        }

        // Parent→children map over the already-loaded flat list (no DB round-trip).
        Map<UUID, List<UUID>> childrenByParent = new HashMap<>();
        for (Department d : flat) {
            if (d.parentId() != null) {
                childrenByParent.computeIfAbsent(d.parentId(), k -> new ArrayList<>()).add(d.id());
            }
        }

        // Bottom-up subtree roll-up, memoized so the whole tree is summed once.
        Map<UUID, Long> subtreeByDept = new HashMap<>();
        List<DepartmentPositionCount> result = new ArrayList<>(visible.size());
        for (Department d : visible) {
            long direct = directByDept.getOrDefault(d.id(), 0L);
            long subtree = subtreeSum(d.id(), childrenByParent, directByDept, subtreeByDept);
            result.add(new DepartmentPositionCount(d.id(), direct, subtree));
        }
        return result;
    }

    /**
     * Self + descendants direct-count sum for {@code deptId}, memoized in
     * {@code memo}. {@code childrenByParent} is built from the flat department
     * list so this never touches the DB and never recurses through a CTE.
     */
    private long subtreeSum(UUID deptId, Map<UUID, List<UUID>> childrenByParent,
                            Map<UUID, Long> directByDept, Map<UUID, Long> memo) {
        Long cached = memo.get(deptId);
        if (cached != null) {
            return cached;
        }
        long sum = directByDept.getOrDefault(deptId, 0L);
        List<UUID> children = childrenByParent.get(deptId);
        if (children != null) {
            for (UUID child : children) {
                sum += subtreeSum(child, childrenByParent, directByDept, memo);
            }
        }
        memo.put(deptId, sum);
        return sum;
    }

    private List<Department> filterByDepartmentScope(TenantContext ctx, List<Department> flat) {
        if (ctx.departmentScope() == null || ctx.departmentScope().isEmpty()) {
            return flat;
        }
        // Bypass roles already see all — ApprovedEntityFilter handles status.
        boolean bypass = ctx.hasRole("HRLAB_SUPER_ADMIN")
                || ctx.hasRole("HRLAB_PROJECT_MANAGER")
                || ctx.hasRole("HRLAB_CONSULTANT")
                || ctx.hasRole("HRLAB_ANALYST")
                || ctx.hasRole("CLIENT_HR_DIRECTOR")
                || ctx.hasRole("CLIENT_COMPANY_ADMIN");
        if (bypass) return flat;
        return flat.stream()
                .filter(d -> ctx.departmentScope().contains(d.id()))
                .toList();
    }
}

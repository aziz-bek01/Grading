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
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.UUID;

@Service
public class FindDepartmentQuery {

    private final DepartmentRepository departments;
    private final AbacGate abacGate;
    private final DepartmentTreeBuilder treeBuilder = new DepartmentTreeBuilder();

    public FindDepartmentQuery(DepartmentRepository departments, AbacGate abacGate) {
        this.departments = departments;
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

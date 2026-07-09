package uz.hrlab.grading.organization.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.organization.domain.Department;
import uz.hrlab.grading.organization.domain.DepartmentStatus;
import uz.hrlab.grading.organization.domain.DepartmentValidationPolicy;
import uz.hrlab.grading.organization.infrastructure.DepartmentJpaEntity;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;
import uz.hrlab.grading.project.application.ProjectAccess;
import uz.hrlab.grading.project.application.ProjectLockedException;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.HashMap;
import java.util.UUID;

@Service
public class UpdateDepartmentUseCase {

    private final DepartmentRepository departments;
    private final ProjectAccess projectAccess;
    private final AuditService audit;
    private final AbacGate abacGate;
    private final DepartmentValidationPolicy policy = new DepartmentValidationPolicy();

    public UpdateDepartmentUseCase(DepartmentRepository departments,
                                   ProjectAccess projectAccess,
                                   AuditService audit,
                                   AbacGate abacGate) {
        this.departments = departments;
        this.projectAccess = projectAccess;
        this.audit = audit;
        this.abacGate = abacGate;
    }

    @Transactional
    public Department update(UUID id, UpdateDepartmentCommand cmd) {
        TenantContext ctx = TenantContextHolder.requireActive();
        DepartmentJpaEntity entity = departments.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        // F-202 — ABAC project + department scope check on write.
        abacGate.enforceCanWriteInDepartment(ctx, entity.getProjectId(), entity.getId());
        if (entity.getStatus() == DepartmentStatus.ARCHIVED) {
            throw new ProjectLockedException();
        }
        projectAccess.requireWritable(ctx, entity.getProjectId());

        if (cmd.parentId() != null && !cmd.parentId().equals(entity.getParentId())) {
            policy.validateParentForUpdate(
                    id, ctx.tenantId(), entity.getProjectId(), cmd.parentId(),
                    pid -> departments.findByIdAndTenantId(pid, ctx.tenantId())
                            .map(DepartmentJpaEntity::toDomain).orElse(null),
                    rootId -> departments.findDescendants(rootId, ctx.tenantId())
                            .stream().map(DepartmentJpaEntity::toDomain).toList());
            entity.setParentId(cmd.parentId());
        }
        if (cmd.nameI18n() != null) entity.setNameI18n(new HashMap<>(cmd.nameI18n()));
        if (cmd.type() != null) entity.setType(cmd.type());
        departments.save(entity);

        audit.record(AuditEvent.builder(ctx)
                .projectId(entity.getProjectId())
                .action(AuditAction.DEPARTMENT_UPDATED)
                .entityType("Department")
                .entityId(id)
                .build());
        return entity.toDomain();
    }
}

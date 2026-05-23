package uz.hrlab.grading.organization.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.organization.domain.DepartmentStatus;
import uz.hrlab.grading.organization.infrastructure.DepartmentJpaEntity;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

@Service
public class ArchiveDepartmentUseCase {

    private final DepartmentRepository departments;
    private final AuditService audit;
    private final AbacGate abacGate;

    public ArchiveDepartmentUseCase(DepartmentRepository departments,
                                    AuditService audit,
                                    AbacGate abacGate) {
        this.departments = departments;
        this.audit = audit;
        this.abacGate = abacGate;
    }

    @Transactional
    public void archive(UUID id) {
        TenantContext ctx = TenantContextHolder.requireActive();
        DepartmentJpaEntity entity = departments.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        // F-202 — ABAC project + department scope check on write.
        abacGate.enforceCanWriteInDepartment(ctx, entity.getProjectId(), entity.getId());
        if (entity.getStatus() == DepartmentStatus.ARCHIVED) return;
        entity.setStatus(DepartmentStatus.ARCHIVED);
        departments.save(entity);
        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(entity.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.DEPARTMENT_ARCHIVED)
                .entityType("Department")
                .entityId(id)
                .build());
    }
}

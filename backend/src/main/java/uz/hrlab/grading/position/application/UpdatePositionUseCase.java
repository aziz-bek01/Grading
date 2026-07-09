package uz.hrlab.grading.position.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.organization.infrastructure.DepartmentJpaEntity;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;
import uz.hrlab.grading.position.domain.Position;
import uz.hrlab.grading.position.domain.PositionStatus;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.project.application.ProjectAccess;
import uz.hrlab.grading.project.application.ProjectLockedException;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.HashMap;
import java.util.UUID;

@Service
public class UpdatePositionUseCase {

    private final PositionRepository positions;
    private final DepartmentRepository departments;
    private final ProjectAccess projectAccess;
    private final AuditService audit;
    private final AbacGate abacGate;

    public UpdatePositionUseCase(PositionRepository positions,
                                 DepartmentRepository departments,
                                 ProjectAccess projectAccess,
                                 AuditService audit,
                                 AbacGate abacGate) {
        this.positions = positions;
        this.departments = departments;
        this.projectAccess = projectAccess;
        this.audit = audit;
        this.abacGate = abacGate;
    }

    @Transactional
    public Position update(UUID id, UpdatePositionCommand cmd) {
        TenantContext ctx = TenantContextHolder.requireActive();
        PositionJpaEntity entity = positions.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        // F-202 — ABAC checks AFTER tenant filter resolves but BEFORE mutation.
        abacGate.enforceCanWriteInProject(ctx, entity.getProjectId());
        abacGate.enforceCanWriteInDepartment(ctx, entity.getProjectId(), entity.getDepartmentId());
        if (entity.getStatus() == PositionStatus.ARCHIVED) {
            throw new ProjectLockedException();
        }
        projectAccess.requireWritable(ctx, entity.getProjectId());

        if (cmd.departmentId() != null && !cmd.departmentId().equals(entity.getDepartmentId())) {
            DepartmentJpaEntity newDept = departments
                    .findByIdAndTenantId(cmd.departmentId(), ctx.tenantId())
                    .orElseThrow(() -> new ValidationException("POSITION_DEPARTMENT_INVALID",
                            "Department not found in project"));
            if (!newDept.getProjectId().equals(entity.getProjectId())) {
                throw new ValidationException("POSITION_DEPARTMENT_INVALID",
                        "Department not found in project");
            }
            // F-202 — re-check ABAC for the NEW department (Department Manager scope).
            abacGate.enforceCanWriteInDepartment(ctx, entity.getProjectId(), cmd.departmentId());
            entity.setDepartmentId(cmd.departmentId());
        }
        if (cmd.titleI18n() != null) entity.setTitleI18n(new HashMap<>(cmd.titleI18n()));
        if (cmd.function() != null) entity.setFunction(cmd.function());
        if (cmd.category() != null) entity.setCategory(cmd.category());
        if (cmd.jobFamily() != null) entity.setJobFamily(cmd.jobFamily());
        if (cmd.jobLevel() != null) entity.setJobLevel(cmd.jobLevel());
        positions.save(entity);

        audit.record(AuditEvent.builder(ctx)
                .projectId(entity.getProjectId())
                .action(AuditAction.POSITION_UPDATED)
                .entityType("Position")
                .entityId(id)
                .build());
        return entity.toDomain();
    }
}

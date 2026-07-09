package uz.hrlab.grading.organization.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.organization.domain.Department;
import uz.hrlab.grading.organization.domain.DepartmentStatus;
import uz.hrlab.grading.organization.domain.DepartmentValidationPolicy;
import uz.hrlab.grading.organization.infrastructure.DepartmentJpaEntity;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;
import uz.hrlab.grading.project.application.ProjectLockedException;
import uz.hrlab.grading.project.domain.ProjectStatus;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

@Service
public class CreateDepartmentUseCase {

    private final DepartmentRepository departments;
    private final ProjectRepository projects;
    private final AuditService audit;
    private final AbacGate abacGate;
    private final DepartmentValidationPolicy policy = new DepartmentValidationPolicy();

    public CreateDepartmentUseCase(DepartmentRepository departments,
                                   ProjectRepository projects,
                                   AuditService audit,
                                   AbacGate abacGate) {
        this.departments = departments;
        this.projects = projects;
        this.audit = audit;
        this.abacGate = abacGate;
    }

    @Transactional
    public Department create(CreateDepartmentCommand cmd) {
        TenantContext ctx = TenantContextHolder.requireActive();
        var project = projects.findByIdAndTenantId(cmd.projectId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        // F-202 — ABAC project-membership check on write.
        abacGate.enforceCanWriteInProject(ctx, cmd.projectId());
        // P2-FIX (Task 2) — when a parent is supplied, ALSO enforce the
        // department-subtree gate so a department-scoped writer cannot create a
        // child under a parent outside their subtree. Root-department creation
        // (no parent) stays gated on the project only. Mirrors the
        // enforceCanWriteInDepartment guard in Update/Archive use cases.
        if (cmd.parentId() != null) {
            abacGate.enforceCanWriteInDepartment(ctx, cmd.projectId(), cmd.parentId());
        }
        if (project.getStatus() == ProjectStatus.LOCKED
                || project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new ProjectLockedException();
        }
        if (departments.existsByTenantIdAndProjectIdAndCode(
                ctx.tenantId(), cmd.projectId(), cmd.code())) {
            throw new ValidationException("DEPARTMENT_CODE_TAKEN",
                    "Department code already exists in project");
        }
        policy.validateParentForCreate(ctx.tenantId(), cmd.projectId(), cmd.parentId(),
                pid -> departments.findByIdAndTenantId(pid, ctx.tenantId())
                        .map(DepartmentJpaEntity::toDomain)
                        .orElse(null));

        UUID id = UUID.randomUUID();
        DepartmentJpaEntity entity = new DepartmentJpaEntity(
                id, ctx.tenantId(), cmd.projectId(), cmd.parentId(), cmd.code(),
                cmd.nameI18n(), cmd.type(), DepartmentStatus.ACTIVE);
        departments.save(entity);

        audit.record(AuditEvent.builder(ctx)
                .projectId(cmd.projectId())
                .action(AuditAction.DEPARTMENT_CREATED)
                .entityType("Department")
                .entityId(id)
                .build());
        return entity.toDomain();
    }
}

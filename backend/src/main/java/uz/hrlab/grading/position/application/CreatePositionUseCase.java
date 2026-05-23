package uz.hrlab.grading.position.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.organization.domain.DepartmentStatus;
import uz.hrlab.grading.organization.infrastructure.DepartmentJpaEntity;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;
import uz.hrlab.grading.position.domain.Position;
import uz.hrlab.grading.position.domain.PositionStatus;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.project.application.ProjectLockedException;
import uz.hrlab.grading.project.domain.ProjectStatus;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

@Service
public class CreatePositionUseCase {

    private final PositionRepository positions;
    private final DepartmentRepository departments;
    private final ProjectRepository projects;
    private final AuditService audit;
    private final AbacGate abacGate;

    public CreatePositionUseCase(PositionRepository positions,
                                 DepartmentRepository departments,
                                 ProjectRepository projects,
                                 AuditService audit,
                                 AbacGate abacGate) {
        this.positions = positions;
        this.departments = departments;
        this.projects = projects;
        this.audit = audit;
        this.abacGate = abacGate;
    }

    @Transactional
    public Position create(CreatePositionCommand cmd) {
        TenantContext ctx = TenantContextHolder.requireActive();

        // 1. project must exist in tenant + be writable
        var project = projects.findByIdAndTenantId(cmd.projectId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        // F-202 — ABAC project-membership check on write. Done BEFORE locking
        // and uniqueness checks so a foreign-project probe yields 404 + audit.
        abacGate.enforceCanWriteInProject(ctx, cmd.projectId());
        if (project.getStatus() == ProjectStatus.LOCKED
                || project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new ProjectLockedException();
        }

        // 2. department must be in SAME tenant + SAME project
        DepartmentJpaEntity dept = departments.findByIdAndTenantId(cmd.departmentId(), ctx.tenantId())
                .orElseThrow(() -> new ValidationException("POSITION_DEPARTMENT_INVALID",
                        "Department not found in project"));
        // F-202 — DepartmentScopePolicy must permit the chosen department for
        // Department Managers (no-op for other roles).
        abacGate.enforceCanWriteInDepartment(ctx, cmd.projectId(), cmd.departmentId());
        if (!dept.getProjectId().equals(cmd.projectId())) {
            // Cross-project department reference — refuse but do NOT reveal that the
            // department exists in another project; audit and 400.
            audit.record(AuditEvent.builder()
                    .tenantId(ctx.tenantId())
                    .projectId(cmd.projectId())
                    .actorUserId(ctx.userId())
                    .action(AuditAction.CROSS_TENANT_OR_PROJECT_ACCESS_ATTEMPT)
                    .entityType("Department")
                    .entityId(cmd.departmentId())
                    .reason("Department belongs to different project")
                    .build());
            throw new ValidationException("POSITION_DEPARTMENT_INVALID",
                    "Department not found in project");
        }
        if (dept.getStatus() == DepartmentStatus.ARCHIVED) {
            throw new ValidationException("POSITION_DEPARTMENT_ARCHIVED",
                    "Cannot create position in archived department");
        }

        if (positions.existsByTenantIdAndProjectIdAndCode(
                ctx.tenantId(), cmd.projectId(), cmd.code())) {
            throw new ValidationException("POSITION_CODE_TAKEN",
                    "Position code already exists in project");
        }

        UUID id = UUID.randomUUID();
        PositionJpaEntity entity = new PositionJpaEntity(
                id, ctx.tenantId(), cmd.projectId(), cmd.departmentId(), cmd.code(),
                cmd.titleI18n(), cmd.function(), cmd.category(), cmd.jobFamily(), cmd.jobLevel(),
                PositionStatus.DRAFT);
        positions.save(entity);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(cmd.projectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.POSITION_CREATED)
                .entityType("Position")
                .entityId(id)
                .build());
        return entity.toDomain();
    }
}

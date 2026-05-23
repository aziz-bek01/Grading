package uz.hrlab.grading.project.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.project.domain.Project;
import uz.hrlab.grading.project.domain.ProjectStatus;
import uz.hrlab.grading.project.infrastructure.ProjectJpaEntity;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

@Service
public class CreateProjectUseCase {

    private final ProjectRepository projects;
    private final AuditService audit;

    public CreateProjectUseCase(ProjectRepository projects, AuditService audit) {
        this.projects = projects;
        this.audit = audit;
    }

    @Transactional
    public Project create(CreateProjectCommand cmd) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (cmd.startDate() != null && cmd.endDate() != null
                && cmd.endDate().isBefore(cmd.startDate())) {
            throw new ValidationException("PROJECT_DATE_RANGE_INVALID",
                    "end_date must not precede start_date");
        }
        if (projects.existsByTenantIdAndCode(ctx.tenantId(), cmd.code())) {
            throw new ValidationException("PROJECT_CODE_TAKEN",
                    "Project code already exists in tenant");
        }
        UUID id = UUID.randomUUID();
        ProjectJpaEntity entity = new ProjectJpaEntity(
                id,
                ctx.tenantId(),               // tenantId ALWAYS from context, never from body
                cmd.code(),
                cmd.nameI18n(),
                cmd.description(),
                ProjectStatus.DRAFT,
                null,                          // methodology_version_id wired in Phase 4
                cmd.startDate(),
                cmd.endDate());
        projects.save(entity);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(id)
                .actorUserId(ctx.userId())
                .action(AuditAction.PROJECT_CREATED)
                .entityType("Project")
                .entityId(id)
                .build());

        return entity.toDomain();
    }
}

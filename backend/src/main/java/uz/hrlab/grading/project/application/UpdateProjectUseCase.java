package uz.hrlab.grading.project.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.project.domain.Project;
import uz.hrlab.grading.project.domain.ProjectStatus;
import uz.hrlab.grading.project.infrastructure.ProjectJpaEntity;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.HashMap;
import java.util.UUID;

@Service
public class UpdateProjectUseCase {

    private final ProjectRepository projects;
    private final AuditService audit;
    private final AbacGate abacGate;

    public UpdateProjectUseCase(ProjectRepository projects, AuditService audit, AbacGate abacGate) {
        this.projects = projects;
        this.audit = audit;
        this.abacGate = abacGate;
    }

    @Transactional
    public Project update(UUID id, UpdateProjectCommand cmd) {
        TenantContext ctx = TenantContextHolder.requireActive();
        ProjectJpaEntity entity = projects.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new); // 404 — never reveal existence

        // F-202: ABAC write-path enforcement. Project-scoped roles must be
        // members of the target project; denial yields 404 + audit row.
        abacGate.enforceCanWriteInProject(ctx, entity.getId());

        if (entity.getStatus() == ProjectStatus.LOCKED
                || entity.getStatus() == ProjectStatus.ARCHIVED) {
            throw new ProjectLockedException();
        }
        // Only explicitly-provided fields are mutated (no mass assignment).
        if (cmd.nameI18n() != null) entity.setNameI18n(new HashMap<>(cmd.nameI18n()));
        if (cmd.description() != null) entity.setDescription(cmd.description());
        if (cmd.startDate() != null) entity.setStartDate(cmd.startDate());
        if (cmd.endDate() != null) entity.setEndDate(cmd.endDate());
        projects.save(entity);

        audit.record(AuditEvent.builder(ctx)
                .projectId(id)
                .action(AuditAction.PROJECT_UPDATED)
                .entityType("Project")
                .entityId(id)
                .build());

        return entity.toDomain();
    }
}

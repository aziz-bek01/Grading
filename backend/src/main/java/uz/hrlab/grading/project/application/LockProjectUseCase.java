package uz.hrlab.grading.project.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.project.domain.ProjectStatus;
import uz.hrlab.grading.project.infrastructure.ProjectJpaEntity;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

@Service
public class LockProjectUseCase {

    private final ProjectRepository projects;
    private final AuditService audit;
    private final AbacGate abacGate;

    public LockProjectUseCase(ProjectRepository projects, AuditService audit, AbacGate abacGate) {
        this.projects = projects;
        this.audit = audit;
        this.abacGate = abacGate;
    }

    @Transactional
    public void lock(UUID id) {
        TenantContext ctx = TenantContextHolder.requireActive();
        ProjectJpaEntity entity = projects.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        // F-202 — ABAC project-membership check.
        abacGate.enforceCanWriteInProject(ctx, entity.getId());
        if (entity.getStatus() == ProjectStatus.LOCKED) return; // idempotent
        if (entity.getStatus() == ProjectStatus.ARCHIVED) {
            throw new ProjectLockedException();
        }
        entity.setStatus(ProjectStatus.LOCKED);
        projects.save(entity);
        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(id)
                .actorUserId(ctx.userId())
                .action(AuditAction.PROJECT_LOCKED)
                .entityType("Project")
                .entityId(id)
                .build());
    }
}

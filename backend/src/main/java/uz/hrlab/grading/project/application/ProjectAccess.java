package uz.hrlab.grading.project.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.project.domain.ProjectStatus;
import uz.hrlab.grading.project.infrastructure.ProjectJpaEntity;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;

import java.util.UUID;

/**
 * BE-038 — the single "load a writable project" guard. Consolidates the
 * tenant-scoped project load + LOCKED/ARCHIVED check that child-entity write use
 * cases (position / department / job profile / questionnaire) repeated inline.
 *
 * <p>Pairs with the neighboring {@code AbacGate.enforceCanWriteInProject} each
 * site already applies: the ABAC write gate stays exactly where it is (it runs
 * on the resolved {@code projectId} before this call), and this only replaces
 * the duplicated load + status guard — so the observable order
 * (tenant-load → ABAC → project-status) is unchanged.
 */
@Component
public class ProjectAccess {

    private final ProjectRepository projects;

    public ProjectAccess(ProjectRepository projects) {
        this.projects = projects;
    }

    /**
     * Loads the tenant-scoped project and asserts it is writable. Missing /
     * cross-tenant ⇒ {@link TenantAccessDeniedException} (404, no existence
     * reveal); {@code LOCKED} or {@code ARCHIVED} ⇒ {@link ProjectLockedException}
     * ({@code PROJECT_LOCKED}). Returns the loaded project for callers that need it.
     */
    public ProjectJpaEntity requireWritable(TenantContext ctx, UUID projectId) {
        ProjectJpaEntity project = projects.findByIdAndTenantId(projectId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (project.getStatus() == ProjectStatus.LOCKED
                || project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new ProjectLockedException();
        }
        return project;
    }
}

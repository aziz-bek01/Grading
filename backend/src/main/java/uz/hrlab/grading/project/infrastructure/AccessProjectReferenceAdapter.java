package uz.hrlab.grading.project.infrastructure;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.access.application.ProjectReferencePort;

import java.util.UUID;

/**
 * Project-side implementation of the access-owned {@link ProjectReferencePort}.
 * Lives here (not in the access module) so the static dependency arrow stays
 * project → access; the access user-scope write path consults project data only
 * through the port.
 *
 * <p>Delegates verbatim to the tenant-aware
 * {@link ProjectRepository#existsByIdAndTenantId}, so the tenant-ownership
 * validation is identical — this class only inverts the compile-time arrow so
 * access no longer imports a project class.
 */
@Component
class AccessProjectReferenceAdapter implements ProjectReferencePort {

    private final ProjectRepository projects;

    AccessProjectReferenceAdapter(ProjectRepository projects) {
        this.projects = projects;
    }

    @Override
    public boolean existsInTenant(UUID projectId, UUID tenantId) {
        return projects.existsByIdAndTenantId(projectId, tenantId);
    }
}

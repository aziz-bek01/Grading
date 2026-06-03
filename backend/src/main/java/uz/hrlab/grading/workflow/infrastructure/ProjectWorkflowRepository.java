package uz.hrlab.grading.workflow.infrastructure;

import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-aware repository for {@link ProjectWorkflowJpaEntity}. Only the
 * project-scoped lookup is required — workflow is 1:1 with project.
 */
public interface ProjectWorkflowRepository
        extends TenantAwareRepository<ProjectWorkflowJpaEntity, UUID> {

    Optional<ProjectWorkflowJpaEntity> findByTenantIdAndProjectId(UUID tenantId, UUID projectId);
}

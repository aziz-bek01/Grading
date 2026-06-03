package uz.hrlab.grading.workflow.infrastructure;

import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectWorkflowStageRepository
        extends TenantAwareRepository<ProjectWorkflowStageJpaEntity, UUID> {

    List<ProjectWorkflowStageJpaEntity>
            findAllByTenantIdAndProjectWorkflowIdOrderBySortOrderAsc(
                    UUID tenantId, UUID projectWorkflowId);
}

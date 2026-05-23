package uz.hrlab.grading.jobanalysis.infrastructure;

import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;

import java.util.List;
import java.util.UUID;

public interface JobAnalysisQuestionnaireRepository
        extends TenantAwareRepository<JobAnalysisQuestionnaireJpaEntity, UUID> {

    List<JobAnalysisQuestionnaireJpaEntity>
            findAllByTenantIdAndProjectIdAndPositionIdOrderByCreatedAtDesc(
                    UUID tenantId, UUID projectId, UUID positionId);
}

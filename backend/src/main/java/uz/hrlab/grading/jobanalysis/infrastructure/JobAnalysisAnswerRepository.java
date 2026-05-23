package uz.hrlab.grading.jobanalysis.infrastructure;

import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobAnalysisAnswerRepository
        extends TenantAwareRepository<JobAnalysisAnswerJpaEntity, UUID> {

    List<JobAnalysisAnswerJpaEntity>
            findAllByTenantIdAndQuestionnaireId(UUID tenantId, UUID questionnaireId);

    Optional<JobAnalysisAnswerJpaEntity>
            findByTenantIdAndQuestionnaireIdAndQuestionIdAndRespondentUserId(
                    UUID tenantId, UUID questionnaireId, UUID questionId,
                    UUID respondentUserId);
}

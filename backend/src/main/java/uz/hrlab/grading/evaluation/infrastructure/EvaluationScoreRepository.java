package uz.hrlab.grading.evaluation.infrastructure;

import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** EvaluationScore repository — tenant-aware. */
public interface EvaluationScoreRepository
        extends TenantAwareRepository<EvaluationScoreJpaEntity, UUID> {

    List<EvaluationScoreJpaEntity> findAllByTenantIdAndEvaluationId(
            UUID tenantId, UUID evaluationId);

    Optional<EvaluationScoreJpaEntity> findByTenantIdAndEvaluationIdAndFactorId(
            UUID tenantId, UUID evaluationId, UUID factorId);

    void delete(EvaluationScoreJpaEntity entity);
}

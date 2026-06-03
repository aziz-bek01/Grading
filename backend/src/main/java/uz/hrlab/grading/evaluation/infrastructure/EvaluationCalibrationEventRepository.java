package uz.hrlab.grading.evaluation.infrastructure;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Append-only repository — only {@code save} (insert) + tenant-aware lookup
 * exposed. No update/delete methods, matching the immutable history model.
 */
public interface EvaluationCalibrationEventRepository
        extends Repository<EvaluationCalibrationEventJpaEntity, UUID> {

    <S extends EvaluationCalibrationEventJpaEntity> S save(S entity);

    List<EvaluationCalibrationEventJpaEntity>
            findAllByTenantIdAndEvaluationIdOrderByDecidedAtDesc(UUID tenantId, UUID evaluationId);
}

package uz.hrlab.grading.evaluation.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationCalibrationEventRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreRepository;
import uz.hrlab.grading.methodology.application.MethodologyReferencePort;

import java.util.UUID;

/**
 * Evaluation-side implementation of {@link MethodologyReferencePort}. Lives here
 * (not in the methodology module) so the static dependency arrow stays
 * evaluation → methodology; the methodology approved-edit write paths consult
 * evaluation data only through the port.
 *
 * <p>All lookups are tenant-scoped (the caller supplies the active tenant id) —
 * defense in depth on top of RLS.
 */
@Component
public class EvaluationMethodologyReferenceAdapter implements MethodologyReferencePort {

    private final EvaluationScoreRepository scores;
    private final EvaluationCalibrationEventRepository calibrationEvents;
    private final EvaluationRepository evaluations;

    public EvaluationMethodologyReferenceAdapter(EvaluationScoreRepository scores,
                                                 EvaluationCalibrationEventRepository calibrationEvents,
                                                 EvaluationRepository evaluations) {
        this.scores = scores;
        this.calibrationEvents = calibrationEvents;
        this.evaluations = evaluations;
    }

    @Override
    public boolean isFactorReferenced(UUID tenantId, UUID factorId) {
        return scores.existsByTenantIdAndFactorId(tenantId, factorId)
                || calibrationEvents.existsByTenantIdAndFactorId(tenantId, factorId);
    }

    @Override
    public boolean isFactorLevelReferenced(UUID tenantId, UUID factorLevelId) {
        return scores.existsByTenantIdAndFactorLevelId(tenantId, factorLevelId);
    }

    @Override
    public long countNonArchivedEvaluationsPinnedToVersion(UUID tenantId, UUID methodologyVersionId) {
        return evaluations.countByTenantIdAndMethodologyVersionIdAndStatusNot(
                tenantId, methodologyVersionId, EvaluationStatus.ARCHIVED);
    }
}

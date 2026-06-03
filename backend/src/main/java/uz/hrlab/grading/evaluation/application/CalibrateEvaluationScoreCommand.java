package uz.hrlab.grading.evaluation.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Input for {@link CalibrateEvaluationScoreUseCase}. {@code reason} must be at
 * least 20 chars (enforced by DB CHECK + app validation).
 */
public record CalibrateEvaluationScoreCommand(
        UUID evaluationId,
        UUID factorId,
        BigDecimal newRawFactorScore,
        String reason
) {
}

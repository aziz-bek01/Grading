package uz.hrlab.grading.evaluation.api;

import uz.hrlab.grading.evaluation.domain.EvaluationScore;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record EvaluationScoreResponse(
        UUID id,
        UUID evaluationId,
        UUID factorId,
        UUID factorLevelId,
        BigDecimal rawFactorScore,
        String commentText,
        boolean manuallyAdjusted,
        BigDecimal originalFactorScore,
        UUID adjustedByUserId,
        OffsetDateTime adjustedAt,
        String adjustmentReason
) {
    public static EvaluationScoreResponse from(EvaluationScore s) {
        return new EvaluationScoreResponse(
                s.id(), s.evaluationId(), s.factorId(), s.factorLevelId(),
                s.rawFactorScore(), s.commentText(),
                s.manuallyAdjusted(), s.originalFactorScore(),
                s.adjustedByUserId(), s.adjustedAt(), s.adjustmentReason());
    }
}

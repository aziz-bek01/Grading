package uz.hrlab.grading.evaluation.api;

import uz.hrlab.grading.evaluation.domain.EvaluationCalibrationEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CalibrationEventResponse(
        UUID id,
        UUID evaluationId,
        UUID factorId,
        BigDecimal originalScore,
        BigDecimal adjustedScore,
        BigDecimal delta,
        String reason,
        UUID decidedBy,
        OffsetDateTime decidedAt
) {
    public static CalibrationEventResponse from(EvaluationCalibrationEvent c) {
        return new CalibrationEventResponse(
                c.id(), c.evaluationId(), c.factorId(),
                c.originalScore(), c.adjustedScore(), c.delta(),
                c.reason(), c.decidedBy(), c.decidedAt());
    }
}

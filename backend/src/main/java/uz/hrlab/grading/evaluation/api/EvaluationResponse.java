package uz.hrlab.grading.evaluation.api;

import uz.hrlab.grading.evaluation.domain.Evaluation;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record EvaluationResponse(
        UUID id,
        UUID projectId,
        UUID positionId,
        UUID methodologyVersionId,
        UUID evaluatorUserId,
        EvaluationStatus status,
        BigDecimal rawTotalScore,
        BigDecimal displayedTotalScore,
        UUID gradeBandId,
        Integer assignedGradeNumber,
        OffsetDateTime submittedAt,
        UUID submittedBy,
        OffsetDateTime approvedAt,
        UUID approvedBy,
        OffsetDateTime lockedAt,
        UUID lockedBy,
        OffsetDateTime archivedAt,
        UUID archivedBy
) {
    public static EvaluationResponse from(Evaluation e) {
        return new EvaluationResponse(
                e.id(), e.projectId(), e.positionId(), e.methodologyVersionId(),
                e.evaluatorUserId(), e.status(), e.rawTotalScore(), e.displayedTotalScore(),
                e.gradeBandId(), e.assignedGradeNumber(),
                e.submittedAt(), e.submittedBy(),
                e.approvedAt(), e.approvedBy(),
                e.lockedAt(), e.lockedBy(),
                e.archivedAt(), e.archivedBy());
    }
}

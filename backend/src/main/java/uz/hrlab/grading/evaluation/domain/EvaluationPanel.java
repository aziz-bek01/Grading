package uz.hrlab.grading.evaluation.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Domain model of an evaluation panel (multi-evaluator case). Immutable snapshot
 * returned from use cases; the JPA entity is the mutable persistence shape.
 */
public record EvaluationPanel(
        UUID id,
        UUID tenantId,
        UUID projectId,
        UUID positionId,
        UUID methodologyVersionId,
        EvaluationPanelStatus status,
        int minEvaluators,
        BigDecimal rawTotalScore,
        BigDecimal displayedTotalScore,
        UUID gradeBandId,
        Integer assignedGradeNumber,
        OffsetDateTime averagedAt,
        UUID averagedBy,
        OffsetDateTime submittedAt,
        UUID submittedBy,
        OffsetDateTime approvedAt,
        UUID approvedBy,
        OffsetDateTime lockedAt,
        UUID lockedBy,
        OffsetDateTime archivedAt,
        UUID archivedBy,
        OffsetDateTime createdAt
) { }

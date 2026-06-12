package uz.hrlab.grading.evaluation.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import uz.hrlab.grading.evaluation.domain.EvaluationPanel;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Wire shape for a panel (snake_case via global strategy + NON_NULL). Server-
 * computed fields ({@code raw_total_score} / {@code displayed_total_score}) are
 * null until AVERAGED; grade fields null until APPROVED.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PanelResponse(
        UUID id,
        UUID projectId,
        UUID positionId,
        Map<String, String> positionTitleI18n,
        UUID methodologyVersionId,
        EvaluationPanelStatus status,
        int minEvaluators,
        int evaluatorCount,
        int completedCount,
        BigDecimal rawTotalScore,
        BigDecimal displayedTotalScore,
        Integer assignedGradeNumber,
        UUID gradeBandId,
        OffsetDateTime averagedAt,
        OffsetDateTime submittedAt,
        OffsetDateTime approvedAt,
        OffsetDateTime createdAt
) {
    public static PanelResponse from(EvaluationPanel p, Map<String, String> positionTitleI18n,
                                     int evaluatorCount, int completedCount) {
        return new PanelResponse(
                p.id(), p.projectId(), p.positionId(), positionTitleI18n,
                p.methodologyVersionId(), p.status(), p.minEvaluators(),
                evaluatorCount, completedCount,
                p.rawTotalScore(), p.displayedTotalScore(),
                p.assignedGradeNumber(), p.gradeBandId(),
                p.averagedAt(), p.submittedAt(), p.approvedAt(), p.createdAt());
    }
}

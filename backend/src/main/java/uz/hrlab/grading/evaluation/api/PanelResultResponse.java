package uz.hrlab.grading.evaluation.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Averaged result read surface (GATED by {@code CAMPAIGN_RESULTS_VIEW}; 403/empty
 * while collecting — REQ-ISO-5). Carries the official averaged totals + per-factor
 * averages and, for result-viewers, the per-evaluator breakdown.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PanelResultResponse(
        UUID panelId,
        BigDecimal displayedTotalScore,
        BigDecimal rawTotalScore,
        int evaluatorCount,
        List<FactorAverage> factorAverages,
        UUID gradeBandId,
        Integer assignedGradeNumber
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FactorAverage(
            UUID factorId,
            Map<String, String> factorNameI18n,
            BigDecimal avgRawFactorScore,
            int evaluatorCount,
            List<PerEvaluator> perEvaluator,
            BigDecimal disagreementRange
    ) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PerEvaluator(
            UUID evaluatorUserId,
            String evaluatorName,
            uz.hrlab.grading.evaluation.domain.EvaluatorRole evaluatorRole,
            BigDecimal rawFactorScore
    ) { }
}

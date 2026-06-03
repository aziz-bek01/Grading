package uz.hrlab.grading.evaluation.domain;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Pure output of {@link EvaluationScoringEngine}.
 *
 * @param rawTotal         total score rounded to 4 decimal places (HALF_UP)
 * @param displayedTotal   total score rounded to 2 decimal places (HALF_UP)
 * @param perFactorRaw     factor id → per-factor raw score (4 decimals)
 */
public record ScoringResult(
        BigDecimal rawTotal,
        BigDecimal displayedTotal,
        Map<UUID, BigDecimal> perFactorRaw
) {
}

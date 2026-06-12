package uz.hrlab.grading.evaluation.domain;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * The materialized averaging result for a panel.
 *
 * @param rawTotal          Σ per-factor averages, re-rounded HALF_UP scale 4
 *                          (matches EvaluationScoringEngine RAW_SCALE)
 * @param displayedTotal    {@code rawTotal} at scale 2 (matches DISPLAYED_SCALE)
 * @param avgByFactor       factor_id → mean raw_factor_score, HALF_UP scale 4
 * @param evaluatorCount    number of COMPLETED contributing sheets (the
 *                          denominator — REQ-AVG-3)
 */
public record PanelAverageResult(
        BigDecimal rawTotal,
        BigDecimal displayedTotal,
        Map<UUID, BigDecimal> avgByFactor,
        int evaluatorCount
) { }

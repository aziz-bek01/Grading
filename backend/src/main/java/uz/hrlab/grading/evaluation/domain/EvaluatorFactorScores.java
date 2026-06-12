package uz.hrlab.grading.evaluation.domain;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * One completed evaluator's already-computed per-factor raw scores — the input
 * unit to {@link PanelAveragingService}. {@code rawFactorScores} is the exact
 * output of {@link EvaluationScoringEngine} for that evaluator's sheet
 * (factor_id → raw_factor_score), NOT re-computed here (REQ-AVG-2, no
 * duplication of the scoring engine).
 *
 * @param evaluatorUserId  the contributing evaluator (for traceability)
 * @param rawFactorScores  factor_id → that evaluator's raw_factor_score (scale 4)
 */
public record EvaluatorFactorScores(
        UUID evaluatorUserId,
        Map<UUID, BigDecimal> rawFactorScores
) { }

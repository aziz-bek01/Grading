package uz.hrlab.grading.evaluation.domain;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure averaging service (BE-3) — the ONLY place panel averaging math lives.
 *
 * <h3>Reproducibility &amp; rounding contract</h3>
 * <p>Mirrors {@link EvaluationScoringEngine}'s purity: no clock, no IO, no
 * randomness — the same {@code List<EvaluatorFactorScores>} always yields a
 * byte-identical {@link PanelAverageResult}. Rounding is EXACTLY the engine's:
 * {@code BigDecimal}, {@link RoundingMode#HALF_UP}, raw scale 4, displayed scale
 * 2 (architecture §15.4). It does NOT re-run scoring — it averages the engine's
 * already-computed {@code raw_factor_score} output (REQ-AVG-2, no duplication).
 *
 * <h3>Algorithm</h3>
 * <pre>
 *   denominator      = number of COMPLETED contributing sheets (REQ-AVG-3)
 *   avg(factor)      = (Σ each evaluator's raw_factor_score for that factor)
 *                        / denominator, HALF_UP scale 4
 *   rawTotal         = Σ avg(factor) over every factor seen, re-rounded scale 4
 *   displayedTotal   = rawTotal at scale 2
 * </pre>
 *
 * <p>Per-factor-then-sum equals average-of-totals when every evaluator scored
 * every factor; computing per factor keeps it correct for the display the
 * product asks for and is robust if a factor is absent from one sheet (that
 * sheet contributes 0 for the missing factor, matching the engine's
 * unselected-factor semantics).
 */
@Component
public class PanelAveragingService {

    private static final int RAW_SCALE = 4;
    private static final int DISPLAYED_SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /**
     * @param completedSheets the COMPLETED contributing evaluators' per-factor
     *                        raw scores. Withdrawn / incomplete sheets MUST be
     *                        excluded by the caller (the denominator is the size
     *                        of this list).
     * @throws IllegalArgumentException when the list is null/empty (cannot
     *         average zero sheets — the caller guards min-evaluators first).
     */
    public PanelAverageResult average(List<EvaluatorFactorScores> completedSheets) {
        if (completedSheets == null || completedSheets.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot average a panel with zero completed evaluations");
        }
        int denominator = completedSheets.size();
        BigDecimal divisor = BigDecimal.valueOf(denominator);

        // Sum per factor across all sheets, preserving first-seen factor order
        // so the result map is deterministic for golden-file assertions.
        Map<UUID, BigDecimal> sumByFactor = new LinkedHashMap<>();
        for (EvaluatorFactorScores sheet : completedSheets) {
            Map<UUID, BigDecimal> scores = sheet.rawFactorScores() == null
                    ? Map.of() : sheet.rawFactorScores();
            for (Map.Entry<UUID, BigDecimal> e : scores.entrySet()) {
                BigDecimal value = e.getValue() == null ? BigDecimal.ZERO : e.getValue();
                sumByFactor.merge(e.getKey(), value, BigDecimal::add);
            }
        }

        Map<UUID, BigDecimal> avgByFactor = new LinkedHashMap<>();
        BigDecimal rawTotal = BigDecimal.ZERO.setScale(RAW_SCALE, ROUNDING);
        for (Map.Entry<UUID, BigDecimal> e : sumByFactor.entrySet()) {
            // mean = sum / denominator, HALF_UP scale 4 (engine RAW_SCALE).
            BigDecimal avg = e.getValue().divide(divisor, RAW_SCALE, ROUNDING);
            avgByFactor.put(e.getKey(), avg);
            rawTotal = rawTotal.add(avg);
        }
        rawTotal = rawTotal.setScale(RAW_SCALE, ROUNDING);
        BigDecimal displayedTotal = rawTotal.setScale(DISPLAYED_SCALE, ROUNDING);
        return new PanelAverageResult(rawTotal, displayedTotal, avgByFactor, denominator);
    }
}

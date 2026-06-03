package uz.hrlab.grading.evaluation.domain;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.methodology.domain.Factor;
import uz.hrlab.grading.methodology.domain.FactorLevel;
import uz.hrlab.grading.methodology.domain.ScoringMode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure scoring engine — implements architecture §15.2 (three scoring modes) and
 * §15.3 (algorithm) + §15.4 (rounding).
 *
 * <h3>Reproducibility</h3>
 * <p>This class is a pure function: the same {@link ScoringInputs} always
 * produces a byte-identical {@link ScoringResult}. No clock, no randomness, no
 * IO. BigDecimal arithmetic with {@code RoundingMode.HALF_UP} at every step;
 * raw stored to 4 decimal places, displayed to 2 (architecture §15.4).
 *
 * <h3>Algorithms</h3>
 * <pre>
 *   DIRECT_POINTS:    factorScore = level.points
 *                     total = Σ factorScore
 *
 *   WEIGHTED_POINTS:  normalised  = level.points / factor.maxPoints
 *                     factorScore = factor.weight * normalised
 *                     total       = Σ factorScore
 *                     (factor.maxPoints == 0  → factorScore = 0)
 *
 *   WEIGHTED_SCALE:   factorScore = factor.weight * level.scaleValue
 *                     total       = Σ factorScore
 *                     (level.scaleValue == null → treated as 0)
 * </pre>
 *
 * <h3>Missing selections</h3>
 * <p>A factor whose selection is absent contributes 0 (the evaluation will be
 * marked INCOMPLETE by {@link EvaluationCompletenessChecker} if any required
 * factor is unselected — see architecture §15.5).
 */
@Component
public class EvaluationScoringEngine {

    private static final int RAW_SCALE = 4;
    private static final int DISPLAYED_SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public ScoringResult compute(ScoringInputs inputs) {
        if (inputs == null || inputs.version() == null) {
            throw new IllegalArgumentException("ScoringInputs and methodology version must not be null");
        }
        ScoringMode mode = inputs.version().scoringMode();
        if (mode == null) {
            throw new IllegalArgumentException("MethodologyVersion has no scoringMode");
        }
        List<Factor> factors = inputs.factors() == null ? List.of() : inputs.factors();
        Map<UUID, List<FactorLevel>> levelsByFactor = inputs.levelsByFactor() == null
                ? Map.of() : inputs.levelsByFactor();
        Map<UUID, UUID> selections = inputs.selections() == null
                ? Map.of() : inputs.selections();

        // Use LinkedHashMap to preserve factor sort order in the per-factor map —
        // makes audit + UI display deterministic.
        Map<UUID, BigDecimal> perFactor = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO.setScale(RAW_SCALE, ROUNDING);

        for (Factor f : factors) {
            BigDecimal factorScore = scoreFactor(mode, f, levelsByFactor.get(f.id()),
                    selections.get(f.id()));
            perFactor.put(f.id(), factorScore);
            total = total.add(factorScore);
        }

        BigDecimal rawTotal = total.setScale(RAW_SCALE, ROUNDING);
        BigDecimal displayed = rawTotal.setScale(DISPLAYED_SCALE, ROUNDING);
        return new ScoringResult(rawTotal, displayed, new HashMap<>(perFactor));
    }

    /** Compute one factor's score; unselected → 0. */
    private BigDecimal scoreFactor(ScoringMode mode, Factor factor,
                                   List<FactorLevel> levels, UUID selectedLevelId) {
        if (selectedLevelId == null) {
            return BigDecimal.ZERO.setScale(RAW_SCALE, ROUNDING);
        }
        FactorLevel selected = findLevel(levels, selectedLevelId);
        if (selected == null) {
            throw new IllegalArgumentException(
                    "FactorLevel " + selectedLevelId + " not found for factor " + factor.id());
        }
        return switch (mode) {
            case DIRECT_POINTS -> nullSafePoints(selected.points())
                    .setScale(RAW_SCALE, ROUNDING);
            case WEIGHTED_POINTS -> weightedPoints(factor, selected);
            case WEIGHTED_SCALE -> weightedScale(factor, selected);
        };
    }

    private BigDecimal weightedPoints(Factor factor, FactorLevel selected) {
        BigDecimal weight = nullSafe(factor.weight());
        BigDecimal levelPoints = nullSafePoints(selected.points());
        BigDecimal max = nullSafe(factor.maxPoints());
        if (max.compareTo(BigDecimal.ZERO) == 0) {
            // Avoid division by zero — undefined factor contributes 0.
            return BigDecimal.ZERO.setScale(RAW_SCALE, ROUNDING);
        }
        // normalised = level.points / max  (precision 8 internal, rounded HALF_UP)
        BigDecimal normalised = levelPoints.divide(max, 8, ROUNDING);
        return weight.multiply(normalised).setScale(RAW_SCALE, ROUNDING);
    }

    private BigDecimal weightedScale(Factor factor, FactorLevel selected) {
        BigDecimal weight = nullSafe(factor.weight());
        BigDecimal scale = selected.scaleValue() == null
                ? BigDecimal.ZERO
                : selected.scaleValue();
        return weight.multiply(scale).setScale(RAW_SCALE, ROUNDING);
    }

    private static FactorLevel findLevel(List<FactorLevel> levels, UUID levelId) {
        if (levels == null) return null;
        for (FactorLevel l : levels) {
            if (levelId.equals(l.id())) {
                return l;
            }
        }
        return null;
    }

    private static BigDecimal nullSafe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal nullSafePoints(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}

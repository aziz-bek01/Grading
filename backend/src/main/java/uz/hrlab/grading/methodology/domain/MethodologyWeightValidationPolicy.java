package uz.hrlab.grading.methodology.domain;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Approve-time validation: per scoring mode, the sum of factor.weight must
 * match a known target (architecture §15 Scoring engine).
 *
 * <ul>
 *   <li>{@code DIRECT_POINTS}: no weight sum constraint (weights are
 *       informational; total = Σ level.points).</li>
 *   <li>{@code WEIGHTED_POINTS}: |Σ weight − 100.0000| ≤ {@link #WEIGHT_SUM_TOLERANCE}.</li>
 *   <li>{@code WEIGHTED_SCALE}: |Σ weight − targetTotalPoints| ≤ {@link #WEIGHT_SUM_TOLERANCE} (targetTotalPoints must be set).</li>
 * </ul>
 *
 * <p>The {@link #WEIGHT_SUM_TOLERANCE} of {@code 0.0001} matches the frontend
 * WeightSumVisualizer "success tone" threshold so the UI's green-light state
 * cannot be invalidated by a server-side reject at APPROVE time (defect
 * D-401). Master plan §14 + §15: {@code BigDecimal scale=4 RoundingMode.HALF_UP}.
 */
@Component
public class MethodologyWeightValidationPolicy {

    public static final BigDecimal HUNDRED_PERCENT =
            new BigDecimal("100.0000");

    /**
     * Tolerance for floating-point drift between summed factor weights and
     * the per-mode target. Aligned with frontend
     * {@code WeightSumVisualizer} (1e-4). Frontend may import this constant
     * for i18n strings; do not change without coordinated frontend update.
     */
    public static final BigDecimal WEIGHT_SUM_TOLERANCE =
            new BigDecimal("0.0001");

    /**
     * @throws MethodologyVersionTransitionRejectedException if the weight
     *         sum invariant is violated for the given mode.
     */
    public void validate(ScoringMode mode, BigDecimal targetTotalPoints,
                         List<? extends FactorWeightView> factors) {
        if (factors == null || factors.isEmpty()) {
            throw new MethodologyVersionTransitionRejectedException(
                    "Methodology version must have at least one factor before approval");
        }
        if (mode == ScoringMode.DIRECT_POINTS) {
            return; // no weight-sum constraint
        }
        BigDecimal sum = factors.stream()
                .map(FactorWeightView::weight)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (mode == ScoringMode.WEIGHTED_POINTS) {
            BigDecimal diff = sum.subtract(HUNDRED_PERCENT).abs();
            if (diff.compareTo(WEIGHT_SUM_TOLERANCE) > 0) {
                throw new MethodologyVersionTransitionRejectedException(
                        "WEIGHTED_POINTS requires factor weight sum == 100.0000 (±"
                                + WEIGHT_SUM_TOLERANCE.toPlainString()
                                + "), got " + sum.toPlainString());
            }
            return;
        }
        // WEIGHTED_SCALE
        if (targetTotalPoints == null) {
            throw new MethodologyVersionTransitionRejectedException(
                    "WEIGHTED_SCALE requires targetTotalPoints to be set");
        }
        BigDecimal diff = sum.subtract(targetTotalPoints).abs();
        if (diff.compareTo(WEIGHT_SUM_TOLERANCE) > 0) {
            throw new MethodologyVersionTransitionRejectedException(
                    "WEIGHTED_SCALE requires factor weight sum == "
                            + targetTotalPoints.toPlainString()
                            + " (±" + WEIGHT_SUM_TOLERANCE.toPlainString() + ")"
                            + ", got " + sum.toPlainString());
        }
    }

    /** View interface so the policy can be called with either domain or JPA list. */
    public interface FactorWeightView {
        BigDecimal weight();
    }
}

package uz.hrlab.grading.methodology.domain;

/**
 * Scoring mode (architecture §15 Scoring engine; PRD Phase 4).
 *
 * <ul>
 *   <li>{@code DIRECT_POINTS}: factor levels carry absolute point values. No
 *       weight sum constraint; total = Σ level.points.</li>
 *   <li>{@code WEIGHTED_POINTS}: factor.weight sums to 100 (percentage); each
 *       factor contributes {@code weight * (level.points / 100)} normalized
 *       points.</li>
 *   <li>{@code WEIGHTED_SCALE}: factor.weight sums to the methodology's
 *       targetTotalPoints; each factor level carries a 1-N scale value;
 *       contribution = {@code weight * (scaleValue / maxScale)}.</li>
 * </ul>
 */
public enum ScoringMode {
    DIRECT_POINTS,
    WEIGHTED_POINTS,
    WEIGHTED_SCALE
}

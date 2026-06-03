package uz.hrlab.grading.evaluation.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * EvaluationScore — one row per (evaluation, factor) selection.
 *
 * <p>{@code rawFactorScore} is computed by {@link EvaluationScoringEngine}
 * based on the {@link uz.hrlab.grading.methodology.domain.ScoringMode}.
 * Manual calibration sets {@code isManuallyAdjusted=true}, preserves the
 * pre-calibration value in {@code originalFactorScore}, and requires a
 * reason ≥ 20 chars + CALIBRATION_EDIT permission.
 */
public final class EvaluationScore {

    private final UUID id;
    private final UUID tenantId;
    private final UUID evaluationId;
    private final UUID factorId;
    private final UUID factorLevelId;
    private final BigDecimal rawFactorScore;
    private final String commentText;
    private final boolean manuallyAdjusted;
    private final BigDecimal originalFactorScore;
    private final UUID adjustedByUserId;
    private final OffsetDateTime adjustedAt;
    private final String adjustmentReason;

    public EvaluationScore(UUID id, UUID tenantId, UUID evaluationId,
                           UUID factorId, UUID factorLevelId,
                           BigDecimal rawFactorScore, String commentText,
                           boolean manuallyAdjusted, BigDecimal originalFactorScore,
                           UUID adjustedByUserId, OffsetDateTime adjustedAt,
                           String adjustmentReason) {
        this.id = id;
        this.tenantId = tenantId;
        this.evaluationId = evaluationId;
        this.factorId = factorId;
        this.factorLevelId = factorLevelId;
        this.rawFactorScore = rawFactorScore;
        this.commentText = commentText;
        this.manuallyAdjusted = manuallyAdjusted;
        this.originalFactorScore = originalFactorScore;
        this.adjustedByUserId = adjustedByUserId;
        this.adjustedAt = adjustedAt;
        this.adjustmentReason = adjustmentReason;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID evaluationId() { return evaluationId; }
    public UUID factorId() { return factorId; }
    public UUID factorLevelId() { return factorLevelId; }
    public BigDecimal rawFactorScore() { return rawFactorScore; }
    public String commentText() { return commentText; }
    public boolean manuallyAdjusted() { return manuallyAdjusted; }
    public BigDecimal originalFactorScore() { return originalFactorScore; }
    public UUID adjustedByUserId() { return adjustedByUserId; }
    public OffsetDateTime adjustedAt() { return adjustedAt; }
    public String adjustmentReason() { return adjustmentReason; }
}

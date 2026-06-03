package uz.hrlab.grading.evaluation.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Append-only calibration history — one row per manual factor-score
 * adjustment (architecture §14 row "Calibration" + PRD Phase 5).
 */
public final class EvaluationCalibrationEvent {

    private final UUID id;
    private final UUID tenantId;
    private final UUID evaluationId;
    private final UUID factorId;
    private final BigDecimal originalScore;
    private final BigDecimal adjustedScore;
    private final BigDecimal delta;
    private final String reason;
    private final UUID decidedBy;
    private final OffsetDateTime decidedAt;

    public EvaluationCalibrationEvent(UUID id, UUID tenantId, UUID evaluationId,
                                      UUID factorId, BigDecimal originalScore,
                                      BigDecimal adjustedScore, BigDecimal delta,
                                      String reason, UUID decidedBy,
                                      OffsetDateTime decidedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.evaluationId = evaluationId;
        this.factorId = factorId;
        this.originalScore = originalScore;
        this.adjustedScore = adjustedScore;
        this.delta = delta;
        this.reason = reason;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID evaluationId() { return evaluationId; }
    public UUID factorId() { return factorId; }
    public BigDecimal originalScore() { return originalScore; }
    public BigDecimal adjustedScore() { return adjustedScore; }
    public BigDecimal delta() { return delta; }
    public String reason() { return reason; }
    public UUID decidedBy() { return decidedBy; }
    public OffsetDateTime decidedAt() { return decidedAt; }
}

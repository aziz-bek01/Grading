package uz.hrlab.grading.evaluation.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import uz.hrlab.grading.evaluation.domain.EvaluationCalibrationEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Append-only calibration history row. Deliberately does NOT extend
 * {@link uz.hrlab.grading.common.persistence.AuditedJpaEntity} — there are no
 * updated_at / updated_by fields because rows are immutable.
 */
@Entity
@Table(name = "evaluation_calibration_events")
public class EvaluationCalibrationEventJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "evaluation_id", nullable = false, updatable = false)
    private UUID evaluationId;

    @Column(name = "factor_id", nullable = false, updatable = false)
    private UUID factorId;

    @Column(name = "original_score", precision = 12, scale = 4, nullable = false, updatable = false)
    private BigDecimal originalScore;

    @Column(name = "adjusted_score", precision = 12, scale = 4, nullable = false, updatable = false)
    private BigDecimal adjustedScore;

    @Column(name = "delta", precision = 12, scale = 4, nullable = false, updatable = false)
    private BigDecimal delta;

    @Column(name = "reason", nullable = false, updatable = false)
    private String reason;

    @Column(name = "decided_by", nullable = false, updatable = false)
    private UUID decidedBy;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private OffsetDateTime decidedAt;

    protected EvaluationCalibrationEventJpaEntity() { }

    public EvaluationCalibrationEventJpaEntity(UUID id, UUID tenantId, UUID evaluationId,
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

    public EvaluationCalibrationEvent toDomain() {
        return new EvaluationCalibrationEvent(id, tenantId, evaluationId, factorId,
                originalScore, adjustedScore, delta, reason, decidedBy, decidedAt);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getEvaluationId() { return evaluationId; }
    public UUID getFactorId() { return factorId; }
    public BigDecimal getOriginalScore() { return originalScore; }
    public BigDecimal getAdjustedScore() { return adjustedScore; }
    public BigDecimal getDelta() { return delta; }
    public String getReason() { return reason; }
    public UUID getDecidedBy() { return decidedBy; }
    public OffsetDateTime getDecidedAt() { return decidedAt; }
}

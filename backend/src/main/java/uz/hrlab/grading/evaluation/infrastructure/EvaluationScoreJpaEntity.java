package uz.hrlab.grading.evaluation.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;
import uz.hrlab.grading.evaluation.domain.EvaluationScore;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "evaluation_scores")
public class EvaluationScoreJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "evaluation_id", nullable = false, updatable = false)
    private UUID evaluationId;

    @Column(name = "factor_id", nullable = false, updatable = false)
    private UUID factorId;

    @Column(name = "factor_level_id", nullable = false)
    private UUID factorLevelId;

    @Column(name = "raw_factor_score", precision = 12, scale = 4, nullable = false)
    private BigDecimal rawFactorScore = BigDecimal.ZERO;

    @Column(name = "comment_text")
    private String commentText;

    @Column(name = "is_manually_adjusted", nullable = false)
    private boolean manuallyAdjusted = false;

    @Column(name = "original_factor_score", precision = 12, scale = 4)
    private BigDecimal originalFactorScore;

    @Column(name = "adjusted_by_user_id")
    private UUID adjustedByUserId;

    @Column(name = "adjusted_at")
    private OffsetDateTime adjustedAt;

    @Column(name = "adjustment_reason")
    private String adjustmentReason;

    protected EvaluationScoreJpaEntity() { }

    public EvaluationScoreJpaEntity(UUID id, UUID tenantId, UUID evaluationId,
                                    UUID factorId, UUID factorLevelId,
                                    BigDecimal rawFactorScore) {
        this.id = id;
        this.tenantId = tenantId;
        this.evaluationId = evaluationId;
        this.factorId = factorId;
        this.factorLevelId = factorLevelId;
        this.rawFactorScore = rawFactorScore == null ? BigDecimal.ZERO : rawFactorScore;
    }

    public EvaluationScore toDomain() {
        return new EvaluationScore(id, tenantId, evaluationId, factorId, factorLevelId,
                rawFactorScore, commentText, manuallyAdjusted, originalFactorScore,
                adjustedByUserId, adjustedAt, adjustmentReason);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getEvaluationId() { return evaluationId; }
    public UUID getFactorId() { return factorId; }
    public UUID getFactorLevelId() { return factorLevelId; }
    public BigDecimal getRawFactorScore() { return rawFactorScore; }
    public String getCommentText() { return commentText; }
    public boolean isManuallyAdjusted() { return manuallyAdjusted; }
    public BigDecimal getOriginalFactorScore() { return originalFactorScore; }
    public UUID getAdjustedByUserId() { return adjustedByUserId; }
    public OffsetDateTime getAdjustedAt() { return adjustedAt; }
    public String getAdjustmentReason() { return adjustmentReason; }

    public void setFactorLevelId(UUID v) { this.factorLevelId = v; }
    public void setRawFactorScore(BigDecimal v) {
        this.rawFactorScore = v == null ? BigDecimal.ZERO : v;
    }
    public void setCommentText(String v) { this.commentText = v; }
    public void setManuallyAdjusted(boolean v) { this.manuallyAdjusted = v; }
    public void setOriginalFactorScore(BigDecimal v) { this.originalFactorScore = v; }
    public void setAdjustedByUserId(UUID v) { this.adjustedByUserId = v; }
    public void setAdjustedAt(OffsetDateTime v) { this.adjustedAt = v; }
    public void setAdjustmentReason(String v) { this.adjustmentReason = v; }
}

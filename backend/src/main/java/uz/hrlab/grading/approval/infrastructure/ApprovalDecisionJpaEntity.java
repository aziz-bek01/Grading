package uz.hrlab.grading.approval.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import uz.hrlab.grading.approval.domain.ApprovalDecisionType;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "approval_decisions")
public class ApprovalDecisionJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "approval_step_id", nullable = false, updatable = false)
    private UUID approvalStepId;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_type", nullable = false, length = 24, updatable = false)
    private ApprovalDecisionType decisionType;

    @Column(name = "reason_text", columnDefinition = "text")
    private String reasonText;

    @Column(name = "decided_by", nullable = false, updatable = false)
    private UUID decidedBy;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private OffsetDateTime decidedAt;

    protected ApprovalDecisionJpaEntity() { }

    public ApprovalDecisionJpaEntity(UUID id, UUID tenantId, UUID approvalStepId,
                                     ApprovalDecisionType decisionType, String reasonText,
                                     UUID decidedBy, OffsetDateTime decidedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.approvalStepId = approvalStepId;
        this.decisionType = decisionType;
        this.reasonText = reasonText;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getApprovalStepId() { return approvalStepId; }
    public ApprovalDecisionType getDecisionType() { return decisionType; }
    public String getReasonText() { return reasonText; }
    public UUID getDecidedBy() { return decidedBy; }
    public OffsetDateTime getDecidedAt() { return decidedAt; }
}

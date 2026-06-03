package uz.hrlab.grading.approval.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.hrlab.grading.approval.domain.ApprovalStepStatus;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "approval_steps")
public class ApprovalStepJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "approval_request_id", nullable = false, updatable = false)
    private UUID approvalRequestId;

    @Column(name = "step_order", nullable = false, updatable = false)
    private int stepOrder;

    @Column(name = "approver_user_id")
    private UUID approverUserId;

    @Column(name = "required_permission", nullable = false, length = 64, updatable = false)
    private String requiredPermission;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private ApprovalStepStatus status;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reason_i18n", columnDefinition = "jsonb")
    private Map<String, String> reasonI18n = new HashMap<>();

    protected ApprovalStepJpaEntity() { }

    public ApprovalStepJpaEntity(UUID id, UUID tenantId, UUID approvalRequestId, int stepOrder,
                                 UUID approverUserId, String requiredPermission,
                                 ApprovalStepStatus status) {
        this.id = id;
        this.tenantId = tenantId;
        this.approvalRequestId = approvalRequestId;
        this.stepOrder = stepOrder;
        this.approverUserId = approverUserId;
        this.requiredPermission = requiredPermission;
        this.status = status;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getApprovalRequestId() { return approvalRequestId; }
    public int getStepOrder() { return stepOrder; }
    public UUID getApproverUserId() { return approverUserId; }
    public String getRequiredPermission() { return requiredPermission; }
    public ApprovalStepStatus getStatus() { return status; }
    public UUID getDecidedBy() { return decidedBy; }
    public OffsetDateTime getDecidedAt() { return decidedAt; }
    public Map<String, String> getReasonI18n() { return reasonI18n; }

    public void setApproverUserId(UUID v) { this.approverUserId = v; }
    public void setStatus(ApprovalStepStatus v) { this.status = v; }
    public void setDecidedBy(UUID v) { this.decidedBy = v; }
    public void setDecidedAt(OffsetDateTime v) { this.decidedAt = v; }
    public void setReasonI18n(Map<String, String> v) {
        this.reasonI18n = v == null ? new HashMap<>() : new HashMap<>(v);
    }
}

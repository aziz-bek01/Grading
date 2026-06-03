package uz.hrlab.grading.approval.domain;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public final class ApprovalStep {

    private final UUID id;
    private final UUID approvalRequestId;
    private final int stepOrder;
    private final UUID approverUserId;
    private final String requiredPermission;
    private final ApprovalStepStatus status;
    private final UUID decidedBy;
    private final OffsetDateTime decidedAt;
    private final Map<String, String> reasonI18n;

    public ApprovalStep(UUID id, UUID approvalRequestId, int stepOrder,
                        UUID approverUserId, String requiredPermission,
                        ApprovalStepStatus status, UUID decidedBy,
                        OffsetDateTime decidedAt, Map<String, String> reasonI18n) {
        this.id = id;
        this.approvalRequestId = approvalRequestId;
        this.stepOrder = stepOrder;
        this.approverUserId = approverUserId;
        this.requiredPermission = requiredPermission;
        this.status = status;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
        this.reasonI18n = reasonI18n;
    }

    public UUID id() { return id; }
    public UUID approvalRequestId() { return approvalRequestId; }
    public int stepOrder() { return stepOrder; }
    public UUID approverUserId() { return approverUserId; }
    public String requiredPermission() { return requiredPermission; }
    public ApprovalStepStatus status() { return status; }
    public UUID decidedBy() { return decidedBy; }
    public OffsetDateTime decidedAt() { return decidedAt; }
    public Map<String, String> reasonI18n() { return reasonI18n; }
}

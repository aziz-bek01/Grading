package uz.hrlab.grading.approval.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ApprovalRequest {

    private final UUID id;
    private final UUID tenantId;
    private final UUID projectId;
    private final ApprovalEntityType entityType;
    private final UUID entityId;
    private final UUID requestedBy;
    private final OffsetDateTime requestedAt;
    private final ApprovalRequestStatus currentStatus;
    private final Map<String, String> notesI18n;
    private final List<ApprovalStep> steps;

    public ApprovalRequest(UUID id, UUID tenantId, UUID projectId,
                           ApprovalEntityType entityType, UUID entityId,
                           UUID requestedBy, OffsetDateTime requestedAt,
                           ApprovalRequestStatus currentStatus,
                           Map<String, String> notesI18n,
                           List<ApprovalStep> steps) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.requestedBy = requestedBy;
        this.requestedAt = requestedAt;
        this.currentStatus = currentStatus;
        this.notesI18n = notesI18n;
        this.steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID projectId() { return projectId; }
    public ApprovalEntityType entityType() { return entityType; }
    public UUID entityId() { return entityId; }
    public UUID requestedBy() { return requestedBy; }
    public OffsetDateTime requestedAt() { return requestedAt; }
    public ApprovalRequestStatus currentStatus() { return currentStatus; }
    public Map<String, String> notesI18n() { return notesI18n; }
    public List<ApprovalStep> steps() { return steps; }
}

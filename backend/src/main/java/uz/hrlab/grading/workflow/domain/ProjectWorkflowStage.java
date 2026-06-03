package uz.hrlab.grading.workflow.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Per-stage row inside a {@link ProjectWorkflow}. Read-only domain projection;
 * mutation goes through {@code WorkflowRecomputeService} which writes a fresh
 * snapshot for each of the 11 stages.
 */
public final class ProjectWorkflowStage {

    private final UUID id;
    private final UUID tenantId;
    private final UUID projectWorkflowId;
    private final UUID projectId;
    private final WorkflowStage stage;
    private final WorkflowStageStatus status;
    private final BigDecimal completionPercent;
    private final UUID responsibleUserId;
    private final OffsetDateTime lastUpdatedAt;
    private final UUID lastUpdatedBy;
    private final int sortOrder;

    public ProjectWorkflowStage(UUID id, UUID tenantId, UUID projectWorkflowId, UUID projectId,
                                WorkflowStage stage, WorkflowStageStatus status,
                                BigDecimal completionPercent, UUID responsibleUserId,
                                OffsetDateTime lastUpdatedAt, UUID lastUpdatedBy, int sortOrder) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectWorkflowId = projectWorkflowId;
        this.projectId = projectId;
        this.stage = stage;
        this.status = status;
        this.completionPercent = completionPercent;
        this.responsibleUserId = responsibleUserId;
        this.lastUpdatedAt = lastUpdatedAt;
        this.lastUpdatedBy = lastUpdatedBy;
        this.sortOrder = sortOrder;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID projectWorkflowId() { return projectWorkflowId; }
    public UUID projectId() { return projectId; }
    public WorkflowStage stage() { return stage; }
    public WorkflowStageStatus status() { return status; }
    public BigDecimal completionPercent() { return completionPercent; }
    public UUID responsibleUserId() { return responsibleUserId; }
    public OffsetDateTime lastUpdatedAt() { return lastUpdatedAt; }
    public UUID lastUpdatedBy() { return lastUpdatedBy; }
    public int sortOrder() { return sortOrder; }
}

package uz.hrlab.grading.workflow.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Pure domain model for a project's workflow aggregate. One per Project (1:1).
 * Contains the current stage pointer plus the 11 computed stage rows.
 */
public final class ProjectWorkflow {

    private final UUID id;
    private final UUID tenantId;
    private final UUID projectId;
    private final WorkflowStage currentStage;
    private final OffsetDateTime startedAt;
    private final OffsetDateTime archivedAt;
    private final List<ProjectWorkflowStage> stages;

    public ProjectWorkflow(UUID id, UUID tenantId, UUID projectId,
                           WorkflowStage currentStage,
                           OffsetDateTime startedAt, OffsetDateTime archivedAt,
                           List<ProjectWorkflowStage> stages) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.currentStage = currentStage;
        this.startedAt = startedAt;
        this.archivedAt = archivedAt;
        this.stages = stages == null ? List.of() : List.copyOf(stages);
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID projectId() { return projectId; }
    public WorkflowStage currentStage() { return currentStage; }
    public OffsetDateTime startedAt() { return startedAt; }
    public OffsetDateTime archivedAt() { return archivedAt; }
    public List<ProjectWorkflowStage> stages() { return stages; }
}

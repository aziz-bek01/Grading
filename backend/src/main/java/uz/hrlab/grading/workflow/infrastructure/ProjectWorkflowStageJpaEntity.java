package uz.hrlab.grading.workflow.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;
import uz.hrlab.grading.workflow.domain.WorkflowStage;
import uz.hrlab.grading.workflow.domain.WorkflowStageStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "project_workflow_stages")
public class ProjectWorkflowStageJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "project_workflow_id", nullable = false, updatable = false)
    private UUID projectWorkflowId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 32, updatable = false)
    private WorkflowStage stage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private WorkflowStageStatus status;

    @Column(name = "completion_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal completionPercent = BigDecimal.ZERO;

    @Column(name = "responsible_user_id")
    private UUID responsibleUserId;

    @Column(name = "last_updated_at")
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    private OffsetDateTime lastUpdatedAt;

    @Column(name = "last_updated_by")
    private UUID lastUpdatedBy;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ProjectWorkflowStageJpaEntity() { }

    public ProjectWorkflowStageJpaEntity(UUID id, UUID tenantId, UUID projectWorkflowId, UUID projectId,
                                          WorkflowStage stage, WorkflowStageStatus status,
                                          BigDecimal completionPercent, int sortOrder) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectWorkflowId = projectWorkflowId;
        this.projectId = projectId;
        this.stage = stage;
        this.status = status;
        this.completionPercent = completionPercent == null ? BigDecimal.ZERO : completionPercent;
        this.sortOrder = sortOrder;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectWorkflowId() { return projectWorkflowId; }
    public UUID getProjectId() { return projectId; }
    public WorkflowStage getStage() { return stage; }
    public WorkflowStageStatus getStatus() { return status; }
    public BigDecimal getCompletionPercent() { return completionPercent; }
    public UUID getResponsibleUserId() { return responsibleUserId; }
    public OffsetDateTime getLastUpdatedAt() { return lastUpdatedAt; }
    public UUID getLastUpdatedBy() { return lastUpdatedBy; }
    public int getSortOrder() { return sortOrder; }

    public void setStatus(WorkflowStageStatus status) { this.status = status; }
    public void setCompletionPercent(BigDecimal percent) {
        this.completionPercent = percent == null ? BigDecimal.ZERO : percent;
    }
    public void setResponsibleUserId(UUID responsibleUserId) { this.responsibleUserId = responsibleUserId; }
    public void setLastUpdatedAt(OffsetDateTime lastUpdatedAt) { this.lastUpdatedAt = lastUpdatedAt; }
    public void setLastUpdatedBy(UUID lastUpdatedBy) { this.lastUpdatedBy = lastUpdatedBy; }
}

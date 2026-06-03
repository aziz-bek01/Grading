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

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "project_workflows")
public class ProjectWorkflowJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_stage", nullable = false, length = 32)
    private WorkflowStage currentStage;

    @Column(name = "started_at")
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    private OffsetDateTime startedAt;

    @Column(name = "archived_at")
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    private OffsetDateTime archivedAt;

    protected ProjectWorkflowJpaEntity() { }

    public ProjectWorkflowJpaEntity(UUID id, UUID tenantId, UUID projectId,
                                    WorkflowStage currentStage, OffsetDateTime startedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.currentStage = currentStage;
        this.startedAt = startedAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public WorkflowStage getCurrentStage() { return currentStage; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getArchivedAt() { return archivedAt; }

    public void setCurrentStage(WorkflowStage stage) { this.currentStage = stage; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public void setArchivedAt(OffsetDateTime archivedAt) { this.archivedAt = archivedAt; }
}

package uz.hrlab.grading.approval.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.approval.domain.ApprovalRequestStatus;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "approval_requests")
public class ApprovalRequestJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 64, updatable = false)
    private ApprovalEntityType entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private OffsetDateTime requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false, length = 32)
    private ApprovalRequestStatus currentStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notes_i18n", columnDefinition = "jsonb")
    private Map<String, String> notesI18n = new HashMap<>();

    protected ApprovalRequestJpaEntity() { }

    public ApprovalRequestJpaEntity(UUID id, UUID tenantId, UUID projectId,
                                    ApprovalEntityType entityType, UUID entityId,
                                    UUID requestedBy, OffsetDateTime requestedAt,
                                    ApprovalRequestStatus currentStatus,
                                    Map<String, String> notesI18n) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.requestedBy = requestedBy;
        this.requestedAt = requestedAt;
        this.currentStatus = currentStatus;
        this.notesI18n = notesI18n == null ? new HashMap<>() : new HashMap<>(notesI18n);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public ApprovalEntityType getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public UUID getRequestedBy() { return requestedBy; }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public ApprovalRequestStatus getCurrentStatus() { return currentStatus; }
    public Map<String, String> getNotesI18n() { return notesI18n; }

    public void setCurrentStatus(ApprovalRequestStatus status) { this.currentStatus = status; }
    public void setNotesI18n(Map<String, String> notesI18n) {
        this.notesI18n = notesI18n == null ? new HashMap<>() : new HashMap<>(notesI18n);
    }
}

package uz.hrlab.grading.audit.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Append-only persistence for {@code public.system_audit_log}
 * (database-blueprint §5.1 / §15).
 *
 * <p>Intentionally exposes NO setters and the corresponding repository
 * intentionally exposes NO update or delete methods (AuditAppendOnlyTest
 * locks this contract).
 *
 * <p>For tenant business audit events the per-tenant schema has its own
 * {@code audit_logs} table — added in Phase 2.
 */
@Entity
@Table(name = "system_audit_log", schema = "public")
public class SystemAuditLogJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", updatable = false)
    private UUID tenantId;

    @Column(name = "project_id", updatable = false)
    private UUID projectId;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(name = "action", nullable = false, length = 100, updatable = false)
    private String action;

    @Column(name = "entity_type", length = 100, updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private UUID entityId;

    @Column(name = "before_json", columnDefinition = "jsonb", updatable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String beforeJson;

    @Column(name = "after_json", columnDefinition = "jsonb", updatable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String afterJson;

    @Column(name = "reason", columnDefinition = "text", updatable = false)
    private String reason;

    @Column(name = "ip_address", length = 64, updatable = false)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "text", updatable = false)
    private String userAgent;

    @Column(name = "correlation_id", length = 100, updatable = false)
    private String correlationId;

    @Column(name = "trace_id", length = 100, updatable = false)
    private String traceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "hash_prev", columnDefinition = "text", updatable = false)
    private String hashPrev;

    @Column(name = "hash_current", columnDefinition = "text", nullable = false, updatable = false)
    private String hashCurrent;

    protected SystemAuditLogJpaEntity() { }

    public SystemAuditLogJpaEntity(UUID id, UUID tenantId, UUID projectId, UUID actorUserId,
                                   String action, String entityType, UUID entityId,
                                   String beforeJson, String afterJson, String reason,
                                   String ipAddress, String userAgent,
                                   String correlationId, String traceId,
                                   OffsetDateTime createdAt, String hashPrev, String hashCurrent) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.actorUserId = actorUserId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
        this.reason = reason;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.correlationId = correlationId;
        this.traceId = traceId;
        this.createdAt = createdAt;
        this.hashPrev = hashPrev;
        this.hashCurrent = hashCurrent;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getAction() { return action; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public String getHashPrev() { return hashPrev; }
    public String getHashCurrent() { return hashCurrent; }
}

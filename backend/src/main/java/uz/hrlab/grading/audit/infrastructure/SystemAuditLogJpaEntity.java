package uz.hrlab.grading.audit.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
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
 *
 * <p><b>Composite primary key (D-5):</b> the underlying table is
 * RANGE-partitioned by {@code created_at} (control-plane/006-audit-logs-
 * partitioning.yaml). PostgreSQL requires every unique constraint to include
 * the partition key, so the PK is {@code (id, created_at)}. The JPA mapping
 * uses {@link IdClass} with {@link SystemAuditLogId} to mirror that —
 * Hibernate validation (ddl-auto=validate) now matches the database schema.
 */
@Entity
@Table(name = "system_audit_log", schema = "public")
@IdClass(SystemAuditLogId.class)
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

    /**
     * <b>Why explicit {@code @JdbcTypeCode(TIMESTAMP_WITH_TIMEZONE)}:</b>
     * the DB column is {@code TIMESTAMPTZ} but Hibernate 6 + pgjdbc default
     * to reading it as {@link java.time.LocalDateTime} via
     * {@code PgResultSet.getTimestamp}, which throws
     * {@code Cannot convert unsupported date type LocalDateTime to OffsetDateTime}
     * when the entity field is {@link OffsetDateTime}. The explicit JDBC
     * type code forces {@code getObject(idx, OffsetDateTime.class)} on the
     * read path and the same on JPQL parameter binding so {@code search(...)}
     * with {@code OffsetDateTime} {@code from}/{@code to} parameters round-
     * trips cleanly.
     */
    @Id
    @Column(name = "created_at", nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
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
    public UUID getProjectId() { return projectId; }
    public UUID getActorUserId() { return actorUserId; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public String getReason() { return reason; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public String getCorrelationId() { return correlationId; }
    public String getTraceId() { return traceId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public String getHashPrev() { return hashPrev; }
    public String getHashCurrent() { return hashCurrent; }
}

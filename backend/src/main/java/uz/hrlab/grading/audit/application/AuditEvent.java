package uz.hrlab.grading.audit.application;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * Carrier for one audit event. Used by services to record domain actions —
 * {@link AuditService} computes hash-chain fields and persists.
 *
 * <p>Salary fields must be redacted by the caller before passing
 * {@link #beforeJson}/{@link #afterJson} (security-blueprint §9.4,
 * database-blueprint §15.4).
 */
public final class AuditEvent {

    private final UUID tenantId;
    private final UUID projectId;
    private final UUID actorUserId;
    private final String action;
    private final String entityType;
    private final UUID entityId;
    private final JsonNode beforeJson;
    private final JsonNode afterJson;
    private final String reason;
    private final String ipAddress;
    private final String userAgent;
    private final String correlationId;
    private final String traceId;

    private AuditEvent(Builder b) {
        this.tenantId = b.tenantId;
        this.projectId = b.projectId;
        this.actorUserId = b.actorUserId;
        this.action = b.action;
        this.entityType = b.entityType;
        this.entityId = b.entityId;
        this.beforeJson = b.beforeJson;
        this.afterJson = b.afterJson;
        this.reason = b.reason;
        this.ipAddress = b.ipAddress;
        this.userAgent = b.userAgent;
        this.correlationId = b.correlationId;
        this.traceId = b.traceId;
    }

    public static Builder builder() { return new Builder(); }

    public UUID tenantId() { return tenantId; }
    public UUID projectId() { return projectId; }
    public UUID actorUserId() { return actorUserId; }
    public String action() { return action; }
    public String entityType() { return entityType; }
    public UUID entityId() { return entityId; }
    public JsonNode beforeJson() { return beforeJson; }
    public JsonNode afterJson() { return afterJson; }
    public String reason() { return reason; }
    public String ipAddress() { return ipAddress; }
    public String userAgent() { return userAgent; }
    public String correlationId() { return correlationId; }
    public String traceId() { return traceId; }

    public static final class Builder {
        private UUID tenantId;
        private UUID projectId;
        private UUID actorUserId;
        private String action;
        private String entityType;
        private UUID entityId;
        private JsonNode beforeJson;
        private JsonNode afterJson;
        private String reason;
        private String ipAddress;
        private String userAgent;
        private String correlationId;
        private String traceId;

        public Builder tenantId(UUID v) { this.tenantId = v; return this; }
        public Builder projectId(UUID v) { this.projectId = v; return this; }
        public Builder actorUserId(UUID v) { this.actorUserId = v; return this; }
        public Builder action(String v) { this.action = v; return this; }
        public Builder entityType(String v) { this.entityType = v; return this; }
        public Builder entityId(UUID v) { this.entityId = v; return this; }
        public Builder beforeJson(JsonNode v) { this.beforeJson = v; return this; }
        public Builder afterJson(JsonNode v) { this.afterJson = v; return this; }
        public Builder reason(String v) { this.reason = v; return this; }
        public Builder ipAddress(String v) { this.ipAddress = v; return this; }
        public Builder userAgent(String v) { this.userAgent = v; return this; }
        public Builder correlationId(String v) { this.correlationId = v; return this; }
        public Builder traceId(String v) { this.traceId = v; return this; }
        public AuditEvent build() { return new AuditEvent(this); }
    }
}

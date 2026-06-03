package uz.hrlab.grading.audit.infrastructure;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for {@link SystemAuditLogJpaEntity}
 * (database-blueprint §15.3, control-plane/006-audit-logs-partitioning.yaml).
 *
 * <p>PostgreSQL requires the partition key ({@code created_at}) to be part of
 * every unique constraint, including the PRIMARY KEY. Therefore the audit log
 * PK is composite {@code (id, created_at)} — this {@code @IdClass} mirrors
 * that on the JPA side.
 *
 * <p>Append-only contract: the entity has no setters and the repository
 * exposes no update/delete methods (see {@code AuditAppendOnlyTest}). This id
 * class is used only during insert and read.
 */
public class SystemAuditLogId implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID id;
    private OffsetDateTime createdAt;

    public SystemAuditLogId() {
    }

    public SystemAuditLogId(UUID id, OffsetDateTime createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SystemAuditLogId other)) return false;
        return Objects.equals(id, other.id)
                && Objects.equals(createdAt, other.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, createdAt);
    }
}

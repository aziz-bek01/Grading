package uz.hrlab.grading.access.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;

import java.util.UUID;

/**
 * Persistence representation of {@code public.user_project_assignments}
 * (E4-S0). This table EXISTED in the schema but had no JPA entity/repository —
 * it was orphaned, so {@code TenantContext.projectIds} was never populated for
 * real users. This entity wires it in.
 *
 * <p>Control-plane RBAC/ABAC mapping table: it lives next to
 * {@code user_tenant_memberships} / {@code user_roles} /
 * {@code user_department_scopes} and is RLS-FREE — isolation is enforced by
 * EXPLICIT {@code tenant_id = ? AND user_id = ?} predicates in the repository
 * (tenant sourced from {@code TenantContext}, never from input). {@code status}
 * mirrors the membership lifecycle: only {@code ACTIVE} rows constrain the user.
 *
 * <p>{@code project_id} is a plain indexed UUID with NO cross-schema FK — a
 * project lives in the tenant schema (integration/data blueprint §6.2), so a
 * control-plane FK would break the database-per-tenant isolation boundary.
 * Existence + tenant ownership are validated in application code.
 */
@Entity
@Table(name = "user_project_assignments", schema = "public",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_upa_user_tenant_project",
                columnNames = {"user_id", "tenant_id", "project_id"}))
public class UserProjectAssignmentJpaEntity extends AuditedJpaEntity {

    /** Status domain — ACTIVE assignments constrain the user; REVOKED kept for audit. */
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REVOKED = "REVOKED";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    protected UserProjectAssignmentJpaEntity() { }

    public UserProjectAssignmentJpaEntity(UUID id, UUID userId, UUID tenantId,
                                          UUID projectId, String status) {
        this.id = id;
        this.userId = userId;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.status = status;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public String getStatus() { return status; }

    public void setStatus(String status) { this.status = status; }
}

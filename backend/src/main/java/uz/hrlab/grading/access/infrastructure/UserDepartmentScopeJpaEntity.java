package uz.hrlab.grading.access.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;

import java.util.UUID;

/**
 * Persistence representation of {@code public.user_department_scopes} (E4-S0).
 *
 * <p>Per-tenant DEPARTMENT scope for a user — the persistence layer behind
 * {@code TenantContext.departmentScope}. A scope row on department {@code D}
 * grants visibility of {@code D} AND (after subtree expansion in the resolver)
 * all of its descendant departments/divisions.
 *
 * <p>Control-plane RBAC/ABAC mapping table — RLS-FREE and APP-GATED, exactly
 * like {@code user_roles} / {@code user_tenant_memberships} /
 * {@code user_project_assignments}. It is read on the auth path BEFORE the RLS
 * GUC is set, so forced RLS would empty it (see the migration comment).
 * Isolation is enforced by EXPLICIT {@code tenant_id} + {@code user_id}
 * predicates; tenant always comes from {@code TenantContext}, never input.
 *
 * <p>{@code department_id} is a plain indexed UUID with NO cross-schema FK —
 * departments live in the tenant schema; existence + tenant ownership are
 * validated in application code against the tenant's departments.
 */
@Entity
@Table(name = "user_department_scopes", schema = "public",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_uds_user_tenant_department",
                columnNames = {"user_id", "tenant_id", "department_id"}))
public class UserDepartmentScopeJpaEntity extends AuditedJpaEntity {

    /** Status domain — ACTIVE scopes are honoured; REVOKED kept for audit. */
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REVOKED = "REVOKED";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "department_id", nullable = false, updatable = false)
    private UUID departmentId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "granted_by_user_id")
    private UUID grantedByUserId;

    protected UserDepartmentScopeJpaEntity() { }

    public UserDepartmentScopeJpaEntity(UUID id, UUID userId, UUID tenantId,
                                        UUID departmentId, String status,
                                        UUID grantedByUserId) {
        this.id = id;
        this.userId = userId;
        this.tenantId = tenantId;
        this.departmentId = departmentId;
        this.status = status;
        this.grantedByUserId = grantedByUserId;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getDepartmentId() { return departmentId; }
    public String getStatus() { return status; }
    public UUID getGrantedByUserId() { return grantedByUserId; }

    public void setStatus(String status) { this.status = status; }
    public void setGrantedByUserId(UUID grantedByUserId) { this.grantedByUserId = grantedByUserId; }
}

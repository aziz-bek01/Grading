package uz.hrlab.grading.access.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.EntityListeners;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Persistence representation of {@code public.user_roles}
 * (database-blueprint §5.1).
 *
 * <p>A {@code user_roles} row attaches a single {@link RoleJpaEntity} to a
 * specific {@link UserTenantMembershipJpaEntity}, encoding the (user, tenant,
 * role) triple. The composite uniqueness is enforced at the DB level by
 * {@code uq_user_roles_membership_role}; the application also fails closed on
 * a duplicate-insert race via {@code DataIntegrityViolationException} → 409.
 *
 * <p>Intentionally does NOT extend {@code AuditedJpaEntity} because the
 * underlying {@code public.user_roles} migration only carries
 * {@code created_at} and {@code created_by} (no {@code version},
 * {@code updated_*}). Role assignments are immutable — to "change" a role
 * the application revokes the row and inserts a new one, both recorded in
 * {@code system_audit_log} via {@link uz.hrlab.grading.audit.application.AuditAction}.
 */
@Entity
@Table(name = "user_roles", schema = "public")
@EntityListeners(AuditingEntityListener.class)
public class UserRoleJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_tenant_membership_id", nullable = false, updatable = false)
    private UUID membershipId;

    @Column(name = "role_id", nullable = false, updatable = false)
    private UUID roleId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    protected UserRoleJpaEntity() { }

    public UserRoleJpaEntity(UUID id, UUID membershipId, UUID roleId) {
        this.id = id;
        this.membershipId = membershipId;
        this.roleId = roleId;
    }

    public UUID getId() { return id; }
    public UUID getMembershipId() { return membershipId; }
    public UUID getRoleId() { return roleId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public UUID getCreatedBy() { return createdBy; }
}

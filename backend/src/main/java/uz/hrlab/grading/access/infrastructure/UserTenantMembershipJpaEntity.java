package uz.hrlab.grading.access.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import uz.hrlab.grading.access.domain.MembershipStatus;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;

import java.util.UUID;

/**
 * Persistence representation of {@code public.user_tenant_memberships}.
 * Defines which user can act in which tenant + whether they get the
 * salary permission gate (database-blueprint §5.1).
 */
@Entity
@Table(name = "user_tenant_memberships", schema = "public",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_tenant_memberships_user_tenant",
                columnNames = {"user_id", "tenant_id"}))
public class UserTenantMembershipJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MembershipStatus status;

    @Column(name = "invited_by")
    private UUID invitedBy;

    @Column(name = "salary_data_permission", nullable = false)
    private boolean salaryDataPermission;

    protected UserTenantMembershipJpaEntity() { }

    public UserTenantMembershipJpaEntity(UUID id, UUID userId, UUID tenantId,
                                         MembershipStatus status, boolean salaryDataPermission) {
        this.id = id;
        this.userId = userId;
        this.tenantId = tenantId;
        this.status = status;
        this.salaryDataPermission = salaryDataPermission;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getTenantId() { return tenantId; }
    public MembershipStatus getStatus() { return status; }
    public boolean isSalaryDataPermission() { return salaryDataPermission; }

    public void setStatus(MembershipStatus status) { this.status = status; }

    public void setSalaryDataPermission(boolean salaryDataPermission) {
        this.salaryDataPermission = salaryDataPermission;
    }
}

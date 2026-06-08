package uz.hrlab.grading.access.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import uz.hrlab.grading.access.domain.UserStatus;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Persistence representation of {@code public.users} (database-blueprint §5.1).
 *
 * <p>Global PII row — never holds tenant business data and intentionally has
 * no {@code tenant_id} column. Tenant linkage flows through
 * {@link UserTenantMembershipJpaEntity}.
 */
@Entity
@Table(name = "users", schema = "public")
public class UserJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false, length = 320, unique = true)
    private String email;

    @Column(name = "external_idp_subject", length = 255)
    private String externalIdpSubject;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private UserStatus status;

    @Column(name = "default_locale", nullable = false, length = 20)
    private String defaultLocale;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    protected UserJpaEntity() { }

    public UserJpaEntity(UUID id, String email, String externalIdpSubject, String fullName,
                         UserStatus status, String defaultLocale) {
        this.id = id;
        this.email = email;
        this.externalIdpSubject = externalIdpSubject;
        this.fullName = fullName;
        this.status = status;
        this.defaultLocale = defaultLocale;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getExternalIdpSubject() { return externalIdpSubject; }
    public String getFullName() { return fullName; }
    public UserStatus getStatus() { return status; }
    public String getDefaultLocale() { return defaultLocale; }
    public OffsetDateTime getLastLoginAt() { return lastLoginAt; }

    public void setStatus(UserStatus status) { this.status = status; }
    public void setLastLoginAt(OffsetDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    /**
     * Populated by {@code InviteUserUseCase} when IdP provisioning succeeds
     * (decision doc 08, Option A) — the stable ZITADEL user id so future logins
     * resolve by {@code sub}, not only by email. Was always null before this
     * feature; never overwritten with null by the invite path.
     */
    public void setExternalIdpSubject(String externalIdpSubject) {
        this.externalIdpSubject = externalIdpSubject;
    }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setDefaultLocale(String defaultLocale) { this.defaultLocale = defaultLocale; }
}

package uz.hrlab.grading.access.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import uz.hrlab.grading.access.domain.RoleScope;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;

import java.util.UUID;

/**
 * Persistence representation of {@code public.roles}.
 *
 * <p>Two flavours, discriminated by {@link #isSystem} (slice E3 schema
 * {@code 008-roles-custom-tenant.yaml}):
 * <ul>
 *   <li><b>System role</b> — {@code is_system = true}, {@code tenant_id = NULL}.
 *       The 11 seeded roles ({@code seeds/002-default-roles.yaml}). Globally
 *       unique by {@code code} (partial unique {@code uq_roles_system_code}).
 *       Immutable through the custom-role API; only its PERMISSION set is editable
 *       (E2's {@code PUT .../permissions}, super-admin only).</li>
 *   <li><b>Custom role</b> — {@code is_system = false}, {@code tenant_id} set,
 *       {@code scope = TENANT}. Defined by a tenant admin (slice E3). Unique by
 *       {@code (tenant_id, code)} (partial unique {@code uq_roles_tenant_code}).
 *       Its name/description and permission set are editable by the owning tenant
 *       admin; its {@code code}/{@code scope}/{@code isSystem}/{@code tenantId}
 *       are fixed after creation (no setters).</li>
 * </ul>
 *
 * <p>The DB CHECK {@code chk_roles_system_tenant} enforces the invariant
 * (system ⇒ no tenant; custom ⇒ tenant + TENANT scope). The system-vs-custom
 * CODE collision is rejected in the backend (no DB trigger) — see
 * {@code CustomRoleUseCase}.
 */
@Entity
@Table(name = "roles", schema = "public")
public class RoleJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, length = 64, updatable = false)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 16, updatable = false)
    private RoleScope scope;

    /**
     * {@code true} for the 11 seeded system roles, {@code false} for tenant-defined
     * custom roles. Immutable after create (no setter) — a role never crosses the
     * system/custom boundary.
     */
    @Column(name = "is_system", nullable = false, updatable = false)
    private boolean isSystem;

    /**
     * Owning tenant for a custom role ({@code is_system = false}); {@code null}
     * for system roles. Set at create, never moved between tenants (no setter).
     */
    @Column(name = "tenant_id", updatable = false)
    private UUID tenantId;

    protected RoleJpaEntity() { }

    /**
     * System-role constructor (back-compat): {@code is_system = true},
     * {@code tenant_id = null}. Used by seeds-via-JPA and the existing tests.
     */
    public RoleJpaEntity(UUID id, String code, String name, RoleScope scope) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.scope = scope;
        this.isSystem = true;
        this.tenantId = null;
    }

    /**
     * Full constructor — covers both flavours. {@link #newCustom} is the
     * preferred factory for custom roles so the TENANT/{@code is_system=false}
     * invariant is centralised.
     */
    public RoleJpaEntity(UUID id, String code, String name, String description,
                         RoleScope scope, boolean isSystem, UUID tenantId) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
        this.scope = scope;
        this.isSystem = isSystem;
        this.tenantId = tenantId;
    }

    /**
     * Factory for a tenant-defined custom role: {@code is_system = false},
     * {@code scope = TENANT}, {@code tenant_id} required — exactly the shape the
     * DB CHECK {@code chk_roles_system_tenant} demands for a custom role.
     */
    public static RoleJpaEntity newCustom(UUID id, UUID tenantId, String code,
                                          String name, String description) {
        return new RoleJpaEntity(id, code, name, description,
                RoleScope.TENANT, false, tenantId);
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public RoleScope getScope() { return scope; }
    public boolean isSystem() { return isSystem; }
    public UUID getTenantId() { return tenantId; }

    /** Mutable display name (custom roles only; system-role names are seed-owned). */
    public void setName(String name) { this.name = name; }

    /** Mutable description (custom roles only). */
    public void setDescription(String description) { this.description = description; }
}

package uz.hrlab.grading.tenancy.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;
import uz.hrlab.grading.tenancy.domain.IsolationMode;
import uz.hrlab.grading.tenancy.domain.Tenant;
import uz.hrlab.grading.tenancy.domain.TenantStatus;

import java.util.UUID;

/**
 * Persistence representation of {@code public.tenants}
 * (database-blueprint §5.1).
 *
 * <p>Lives in control plane (shared {@code public} schema) — has no
 * {@code tenant_id} column, intentionally.
 */
@Entity
@Table(name = "tenants", schema = "public")
public class TenantJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "slug", nullable = false, length = 64, unique = true)
    private String slug;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "isolation_mode", nullable = false, length = 16)
    private IsolationMode isolationMode;

    @Column(name = "schema_name", length = 80)
    private String schemaName;

    @Column(name = "database_name", length = 80)
    private String databaseName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TenantStatus status;

    @Column(name = "default_locale", nullable = false, length = 20)
    private String defaultLocale;

    @Column(name = "encryption_key_ref", length = 200)
    private String encryptionKeyRef;

    @Column(name = "object_storage_prefix", length = 200)
    private String objectStoragePrefix;

    protected TenantJpaEntity() { }

    public TenantJpaEntity(UUID id, String slug, String displayName, IsolationMode isolationMode,
                           String schemaName, String databaseName, TenantStatus status,
                           String defaultLocale) {
        this.id = id;
        this.slug = slug;
        this.displayName = displayName;
        this.isolationMode = isolationMode;
        this.schemaName = schemaName;
        this.databaseName = databaseName;
        this.status = status;
        this.defaultLocale = defaultLocale;
    }

    public Tenant toDomain() {
        return new Tenant(id, slug, displayName, isolationMode, schemaName, databaseName,
                status, defaultLocale);
    }

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public String getDisplayName() { return displayName; }
    public IsolationMode getIsolationMode() { return isolationMode; }
    public String getSchemaName() { return schemaName; }
    public String getDatabaseName() { return databaseName; }
    public TenantStatus getStatus() { return status; }
    public String getDefaultLocale() { return defaultLocale; }

    public void setStatus(TenantStatus status) { this.status = status; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setDefaultLocale(String defaultLocale) { this.defaultLocale = defaultLocale; }
}

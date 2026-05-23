package uz.hrlab.grading.tenancy.domain;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Pure domain model for a Tenant (control plane).
 * Mirrors database-blueprint §5.1 {@code public.tenants}.
 *
 * <p>{@link #slug} regex {@code ^[a-z][a-z0-9_]{2,63}$} is the canonical rule
 * (database-blueprint §5.1) — it deterministically defines schema name
 * {@code tenant_<slug>} (database-blueprint §7.10).
 */
public final class Tenant {

    public static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{2,63}$");

    private final UUID id;
    private final String slug;
    private final String displayName;
    private final IsolationMode isolationMode;
    private final String schemaName;
    private final String databaseName;
    private final TenantStatus status;
    private final String defaultLocale;

    public Tenant(UUID id, String slug, String displayName, IsolationMode isolationMode,
                  String schemaName, String databaseName, TenantStatus status, String defaultLocale) {
        this.id = id;
        this.slug = slug;
        this.displayName = displayName;
        this.isolationMode = isolationMode;
        this.schemaName = schemaName;
        this.databaseName = databaseName;
        this.status = status;
        this.defaultLocale = defaultLocale;
    }

    public static String schemaNameFor(String slug) {
        if (!SLUG_PATTERN.matcher(slug).matches()) {
            throw new IllegalArgumentException("slug does not match canonical pattern");
        }
        return "tenant_" + slug;
    }

    public UUID id() { return id; }
    public String slug() { return slug; }
    public String displayName() { return displayName; }
    public IsolationMode isolationMode() { return isolationMode; }
    public String schemaName() { return schemaName; }
    public String databaseName() { return databaseName; }
    public TenantStatus status() { return status; }
    public String defaultLocale() { return defaultLocale; }
}

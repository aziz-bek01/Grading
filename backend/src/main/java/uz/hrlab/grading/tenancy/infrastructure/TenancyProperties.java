package uz.hrlab.grading.tenancy.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Switch between shared-schema (MVP 1 local dev) and schema-per-tenant
 * (production target — database-blueprint §2).
 *
 * <p>Example:
 * <pre>grading.tenancy.mode: shared       # default for tests / dev</pre>
 * <pre>grading.tenancy.mode: schema-per-tenant  # production</pre>
 */
@ConfigurationProperties(prefix = "grading.tenancy")
public class TenancyProperties {

    public enum Mode { SHARED, SCHEMA_PER_TENANT }

    private Mode mode = Mode.SHARED;

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
}

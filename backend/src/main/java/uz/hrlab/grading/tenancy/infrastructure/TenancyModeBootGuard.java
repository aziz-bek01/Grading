package uz.hrlab.grading.tenancy.infrastructure;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fail-fast boot guard for the DOCUMENTED-BUT-UNBUILT
 * {@link TenancyProperties.Mode#SCHEMA_PER_TENANT} tenancy mode (Task #7).
 *
 * <p>Production runs SHARED schema + Postgres RLS. Schema-per-tenant is a
 * post-MVP 1 target: there is NO schema-routing datasource and
 * {@link TenantSchemaProvisioner} is never wired into any request/creation path
 * (it is dead code today — {@code provision(...)} has no caller). If someone set
 * {@code GRADING_TENANCY_MODE=schema_per_tenant} the app would come up looking
 * healthy while every tenant silently resolved against the shared {@code public}
 * schema — a correctness/isolation hazard.
 *
 * <p>So the app REFUSES TO START in that mode: this {@link PostConstruct} throws
 * during context refresh with a clear, actionable message naming the property /
 * env var and the fact that the mode is unimplemented. SHARED mode (the default)
 * is a NO-OP and starts exactly as before — no behaviour change.
 *
 * <p>This is only the guard; the provisioning saga (schema creation, routing
 * datasource, per-tenant migrations, RLS) is intentionally NOT built here.
 */
@Component
public class TenancyModeBootGuard {

    private static final Logger log = LoggerFactory.getLogger(TenancyModeBootGuard.class);

    private final TenancyProperties properties;

    public TenancyModeBootGuard(TenancyProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void verifySupportedMode() {
        if (properties.getMode() == TenancyProperties.Mode.SCHEMA_PER_TENANT) {
            throw new IllegalStateException(
                    "Tenancy mode SCHEMA_PER_TENANT (property 'grading.tenancy.mode' / env "
                    + "GRADING_TENANCY_MODE=schema_per_tenant) is DOCUMENTED-BUT-UNBUILT: there "
                    + "is no schema-routing datasource and TenantSchemaProvisioner is not wired, "
                    + "so tenants would silently break against the shared 'public' schema. "
                    + "Production runs SHARED schema + Postgres RLS. Set GRADING_TENANCY_MODE=shared "
                    + "(or remove the override) to start. Do NOT enable schema_per_tenant until the "
                    + "per-tenant provisioning saga + routing datasource are implemented.");
        }
        log.debug("Tenancy mode {} accepted by boot guard.", properties.getMode());
    }
}

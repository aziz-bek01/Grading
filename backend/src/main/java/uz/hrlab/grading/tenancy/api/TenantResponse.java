package uz.hrlab.grading.tenancy.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import uz.hrlab.grading.tenancy.domain.Tenant;

import java.util.UUID;

/** Response DTO for tenant control-plane endpoints. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantResponse(
        UUID id,
        String slug,
        String displayName,
        String isolationMode,
        String schemaName,
        String status,
        String defaultLocale
) {
    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(
                tenant.id(),
                tenant.slug(),
                tenant.displayName(),
                tenant.isolationMode().name(),
                tenant.schemaName(),
                tenant.status().name(),
                tenant.defaultLocale()
        );
    }
}

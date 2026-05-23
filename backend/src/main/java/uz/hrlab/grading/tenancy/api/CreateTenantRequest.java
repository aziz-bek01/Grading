package uz.hrlab.grading.tenancy.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Admin request body for {@code POST /api/v1/admin/tenants}.
 *
 * <p>Note: never accepts a {@code tenant_id} (security-blueprint §20.1
 * finding 2). The new tenant id is generated server-side.
 */
public record CreateTenantRequest(
        @NotBlank
        @Pattern(regexp = "^[a-z][a-z0-9_]{2,63}$",
                message = "slug must match ^[a-z][a-z0-9_]{2,63}$")
        String slug,

        @NotBlank @Size(max = 255)
        String displayName,

        @NotBlank
        @Pattern(regexp = "ru-RU|uz-Cyrl-UZ|uz-Latn-UZ|en-US")
        String defaultLocale,

        @NotBlank
        @Pattern(regexp = "SCHEMA|DATABASE")
        String isolationMode,

        @NotBlank @Size(max = 500)
        String companyLegalName,

        @Size(max = 255)
        String companyBrandName,

        @Size(max = 100)
        String companyIndustry
) { }

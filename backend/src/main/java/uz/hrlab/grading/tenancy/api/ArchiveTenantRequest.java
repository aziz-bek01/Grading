package uz.hrlab.grading.tenancy.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/admin/tenants/{tenantId}/archive}.
 * Reason is mandatory — archive is a heavy retention action; the reason is
 * persisted to the audit log for compliance review.
 */
public record ArchiveTenantRequest(
        @NotBlank
        @Size(min = 3, max = 500)
        String reason
) {
}

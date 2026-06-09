package uz.hrlab.grading.access.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Request body for {@code PUT /api/v1/users/{id}/department-scopes} (E4-S1).
 *
 * <p>REPLACE-SET semantics: {@code department_ids} is the COMPLETE desired set
 * of department scope roots for the user in {@code tenant_id}. Missing roots are
 * REVOKED; new roots are upserted ACTIVE. An empty list clears the scope.
 *
 * <p>{@code tenant_id} in the body is matched against the caller's active tenant
 * context — it is NEVER trusted as the tenant identity on its own
 * (security-blueprint §20.1). Each department is validated to belong to that
 * tenant before persistence.
 */
public record SetDepartmentScopesRequest(
        @JsonProperty("tenant_id") @NotNull UUID tenantId,
        @JsonProperty("department_ids") @NotNull List<UUID> departmentIds
) {
}

package uz.hrlab.grading.access.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Request body for {@code PUT /api/v1/users/{id}/project-assignments} (E4-S1).
 *
 * <p>REPLACE-SET semantics: {@code project_ids} is the COMPLETE desired set of
 * project assignments for the user in {@code tenant_id}. Missing projects are
 * REVOKED; new projects are upserted ACTIVE. An empty list clears assignments.
 *
 * <p>{@code tenant_id} in the body is matched against the caller's active tenant
 * context — never trusted alone. Each project is validated to belong to that
 * tenant before persistence.
 */
public record SetProjectAssignmentsRequest(
        @JsonProperty("tenant_id") @NotNull UUID tenantId,
        @JsonProperty("project_ids") @NotNull List<UUID> projectIds
) {
}

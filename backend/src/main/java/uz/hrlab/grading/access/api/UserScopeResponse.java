package uz.hrlab.grading.access.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

/**
 * Response for {@code GET /api/v1/users/{id}/scopes?tenantId=...} (E4-S1).
 *
 * <p>The CURRENTLY ACTIVE ABAC scope for a user in one tenant: the department
 * SCOPE ROOTS (not the expanded subtree — the UI assigns roots) and the project
 * assignments. Snake_case wire format to match the rest of the access admin API.
 */
public record UserScopeResponse(
        @JsonProperty("user_id") UUID userId,
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("department_ids") List<UUID> departmentIds,
        @JsonProperty("project_ids") List<UUID> projectIds
) {
}

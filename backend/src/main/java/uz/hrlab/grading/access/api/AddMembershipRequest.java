package uz.hrlab.grading.access.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Body for {@code POST /api/v1/users/{id}/memberships} — attach the target
 * user to an additional tenant. The caller MUST already be authorized for
 * that tenant ({@code UserManagementPolicy}); HRLab Super Admin bypasses.
 *
 * <p>A re-add for a previously REVOKED membership re-activates it (status →
 * ACTIVE) so the audit trail keeps a single immutable id.
 */
public record AddMembershipRequest(
        @NotNull UUID tenantId,
        @NotEmpty List<@NotBlank @Size(max = 64) String> roleCodes
) {
}

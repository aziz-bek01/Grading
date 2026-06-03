package uz.hrlab.grading.access.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response for {@code GET /api/v1/users/{id}}.
 *
 * <p>Returned ONLY when the caller has at least one overlapping tenant
 * membership with the target user — {@code UserManagementPolicy} enforces this
 * before the use case loads the entity. The {@code memberships[]} list is
 * SCOPED: HRLab Super Admins see all overlapping memberships, all other
 * callers see only memberships in tenants they themselves belong to.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserDetailsResponse(
        UUID id,
        String email,
        String fullName,
        String status,
        String defaultLocale,
        OffsetDateTime lastLoginAt,
        List<UserMembershipSummary> memberships
) {
}

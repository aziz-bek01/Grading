package uz.hrlab.grading.access.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Membership card embedded in {@link UserDetailsResponse} and
 * {@link UserListResponse}. Carries the (tenant, status, roles, salary gate)
 * tuple — one entry per {@code public.user_tenant_memberships} row visible to
 * the caller.
 *
 * <p>{@code salaryDataPermission} is ALWAYS returned (even to callers that
 * cannot toggle it) because it is metadata — viewing the flag is harmless;
 * viewing the actual salary values requires {@code SALARY_VIEW} on the
 * compensation endpoints.
 *
 * <p>HRLab platform roles are flagged so the frontend can render a distinct
 * badge — and so the salary-permission toggle is hidden for HRLab memberships
 * (architecture §8.4: HRLab consultants do not view client salary).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserMembershipSummary(
        UUID id,
        UUID tenantId,
        String tenantSlug,
        String tenantBrandName,
        String status,
        boolean salaryDataPermission,
        /**
         * When the membership row was created — i.e. when the user was invited
         * into this tenant (the {@code user_tenant_memberships.created_at} audit
         * column). The frontend membership card renders this as the "Invited"
         * date; without it the card showed "Invalid Date".
         */
        OffsetDateTime invitedAt,
        List<RoleSummary> roles
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RoleSummary(
            UUID userRoleId,
            UUID roleId,
            String code,
            String name,
            String scope,
            boolean hrlabRole
    ) {
    }
}

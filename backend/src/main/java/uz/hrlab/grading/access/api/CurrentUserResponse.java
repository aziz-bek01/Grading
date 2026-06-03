package uz.hrlab.grading.access.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Response payload for {@code GET /api/v1/users/me}.
 *
 * <p>Contract (BE-TI-005, mirrors {@code frontend/src/shared/auth/authTypes.ts}):
 * <ul>
 *   <li>{@code user} — identity of the caller (never includes password/secret).</li>
 *   <li>{@code tenants} — ALL non-revoked memberships of the caller; the
 *       frontend tenant switcher renders one card per entry. Sourced from
 *       {@code user_tenant_memberships} (NOT from the JWT) so the list is
 *       always fresh even when a token claim is stale.</li>
 *   <li>{@code activeTenantId} — the tenant resolved from the JWT/dev-auth
 *       headers for THIS request (may be {@code null} if the caller has no
 *       active tenant yet — common right after login).</li>
 *   <li>{@code roles} / {@code permissions} — derived from the JWT/dev-auth
 *       context and scoped to the active tenant.</li>
 *   <li>{@code salaryDataPermission} — separate gate (security-blueprint §8).</li>
 * </ul>
 *
 * <p>Never trust the response to reveal cross-tenant scope: roles/permissions
 * are always those of the currently active tenant only.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CurrentUserResponse(
        UserIdentity user,
        List<TenantMembershipSummary> tenants,
        UUID activeTenantId,
        Set<String> roles,
        Set<String> permissions,
        boolean salaryDataPermission
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UserIdentity(
            UUID id,
            String email,
            String fullName,
            String locale,
            String status
    ) {
    }
}

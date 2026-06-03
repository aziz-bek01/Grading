package uz.hrlab.grading.access.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One row of {@code GET /api/v1/users?tenantId=...} — flattened user +
 * membership tuple. Each row carries the membership status and role codes
 * for the queried tenant (NOT every tenant the user belongs to), so the
 * list cannot reveal cross-tenant membership data.
 *
 * <p>The wrapping pagination envelope is the project-wide
 * {@link uz.hrlab.grading.common.api.PageResponse}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserListResponse(
        UUID id,
        String email,
        String fullName,
        String status,
        String defaultLocale,
        OffsetDateTime lastLoginAt,
        UUID membershipId,
        UUID tenantId,
        String membershipStatus,
        boolean salaryDataPermission,
        List<String> roleCodes
) {
}

package uz.hrlab.grading.access.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code PATCH /api/v1/users/{id}/memberships/{tenantId}/salary-permission}.
 *
 * <p>A non-blank {@code reason} is MANDATORY (architecture §8.4: every
 * salary-permission flip carries a mandatory rationale that lands in the
 * audit log {@code reason} column). The field is truncated to 1000 chars
 * by the use case before audit insert.
 */
public record SalaryPermissionToggleRequest(
        @NotNull  Boolean enabled,
        @NotBlank @Size(max = 1000) String reason
) {
}

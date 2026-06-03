package uz.hrlab.grading.access.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/users/{id}/memberships/{tenantId}/roles}.
 *
 * <p>The role catalog ({@link uz.hrlab.grading.access.application.RoleCodes})
 * is open enough that we keep the validation as a free-form string and
 * resolve the row in {@code AssignRoleUseCase}. Unknown codes yield a 400.
 */
public record AssignRoleRequest(
        @NotBlank @Size(max = 64) String roleCode
) {
}

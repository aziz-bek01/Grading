package uz.hrlab.grading.access.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Body for {@code POST /api/v1/users} — creates a {@code public.users} row
 * (idempotently by email) AND a {@code public.user_tenant_memberships} row
 * with status {@code INVITED}, plus initial {@code user_roles} entries.
 *
 * <p>{@code tenantId} is REQUIRED in the body so HRLab Super Admins can invite
 * into any tenant they manage, but {@link
 * uz.hrlab.grading.access.application.UserManagementPolicy} STILL verifies the
 * value: non-super-admins are constrained to {@code ctx.tenantId()}. This is
 * the only USER_* endpoint where tenant_id is body-supplied — every other
 * action infers the scope from the path target's membership.
 */
public record InviteUserRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 255)        String fullName,
        @NotBlank
        @Pattern(regexp = "ru-RU|uz-Cyrl-UZ|uz-Latn-UZ|en-US",
                 message = "locale must be one of ru-RU, uz-Cyrl-UZ, uz-Latn-UZ, en-US")
        String locale,
        @NotNull                          UUID tenantId,
        @NotEmpty                         List<@NotBlank @Size(max = 64) String> roleCodes
) {
}

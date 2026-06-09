package uz.hrlab.grading.access.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/users/{id}/reset-login} — (re)issue a working
 * login for a user whose IdP account is missing, not-yet-initialised
 * ({@code USER_STATE_INITIAL}), or mis-linked to the wrong subject (admin-set
 * password onboarding, NO SMTP).
 *
 * <p>{@code password} is the admin-set credential. It is {@code @NotBlank}
 * because a reset-login with no password is meaningless — unlike the optional
 * password on invite/patch, this endpoint exists specifically to install a
 * working credential. The full ZITADEL complexity rule is enforced in {@link
 * uz.hrlab.grading.access.application.ResetLoginUseCase} via the shared {@link
 * uz.hrlab.grading.access.application.PasswordPolicy} (clear
 * {@code USER_INVITE_WEAK_PASSWORD}) — it cannot be a static bean rule because
 * it mirrors the invite flow. The {@code @Size(max = 200)} cap is a safety
 * bound only; the password is never logged and never echoed back.
 */
public record ResetLoginRequest(
        @NotBlank @Size(max = 200) String password
) {
}

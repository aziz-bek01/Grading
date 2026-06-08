package uz.hrlab.grading.access.application;

import uz.hrlab.grading.common.exception.ValidationException;

/**
 * Shared password-complexity rule for the admin-set IdP credential
 * (decision doc 08, Option A — admin-set-password, NO SMTP).
 *
 * <p>Mirrors the ZITADEL default password policy: min length 8, at least one
 * upper-case letter, one lower-case letter, one digit and one symbol. Extracted
 * here so the invite flow ({@link InviteUserUseCase}) and the edit flow
 * ({@link PatchUserUseCase} password reset) validate IDENTICALLY — a single
 * source of truth, no drift between the two entry points.
 *
 * <h3>Security contract</h3>
 * The raw password is NEVER logged, never echoed and never placed in the
 * exception message — only the stable code/translation key surfaces to the
 * caller.
 */
public final class PasswordPolicy {

    private PasswordPolicy() { }

    /**
     * Validate {@code pw} against the IdP complexity policy.
     *
     * @param pw   the raw admin-set password (never logged)
     * @param code the stable {@link ValidationException} code to raise on
     *             failure, so each caller keeps its own translation key
     *             ({@code USER_INVITE_WEAK_PASSWORD} vs
     *             {@code USER_PASSWORD_WEAK})
     * @throws ValidationException when the password is too weak
     */
    public static void validate(String pw, String code) {
        if (pw == null || pw.length() < 8
                || !pw.matches(".*[A-Z].*")
                || !pw.matches(".*[a-z].*")
                || !pw.matches(".*[0-9].*")
                || !pw.matches(".*[^A-Za-z0-9].*")) {
            throw new ValidationException(code,
                    "Password must be at least 8 characters and include an uppercase letter, "
                            + "a lowercase letter, a number and a symbol.");
        }
    }
}

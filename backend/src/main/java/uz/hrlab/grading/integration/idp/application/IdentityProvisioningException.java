package uz.hrlab.grading.integration.idp.application;

import uz.hrlab.grading.common.exception.BaseDomainException;

/**
 * Thrown when the external IdP could not provision/link a login account, or
 * rejected a credential/email edit.
 *
 * <h3>Two failure classes, two HTTP mappings</h3>
 * <ul>
 *   <li><b>Genuine upstream failure</b> (network, auth, 5xx, unknown) — the
 *       grading request was valid but ZITADEL is unreachable/broken. Carries the
 *       generic {@link #getCode() code} {@code IDP_PROVISIONING_FAILED} and is
 *       mapped by {@code GlobalExceptionHandler} to {@code 502 BAD_GATEWAY}. This
 *       is the historical behaviour and the DEFAULT for both legacy constructors.</li>
 *   <li><b>Actionable client-side rejection</b> (a 4xx the admin can fix: the
 *       target IdP account is not yet initialized, the new password violates the
 *       IdP policy, the new email is already used at the IdP). Carries a SPECIFIC
 *       {@link Reason} whose {@link Reason#errorCode()} the frontend can switch on
 *       and which {@code GlobalExceptionHandler} maps to {@code 400 BAD_REQUEST} —
 *       so the admin gets a clear, fixable message instead of a confusing 502.</li>
 * </ul>
 *
 * <p>The {@link #getMessage() message} is sanitised and carries NO
 * password/token/PII beyond what is safe to surface.
 */
public class IdentityProvisioningException extends BaseDomainException {

    /**
     * Specific, actionable IdP rejection reasons. Each maps to a stable
     * {@code errorCode} the frontend switches on, and is rendered as an HTTP 400
     * by {@code GlobalExceptionHandler}. The generic upstream-failure case has no
     * {@code Reason} (it keeps {@code IDP_PROVISIONING_FAILED} → 502).
     */
    public enum Reason {

        /**
         * ZITADEL state {@code USER_STATE_INITIAL}: the account was created but
         * never activated, so a password cannot be set on it directly
         * (ZITADEL {@code COMMAND-M9dse}, "User is not yet initialized"). Typical
         * for legacy/manually-created accounts that did not go through our invite
         * flow. The admin must (re)initialise/invite the account first.
         */
        USER_NOT_INITIALIZED("USER_IDP_NOT_INITIALIZED"),

        /**
         * ZITADEL rejected the new password for a policy reason other than the
         * not-initialized state (complexity, history, length at the IdP, etc.).
         * The admin can supply a different password.
         */
        PASSWORD_REJECTED("USER_IDP_PASSWORD_REJECTED"),

        /**
         * ZITADEL rejected the new email (e.g. already used by another IdP
         * account). The admin can supply a different email.
         */
        EMAIL_REJECTED("USER_IDP_EMAIL_REJECTED");

        private final String errorCode;

        Reason(String errorCode) {
            this.errorCode = errorCode;
        }

        /** Stable error envelope {@code code} the frontend maps to a message/key. */
        public String errorCode() {
            return errorCode;
        }
    }

    /** Generic upstream failure — {@code IDP_PROVISIONING_FAILED} → 502. */
    public IdentityProvisioningException(String safeMessage) {
        super("IDP_PROVISIONING_FAILED", safeMessage);
    }

    /** Generic upstream failure — {@code IDP_PROVISIONING_FAILED} → 502. */
    public IdentityProvisioningException(String safeMessage, Throwable cause) {
        super("IDP_PROVISIONING_FAILED", safeMessage, cause);
    }

    /**
     * Actionable 4xx rejection — the {@code code} is {@code reason.errorCode()}
     * (e.g. {@code USER_IDP_NOT_INITIALIZED}) and {@code GlobalExceptionHandler}
     * maps it to {@code 400 BAD_REQUEST}.
     */
    public IdentityProvisioningException(Reason reason, String safeMessage, Throwable cause) {
        super(reason.errorCode(), safeMessage, cause);
    }

    /**
     * True when this exception carries a specific, admin-actionable {@link Reason}
     * (→ 400) rather than the generic upstream failure (→ 502). Lets the handler
     * branch without string-matching the code.
     */
    public boolean isActionable() {
        return !"IDP_PROVISIONING_FAILED".equals(getCode());
    }
}

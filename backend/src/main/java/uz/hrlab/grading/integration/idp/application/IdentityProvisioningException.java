package uz.hrlab.grading.integration.idp.application;

import uz.hrlab.grading.common.exception.BaseDomainException;

/**
 * Thrown when the external IdP could not provision/link a login account.
 *
 * <p>Mapped by {@code GlobalExceptionHandler} to {@code 502 BAD_GATEWAY}: the
 * grading request itself was valid, but an upstream dependency (ZITADEL) failed,
 * so the invite cannot complete. The {@link #getMessage() message} is sanitised
 * and carries NO password/token/PII beyond what is safe to surface.
 *
 * <p>Carrying a {@link BaseDomainException} {@code code} lets the frontend map it
 * to a translation key, consistent with the §21.6 error envelope contract.
 */
public class IdentityProvisioningException extends BaseDomainException {

    public IdentityProvisioningException(String safeMessage) {
        super("IDP_PROVISIONING_FAILED", safeMessage);
    }

    public IdentityProvisioningException(String safeMessage, Throwable cause) {
        super("IDP_PROVISIONING_FAILED", safeMessage, cause);
    }
}

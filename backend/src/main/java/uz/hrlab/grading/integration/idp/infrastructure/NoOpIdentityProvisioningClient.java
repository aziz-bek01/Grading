package uz.hrlab.grading.integration.idp.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uz.hrlab.grading.integration.idp.application.IdentityProvisioningPort;

/**
 * No-op {@link IdentityProvisioningPort} used when
 * {@code grading.idp.zitadel.enabled} is absent or {@code false}.
 *
 * <p>This is the DEFAULT in local, test and CI: the invite flow stays exactly
 * as it was before IdP provisioning existed — grading DB rows only, no network,
 * no IdP account, {@code external_idp_subject} left null. This keeps every
 * pre-existing invite test green without an IdP dependency.
 *
 * <p>{@code matchIfMissing = true} so a profile that simply never sets the flag
 * (the common case for unit-test slices) still gets this bean.
 */
@Component
@ConditionalOnProperty(prefix = "grading.idp.zitadel", name = "enabled",
        havingValue = "false", matchIfMissing = true)
public class NoOpIdentityProvisioningClient implements IdentityProvisioningPort {

    /**
     * No-op: returns a result that signals "do not populate
     * external_idp_subject". The caller treats a {@code null} subject as
     * "DB-only invite" and skips the IdP audit events.
     */
    @Override
    public ProvisionResult provisionUser(String email, String givenName, String familyName,
                                         String rawPassword) {
        return new ProvisionResult(null, false);
    }

    /** No-op. */
    @Override
    public void deactivateUser(String externalSubject) {
        // intentionally empty — IdP disabled
    }

    /** No-op. */
    @Override
    public void reactivateUser(String externalSubject) {
        // intentionally empty — IdP disabled
    }
}

package uz.hrlab.grading.access.application;

/**
 * Access-owned view of the IdP master switch ({@code grading.idp.zitadel.enabled}).
 *
 * <p>The IdP-provisioning use cases ({@code InviteUserUseCase},
 * {@code PatchUserUseCase}, {@code ResetLoginUseCase},
 * {@code RevokeMembershipUseCase}) branch on this flag — password validation on
 * invite, the INVITED→ACTIVE login-ability flip, and offboarding
 * deactivate/reactivate all depend on whether the IdP is wired. That branching is
 * BUSINESS LOGIC owned by the access module, so the flag is exposed through this
 * access-owned interface rather than by importing the integration-side
 * configuration class — keeping the static arrow at integration → access (the IdP
 * adapter depends on the authorization kernel, never the reverse).
 *
 * <p>The sole implementation is {@code ZitadelIdpProperties} in
 * {@code integration.idp.infrastructure}, which returns the bound
 * {@code enabled} value verbatim: this interface only inverts the compile-time
 * arrow, the runtime value is byte-for-byte the same flag the use cases read
 * before.
 */
public interface IdentityProvisioningToggle {

    /**
     * True when the external IdP is wired ({@code grading.idp.zitadel.enabled=true})
     * so provisioning calls are live; false (local/test/CI default) when the
     * {@code NoOp} port is active and every invite stays DB-only.
     */
    boolean isEnabled();
}

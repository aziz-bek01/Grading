package uz.hrlab.grading.integration.idp.application;

/**
 * Port for provisioning login identities in the external Identity Provider
 * (ZITADEL in production) — the IdP-provisioning gap closed per
 * {@code docs/mvp1/08-identity-provisioning-decision.md} Option A,
 * admin-set-password variant (NO SMTP).
 *
 * <p>The grading control plane is the system of record for AUTHORIZATION
 * (memberships/roles); the IdP is the system of record for CREDENTIALS. When an
 * admin invites a user, the use case calls this port so the invited person can
 * actually log in via the prod OIDC flow with a password the admin sets.
 *
 * <h3>Implementations</h3>
 * <ul>
 *   <li>{@code ZitadelIdentityProvisioningClient} — real adapter, active only
 *       when {@code grading.idp.zitadel.enabled=true}.</li>
 *   <li>{@code NoOpIdentityProvisioningClient} — default; active when the flag
 *       is absent/false (local, test, CI). Invite stays DB-only, exactly as it
 *       behaved before this feature existed.</li>
 * </ul>
 *
 * <h3>Security contract (every implementation MUST honour)</h3>
 * <ul>
 *   <li>NEVER log the raw password or the IdP API token.</li>
 *   <li>NEVER return secrets to the caller.</li>
 *   <li>The returned value is the stable external subject id
 *       ({@code public.users.external_idp_subject}).</li>
 * </ul>
 */
public interface IdentityProvisioningPort {

    /**
     * Create the IdP login account for {@code email}, or LINK to an existing one
     * if the email is already known to the IdP (idempotent). The created/linked
     * account is immediately login-able with {@code rawPassword} (no email
     * verification step — the admin set the password and it is pre-verified).
     *
     * @param email        login username / email (case as supplied; the adapter
     *                     trims/normalises as the IdP requires)
     * @param givenName    first name for the IdP profile (never null/blank)
     * @param familyName   last name for the IdP profile (never null/blank)
     * @param rawPassword  admin-set password meeting the IdP complexity policy;
     *                     used only for the create call, never stored/logged
     * @return the external subject id (ZITADEL {@code userId}) to persist in
     *         {@code public.users.external_idp_subject}
     * @throws IdentityProvisioningException on any non-recoverable IdP failure
     *         (network, auth, unexpected status). The message is sanitised and
     *         carries NO password/token.
     */
    ProvisionResult provisionUser(String email, String givenName, String familyName,
                                  String rawPassword);

    /**
     * Deactivate (NOT delete) the IdP account so an offboarded person can no
     * longer authenticate, preserving audit/history. Symmetric-offboarding hook
     * for US-3 in the decision doc.
     *
     * <p>NOTE (scope): the real ZITADEL call ({@code POST /v2/users/{id}/deactivate})
     * is intentionally left as a documented TODO in the adapter — wiring the
     * revoke side into {@code RevokeMembershipUseCase}/{@code PatchUserUseCase}
     * is out of scope for this change. The no-op implementation is a clean
     * no-op. This method exists so the port shape is final and the offboarding
     * step is a pure additive follow-up, not a rewrite.
     *
     * @param externalSubject the {@code external_idp_subject} to deactivate
     * @throws IdentityProvisioningException on failure
     */
    void deactivateUser(String externalSubject);

    /**
     * Outcome of {@link #provisionUser}. Distinguishes a freshly created IdP
     * account from a link to a pre-existing one so the caller can emit the
     * correct audit action ({@code USER_IDP_ACCOUNT_CREATED} vs
     * {@code USER_IDP_ACCOUNT_LINKED}).
     *
     * @param externalSubject the IdP user id (never null/blank on success)
     * @param linkedExisting  true when an existing IdP account was reused
     *                        (409 ALREADY_EXISTS path), false when newly created
     */
    record ProvisionResult(String externalSubject, boolean linkedExisting) {
    }
}

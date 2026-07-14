package uz.hrlab.grading.access.application;

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
     * <p>Real ZITADEL call: {@code POST /v2/users/{id}/deactivate} (no body, 200
     * on success). Wired into {@code PatchUserUseCase} (status →{@code DISABLED})
     * and {@code RevokeMembershipUseCase} (last active membership revoked).
     *
     * <h3>Idempotency contract</h3>
     * Implementations MUST be idempotent-friendly: deactivating an account that
     * is already inactive — or whose id no longer exists in the IdP — MUST be
     * logged and SWALLOWED, not turned into an {@link IdentityProvisioningException}.
     * Offboarding the same user twice, or after the IdP account was removed out
     * of band, must never break the admin operation. Only genuinely unexpected
     * failures (auth, 5xx, network) surface as exceptions for the caller to flag.
     *
     * @param externalSubject the {@code external_idp_subject} to deactivate;
     *                        callers guard for null and skip the call entirely
     * @throws IdentityProvisioningException on a genuine, non-idempotent failure
     */
    void deactivateUser(String externalSubject);

    /**
     * Reactivate a previously deactivated IdP account so a re-enabled person can
     * authenticate again. Symmetric counterpart of {@link #deactivateUser} for
     * the re-enable path ({@code PatchUserUseCase} status {@code DISABLED}→
     * {@code ACTIVE}).
     *
     * <p>Real ZITADEL call: {@code POST /v2/users/{id}/reactivate} (no body, 200
     * on success).
     *
     * <h3>Idempotency contract</h3>
     * As with {@link #deactivateUser}: reactivating an already-active account, or
     * one that no longer exists, MUST be logged and swallowed rather than failing
     * the admin operation. Only genuine failures surface as exceptions.
     *
     * @param externalSubject the {@code external_idp_subject} to reactivate;
     *                        callers guard for null and skip the call entirely
     * @throws IdentityProvisioningException on a genuine, non-idempotent failure
     */
    void reactivateUser(String externalSubject);

    /**
     * Set (reset) the credential of an existing IdP account to {@code rawPassword}.
     * Symmetric with the admin-set-password invite path — the new password is
     * applied with {@code changeRequired=false} so the user can log in with it
     * immediately, no email/verify step.
     *
     * <p>Real ZITADEL call: {@code POST /v2/users/{id}/password} with body
     * {@code {"newPassword":{"password":"<pw>","changeRequired":false}}} ⇒ 200.
     *
     * <h3>Security contract</h3>
     * The {@code rawPassword} is NEVER logged, never echoed, never placed in an
     * exception message. Implementations guard for a null/blank subject and
     * short-circuit (a user with no IdP account is the caller's concern — the
     * use case raises the user-facing error before calling here).
     *
     * @param externalSubject the {@code external_idp_subject} whose password to
     *                        set; null/blank short-circuits to a no-op
     * @param rawPassword     the new credential, meeting the IdP complexity policy
     * @throws IdentityProvisioningException on a genuine, non-recoverable failure
     *         (auth, 5xx, network); the message carries NO password/token
     */
    void setPassword(String externalSubject, String rawPassword);

    /**
     * Change the e-mail/username of an existing IdP account to {@code newEmail},
     * pre-verified so it is usable as the login identity immediately. The email
     * is the principal resolve key (the JWT email claim is matched against
     * {@code public.users.email}), so the caller keeps grading and the IdP in
     * sync; see {@code PatchUserUseCase} for the all-or-nothing wiring.
     *
     * <p>Real ZITADEL call: {@code POST /v2/users/{id}/email} with body
     * {@code {"email":"<new>","isVerified":true}} ⇒ 200.
     *
     * <h3>Idempotency / failure contract</h3>
     * A genuine failure (auth, 5xx, network) surfaces as
     * {@link IdentityProvisioningException} so the caller's transaction can roll
     * back the grading email and keep both stores consistent. Implementations
     * guard for a null/blank subject and short-circuit.
     *
     * @param externalSubject the {@code external_idp_subject} to update;
     *                        null/blank short-circuits to a no-op
     * @param newEmail        the new, already-uniqueness-checked email
     * @throws IdentityProvisioningException on a genuine, non-recoverable failure
     */
    void changeEmail(String externalSubject, String newEmail);

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

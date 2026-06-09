package uz.hrlab.grading.access.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.api.UserDetailsResponse;
import uz.hrlab.grading.access.domain.MembershipStatus;
import uz.hrlab.grading.access.domain.UserStatus;
import uz.hrlab.grading.access.infrastructure.UserJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserRepository;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.integration.idp.application.IdentityProvisioningException;
import uz.hrlab.grading.integration.idp.application.IdentityProvisioningPort;
import uz.hrlab.grading.integration.idp.infrastructure.ZitadelIdpProperties;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.UUID;

/**
 * Use case for {@code POST /api/v1/users/{id}/reset-login} — (re)issue a working
 * login for a user whose IdP account is MISSING, NOT-YET-INITIALISED
 * ({@code USER_STATE_INITIAL}, created without a password), or MIS-LINKED to the
 * wrong external subject. This is the recovery action for the admin-set-password
 * onboarding flow (decision doc 08, Option A — NO SMTP).
 *
 * <h3>Why this exists (and why PATCH password is not enough)</h3>
 * {@link PatchUserUseCase} password reset calls {@code setPassword} on the user's
 * EXISTING {@code external_idp_subject}. That fails with
 * {@code USER_IDP_NOT_INITIALIZED} when the linked account is in
 * {@code USER_STATE_INITIAL}, and it sets the password on the WRONG account when
 * the stored subject points to a different email's Zitadel user (legacy
 * mis-linking — e.g. grading email {@code asr.3x10@gmail.com} but the subject
 * points to an account whose email is {@code az.asqarov@gmail.com}). This use
 * case fixes both by RE-PROVISIONING from the user's CURRENT grading email.
 *
 * <h3>Behaviour (idempotent, safe, all-or-nothing on the resolve key)</h3>
 * <ol>
 *   <li>Validate the admin-set password against the shared {@link PasswordPolicy}
 *       ({@code USER_INVITE_WEAK_PASSWORD}) BEFORE any IdP call or DB write.</li>
 *   <li><b>Re-provision by the CURRENT grading email</b> via the existing
 *       {@code provisionUser} (create-or-link by email — REUSED, not duplicated):
 *       <ul>
 *         <li>No Zitadel account for that email → a fresh ACTIVE account is
 *             created (email verified, no change-required) → safe.</li>
 *         <li>An account already exists for that email → it is LINKED and its
 *             subject returned — this re-links a mis-linked user to the CORRECT
 *             account keyed by their email.</li>
 *       </ul>
 *       The OLD / mis-linked subject is <b>NEVER deleted</b> — it may belong to a
 *       real operator (e.g. {@code az.asqarov@gmail.com}); deleting it could break
 *       their login.</li>
 *   <li>Persist the resolved subject on the grading user when it CHANGED (re-link)
 *       so future logins resolve by the correct {@code sub}. Grading
 *       {@code users.email} and the Zitadel account email are consistent after
 *       this (the resolve key) because we provision by the grading email and the
 *       create path sets that exact email pre-verified.</li>
 *   <li>{@code setPassword(subject, raw)} so the admin-set password is active.</li>
 *   <li>If the user / its memberships were {@code INVITED} (the grading mirror of
 *       {@code USER_STATE_INITIAL}), flip them to {@code ACTIVE} — mirroring the
 *       invite flow's login-ability handling (admin-set password is pre-verified,
 *       there is no second verification step).</li>
 *   <li>Audit {@code USER_LOGIN_RESET} — NEVER the password; only the subject id,
 *       the email and whether the subject was re-linked. The audit is written in
 *       the same REQUIRES_NEW {@link AuditService} as the rest of the module.</li>
 * </ol>
 *
 * <h3>Consistency / failure mapping</h3>
 * The whole method is {@code @Transactional} — all-or-nothing where it affects
 * the resolve key. If {@code provisionUser} or {@code setPassword} throws an
 * {@link IdentityProvisioningException}, it PROPAGATES so the grading subject
 * update rolls back and both stores stay consistent. An ACTIONABLE 4xx (e.g.
 * {@code USER_IDP_NOT_INITIALIZED} when {@code provisionUser} linked to an
 * existing account that is itself still INITIAL with that exact email and
 * {@code setPassword} still fails) surfaces as a clear {@code 400} via
 * {@code GlobalExceptionHandler} — NOT a confusing {@code 502}. Genuine upstream
 * failures keep the {@code 502 IDP_PROVISIONING_FAILED} mapping.
 *
 * <h3>Flag OFF (NoOp)</h3>
 * When {@code grading.idp.zitadel.enabled=false} there is no credential store to
 * write to, so a reset-login is meaningless. We surface the same clear,
 * actionable {@code USER_NO_IDP_ACCOUNT} as the PATCH password path — never a
 * silent no-op that pretends to have set a login. Existing flag-off tests stay
 * green because the IdP is never touched.
 */
@Service
public class ResetLoginUseCase {

    private static final Logger log = LoggerFactory.getLogger(ResetLoginUseCase.class);

    private final UserManagementPolicy policy;
    private final UserRepository userRepo;
    private final UserTenantMembershipRepository membershipRepo;
    private final GetUserDetailsQuery detailsQuery;
    private final AuditService audit;
    private final IdentityProvisioningPort identityProvisioning;
    private final ZitadelIdpProperties idpProps;

    public ResetLoginUseCase(UserManagementPolicy policy,
                             UserRepository userRepo,
                             UserTenantMembershipRepository membershipRepo,
                             GetUserDetailsQuery detailsQuery,
                             AuditService audit,
                             IdentityProvisioningPort identityProvisioning,
                             ZitadelIdpProperties idpProps) {
        this.policy = policy;
        this.userRepo = userRepo;
        this.membershipRepo = membershipRepo;
        this.detailsQuery = detailsQuery;
        this.audit = audit;
        this.identityProvisioning = identityProvisioning;
        this.idpProps = idpProps;
    }

    @Transactional
    public UserDetailsResponse resetLogin(UUID userId, String rawPassword) {
        TenantContext ctx = TenantContextHolder.requireActive();

        UserJpaEntity user = userRepo.findById(userId)
                .orElseThrow(TenantAccessDeniedException::new);

        // Same guard as PATCH: ABAC overlap with the caller, then manage-in-tenant
        // against the caller's active tenant (the door the rest of the module uses).
        List<UserTenantMembershipJpaEntity> memberships =
                membershipRepo.findAllByUserId(userId);
        policy.requireCanViewUser(ctx, memberships);
        policy.requireCanManageInTenant(ctx, ctx.tenantId());

        // 1) Complexity FIRST — before any IdP call or DB write (fail-fast, mirrors
        //    invite). Reuses the shared PasswordPolicy → identical rule, no drift.
        PasswordPolicy.validate(rawPassword, "USER_INVITE_WEAK_PASSWORD");

        // Flag OFF → no credential store; surface the same clear error as PATCH
        // password rather than pretend to have issued a login.
        if (!idpProps.isEnabled()) {
            throw new ValidationException("USER_NO_IDP_ACCOUNT",
                    "Identity provider is disabled, so a login cannot be (re)issued.");
        }

        String[] names = splitName(user.getFullName());
        String previousSubject = user.getExternalIdpSubject();

        // 2) Re-provision by the CURRENT grading email — REUSES the create-or-link
        //    port. Returns the subject keyed to the grading email: a fresh ACTIVE
        //    account, or the existing one (re-link). The old/mis-linked subject is
        //    NEVER deleted. A genuine failure propagates → tx rolls back.
        IdentityProvisioningPort.ProvisionResult result =
                identityProvisioning.provisionUser(user.getEmail(), names[0], names[1], rawPassword);
        String subject = result.externalSubject();
        boolean reLinked = subject != null && !subject.equals(previousSubject);

        // 3) Persist the resolved subject when it changed (re-link / first link).
        if (subject != null && !subject.equals(previousSubject)) {
            user.setExternalIdpSubject(subject);
        }

        // 4) Ensure the admin-set password is active on the RESOLVED account. The
        //    create path already set it, but setPassword is idempotent and also
        //    covers the link path (existing account whose password we must (re)set).
        //    If the linked account is itself still INITIAL and Zitadel rejects the
        //    password, the ACTIONABLE USER_IDP_NOT_INITIALIZED propagates → 400
        //    (not 502); the tx rolls back the subject change (all-or-nothing).
        identityProvisioning.setPassword(subject, rawPassword);

        // 5) Login-ability — mirror the invite flow: admin-set password is
        //    pre-verified, so a user/membership left INVITED (the grading mirror of
        //    USER_STATE_INITIAL) is flipped to ACTIVE so the person can actually work.
        if (user.getStatus() == UserStatus.INVITED) {
            user.setStatus(UserStatus.ACTIVE);
        }
        userRepo.save(user);
        for (UserTenantMembershipJpaEntity m : memberships) {
            if (m.getStatus() == MembershipStatus.INVITED) {
                m.setStatus(MembershipStatus.ACTIVE);
                membershipRepo.save(m);
            }
        }

        // 6) Audit — subject id + email + re-link flag ONLY. NEVER the password.
        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .actorUserId(ctx.userId())
                .action(AuditAction.USER_LOGIN_RESET)
                .entityType("User")
                .entityId(userId)
                .reason("externalIdpSubject=" + subject
                        + " email=" + user.getEmail()
                        + " reLinked=" + reLinked
                        + " linkedExisting=" + result.linkedExisting())
                .build());
        log.info("Login reset issued. userId={} subject={} reLinked={} linkedExisting={}",
                userId, subject, reLinked, result.linkedExisting());

        return detailsQuery.byId(userId);
    }

    /**
     * Split a full name into [givenName, familyName] for the IdP profile —
     * identical contract to {@code InviteUserUseCase#splitName}. Kept local rather
     * than shared because it is a two-line private helper; extracting a shared
     * utility for it would add more surface than it removes.
     */
    private static String[] splitName(String fullName) {
        String trimmed = fullName == null ? "" : fullName.trim();
        if (trimmed.isEmpty()) {
            return new String[]{"User", "User"};
        }
        int sp = trimmed.indexOf(' ');
        if (sp < 0) {
            return new String[]{trimmed, trimmed};
        }
        String given = trimmed.substring(0, sp).trim();
        String family = trimmed.substring(sp + 1).trim();
        return new String[]{given.isEmpty() ? trimmed : given,
                family.isEmpty() ? trimmed : family};
    }
}

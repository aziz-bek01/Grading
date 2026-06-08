package uz.hrlab.grading.access.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.api.UserDetailsResponse;
import uz.hrlab.grading.access.domain.MembershipStatus;
import uz.hrlab.grading.access.domain.UserStatus;
import uz.hrlab.grading.access.infrastructure.RoleJpaEntity;
import uz.hrlab.grading.access.infrastructure.RoleRepository;
import uz.hrlab.grading.access.infrastructure.UserJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserRepository;
import uz.hrlab.grading.access.infrastructure.UserRoleJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserRoleRepository;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.integration.idp.application.IdentityProvisioningException;
import uz.hrlab.grading.integration.idp.application.IdentityProvisioningPort;
import uz.hrlab.grading.integration.idp.infrastructure.ZitadelIdpProperties;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;
import uz.hrlab.grading.tenancy.infrastructure.TenantRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Use case for {@code POST /api/v1/users} — invites a new user OR adds an
 * existing user to the target tenant.
 *
 * <p>Steps (single TX):
 * <ol>
 *   <li>ABAC: caller must be able to manage in {@code tenantId}.</li>
 *   <li>Resolve every supplied {@code roleCode} — unknown codes ⇒ 400.</li>
 *   <li>Per-role check: HRLab roles require {@code USER_ROLE_ASSIGN_HRLAB}.</li>
 *   <li>Find-or-create {@code public.users} by email (lower-case unique idx).
 *       New rows start as {@code INVITED}; an existing {@code ACTIVE} user is
 *       re-used as-is so adding-to-another-tenant does not regress status.</li>
 *   <li>Find-or-create {@code user_tenant_memberships} with status
 *       {@code INVITED} (or re-activate from {@code REVOKED} to {@code ACTIVE}).</li>
 *   <li>Insert any missing {@code user_roles}; duplicates ignored.</li>
 *   <li><b>IdP provisioning (decision doc 08, Option A — admin-set-password,
 *       NO SMTP):</b> when {@code grading.idp.zitadel.enabled=true}, create or
 *       link the ZITADEL login account, set {@code external_idp_subject}, and
 *       flip the user + membership to {@code ACTIVE} so the invitee can log in
 *       immediately (see login-ability decision below). When the flag is off
 *       this step is a NO-OP and the invite stays DB-only exactly as before.</li>
 *   <li>Audit: USER_CREATED (if new), USER_INVITED, USER_MEMBERSHIP_ADDED,
 *       USER_ROLE_ASSIGNED per attached role, plus USER_IDP_ACCOUNT_CREATED /
 *       USER_IDP_ACCOUNT_LINKED on provisioning, or USER_IDP_PROVISIONING_FAILED
 *       on failure.</li>
 * </ol>
 *
 * <h3>Login-ability / status decision (deliberate)</h3>
 * {@code JwtTenantContextResolver.pickActiveMembership} selects ONLY memberships
 * whose status is {@code ACTIVE}. A user left {@code INVITED} (user and/or
 * membership) therefore authenticates but resolves to a context with no active
 * tenant and no roles — they can sign in but cannot actually work. Because the
 * admin-set password is pre-verified at the IdP ({@code isVerified:true},
 * {@code changeRequired:false}), there is NO second verification step that would
 * justify keeping the user {@code INVITED}. So, ON SUCCESSFUL PROVISIONING, this
 * use case flips both the {@code public.users} row and the target membership to
 * {@code ACTIVE}. When IdP is disabled the legacy {@code INVITED} state is kept
 * (Option B manual flow: the row flips to ACTIVE on first login elsewhere).
 *
 * <h3>Consistency (all-or-nothing)</h3>
 * Provisioning runs inside the same {@code @Transactional} boundary. If it
 * fails, the method throws {@link IdentityProvisioningException} and the whole
 * invite ROLLS BACK — we never leave a user who looks invited in-app but cannot
 * log in. The {@code USER_IDP_PROVISIONING_FAILED} audit row is written by
 * {@code JpaAuditService} in {@code REQUIRES_NEW} propagation, so the failure
 * remains on record even though the business rows are rolled back. This is the
 * simplest correct behaviour; the commit-then-reconcile / PENDING mode in the
 * decision doc is a later additive option and is intentionally NOT built here.
 *
 * <p>Returns the freshly built {@link UserDetailsResponse} so the frontend can
 * render the new user card without a follow-up GET.
 */
@Service
public class InviteUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(InviteUserUseCase.class);

    private final UserManagementPolicy policy;
    private final UserRepository userRepo;
    private final UserTenantMembershipRepository membershipRepo;
    private final UserRoleRepository userRoleRepo;
    private final RoleRepository roleRepo;
    private final TenantRepository tenantRepo;
    private final GetUserDetailsQuery detailsQuery;
    private final AuditService audit;
    private final IdentityProvisioningPort identityProvisioning;
    private final ZitadelIdpProperties idpProps;

    public InviteUserUseCase(UserManagementPolicy policy,
                             UserRepository userRepo,
                             UserTenantMembershipRepository membershipRepo,
                             UserRoleRepository userRoleRepo,
                             RoleRepository roleRepo,
                             TenantRepository tenantRepo,
                             GetUserDetailsQuery detailsQuery,
                             AuditService audit,
                             IdentityProvisioningPort identityProvisioning,
                             ZitadelIdpProperties idpProps) {
        this.policy = policy;
        this.userRepo = userRepo;
        this.membershipRepo = membershipRepo;
        this.userRoleRepo = userRoleRepo;
        this.roleRepo = roleRepo;
        this.tenantRepo = tenantRepo;
        this.detailsQuery = detailsQuery;
        this.audit = audit;
        this.identityProvisioning = identityProvisioning;
        this.idpProps = idpProps;
    }

    /**
     * Back-compat overload (no password) — used by any caller/test that predates
     * IdP provisioning. Behaves identically to the legacy DB-only invite when
     * the IdP flag is off; if the flag is ON it will fail password validation,
     * which is the correct contract (a password is required to make a real
     * login account).
     */
    @Transactional
    public UserDetailsResponse invite(String email, String fullName, String locale,
                                      UUID tenantId, List<String> roleCodes) {
        return invite(email, fullName, locale, tenantId, roleCodes, null);
    }

    @Transactional
    public UserDetailsResponse invite(String email, String fullName, String locale,
                                      UUID tenantId, List<String> roleCodes,
                                      String rawPassword) {
        TenantContext ctx = TenantContextHolder.requireActive();
        policy.requireCanManageInTenant(ctx, tenantId);

        if (tenantRepo.findById(tenantId).isEmpty()) {
            // Tenant does not exist → 404 (no probing).
            throw new ValidationException("USER_INVITE_INVALID_TENANT",
                    "Target tenant not found");
        }

        boolean idpEnabled = idpProps.isEnabled();
        // Validate the admin-set password up-front (before any write) ONLY when
        // the IdP will be used. When disabled, the password is ignored entirely.
        if (idpEnabled) {
            PasswordPolicy.validate(rawPassword, "USER_INVITE_WEAK_PASSWORD");
        }

        // Resolve roles + per-role HRLab gate BEFORE any write.
        List<RoleJpaEntity> resolved = new ArrayList<>(roleCodes.size());
        for (String code : roleCodes) {
            RoleJpaEntity role = roleRepo.findByCode(code)
                    .orElseThrow(() -> new ValidationException("USER_INVITE_UNKNOWN_ROLE",
                            "Unknown role code: " + code));
            policy.requireCanAssignRole(ctx, role);
            resolved.add(role);
        }

        boolean userCreated = false;
        UserJpaEntity user = userRepo.findByEmailIgnoreCase(email.trim()).orElse(null);
        if (user == null) {
            user = new UserJpaEntity(UUID.randomUUID(), email.trim().toLowerCase(),
                    null, fullName.trim(), UserStatus.INVITED, locale);
            userRepo.save(user);
            userCreated = true;
            audit(ctx, tenantId, user.getId(), AuditAction.USER_CREATED,
                    "email=" + user.getEmail());
        }

        // Find-or-create membership.
        UserTenantMembershipJpaEntity membership = membershipRepo
                .findByUserIdAndTenantId(user.getId(), tenantId)
                .orElse(null);
        boolean membershipCreated = false;
        if (membership == null) {
            membership = new UserTenantMembershipJpaEntity(UUID.randomUUID(),
                    user.getId(), tenantId, MembershipStatus.INVITED, false);
            membershipRepo.save(membership);
            membershipCreated = true;
        } else if (membership.getStatus() == MembershipStatus.REVOKED) {
            membership.setStatus(MembershipStatus.ACTIVE);
            membershipRepo.save(membership);
            membershipCreated = true;
        }

        if (membershipCreated) {
            audit(ctx, tenantId, user.getId(), AuditAction.USER_MEMBERSHIP_ADDED,
                    "membershipId=" + membership.getId());
        }
        // Always emit USER_INVITED for the request itself — distinct from
        // USER_CREATED (which only fires for a new public.users row).
        audit(ctx, tenantId, user.getId(), AuditAction.USER_INVITED,
                "invitedTo=" + tenantId + " userCreated=" + userCreated);

        // Insert missing user_roles; idempotent.
        for (RoleJpaEntity role : resolved) {
            if (!userRoleRepo.existsByMembershipIdAndRoleId(membership.getId(), role.getId())) {
                UserRoleJpaEntity ur = new UserRoleJpaEntity(UUID.randomUUID(),
                        membership.getId(), role.getId());
                userRoleRepo.save(ur);
                audit(ctx, tenantId, user.getId(), AuditAction.USER_ROLE_ASSIGNED,
                        "roleCode=" + role.getCode() + " membershipId=" + membership.getId());
            }
        }

        // IdP provisioning — create/link the ZITADEL login account and make the
        // invitee LOGIN-ABLE immediately. NO-OP when the flag is off.
        if (idpEnabled) {
            provisionAndActivate(ctx, tenantId, user, membership, rawPassword);
        }

        return detailsQuery.byId(user.getId());
    }

    /**
     * Create-or-link the IdP account, persist {@code external_idp_subject}, and
     * flip the user + membership to {@code ACTIVE}. On failure: record
     * {@code USER_IDP_PROVISIONING_FAILED} (survives via REQUIRES_NEW) and
     * rethrow so the whole invite rolls back.
     */
    private void provisionAndActivate(TenantContext ctx, UUID tenantId,
                                      UserJpaEntity user,
                                      UserTenantMembershipJpaEntity membership,
                                      String rawPassword) {
        String[] names = splitName(user.getFullName());
        try {
            IdentityProvisioningPort.ProvisionResult result =
                    identityProvisioning.provisionUser(user.getEmail(), names[0], names[1], rawPassword);

            // Persist the stable external subject so future logins resolve by `sub`,
            // not only by email (decision doc §4.6 — prefer external_idp_subject).
            user.setExternalIdpSubject(result.externalSubject());

            // Login-ability: admin-set password is pre-verified ⇒ activate now.
            if (user.getStatus() == UserStatus.INVITED) {
                user.setStatus(UserStatus.ACTIVE);
            }
            if (membership.getStatus() == MembershipStatus.INVITED) {
                membership.setStatus(MembershipStatus.ACTIVE);
            }
            userRepo.save(user);
            membershipRepo.save(membership);

            String action = result.linkedExisting()
                    ? AuditAction.USER_IDP_ACCOUNT_LINKED
                    : AuditAction.USER_IDP_ACCOUNT_CREATED;
            // Audit carries the external subject + email ONLY — never the password.
            audit(ctx, tenantId, user.getId(), action,
                    "externalIdpSubject=" + result.externalSubject() + " email=" + user.getEmail());
        } catch (IdentityProvisioningException ex) {
            // Failure audit (no password, no token). Written in REQUIRES_NEW so it
            // is committed even though the invite transaction is about to roll back.
            audit(ctx, tenantId, user.getId(), AuditAction.USER_IDP_PROVISIONING_FAILED,
                    "email=" + user.getEmail() + " reason=" + ex.getCode());
            log.warn("IdP provisioning failed during invite; rolling back. email={} code={}",
                    user.getEmail(), ex.getCode());
            throw ex; // rolls back the grading rows — no half-provisioned user
        }
    }

    /**
     * Split a full name into [givenName, familyName] for the IdP profile. The
     * first whitespace-separated token is the given name; the remainder is the
     * family name. A single-token name uses it for both so neither IdP field is
     * blank.
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

    private void audit(TenantContext ctx, UUID tenantId, UUID targetUserId,
                       String action, String reason) {
        audit.record(AuditEvent.builder()
                .tenantId(tenantId)
                .actorUserId(ctx.userId())
                .action(action)
                .entityType("User")
                .entityId(targetUserId)
                .reason(reason)
                .build());
    }
}

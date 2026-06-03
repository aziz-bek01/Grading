package uz.hrlab.grading.access.application;

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
 *   <li>Audit: USER_CREATED (if new), USER_INVITED, USER_MEMBERSHIP_ADDED,
 *       USER_ROLE_ASSIGNED per attached role.</li>
 * </ol>
 *
 * <p>Returns the freshly built {@link UserDetailsResponse} so the frontend can
 * render the new user card without a follow-up GET.
 */
@Service
public class InviteUserUseCase {

    private final UserManagementPolicy policy;
    private final UserRepository userRepo;
    private final UserTenantMembershipRepository membershipRepo;
    private final UserRoleRepository userRoleRepo;
    private final RoleRepository roleRepo;
    private final TenantRepository tenantRepo;
    private final GetUserDetailsQuery detailsQuery;
    private final AuditService audit;

    public InviteUserUseCase(UserManagementPolicy policy,
                             UserRepository userRepo,
                             UserTenantMembershipRepository membershipRepo,
                             UserRoleRepository userRoleRepo,
                             RoleRepository roleRepo,
                             TenantRepository tenantRepo,
                             GetUserDetailsQuery detailsQuery,
                             AuditService audit) {
        this.policy = policy;
        this.userRepo = userRepo;
        this.membershipRepo = membershipRepo;
        this.userRoleRepo = userRoleRepo;
        this.roleRepo = roleRepo;
        this.tenantRepo = tenantRepo;
        this.detailsQuery = detailsQuery;
        this.audit = audit;
    }

    @Transactional
    public UserDetailsResponse invite(String email, String fullName, String locale,
                                      UUID tenantId, List<String> roleCodes) {
        TenantContext ctx = TenantContextHolder.requireActive();
        policy.requireCanManageInTenant(ctx, tenantId);

        if (tenantRepo.findById(tenantId).isEmpty()) {
            // Tenant does not exist → 404 (no probing).
            throw new ValidationException("USER_INVITE_INVALID_TENANT",
                    "Target tenant not found");
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

        return detailsQuery.byId(user.getId());
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

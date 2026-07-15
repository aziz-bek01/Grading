package uz.hrlab.grading.access.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.api.UserDetailsResponse;
import uz.hrlab.grading.access.domain.MembershipStatus;
import uz.hrlab.grading.access.infrastructure.RoleJpaEntity;
import uz.hrlab.grading.access.infrastructure.RoleRepository;
import uz.hrlab.grading.access.infrastructure.UserRepository;
import uz.hrlab.grading.access.infrastructure.UserRoleJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserRoleRepository;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;
import uz.hrlab.grading.tenancy.infrastructure.TenantRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Use case for {@code POST /api/v1/users/{id}/memberships} — attach an
 * existing user to one MORE tenant (caller's own, unless Super Admin).
 *
 * <p>Re-activating a REVOKED membership keeps the same row id — auditors
 * trace the lifecycle by following USER_MEMBERSHIP_ADDED / _REVOKED for the
 * same membership.id over time.
 */
@Service
public class AddMembershipUseCase {

    private final UserManagementPolicy policy;
    private final UserRepository userRepo;
    private final UserTenantMembershipRepository membershipRepo;
    private final UserRoleRepository userRoleRepo;
    private final RoleRepository roleRepo;
    private final TenantRepository tenantRepo;
    private final GetUserDetailsQuery detailsQuery;
    private final AuditService audit;
    private final SuperAdminRoleAuditor superAdminAuditor;

    public AddMembershipUseCase(UserManagementPolicy policy,
                                UserRepository userRepo,
                                UserTenantMembershipRepository membershipRepo,
                                UserRoleRepository userRoleRepo,
                                RoleRepository roleRepo,
                                TenantRepository tenantRepo,
                                GetUserDetailsQuery detailsQuery,
                                AuditService audit,
                                SuperAdminRoleAuditor superAdminAuditor) {
        this.policy = policy;
        this.userRepo = userRepo;
        this.membershipRepo = membershipRepo;
        this.userRoleRepo = userRoleRepo;
        this.roleRepo = roleRepo;
        this.tenantRepo = tenantRepo;
        this.detailsQuery = detailsQuery;
        this.audit = audit;
        this.superAdminAuditor = superAdminAuditor;
    }

    @Transactional
    public UserDetailsResponse add(UUID userId, UUID tenantId, List<String> roleCodes) {
        TenantContext ctx = TenantContextHolder.requireActive();
        policy.requireCanManageInTenant(ctx, tenantId);

        if (userRepo.findById(userId).isEmpty()) {
            throw new TenantAccessDeniedException();
        }
        if (tenantRepo.findById(tenantId).isEmpty()) {
            throw new ValidationException("USER_MEMBERSHIP_INVALID_TENANT",
                    "Target tenant not found");
        }
        List<RoleJpaEntity> resolved = new ArrayList<>();
        for (String code : roleCodes) {
            RoleJpaEntity role = roleRepo.findByCode(code)
                    .orElseThrow(() -> new ValidationException("USER_MEMBERSHIP_UNKNOWN_ROLE",
                            "Unknown role code: " + code));
            policy.requireCanAssignRole(ctx, role);
            resolved.add(role);
        }

        UserTenantMembershipJpaEntity membership = membershipRepo
                .findByUserIdAndTenantId(userId, tenantId).orElse(null);
        boolean created = false;
        if (membership == null) {
            membership = new UserTenantMembershipJpaEntity(UUID.randomUUID(),
                    userId, tenantId, MembershipStatus.ACTIVE, false);
            membershipRepo.save(membership);
            created = true;
        } else if (membership.getStatus() == MembershipStatus.REVOKED) {
            membership.setStatus(MembershipStatus.ACTIVE);
            membershipRepo.save(membership);
            created = true;
        }

        if (created) {
            audit.record(AuditEvent.builder()
                    .tenantId(tenantId)
                    .actorUserId(ctx.userId())
                    .action(AuditAction.USER_MEMBERSHIP_ADDED)
                    .entityType("UserTenantMembership")
                    .entityId(membership.getId())
                    .reason("userId=" + userId)
                    .build());
        }

        for (RoleJpaEntity role : resolved) {
            if (!userRoleRepo.existsByMembershipIdAndRoleId(membership.getId(), role.getId())) {
                UserRoleJpaEntity ur = new UserRoleJpaEntity(UUID.randomUUID(),
                        membership.getId(), role.getId());
                userRoleRepo.save(ur);
                audit.record(AuditEvent.builder()
                        .tenantId(tenantId)
                        .actorUserId(ctx.userId())
                        .action(AuditAction.USER_ROLE_ASSIGNED)
                        .entityType("UserRole")
                        .entityId(ur.getId())
                        .reason("roleCode=" + role.getCode() + " userId=" + userId)
                        .build());
                // Blast-radius control: additive audit + alert for a super-admin
                // grant (NO-OP for any other role).
                superAdminAuditor.onRoleGranted(ctx.userId(), tenantId, userId,
                        role.getCode(), ur.getId(), "membership-add");
            }
        }

        return detailsQuery.byId(userId);
    }
}

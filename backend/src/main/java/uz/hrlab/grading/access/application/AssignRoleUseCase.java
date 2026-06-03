package uz.hrlab.grading.access.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.api.UserDetailsResponse;
import uz.hrlab.grading.access.infrastructure.RoleJpaEntity;
import uz.hrlab.grading.access.infrastructure.RoleRepository;
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

import java.util.UUID;

/**
 * Use case for {@code POST /api/v1/users/{id}/memberships/{tenantId}/roles}
 * and {@code DELETE /api/v1/users/{id}/memberships/{tenantId}/roles/{roleId}}.
 *
 * <p>Both paths verify the (user, tenant) membership exists in the caller's
 * scope. Role-assign goes through {@link UserManagementPolicy#requireCanAssignRole}
 * so HRLab roles need {@code USER_ROLE_ASSIGN_HRLAB}.
 */
@Service
public class AssignRoleUseCase {

    private final UserManagementPolicy policy;
    private final UserTenantMembershipRepository membershipRepo;
    private final UserRoleRepository userRoleRepo;
    private final RoleRepository roleRepo;
    private final GetUserDetailsQuery detailsQuery;
    private final AuditService audit;

    public AssignRoleUseCase(UserManagementPolicy policy,
                             UserTenantMembershipRepository membershipRepo,
                             UserRoleRepository userRoleRepo,
                             RoleRepository roleRepo,
                             GetUserDetailsQuery detailsQuery,
                             AuditService audit) {
        this.policy = policy;
        this.membershipRepo = membershipRepo;
        this.userRoleRepo = userRoleRepo;
        this.roleRepo = roleRepo;
        this.detailsQuery = detailsQuery;
        this.audit = audit;
    }

    @Transactional
    public UserDetailsResponse assign(UUID userId, UUID tenantId, String roleCode) {
        TenantContext ctx = TenantContextHolder.requireActive();
        policy.requireCanManageInTenant(ctx, tenantId);

        UserTenantMembershipJpaEntity membership = membershipRepo
                .findByUserIdAndTenantId(userId, tenantId)
                .orElseThrow(TenantAccessDeniedException::new);

        RoleJpaEntity role = roleRepo.findByCode(roleCode)
                .orElseThrow(() -> new ValidationException("USER_ROLE_UNKNOWN",
                        "Unknown role code: " + roleCode));
        policy.requireCanAssignRole(ctx, role);

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
        }
        return detailsQuery.byId(userId);
    }

    @Transactional
    public UserDetailsResponse remove(UUID userId, UUID tenantId, UUID userRoleId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        policy.requireCanManageInTenant(ctx, tenantId);

        UserTenantMembershipJpaEntity membership = membershipRepo
                .findByUserIdAndTenantId(userId, tenantId)
                .orElseThrow(TenantAccessDeniedException::new);

        UserRoleJpaEntity userRole = userRoleRepo
                .findByIdAndMembershipId(userRoleId, membership.getId())
                .orElseThrow(TenantAccessDeniedException::new);

        // Role-removal still goes through requireCanAssignRole so removing an
        // HRLab role from someone is just as gated as adding it.
        RoleJpaEntity role = roleRepo.findById(userRole.getRoleId()).orElse(null);
        if (role != null) {
            policy.requireCanAssignRole(ctx, role);
        }

        userRoleRepo.delete(userRole);
        audit.record(AuditEvent.builder()
                .tenantId(tenantId)
                .actorUserId(ctx.userId())
                .action(AuditAction.USER_ROLE_REMOVED)
                .entityType("UserRole")
                .entityId(userRole.getId())
                .reason("roleCode=" + (role == null ? "?" : role.getCode())
                        + " userId=" + userId)
                .build());
        return detailsQuery.byId(userId);
    }
}

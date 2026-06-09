package uz.hrlab.grading.access.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.api.UserScopeResponse;
import uz.hrlab.grading.access.infrastructure.UserDepartmentScopeRepository;
import uz.hrlab.grading.access.infrastructure.UserProjectAssignmentRepository;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.UUID;

/**
 * Query handler for {@code GET /api/v1/users/{id}/scopes?tenantId=...} (E4-S1).
 *
 * <p>Returns the user's CURRENTLY ACTIVE department scope roots + project
 * assignments in the requested tenant. The tenant is validated against the
 * caller's management scope ({@link UserManagementPolicy#requireCanManageInTenant}),
 * which also pins it to the caller's active tenant for non-super-admins — a
 * cross-tenant target therefore resolves to 404, not a data leak.
 *
 * <p>The target user MUST be a MEMBER of the tenant (P2-1) — not merely exist
 * globally — so a probe for "does user X exist in tenant Y" cannot be answered
 * by the difference between 404 and an empty-scope 200. A non-member → 404,
 * exactly like {@link AssignRoleUseCase}.
 */
@Service
public class GetUserScopesQuery {

    private final UserManagementPolicy policy;
    private final UserTenantMembershipRepository membershipRepo;
    private final UserDepartmentScopeRepository departmentScopes;
    private final UserProjectAssignmentRepository projectAssignments;

    public GetUserScopesQuery(UserManagementPolicy policy,
                              UserTenantMembershipRepository membershipRepo,
                              UserDepartmentScopeRepository departmentScopes,
                              UserProjectAssignmentRepository projectAssignments) {
        this.policy = policy;
        this.membershipRepo = membershipRepo;
        this.departmentScopes = departmentScopes;
        this.projectAssignments = projectAssignments;
    }

    @Transactional(readOnly = true)
    public UserScopeResponse byUserAndTenant(UUID userId, UUID tenantId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        policy.requireCanManageInTenant(ctx, tenantId);

        // P2-1: target must be a MEMBER of the tenant (not merely exist
        // globally) — mirrors AssignRoleUseCase; a non-member resolves to 404.
        if (membershipRepo.findByUserIdAndTenantId(userId, tenantId).isEmpty()) {
            throw new TenantAccessDeniedException();
        }

        List<UUID> departmentIds = departmentScopes.findActiveDepartmentIds(userId, tenantId);
        List<UUID> projectIds = projectAssignments.findActiveProjectIds(userId, tenantId);

        return new UserScopeResponse(userId, tenantId, departmentIds, projectIds);
    }
}

package uz.hrlab.grading.access.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.api.UserScopeResponse;
import uz.hrlab.grading.access.domain.DepartmentScopePolicy;
import uz.hrlab.grading.access.infrastructure.UserDepartmentScopeJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserDepartmentScopeRepository;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Use case for {@code PUT /api/v1/users/{id}/department-scopes} (E4-S1).
 *
 * <p>REPLACE-SET semantics: the request carries the COMPLETE desired set of
 * department scope ROOTS for the (user, tenant). Departments already ACTIVE but
 * absent from the request are REVOKED; new ones are upserted ACTIVE (re-activating
 * a previously REVOKED row keeps its id for audit continuity). Each individual
 * change emits one {@code USER_DEPARTMENT_SCOPE_GRANTED}/{@code _REVOKED} audit
 * row so the delta is reconstructible.
 *
 * <h3>Multi-tenant safety</h3>
 * <ul>
 *   <li>Tenant comes from the {@code TenantContext}; the body's {@code tenant_id}
 *       must match the path-scoped management gate
 *       ({@link UserManagementPolicy#requireCanManageInTenant}). A cross-tenant
 *       target → 404.</li>
 *   <li>Every requested department is validated to BELONG to that tenant
 *       (batched count against {@code departments}) before any write — a
 *       department from another tenant → 400.</li>
 *   <li>The target user MUST be a MEMBER of the tenant (P2-1) — assigning scope
 *       to a non-member would be both an existence oracle and access
 *       pre-positioning. A non-member target → 404, exactly like
 *       {@link AssignRoleUseCase}.</li>
 *   <li>For a NON-bypass caller (not Super Admin / not a
 *       {@link DepartmentScopePolicy#isTenantWideBypass tenant-wide-bypass}
 *       role) every requested department MUST fall inside the caller's OWN
 *       {@code departmentScope()} subtree (P2-2, defense in depth) — a caller
 *       cannot grant scope wider than they themselves hold. Tenant-wide-bypass
 *       callers (CLIENT_COMPANY_ADMIN, HRLAB_SUPER_ADMIN, …) are unaffected.</li>
 * </ul>
 */
@Service
public class SetUserDepartmentScopesUseCase {

    private final UserManagementPolicy policy;
    private final UserTenantMembershipRepository membershipRepo;
    private final UserDepartmentScopeRepository scopeRepo;
    private final DepartmentRepository departmentRepo;
    private final GetUserScopesQuery scopesQuery;
    private final AuditService audit;

    public SetUserDepartmentScopesUseCase(UserManagementPolicy policy,
                                          UserTenantMembershipRepository membershipRepo,
                                          UserDepartmentScopeRepository scopeRepo,
                                          DepartmentRepository departmentRepo,
                                          GetUserScopesQuery scopesQuery,
                                          AuditService audit) {
        this.policy = policy;
        this.membershipRepo = membershipRepo;
        this.scopeRepo = scopeRepo;
        this.departmentRepo = departmentRepo;
        this.scopesQuery = scopesQuery;
        this.audit = audit;
    }

    @Transactional
    public UserScopeResponse replace(UUID userId, UUID tenantId, List<UUID> departmentIds) {
        TenantContext ctx = TenantContextHolder.requireActive();
        policy.requireCanManageInTenant(ctx, tenantId);

        // P2-1: target must be a MEMBER of the tenant (not merely exist
        // globally) — mirrors AssignRoleUseCase; a non-member resolves to 404.
        if (membershipRepo.findByUserIdAndTenantId(userId, tenantId).isEmpty()) {
            throw new TenantAccessDeniedException();
        }

        // De-dupe while preserving caller intent; null entries are rejected.
        Set<UUID> desired = new LinkedHashSet<>();
        for (UUID id : departmentIds) {
            if (id == null) {
                throw new ValidationException("USER_SCOPE_INVALID_DEPARTMENT",
                        "department_ids must not contain null");
            }
            desired.add(id);
        }

        // Validate every requested department belongs to the active tenant —
        // BEFORE any write. Reuses the organization repo (no tree reimpl).
        if (!desired.isEmpty()) {
            long owned = departmentRepo.countByIdInAndTenantId(desired, tenantId);
            if (owned != desired.size()) {
                throw new ValidationException("USER_SCOPE_INVALID_DEPARTMENT",
                        "One or more departments do not belong to the tenant");
            }

            // P2-2 (defense in depth): a non-bypass caller may only grant scope
            // they themselves hold — every requested department must be inside
            // the caller's own departmentScope() subtree. Bypass roles
            // (Super Admin / Client Company Admin / HR Director, …) see the whole
            // tenant and so may grant any tenant-owned department.
            if (!DepartmentScopePolicy.isTenantWideBypass(ctx)) {
                Set<UUID> callerScope = ctx.departmentScope();
                if (callerScope == null || !callerScope.containsAll(desired)) {
                    // 404 (TenantAccessDeniedException), not 400: do not reveal
                    // which department is outside the caller's reach.
                    throw new TenantAccessDeniedException();
                }
            }
        }

        List<UserDepartmentScopeJpaEntity> existing =
                scopeRepo.findAllByUserIdAndTenantId(userId, tenantId);

        // Upsert desired (activate new / re-activate revoked).
        for (UUID deptId : desired) {
            UserDepartmentScopeJpaEntity row = existing.stream()
                    .filter(s -> s.getDepartmentId().equals(deptId))
                    .findFirst().orElse(null);
            if (row == null) {
                row = new UserDepartmentScopeJpaEntity(UUID.randomUUID(), userId, tenantId,
                        deptId, UserDepartmentScopeJpaEntity.STATUS_ACTIVE, ctx.userId());
                scopeRepo.save(row);
                auditGranted(ctx, tenantId, userId, row, deptId);
            } else if (!UserDepartmentScopeJpaEntity.STATUS_ACTIVE.equals(row.getStatus())) {
                row.setStatus(UserDepartmentScopeJpaEntity.STATUS_ACTIVE);
                row.setGrantedByUserId(ctx.userId());
                scopeRepo.save(row);
                auditGranted(ctx, tenantId, userId, row, deptId);
            }
        }

        // Revoke ACTIVE rows no longer desired.
        for (UserDepartmentScopeJpaEntity row : existing) {
            if (UserDepartmentScopeJpaEntity.STATUS_ACTIVE.equals(row.getStatus())
                    && !desired.contains(row.getDepartmentId())) {
                row.setStatus(UserDepartmentScopeJpaEntity.STATUS_REVOKED);
                scopeRepo.save(row);
                audit.record(AuditEvent.builder()
                        .tenantId(tenantId)
                        .actorUserId(ctx.userId())
                        .action(AuditAction.USER_DEPARTMENT_SCOPE_REVOKED)
                        .entityType("UserDepartmentScope")
                        .entityId(row.getId())
                        .reason("userId=" + userId + " departmentId=" + row.getDepartmentId())
                        .build());
            }
        }

        return scopesQuery.byUserAndTenant(userId, tenantId);
    }

    private void auditGranted(TenantContext ctx, UUID tenantId, UUID userId,
                              UserDepartmentScopeJpaEntity row, UUID deptId) {
        audit.record(AuditEvent.builder()
                .tenantId(tenantId)
                .actorUserId(ctx.userId())
                .action(AuditAction.USER_DEPARTMENT_SCOPE_GRANTED)
                .entityType("UserDepartmentScope")
                .entityId(row.getId())
                .reason("userId=" + userId + " departmentId=" + deptId)
                .build());
    }
}

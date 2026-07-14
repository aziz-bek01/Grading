package uz.hrlab.grading.access.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.api.UserScopeResponse;
import uz.hrlab.grading.access.infrastructure.UserProjectAssignmentJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserProjectAssignmentRepository;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Use case for {@code PUT /api/v1/users/{id}/project-assignments} (E4-S1).
 *
 * <p>REPLACE-SET semantics analogous to
 * {@link SetUserDepartmentScopesUseCase}: the request carries the COMPLETE
 * desired set of project assignments for the (user, tenant); absent ACTIVE rows
 * are REVOKED, new ones upserted ACTIVE. Each change emits one
 * {@code USER_PROJECT_ASSIGNMENT_ADDED}/{@code _REMOVED} audit row.
 *
 * <h3>Multi-tenant safety</h3>
 * Tenant comes from the {@code TenantContext} (gated by
 * {@link UserManagementPolicy#requireCanManageInTenant}); cross-tenant target →
 * 404. The target user MUST be a MEMBER of the tenant (P2-1) — not merely exist
 * globally — exactly like {@link AssignRoleUseCase}; a non-member → 404. Each
 * requested project is validated to belong to the tenant via the access-owned
 * {@link ProjectReferencePort#existsInTenant} (a dependency-inversion port whose
 * project-side adapter delegates to the tenant-aware
 * {@code ProjectRepository.existsByIdAndTenantId} — anti-BOLA, never a bare
 * {@code findById}); a foreign project → 400.
 */
@Service
public class SetUserProjectAssignmentsUseCase {

    private final UserManagementPolicy policy;
    private final UserTenantMembershipRepository membershipRepo;
    private final UserProjectAssignmentRepository assignmentRepo;
    private final ProjectReferencePort projectReference;
    private final GetUserScopesQuery scopesQuery;
    private final AuditService audit;

    public SetUserProjectAssignmentsUseCase(UserManagementPolicy policy,
                                            UserTenantMembershipRepository membershipRepo,
                                            UserProjectAssignmentRepository assignmentRepo,
                                            ProjectReferencePort projectReference,
                                            GetUserScopesQuery scopesQuery,
                                            AuditService audit) {
        this.policy = policy;
        this.membershipRepo = membershipRepo;
        this.assignmentRepo = assignmentRepo;
        this.projectReference = projectReference;
        this.scopesQuery = scopesQuery;
        this.audit = audit;
    }

    @Transactional
    public UserScopeResponse replace(UUID userId, UUID tenantId, List<UUID> projectIds) {
        TenantContext ctx = TenantContextHolder.requireActive();
        policy.requireCanManageInTenant(ctx, tenantId);

        // P2-1: target must be a MEMBER of the tenant (not merely exist
        // globally) — mirrors AssignRoleUseCase; a non-member resolves to 404.
        if (membershipRepo.findByUserIdAndTenantId(userId, tenantId).isEmpty()) {
            throw new TenantAccessDeniedException();
        }

        Set<UUID> desired = new LinkedHashSet<>();
        for (UUID id : projectIds) {
            if (id == null) {
                throw new ValidationException("USER_SCOPE_INVALID_PROJECT",
                        "project_ids must not contain null");
            }
            desired.add(id);
        }

        // Validate every requested project belongs to the active tenant — BEFORE
        // any write. Uses the tenant-aware repo (no bare findById).
        for (UUID projectId : desired) {
            if (!projectReference.existsInTenant(projectId, tenantId)) {
                throw new ValidationException("USER_SCOPE_INVALID_PROJECT",
                        "One or more projects do not belong to the tenant");
            }
        }

        List<UserProjectAssignmentJpaEntity> existing =
                assignmentRepo.findAllByUserIdAndTenantId(userId, tenantId);

        // Upsert desired (activate new / re-activate revoked).
        for (UUID projectId : desired) {
            UserProjectAssignmentJpaEntity row = existing.stream()
                    .filter(a -> a.getProjectId().equals(projectId))
                    .findFirst().orElse(null);
            if (row == null) {
                row = new UserProjectAssignmentJpaEntity(UUID.randomUUID(), userId, tenantId,
                        projectId, UserProjectAssignmentJpaEntity.STATUS_ACTIVE);
                assignmentRepo.save(row);
                auditAdded(ctx, tenantId, userId, row, projectId);
            } else if (!UserProjectAssignmentJpaEntity.STATUS_ACTIVE.equals(row.getStatus())) {
                row.setStatus(UserProjectAssignmentJpaEntity.STATUS_ACTIVE);
                assignmentRepo.save(row);
                auditAdded(ctx, tenantId, userId, row, projectId);
            }
        }

        // Revoke ACTIVE rows no longer desired.
        for (UserProjectAssignmentJpaEntity row : existing) {
            if (UserProjectAssignmentJpaEntity.STATUS_ACTIVE.equals(row.getStatus())
                    && !desired.contains(row.getProjectId())) {
                row.setStatus(UserProjectAssignmentJpaEntity.STATUS_REVOKED);
                assignmentRepo.save(row);
                audit.record(AuditEvent.builder()
                        .tenantId(tenantId)
                        .actorUserId(ctx.userId())
                        .action(AuditAction.USER_PROJECT_ASSIGNMENT_REMOVED)
                        .entityType("UserProjectAssignment")
                        .entityId(row.getId())
                        .reason("userId=" + userId + " projectId=" + row.getProjectId())
                        .build());
            }
        }

        return scopesQuery.byUserAndTenant(userId, tenantId);
    }

    private void auditAdded(TenantContext ctx, UUID tenantId, UUID userId,
                            UserProjectAssignmentJpaEntity row, UUID projectId) {
        audit.record(AuditEvent.builder()
                .tenantId(tenantId)
                .actorUserId(ctx.userId())
                .action(AuditAction.USER_PROJECT_ASSIGNMENT_ADDED)
                .entityType("UserProjectAssignment")
                .entityId(row.getId())
                .reason("userId=" + userId + " projectId=" + projectId)
                .build());
    }
}

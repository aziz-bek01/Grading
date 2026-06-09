package uz.hrlab.grading.access.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@code public.user_project_assignments} (E4-S0).
 *
 * <p>This is a control-plane mapping table, NOT a tenant-business table, so it
 * extends {@link JpaRepository} like its siblings ({@code UserRoleRepository},
 * {@code UserTenantMembershipRepository}). Cross-tenant isolation is enforced
 * by EXPLICIT {@code tenant_id} + {@code user_id} predicates on every finder —
 * never trust input; the tenant always comes from {@code TenantContext}. We do
 * NOT expose a bare {@code project_id}-only lookup (BOLA defense), mirroring the
 * deliberate omission in {@link UserRoleRepository}.
 */
public interface UserProjectAssignmentRepository
        extends JpaRepository<UserProjectAssignmentJpaEntity, UUID> {

    /**
     * Project ids the user is ACTIVE-assigned to in this tenant. Used during
     * {@code TenantContext.projectIds} expansion on the auth path — a single
     * query, no N+1.
     */
    @org.springframework.data.jpa.repository.Query("""
            select a.projectId from UserProjectAssignmentJpaEntity a
            where a.userId = :userId and a.tenantId = :tenantId
              and a.status = 'ACTIVE'
            """)
    List<UUID> findActiveProjectIds(
            @org.springframework.data.repository.query.Param("userId") UUID userId,
            @org.springframework.data.repository.query.Param("tenantId") UUID tenantId);

    /** All rows (ACTIVE + REVOKED) for a (user, tenant) — used by the assign API. */
    List<UserProjectAssignmentJpaEntity> findAllByUserIdAndTenantId(UUID userId, UUID tenantId);

    Optional<UserProjectAssignmentJpaEntity> findByUserIdAndTenantIdAndProjectId(
            UUID userId, UUID tenantId, UUID projectId);
}

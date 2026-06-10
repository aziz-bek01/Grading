package uz.hrlab.grading.access.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@code public.roles} (control-plane — whitelisted in
 * {@code ArchitectureTest} as not tenant-RLS-scoped business data; isolation of
 * custom roles is enforced by the APP-LAYER predicate
 * {@code is_system = true OR tenant_id = :tenantId}, never by RLS).
 *
 * <p>No single-arg {@code findById} BOLA hazard is exposed for tenant data:
 * custom-role lookups go through {@link #findByIdAndTenantId} so a tenant admin
 * can only resolve a custom role it owns (cross-tenant target → empty → 404).
 */
public interface RoleRepository extends JpaRepository<RoleJpaEntity, UUID> {

    Optional<RoleJpaEntity> findByCode(String code);

    /**
     * Resolve a SYSTEM role by {@code code}. System codes are globally unique
     * (partial unique {@code uq_roles_system_code}) and carry no tenant, so this is
     * the safe, unambiguous way to resolve a system role by code for the E2
     * permission-matrix endpoints (a custom code can never collide with a system
     * code — the create path in {@code CustomRoleUseCase} rejects that — so a
     * by-code lookup tries this first, then the caller-tenant custom finder).
     */
    Optional<RoleJpaEntity> findByCodeAndIsSystemTrue(String code);

    /**
     * True when ANY role (system OR any tenant's custom) already uses {@code code}.
     *
     * <p>Used by the custom-role create path to reject a code that collides with a
     * SYSTEM code namespace at the application layer (the two partial unique
     * indexes from slice E3 only guarantee uniqueness WITHIN each namespace, so a
     * cross-namespace collision must be caught here). {@code RoleCodes} is the
     * canonical system-code set; this exists-check is the defensive belt against
     * any non-canonical system row.
     */
    boolean existsByCode(String code);

    /**
     * Resolve a CUSTOM role inside one tenant's namespace by {@code (tenant_id, code)}.
     * Returns empty for a system role (its {@code tenant_id} is NULL) or another
     * tenant's custom role — the create path uses this to reject an in-tenant
     * duplicate code without revealing cross-tenant rows.
     */
    Optional<RoleJpaEntity> findByTenantIdAndCode(UUID tenantId, String code);

    /**
     * Tenant-scoped lookup by id for a CUSTOM role. A tenant admin may only edit
     * or delete a custom role it owns; a cross-tenant id (or a system role, whose
     * {@code tenant_id} is NULL) yields empty → mapped to 404 (no existence reveal,
     * defeats cross-tenant probing).
     */
    Optional<RoleJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Catalog finder for {@code GET /api/v1/roles} (slice E1, now E3-aware):
     * the system catalog PLUS the active tenant's own custom roles. Other tenants'
     * custom roles are invisible (the {@code tenant_id = :tenantId} branch never
     * matches a foreign row). Ordered by {@code scope} (PLATFORM before TENANT)
     * then {@code code} for a stable listing.
     *
     * <p>Replaces the old tenant-agnostic {@code findAllByOrderByScopeAscCodeAsc}.
     */
    List<RoleJpaEntity> findByIsSystemTrueOrTenantIdOrderByScopeAscCodeAsc(UUID tenantId);
}

package uz.hrlab.grading.organization.infrastructure;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepartmentRepository
        extends TenantAwareRepository<DepartmentJpaEntity, UUID> {

    List<DepartmentJpaEntity> findByTenantIdAndProjectId(UUID tenantId, UUID projectId);

    /**
     * PERF (P1) — tenant-scoped batch lookup by id, for grid assembly that needs
     * a page of departments (and their parents) at once instead of one
     * {@code findByIdAndTenantId} per row (kills the N+1). {@code tenant_id} is
     * always pinned, so any id from another tenant contributes no row — this is
     * NOT the BOLA-prone {@code findAllById} and never widens scope.
     */
    List<DepartmentJpaEntity> findAllByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

    List<DepartmentJpaEntity> findByTenantIdAndProjectIdAndParentId(UUID tenantId, UUID projectId,
                                                                   UUID parentId);

    boolean existsByTenantIdAndProjectIdAndCode(UUID tenantId, UUID projectId, String code);

    Optional<DepartmentJpaEntity> findByTenantIdAndProjectIdAndCode(UUID tenantId, UUID projectId,
                                                                   String code);

    /**
     * Recursive descendant lookup (cycle prevention). Uses native CTE for
     * efficiency — query is tenant- and project-scoped.
     */
    @Query(value = """
            WITH RECURSIVE descendants AS (
                SELECT d.* FROM departments d
                WHERE d.id = :rootId AND d.tenant_id = :tenantId
                UNION ALL
                SELECT c.* FROM departments c
                JOIN descendants p ON c.parent_id = p.id
                WHERE c.tenant_id = :tenantId
            )
            SELECT * FROM descendants WHERE id <> :rootId
            """, nativeQuery = true)
    List<DepartmentJpaEntity> findDescendants(UUID rootId, UUID tenantId);

    /**
     * Batched subtree CLOSURE for ABAC department-scope expansion (E4-S0):
     * given a set of scope-root department ids, return the ids of those roots
     * PLUS every descendant — in a single recursive CTE (no per-root N+1). This
     * is the closure the resolver applies so a scope on department {@code D}
     * includes {@code D} + all sub-departments/divisions.
     *
     * <p>Tenant-scoped — {@code tenant_id} is sourced from {@code TenantContext}.
     * Roots that do not belong to the tenant simply contribute nothing (fail
     * safe, no widening). Only the {@code id} column is selected to keep the auth
     * path light. Returns an EMPTY list when {@code rootIds} is empty.
     *
     * <p>SECURITY (P2-3): this CTE runs on the AUTH path for every
     * department-scoped request. A cyclic {@code parent_id} chain (data
     * corruption / bad import) would otherwise loop until statement-timeout on
     * every request — a per-request DoS. The recursion carries a {@code depth}
     * counter bounded at 64 levels; org trees are shallow so this is free
     * insurance, not a real limit. The {@code tenant_id = :tenantId} predicate
     * stays in BOTH arms.
     */
    @Query(value = """
            WITH RECURSIVE closure AS (
                SELECT d.id, d.parent_id, 1 AS depth FROM departments d
                WHERE d.id IN (:rootIds) AND d.tenant_id = :tenantId
                UNION ALL
                SELECT c.id, c.parent_id, p.depth + 1 FROM departments c
                JOIN closure p ON c.parent_id = p.id
                WHERE c.tenant_id = :tenantId AND p.depth < 64
            )
            SELECT id FROM closure
            """, nativeQuery = true)
    List<UUID> findSubtreeIds(@Param("rootIds") Collection<UUID> rootIds,
                              @Param("tenantId") UUID tenantId);

    /**
     * Count of the given department ids that belong to the tenant — used by the
     * department-scope assign API to validate that every requested department is
     * owned by the active tenant before persisting scope rows.
     */
    @Query(value = """
            SELECT count(*) FROM departments d
            WHERE d.id IN (:ids) AND d.tenant_id = :tenantId
            """, nativeQuery = true)
    long countByIdInAndTenantId(@Param("ids") Collection<UUID> ids,
                                @Param("tenantId") UUID tenantId);
}

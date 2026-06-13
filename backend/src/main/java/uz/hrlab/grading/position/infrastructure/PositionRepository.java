package uz.hrlab.grading.position.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;
import uz.hrlab.grading.position.domain.PositionStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PositionRepository
        extends TenantAwareRepository<PositionJpaEntity, UUID> {

    boolean existsByTenantIdAndProjectIdAndCode(UUID tenantId, UUID projectId, String code);

    /**
     * Tenant- + project-scoped lookup by external code (the {@code code}
     * column populated from the import {@code external_id}). Used by the
     * JOB_PROFILE_V1 importer to resolve {@code position_external_id} → a
     * Position. Cross-tenant safety: a code that exists in another tenant /
     * project cannot resolve through this scoped query.
     */
    Optional<PositionJpaEntity> findByTenantIdAndProjectIdAndCode(
            UUID tenantId, UUID projectId, String code);

    /**
     * Tenant-scoped paged listing with optional filters
     * (departmentId, status, jobFamily) — null filters are ignored.
     */
    @Query("""
            SELECT p FROM PositionJpaEntity p
            WHERE p.tenantId = :tenantId
              AND p.projectId = :projectId
              AND (:departmentId IS NULL OR p.departmentId = :departmentId)
              AND (:status IS NULL OR p.status = :status)
              AND (:jobFamily IS NULL OR p.jobFamily = :jobFamily)
            """)
    Page<PositionJpaEntity> search(@Param("tenantId") UUID tenantId,
                                   @Param("projectId") UUID projectId,
                                   @Param("departmentId") UUID departmentId,
                                   @Param("status") PositionStatus status,
                                   @Param("jobFamily") String jobFamily,
                                   Pageable pageable);

    /**
     * E4-S2 — department-scoped variant of {@link #search}. Same tenant /
     * project / status / jobFamily predicates, but ALSO confines the result to
     * positions whose {@code department_id} is in {@code scopeDepartmentIds}.
     * The predicate {@code p.departmentId IN (:scope)} ALSO drives the JPA
     * count query, so the total / pagination reflect only visible rows (no
     * count or existence leak — security-blueprint §11).
     *
     * <p>Callers MUST NOT invoke this with an empty {@code scopeDepartmentIds}
     * collection (JPQL {@code IN ()} is provider-dependent); the query layer
     * short-circuits an empty department scope to an empty page (fail-closed)
     * before reaching the DB.
     */
    @Query("""
            SELECT p FROM PositionJpaEntity p
            WHERE p.tenantId = :tenantId
              AND p.projectId = :projectId
              AND p.departmentId IN (:scope)
              AND (:departmentId IS NULL OR p.departmentId = :departmentId)
              AND (:status IS NULL OR p.status = :status)
              AND (:jobFamily IS NULL OR p.jobFamily = :jobFamily)
            """)
    Page<PositionJpaEntity> searchInDepartments(@Param("tenantId") UUID tenantId,
                                                @Param("projectId") UUID projectId,
                                                @Param("departmentId") UUID departmentId,
                                                @Param("status") PositionStatus status,
                                                @Param("jobFamily") String jobFamily,
                                                @Param("scope") Collection<UUID> scopeDepartmentIds,
                                                Pageable pageable);

    /**
     * Defect-1 BE — server-authoritative DIRECT position counts grouped by
     * department. ONE grouped query (no N+1, no per-department round-trip) over
     * every {@code ACTIVE} position in the tenant + project, returning one
     * {@code (department_id, count)} row per department that has at least one
     * active position. Departments with zero active positions are simply absent
     * from the result (the caller defaults them to 0). The subtree roll-up is
     * computed in Java by {@code FindDepartmentQuery.positionCounts} over the
     * already-loaded department tree — this query supplies only the leaf-level
     * direct counts, never a recursive descent.
     */
    @Query("""
            SELECT p.departmentId AS departmentId, COUNT(p.id) AS count
            FROM PositionJpaEntity p
            WHERE p.tenantId = :tenantId
              AND p.projectId = :projectId
              AND p.status = uz.hrlab.grading.position.domain.PositionStatus.ACTIVE
            GROUP BY p.departmentId
            """)
    List<DeptCountProjection> countActiveByDepartment(@Param("tenantId") UUID tenantId,
                                                      @Param("projectId") UUID projectId);

    /** Projection for {@link #countActiveByDepartment} — direct active count per department. */
    interface DeptCountProjection {
        UUID getDepartmentId();
        long getCount();
    }
}

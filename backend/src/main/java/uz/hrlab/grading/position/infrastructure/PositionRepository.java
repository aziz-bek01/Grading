package uz.hrlab.grading.position.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;
import uz.hrlab.grading.position.domain.PositionStatus;

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
}

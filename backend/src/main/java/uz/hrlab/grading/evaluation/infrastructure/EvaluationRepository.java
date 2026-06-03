package uz.hrlab.grading.evaluation.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Evaluation repository — tenant-aware. */
public interface EvaluationRepository
        extends TenantAwareRepository<EvaluationJpaEntity, UUID> {

    Page<EvaluationJpaEntity> findAllByTenantIdAndProjectId(
            UUID tenantId, UUID projectId, Pageable pageable);

    Page<EvaluationJpaEntity> findAllByTenantIdAndProjectIdAndStatus(
            UUID tenantId, UUID projectId, EvaluationStatus status, Pageable pageable);

    Page<EvaluationJpaEntity> findAllByTenantIdAndPositionId(
            UUID tenantId, UUID positionId, Pageable pageable);

    Page<EvaluationJpaEntity> findAllByTenantIdAndEvaluatorUserId(
            UUID tenantId, UUID evaluatorUserId, Pageable pageable);

    List<EvaluationJpaEntity> findAllByTenantIdAndPositionIdAndMethodologyVersionId(
            UUID tenantId, UUID positionId, UUID methodologyVersionId);

    Optional<EvaluationJpaEntity> findFirstByTenantIdAndPositionIdAndMethodologyVersionIdAndStatusNot(
            UUID tenantId, UUID positionId, UUID methodologyVersionId, EvaluationStatus excludedStatus);

    boolean existsByTenantIdAndPositionIdAndMethodologyVersionIdAndStatusNot(
            UUID tenantId, UUID positionId, UUID methodologyVersionId, EvaluationStatus excludedStatus);

    /**
     * Backing query for the "Excel K-sheet" UX (groupBy=factor). Returns one
     * evaluation per row scoped to {@code projectId} + optional
     * {@code status} / {@code departmentId} filters. The factor-scoped score
     * itself (level + raw value + comment) is loaded separately by
     * {@code EvaluationScoreRepository.findByTenantIdAndEvaluationIdAndFactorId}
     * to keep this query small and JPA-friendly (no scalar projection).
     *
     * <p>Tenant scoping is enforced by the {@code :tenantId} bind parameter
     * (defense in depth — TenantContext provides the value; never trust input).
     */
    @Query("""
           SELECT e FROM EvaluationJpaEntity e
           WHERE e.tenantId = :tenantId
             AND e.projectId = :projectId
             AND (:status IS NULL OR e.status = :status)
             AND (:departmentId IS NULL OR e.positionId IN (
                   SELECT p.id FROM uz.hrlab.grading.position.infrastructure.PositionJpaEntity p
                   WHERE p.tenantId = :tenantId AND p.departmentId = :departmentId
             ))
           """)
    Page<EvaluationJpaEntity> findForFactorGrid(
            @Param("tenantId") UUID tenantId,
            @Param("projectId") UUID projectId,
            @Param("status") EvaluationStatus status,
            @Param("departmentId") UUID departmentId,
            Pageable pageable);
}

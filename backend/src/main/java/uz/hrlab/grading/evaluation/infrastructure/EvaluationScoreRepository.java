package uz.hrlab.grading.evaluation.infrastructure;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** EvaluationScore repository — tenant-aware. */
public interface EvaluationScoreRepository
        extends TenantAwareRepository<EvaluationScoreJpaEntity, UUID> {

    List<EvaluationScoreJpaEntity> findAllByTenantIdAndEvaluationId(
            UUID tenantId, UUID evaluationId);

    /**
     * PERF (P1) — batch variant of {@link #findAllByTenantIdAndEvaluationId}
     * for grid/report assembly: load every score of a page of evaluations in
     * ONE round-trip instead of one query per evaluation (kills the N+1).
     * Stays tenant-scoped — {@code tenant_id} is always pinned, so an id that
     * belongs to another tenant simply contributes no rows (no widening).
     */
    List<EvaluationScoreJpaEntity> findAllByTenantIdAndEvaluationIdIn(
            UUID tenantId, Collection<UUID> evaluationIds);

    /**
     * PERF (P1) — grouped filled-score count per evaluation in ONE query,
     * replacing the per-evaluation {@code findAllByTenantIdAndEvaluationId(..).size()}
     * round-trips. Returns one {@code (evaluationId, count)} row per evaluation
     * that has at least one score; evaluations with zero scores are absent
     * (the caller defaults them to 0). Tenant-scoped — {@code tenant_id} is
     * pinned in BOTH the filter and the grouping, so no cross-tenant leak.
     */
    @Query("""
            SELECT s.evaluationId AS evaluationId, COUNT(s.id) AS count
            FROM EvaluationScoreJpaEntity s
            WHERE s.tenantId = :tenantId
              AND s.evaluationId IN (:evaluationIds)
            GROUP BY s.evaluationId
            """)
    List<EvalScoreCountProjection> countByTenantIdAndEvaluationIdIn(
            @Param("tenantId") UUID tenantId,
            @Param("evaluationIds") Collection<UUID> evaluationIds);

    /** Projection for {@link #countByTenantIdAndEvaluationIdIn} — filled-score count per evaluation. */
    interface EvalScoreCountProjection {
        UUID getEvaluationId();
        long getCount();
    }

    Optional<EvaluationScoreJpaEntity> findByTenantIdAndEvaluationIdAndFactorId(
            UUID tenantId, UUID evaluationId, UUID factorId);

    /** BE-4 — any score row references this factor (tenant-scoped). */
    boolean existsByTenantIdAndFactorId(UUID tenantId, UUID factorId);

    /** BE-4 — any score row references this factor level (tenant-scoped). */
    boolean existsByTenantIdAndFactorLevelId(UUID tenantId, UUID factorLevelId);

    void delete(EvaluationScoreJpaEntity entity);
}

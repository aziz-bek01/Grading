package uz.hrlab.grading.evaluation.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;

import java.util.Collection;
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
     * Tenant-scoped hard delete (Item 1, BE-2). {@link TenantAwareRepository}
     * intentionally hides the BOLA-prone single-arg {@code deleteById(id)}; this
     * derived deleter keeps the tenant filter in the predicate so a row from
     * another tenant can never be removed. Returns the number of rows deleted so
     * the caller can distinguish a no-op (0) — though the use case always loads +
     * tenant-checks the row first via {@link EvaluationContextLoader}.
     *
     * <p>Caller MUST delete dependent {@code evaluation_scores} rows first to
     * avoid FK orphans (the use case does so via {@code EvaluationScoreRepository}).
     */
    long deleteByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Backing query for the "Excel K-sheet" UX (groupBy=factor). Returns one
     * evaluation per row scoped to {@code projectId} + the SELECTED
     * {@code methodologyVersionId} + optional {@code status} /
     * {@code departmentId} filters. The factor-scoped score itself (level + raw
     * value + comment) is loaded separately by
     * {@code EvaluationScoreRepository.findByTenantIdAndEvaluationIdAndFactorId}
     * to keep this query small and JPA-friendly (no scalar projection).
     *
     * <p>METHODOLOGY-VERSION SCOPING (defect fix): the K-sheet is a single
     * methodology version (the version the requested factor belongs to). Without
     * the {@code e.methodologyVersionId = :methodologyVersionId} predicate the
     * grid mixed in evaluations of OTHER methodologies — letting a user score an
     * HR-Lab (12-factor) evaluation against an 8-factor-methodology factor. The
     * version is derived server-side from the requested factorId (a factor
     * belongs to exactly one version), never trusted from the client.
     *
     * <p>Tenant scoping is enforced by the {@code :tenantId} bind parameter
     * (defense in depth — TenantContext provides the value; never trust input).
     */
    @Query("""
           SELECT e FROM EvaluationJpaEntity e
           WHERE e.tenantId = :tenantId
             AND e.projectId = :projectId
             AND e.methodologyVersionId = :methodologyVersionId
             AND (:status IS NULL OR e.status = :status)
             AND (:departmentId IS NULL OR e.positionId IN (
                   SELECT p.id FROM uz.hrlab.grading.position.infrastructure.PositionJpaEntity p
                   WHERE p.tenantId = :tenantId AND p.departmentId = :departmentId
             ))
           """)
    Page<EvaluationJpaEntity> findForFactorGrid(
            @Param("tenantId") UUID tenantId,
            @Param("projectId") UUID projectId,
            @Param("methodologyVersionId") UUID methodologyVersionId,
            @Param("status") EvaluationStatus status,
            @Param("departmentId") UUID departmentId,
            Pageable pageable);

    /**
     * E4-S2 — department-scoped variant of the general {@code list} read.
     * Evaluations carry no department of their own; they inherit it from their
     * Position. This finder confines the result to evaluations whose POSITION
     * lives in a department within {@code scopeDepartmentIds}, via a tenant-
     * scoped subquery. All other filters ({@code projectId}, {@code positionId},
     * {@code evaluatorUserId}, {@code status}) are optional (null ⇒ ignored),
     * mirroring the unfiltered branches in {@code EvaluationQueries.list}.
     *
     * <p>The {@code IN (:scope)} predicate ALSO drives the JPA count query, so
     * total / pagination reflect only visible rows (no count leak). Callers
     * MUST NOT pass an empty {@code scopeDepartmentIds}; the query layer short-
     * circuits an empty scope to an empty page (fail-closed) before the DB.
     */
    @Query("""
           SELECT e FROM EvaluationJpaEntity e
           WHERE e.tenantId = :tenantId
             AND (:projectId IS NULL OR e.projectId = :projectId)
             AND (:positionId IS NULL OR e.positionId = :positionId)
             AND (:evaluatorUserId IS NULL OR e.evaluatorUserId = :evaluatorUserId)
             AND (:status IS NULL OR e.status = :status)
             AND e.positionId IN (
                   SELECT p.id FROM uz.hrlab.grading.position.infrastructure.PositionJpaEntity p
                   WHERE p.tenantId = :tenantId AND p.departmentId IN (:scope)
             )
           """)
    Page<EvaluationJpaEntity> findInDepartments(
            @Param("tenantId") UUID tenantId,
            @Param("projectId") UUID projectId,
            @Param("positionId") UUID positionId,
            @Param("evaluatorUserId") UUID evaluatorUserId,
            @Param("status") EvaluationStatus status,
            @Param("scope") Collection<UUID> scopeDepartmentIds,
            Pageable pageable);

    /**
     * E4-S2 — department-scoped variant of {@link #findForFactorGrid}. Adds the
     * caller's department-subtree confinement on top of the existing project /
     * status / optional {@code departmentId} filters. The {@code departmentId}
     * filter and the {@code scope} confinement combine: a scoped caller asking
     * for a department outside their subtree gets zero rows.
     *
     * <p>Same empty-scope contract as {@link #findInDepartments}: callers short-
     * circuit an empty scope to an empty page before invoking this finder.
     *
     * <p>Also methodology-version-scoped (defect fix) — see
     * {@link #findForFactorGrid} for the rationale; the version is derived
     * server-side from the requested factorId.
     */
    @Query("""
           SELECT e FROM EvaluationJpaEntity e
           WHERE e.tenantId = :tenantId
             AND e.projectId = :projectId
             AND e.methodologyVersionId = :methodologyVersionId
             AND (:status IS NULL OR e.status = :status)
             AND e.positionId IN (
                   SELECT p.id FROM uz.hrlab.grading.position.infrastructure.PositionJpaEntity p
                   WHERE p.tenantId = :tenantId
                     AND p.departmentId IN (:scope)
                     AND (:departmentId IS NULL OR p.departmentId = :departmentId)
             )
           """)
    Page<EvaluationJpaEntity> findForFactorGridInDepartments(
            @Param("tenantId") UUID tenantId,
            @Param("projectId") UUID projectId,
            @Param("methodologyVersionId") UUID methodologyVersionId,
            @Param("status") EvaluationStatus status,
            @Param("departmentId") UUID departmentId,
            @Param("scope") Collection<UUID> scopeDepartmentIds,
            Pageable pageable);
}

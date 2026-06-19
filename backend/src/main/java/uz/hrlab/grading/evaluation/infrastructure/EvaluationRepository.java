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

    /**
     * "My evaluations" inbox (self-scoped) — every sheet whose evaluator IS the
     * caller, ordered newest-updated first. Tenant + evaluator are BOTH pinned in
     * the predicate, so this can NEVER return another user's or another tenant's
     * row. Explicit ownership of the {@code Evaluation} (evaluator_user_id) IS the
     * authorization here, so this read deliberately does NOT route through the
     * department-scope fail-closed filter (a committee member with no
     * user_department_scopes row must still see their own assigned sheets).
     */
    List<EvaluationJpaEntity> findAllByTenantIdAndEvaluatorUserIdOrderByUpdatedAtDesc(
            UUID tenantId, UUID evaluatorUserId);

    /**
     * "My evaluations" inbox (self-scoped) EXCLUDING sheets whose methodology
     * version belongs to an ARCHIVED methodology container. Same self-scoping
     * contract as {@link #findAllByTenantIdAndEvaluatorUserIdOrderByUpdatedAtDesc}
     * (tenant + evaluator BOTH pinned — never another user's / tenant's row), but
     * the version-in-active-container subquery drops rows of methodologies that
     * have since been archived so the inbox does not deep-link a scorer into a
     * retired methodology.
     *
     * <p>SCOPE: this active-methodology filter is for the SELF inbox
     * ({@code EvaluationQueries.listMine}) ONLY. The project-level evaluation list
     * (managers) must NOT inherit it.
     */
    @Query("""
           SELECT e FROM EvaluationJpaEntity e
           WHERE e.tenantId = :tenantId
             AND e.evaluatorUserId = :evaluatorUserId
             AND e.methodologyVersionId IN (
                   SELECT v.id FROM uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity v
                   WHERE v.tenantId = :tenantId
                     AND v.methodologyId IN (
                           SELECT m.id FROM uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity m
                           WHERE m.tenantId = :tenantId
                             AND m.status = uz.hrlab.grading.methodology.domain.MethodologyStatus.ACTIVE
                     )
             )
           ORDER BY e.updatedAt DESC
           """)
    List<EvaluationJpaEntity> findActiveMethodologyEvaluationsByEvaluator(
            @Param("tenantId") UUID tenantId,
            @Param("evaluatorUserId") UUID evaluatorUserId);

    /**
     * PART B — distinct methodology-version ids the caller has at least one OWN,
     * non-ARCHIVED evaluation under, within a project. Drives the evaluator-scoped
     * methodology list ({@code GET /api/v1/methodologies/my}). Tenant + project +
     * evaluator are all pinned in the predicate; ARCHIVED sheets are excluded so a
     * cancelled assignment does not surface its methodology.
     */
    @Query("""
           SELECT DISTINCT e.methodologyVersionId FROM EvaluationJpaEntity e
           WHERE e.tenantId = :tenantId
             AND e.projectId = :projectId
             AND e.evaluatorUserId = :evaluatorUserId
             AND e.status <> :excludedStatus
           """)
    List<UUID> findDistinctMethodologyVersionIdsByEvaluatorInProject(
            @Param("tenantId") UUID tenantId,
            @Param("projectId") UUID projectId,
            @Param("evaluatorUserId") UUID evaluatorUserId,
            @Param("excludedStatus") EvaluationStatus excludedStatus);

    /**
     * PART D — in-progress (not-yet-submitted) evaluation count for a set of
     * methodology versions (the versions of one methodology container). Backs the
     * deactivate/archive confirmation dialog. Tenant-scoped; statuses are the
     * pre-submission set (DRAFT / INCOMPLETE / COMPLETE — see
     * {@code EvaluationStatus.isPreSubmission}).
     */
    long countByTenantIdAndMethodologyVersionIdInAndStatusIn(
            UUID tenantId, Collection<UUID> methodologyVersionIds,
            Collection<EvaluationStatus> statuses);

    List<EvaluationJpaEntity> findAllByTenantIdAndPositionIdAndMethodologyVersionId(
            UUID tenantId, UUID positionId, UUID methodologyVersionId);

    Optional<EvaluationJpaEntity> findFirstByTenantIdAndPositionIdAndMethodologyVersionIdAndStatusNot(
            UUID tenantId, UUID positionId, UUID methodologyVersionId, EvaluationStatus excludedStatus);

    boolean existsByTenantIdAndPositionIdAndMethodologyVersionIdAndStatusNot(
            UUID tenantId, UUID positionId, UUID methodologyVersionId, EvaluationStatus excludedStatus);

    /**
     * BE-5 — blast radius for the approved-edit umbrella audit: count of
     * evaluations pinned to {@code methodologyVersionId} whose status is NOT the
     * excluded one (caller passes {@code ARCHIVED}). Tenant-scoped.
     */
    long countByTenantIdAndMethodologyVersionIdAndStatusNot(
            UUID tenantId, UUID methodologyVersionId, EvaluationStatus excludedStatus);

    /**
     * MVP2 multi-evaluator — all per-evaluator sheets belonging to a panel.
     * Tenant-scoped (defense in depth). Drives the averaging input load
     * (ComputePanelAverageUseCase) and the completion watcher.
     */
    List<EvaluationJpaEntity> findAllByTenantIdAndPanelId(UUID tenantId, UUID panelId);

    /**
     * MVP2 multi-evaluator — guard "one active evaluation per (panel, evaluator)"
     * (the relaxed unique index replaces the old one-per-position rule). The
     * unique index is the source of truth; this is the friendly pre-check.
     */
    boolean existsByTenantIdAndPanelIdAndEvaluatorUserIdAndStatusNot(
            UUID tenantId, UUID panelId, UUID evaluatorUserId, EvaluationStatus excludedStatus);

    /**
     * Blind-scoring peer test (GRID): does the caller hold at least one of their
     * OWN evaluations within the K-sheet's (tenant, project, methodologyVersionId)
     * scope? If so they are a PANEL MEMBER/evaluator in this grid's scope and must
     * be treated as a peer (blind to other members), even when they also hold an
     * HRLab oversight role. A pure overseer (no own evaluation in scope) returns
     * false and keeps the calibration bypass. Tenant-scoped; any status counts
     * (membership, not progress, is the question).
     */
    boolean existsByTenantIdAndProjectIdAndMethodologyVersionIdAndEvaluatorUserId(
            UUID tenantId, UUID projectId, UUID methodologyVersionId, UUID evaluatorUserId);

    /**
     * Blind-scoring peer test (SINGLE SHEET): does the caller hold at least one of
     * their OWN evaluations in the TARGET sheet's panel? If so they are a member of
     * that panel and must be blind to a co-evaluator's sheet, even when they also
     * hold an HRLab oversight role. A pure overseer (no own evaluation in the panel)
     * returns false and keeps the per-sheet calibration bypass. Tenant-scoped.
     */
    boolean existsByTenantIdAndPanelIdAndEvaluatorUserId(
            UUID tenantId, UUID panelId, UUID evaluatorUserId);

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
             AND e.panelId IS NOT NULL
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
     * BE-11 bias-isolation — K-sheet grid confined to ONE evaluator's own rows.
     * When {@code ownEvaluatorUserId} is non-null, only that evaluator's
     * evaluations are returned (the blind-rule predicate
     * {@code AND evaluator_user_id = :ownEvaluatorUserId}). The confinement also
     * drives the JPA count, so pagination reflects only the caller's visible rows
     * (no count leak). When the caller holds CAMPAIGN_RESULTS_VIEW the query
     * layer passes a non-confining path (see {@link #findForFactorGrid}).
     */
    @Query("""
           SELECT e FROM EvaluationJpaEntity e
           WHERE e.tenantId = :tenantId
             AND e.projectId = :projectId
             AND e.methodologyVersionId = :methodologyVersionId
             AND e.panelId IS NOT NULL
             AND e.evaluatorUserId = :ownEvaluatorUserId
             AND (:status IS NULL OR e.status = :status)
             AND (:departmentId IS NULL OR e.positionId IN (
                   SELECT p.id FROM uz.hrlab.grading.position.infrastructure.PositionJpaEntity p
                   WHERE p.tenantId = :tenantId AND p.departmentId = :departmentId
             ))
           """)
    Page<EvaluationJpaEntity> findForFactorGridOwnOnly(
            @Param("tenantId") UUID tenantId,
            @Param("projectId") UUID projectId,
            @Param("methodologyVersionId") UUID methodologyVersionId,
            @Param("status") EvaluationStatus status,
            @Param("departmentId") UUID departmentId,
            @Param("ownEvaluatorUserId") UUID ownEvaluatorUserId,
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
             AND e.panelId IS NOT NULL
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

    /**
     * BE-11 bias-isolation — department-scoped K-sheet grid ALSO confined to the
     * caller's own evaluations ({@code AND evaluator_user_id =
     * :ownEvaluatorUserId}). Combines the existing department-subtree confinement
     * with the blind-rule own-only predicate so a scoped, non-result-viewer
     * caller sees only their own rows within their subtree.
     */
    @Query("""
           SELECT e FROM EvaluationJpaEntity e
           WHERE e.tenantId = :tenantId
             AND e.projectId = :projectId
             AND e.methodologyVersionId = :methodologyVersionId
             AND e.panelId IS NOT NULL
             AND e.evaluatorUserId = :ownEvaluatorUserId
             AND (:status IS NULL OR e.status = :status)
             AND e.positionId IN (
                   SELECT p.id FROM uz.hrlab.grading.position.infrastructure.PositionJpaEntity p
                   WHERE p.tenantId = :tenantId
                     AND p.departmentId IN (:scope)
                     AND (:departmentId IS NULL OR p.departmentId = :departmentId)
             )
           """)
    Page<EvaluationJpaEntity> findForFactorGridInDepartmentsOwnOnly(
            @Param("tenantId") UUID tenantId,
            @Param("projectId") UUID projectId,
            @Param("methodologyVersionId") UUID methodologyVersionId,
            @Param("status") EvaluationStatus status,
            @Param("departmentId") UUID departmentId,
            @Param("scope") Collection<UUID> scopeDepartmentIds,
            @Param("ownEvaluatorUserId") UUID ownEvaluatorUserId,
            Pageable pageable);
}

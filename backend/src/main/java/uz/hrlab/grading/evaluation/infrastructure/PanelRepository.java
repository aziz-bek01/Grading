package uz.hrlab.grading.evaluation.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Evaluation panel repository — tenant-aware (BE-2). Never exposes single-arg findById (BOLA). */
public interface PanelRepository
        extends TenantAwareRepository<EvaluationPanelJpaEntity, UUID> {

    Page<EvaluationPanelJpaEntity> findAllByTenantIdAndProjectId(
            UUID tenantId, UUID projectId, Pageable pageable);

    /**
     * BE-19 — batch by-id resolution (tenant-scoped, no N+1). Resolves a PAGE of
     * distinct panel ids in ONE round-trip so the report panel-averages build stops
     * issuing a {@code findByIdAndTenantId} per panel in a loop. Tenant is pinned,
     * so a foreign id contributes no row (never the BOLA-prone single-arg findById).
     */
    List<EvaluationPanelJpaEntity> findAllByTenantIdAndIdIn(
            UUID tenantId, Collection<UUID> ids);

    Page<EvaluationPanelJpaEntity> findAllByTenantIdAndProjectIdAndPositionId(
            UUID tenantId, UUID projectId, UUID positionId, Pageable pageable);

    Page<EvaluationPanelJpaEntity> findAllByTenantIdAndPositionId(
            UUID tenantId, UUID positionId, Pageable pageable);

    /**
     * The one-active-panel guard: a panel that is NOT archived for a given
     * (position, methodology_version). The partial unique index
     * {@code uq_panels_active_per_position_version} is the source of truth; this
     * is the friendly app-layer pre-check.
     */
    Optional<EvaluationPanelJpaEntity>
        findFirstByTenantIdAndPositionIdAndMethodologyVersionIdAndStatusNot(
            UUID tenantId, UUID positionId, UUID methodologyVersionId,
            EvaluationPanelStatus excludedStatus);

    boolean existsByTenantIdAndPositionIdAndMethodologyVersionIdAndStatusNot(
            UUID tenantId, UUID positionId, UUID methodologyVersionId,
            EvaluationPanelStatus excludedStatus);

    List<EvaluationPanelJpaEntity> findAllByTenantIdAndProjectIdAndStatus(
            UUID tenantId, UUID projectId, EvaluationPanelStatus status);

    /**
     * All panels in one status for a tenant (project-agnostic). Tenant-scoped
     * (defense-in-depth on top of forced RLS). Backs the one-time
     * {@code BackfillPanelApprovalsMigration} sweep that opens the missing CEO
     * approval for every {@code SUBMITTED} panel lacking one.
     */
    List<EvaluationPanelJpaEntity> findAllByTenantIdAndStatus(
            UUID tenantId, EvaluationPanelStatus status);

    /**
     * REQ-CEO — tenant-wide, project-agnostic page of panels filtered to a SET of
     * statuses. Backs the {@code GET /api/v1/panels?status=...} org-view filter so
     * the CEO can pull e.g. only SUBMITTED panels awaiting sign-off across every
     * department in one page. Tenant-scoped in the predicate (defense-in-depth on
     * top of forced RLS); reuses the same {@link EvaluationPanelJpaEntity} -> batched
     * {@code PanelQueries.list} mapping (no duplicate mapping path).
     */
    Page<EvaluationPanelJpaEntity> findAllByTenantIdAndStatusIn(
            UUID tenantId, java.util.Collection<EvaluationPanelStatus> statuses, Pageable pageable);

    /**
     * EPIC-001 — department-scoped variant of the org-wide {@code list} read
     * (object-level ABAC). A panel carries no department of its own; it inherits
     * the department of its Position. This finder confines the result to panels
     * whose POSITION lives in a department within {@code scopeDepartmentIds}, via
     * a tenant-scoped subquery — the SAME shape as
     * {@code EvaluationRepository.findInDepartments} (an evaluation also inherits
     * its department from its position). The optional {@code projectId} /
     * {@code positionId} filters are null-safe ({@code null} ⇒ ignored), mirroring
     * the unfiltered branches in {@code PanelQueries.list}.
     *
     * <p>Status is passed as a (always non-empty) collection: the caller supplies
     * the requested statuses for the tenant-wide status pull, or the full status
     * set (a no-op filter) for the other branches — so this ONE finder is the
     * single source of the scoped query without re-listing every branch. An empty
     * status collection is never passed (an {@code IN ()} is not portable SQL).
     *
     * <p>The {@code IN (:scope)} predicate ALSO drives the JPA count query, so the
     * page total reflects only visible rows (no count leak). Callers MUST NOT pass
     * an empty {@code scopeDepartmentIds}; the query layer short-circuits an empty
     * scope to an empty page (fail-closed) before ever reaching the DB.
     */
    @Query("""
           SELECT ep FROM EvaluationPanelJpaEntity ep
           WHERE ep.tenantId = :tenantId
             AND (:projectId IS NULL OR ep.projectId = :projectId)
             AND (:positionId IS NULL OR ep.positionId = :positionId)
             AND ep.status IN (:statuses)
             AND ep.positionId IN (
                   SELECT p.id FROM uz.hrlab.grading.position.infrastructure.PositionJpaEntity p
                   WHERE p.tenantId = :tenantId AND p.departmentId IN (:scope)
             )
           """)
    Page<EvaluationPanelJpaEntity> findInDepartments(
            @Param("tenantId") UUID tenantId,
            @Param("projectId") UUID projectId,
            @Param("positionId") UUID positionId,
            @Param("statuses") Collection<EvaluationPanelStatus> statuses,
            @Param("scope") Collection<UUID> scopeDepartmentIds,
            Pageable pageable);

    /**
     * Tenant-scoped hard delete (Defect-2 BE). Mirrors
     * {@code EvaluationRepository.deleteByIdAndTenantId}: {@link TenantAwareRepository}
     * intentionally hides the BOLA-prone single-arg {@code deleteById(id)}; this
     * derived deleter keeps the tenant filter in the predicate so a row from
     * another tenant can never be removed. Returns the number of rows deleted so
     * the caller can distinguish a no-op (0) — though the use case always loads +
     * tenant-checks the row first via {@link uz.hrlab.grading.evaluation.application.PanelLoader}.
     *
     * <p>Caller MUST remove dependent {@code panel_assignments} rows first to keep
     * the delete deterministic (the ON DELETE CASCADE FK also covers this, but the
     * use case pre-deletes — mirroring how {@code DeleteEvaluationUseCase} removes
     * dependent score rows before the parent).
     */
    long deleteByIdAndTenantId(UUID id, UUID tenantId);
}

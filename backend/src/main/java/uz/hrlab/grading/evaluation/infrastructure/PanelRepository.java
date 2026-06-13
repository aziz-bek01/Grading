package uz.hrlab.grading.evaluation.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Evaluation panel repository — tenant-aware (BE-2). Never exposes single-arg findById (BOLA). */
public interface PanelRepository
        extends TenantAwareRepository<EvaluationPanelJpaEntity, UUID> {

    Page<EvaluationPanelJpaEntity> findAllByTenantIdAndProjectId(
            UUID tenantId, UUID projectId, Pageable pageable);

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

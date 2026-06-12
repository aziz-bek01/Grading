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
}

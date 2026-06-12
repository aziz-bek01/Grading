package uz.hrlab.grading.evaluation.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.domain.EvaluatorFactorScores;
import uz.hrlab.grading.evaluation.domain.PanelAverageResult;
import uz.hrlab.grading.evaluation.domain.PanelAveragingService;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelFactorAverageJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelFactorAverageRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BE-8 — compute and STORE a panel's average (server-only, REQ-AVG-1).
 *
 * <p>Loads contributing evaluations via tenant-scoped finders only, builds the
 * {@link EvaluatorFactorScores} input from each COMPLETED sheet's persisted
 * {@code evaluation_scores} (the engine's already-computed output — REQ-AVG-2,
 * no re-scoring), calls the pure {@link PanelAveragingService}, then writes
 * {@code panel.raw_total_score / displayed_total_score / averaged_at /
 * averaged_by} + {@code panel_factor_averages} rows (evaluator_count = the
 * denominator).
 *
 * <p>Denominator integrity (REQ-AVG-3): only COMPLETED sheets contribute; the
 * count is server-derived, never client-supplied. Idempotent — stale averages
 * are cleared and recomputed deterministically (engine rounding).
 *
 * <p>Invoked internally by the completion watcher (BE-7) and the reopen flow
 * (BE-12). It is NOT a public endpoint; client-supplied totals are impossible.
 */
@Service
public class ComputePanelAverageUseCase {

    private final PanelRepository panels;
    private final EvaluationRepository evaluations;
    private final EvaluationScoreRepository scores;
    private final PanelFactorAverageRepository averages;
    private final PanelAveragingService averaging;
    private final AuditService audit;

    public ComputePanelAverageUseCase(PanelRepository panels,
                                      EvaluationRepository evaluations,
                                      EvaluationScoreRepository scores,
                                      PanelFactorAverageRepository averages,
                                      PanelAveragingService averaging,
                                      AuditService audit) {
        this.panels = panels;
        this.evaluations = evaluations;
        this.scores = scores;
        this.averages = averages;
        this.averaging = averaging;
        this.audit = audit;
    }

    /**
     * @param tenantId    server-derived active tenant
     * @param panelId     the panel to average (already tenant-loaded by caller)
     * @param actorUserId acting user (for averaged_by + audit)
     */
    @Transactional
    public PanelAverageResult compute(UUID tenantId, UUID panelId, UUID actorUserId) {
        EvaluationPanelJpaEntity panel = panels.findByIdAndTenantId(panelId, tenantId)
                .orElseThrow(() -> new ValidationException("PANEL_NOT_FOUND"));

        // COMPLETED contributing sheets only (REQ-AVG-3). ARCHIVED / WITHDRAWN
        // sheets are excluded; an incomplete sheet (DRAFT/INCOMPLETE) does not
        // contribute and would have blocked the AVERAGED transition upstream.
        List<EvaluationJpaEntity> sheets = evaluations
                .findAllByTenantIdAndPanelId(tenantId, panelId).stream()
                .filter(e -> e.getStatus() == EvaluationStatus.COMPLETE)
                .toList();
        if (sheets.isEmpty()) {
            throw new ValidationException(
                    "NO_COMPLETED_SHEETS: panel cannot be averaged with zero completed evaluations");
        }

        List<EvaluatorFactorScores> input = new ArrayList<>(sheets.size());
        for (EvaluationJpaEntity sheet : sheets) {
            Map<UUID, BigDecimal> perFactor = new LinkedHashMap<>();
            for (EvaluationScoreJpaEntity s : scores
                    .findAllByTenantIdAndEvaluationId(tenantId, sheet.getId())) {
                perFactor.put(s.getFactorId(), s.getRawFactorScore());
            }
            input.add(new EvaluatorFactorScores(sheet.getEvaluatorUserId(), perFactor));
        }

        PanelAverageResult result = averaging.average(input);

        // Replace any stale per-factor averages, then persist the fresh set.
        averages.deleteAllByTenantIdAndPanelId(tenantId, panelId);
        for (Map.Entry<UUID, BigDecimal> e : result.avgByFactor().entrySet()) {
            averages.save(new PanelFactorAverageJpaEntity(
                    UUID.randomUUID(), tenantId, panelId, e.getKey(),
                    e.getValue(), result.evaluatorCount()));
        }

        OffsetDateTime now = OffsetDateTime.now();
        panel.setRawTotalScore(result.rawTotal());
        panel.setDisplayedTotalScore(result.displayedTotal());
        panel.setAveragedAt(now);
        panel.setAveragedBy(actorUserId);
        if (panel.getStatus() == EvaluationPanelStatus.AWAITING_EVALUATIONS) {
            panel.setStatus(EvaluationPanelStatus.AVERAGED);
        }
        panels.save(panel);

        // REQ-AUD-3 — record evaluator_count + total only; never per-evaluator comments.
        audit.record(AuditEvent.builder()
                .tenantId(tenantId)
                .projectId(panel.getProjectId())
                .actorUserId(actorUserId)
                .action(AuditAction.EVALUATION_PANEL_AVERAGED)
                .entityType("EvaluationPanel")
                .entityId(panelId)
                .reason("evaluatorCount=" + result.evaluatorCount()
                        + ";rawTotal=" + result.rawTotal())
                .build());
        return result;
    }
}

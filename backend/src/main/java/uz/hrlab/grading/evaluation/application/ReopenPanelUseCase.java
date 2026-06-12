package uz.hrlab.grading.evaluation.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.evaluation.domain.EvaluationPanel;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelFactorAverageRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;

import java.util.UUID;

/**
 * BE-12 — reopen a panel after the CEO requests changes:
 * {@code SUBMITTED/AVERAGED -> AWAITING_EVALUATIONS}.
 *
 * <p>Resets the panel so evaluators can re-score, clears the materialized
 * {@code panel_factor_averages} + the stored totals so a fresh average is
 * required (never silently recompute behind the CEO — REQ-AVG-4). The
 * contributing sheets are NOT touched here; the watcher will re-average once the
 * re-scored sheets reach COMPLETE again. (Untouched evaluators keep their
 * COMPLETE state — their immutability is preserved; re-scoring is an explicit
 * per-evaluator action.)
 *
 * <p>Invoked manually by the approval coupling on a CEO CHANGES_REQUESTED
 * decision for an {@code EVALUATION_PANEL} request (no event bus).
 */
@Service
public class ReopenPanelUseCase {

    private final PanelRepository panels;
    private final PanelFactorAverageRepository averages;
    private final AuditService audit;

    public ReopenPanelUseCase(PanelRepository panels,
                              PanelFactorAverageRepository averages,
                              AuditService audit) {
        this.panels = panels;
        this.averages = averages;
        this.audit = audit;
    }

    @Transactional
    public EvaluationPanel onChangesRequested(UUID tenantId, UUID panelId, UUID actorUserId) {
        EvaluationPanelJpaEntity panel = panels.findByIdAndTenantId(panelId, tenantId)
                .orElse(null);
        if (panel == null) {
            return null; // fail-soft — do not break the approval decision
        }
        EvaluationPanelStatus status = panel.getStatus();
        if (status != EvaluationPanelStatus.SUBMITTED
                && status != EvaluationPanelStatus.AVERAGED) {
            return panel.toDomain(); // nothing to reopen
        }

        panel.setStatus(EvaluationPanelStatus.AWAITING_EVALUATIONS);
        // Clear stored average + per-factor rows so the next completion recomputes.
        panel.setRawTotalScore(null);
        panel.setDisplayedTotalScore(null);
        panel.setAveragedAt(null);
        panel.setAveragedBy(null);
        panels.save(panel);
        averages.deleteAllByTenantIdAndPanelId(tenantId, panelId);

        audit.record(AuditEvent.builder()
                .tenantId(tenantId)
                .projectId(panel.getProjectId())
                .actorUserId(actorUserId)
                .action(AuditAction.EVALUATION_PANEL_REOPENED)
                .entityType("EvaluationPanel")
                .entityId(panelId)
                .reason("reopened from " + status)
                .build());
        return panel.toDomain();
    }
}

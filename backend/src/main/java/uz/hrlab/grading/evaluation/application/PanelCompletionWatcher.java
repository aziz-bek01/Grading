package uz.hrlab.grading.evaluation.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.domain.PanelAssignmentStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelAssignmentJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelAssignmentRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;

import java.util.UUID;

/**
 * BE-7 — panel completion watcher. Invoked (in the SAME transaction) whenever an
 * evaluation's status is recomputed, so the matching panel reacts to its sheets
 * completing.
 *
 * <p>Behaviour:
 * <ol>
 *   <li>Sync the matching {@code panel_assignment} status from the evaluation's
 *       new status (COMPLETE → COMPLETED, otherwise IN_PROGRESS) and emit
 *       {@code EVALUATION_PANEL_EVALUATOR_COMPLETED} on the COMPLETE edge.</li>
 *   <li>When ALL non-WITHDRAWN assignments are COMPLETED — derived via the
 *       single {@link PanelAssignmentStatus#isActive()} / COMPLETED predicate,
 *       never an inlined status list — transition the panel
 *       {@code AWAITING_EVALUATIONS -> AVERAGED} and invoke
 *       {@link ComputePanelAverageUseCase}.</li>
 * </ol>
 *
 * <p>Reuses the existing per-evaluator completeness (the evaluation's own
 * recompute already decided COMPLETE vs INCOMPLETE) — no parallel completeness
 * engine.
 */
@Service
public class PanelCompletionWatcher {

    private static final Logger log = LoggerFactory.getLogger(PanelCompletionWatcher.class);

    private final PanelRepository panels;
    private final PanelAssignmentRepository assignments;
    private final ComputePanelAverageUseCase computeAverage;
    private final SubmitPanelToCeoUseCase submitToCeo;
    private final PanelCompletionChecker completionChecker;
    private final AuditService audit;

    public PanelCompletionWatcher(PanelRepository panels,
                                  PanelAssignmentRepository assignments,
                                  ComputePanelAverageUseCase computeAverage,
                                  SubmitPanelToCeoUseCase submitToCeo,
                                  PanelCompletionChecker completionChecker,
                                  AuditService audit) {
        this.panels = panels;
        this.assignments = assignments;
        this.computeAverage = computeAverage;
        this.submitToCeo = submitToCeo;
        this.completionChecker = completionChecker;
        this.audit = audit;
    }

    /**
     * Called after an evaluation's status was (re)computed. No-op for panelless
     * evaluations and for panels that are not awaiting evaluations.
     *
     * @param evaluation  the just-recomputed evaluation (carries panel_id)
     * @param actorUserId the acting user (for averaged_by + audit)
     */
    public void onEvaluationStatusRecomputed(EvaluationJpaEntity evaluation, UUID actorUserId) {
        UUID panelId = evaluation.getPanelId();
        if (panelId == null) {
            return; // legacy / panelless evaluation
        }
        UUID tenantId = evaluation.getTenantId();
        EvaluationPanelJpaEntity panel = panels.findByIdAndTenantId(panelId, tenantId)
                .orElse(null);
        if (panel == null || panel.getStatus() != EvaluationPanelStatus.AWAITING_EVALUATIONS) {
            return; // panel not in the scoring window (e.g. already averaged/approved)
        }

        PanelAssignmentJpaEntity seat = assignments
                .findByTenantIdAndEvaluationId(tenantId, evaluation.getId())
                .orElse(null);
        if (seat == null || seat.getAssignmentStatus() == PanelAssignmentStatus.WITHDRAWN) {
            return;
        }

        boolean nowComplete = evaluation.getStatus() == EvaluationStatus.COMPLETE;
        PanelAssignmentStatus target = nowComplete
                ? PanelAssignmentStatus.COMPLETED
                : PanelAssignmentStatus.IN_PROGRESS;
        boolean reachedCompleteEdge =
                target == PanelAssignmentStatus.COMPLETED
                        && seat.getAssignmentStatus() != PanelAssignmentStatus.COMPLETED;
        if (seat.getAssignmentStatus() != target) {
            seat.setAssignmentStatus(target);
            assignments.save(seat);
        }
        if (reachedCompleteEdge) {
            audit.record(AuditEvent.builder()
                    .tenantId(tenantId)
                    .projectId(panel.getProjectId())
                    .actorUserId(actorUserId)
                    .action(AuditAction.EVALUATION_PANEL_EVALUATOR_COMPLETED)
                    .entityType("EvaluationPanel")
                    .entityId(panelId)
                    .reason("evaluator=" + seat.getEvaluatorUserId()
                            + ";role=" + seat.getEvaluatorRole())
                    .build());
        }

        if (completionChecker.allActiveComplete(tenantId, panelId)) {
            // AWAITING_EVALUATIONS -> AVERAGED happens inside the compute use case.
            computeAverage.compute(tenantId, panelId, actorUserId);
            // Consolidated panel flow — the moment a panel averages, open the
            // single CEO approval automatically (AVERAGED -> SUBMITTED), so panel
            // members no longer need a manual "submit to CEO" button.
            //
            // Wired HERE (watcher) rather than inside ComputePanelAverageUseCase to
            // avoid a circular bean dependency (compute is the lowest layer, reused
            // by reopen too) and to keep this in the SAME transaction as the last
            // evaluator's submit.
            //
            // Fail-soft + idempotent: averaging already succeeded; a CEO request
            // that already exists is swallowed inside submitSystem
            // (ANOTHER_PENDING_REQUEST_EXISTS) leaving the panel SUBMITTED, and any
            // other non-fatal failure here must NOT roll back / break the
            // evaluator's submit, so it is logged and swallowed.
            try {
                submitToCeo.submitSystem(panelId, actorUserId);
            } catch (RuntimeException autoSubmitFailed) {
                log.warn("Auto-submit of averaged panel {} to CEO failed; panel left AVERAGED, "
                                + "manual submit still available", panelId, autoSubmitFailed);
            }
        }
    }

}

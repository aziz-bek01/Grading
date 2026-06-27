package uz.hrlab.grading.evaluation.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.evaluation.domain.EvaluationPanel;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.domain.PanelAssignmentStatus;
import uz.hrlab.grading.evaluation.domain.PanelStatusTransition;
import uz.hrlab.grading.evaluation.domain.PanelStatusTransitionPolicy;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelAssignmentJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelAssignmentRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;

import java.util.List;
import java.util.UUID;

/**
 * CEO REJECT of an EVALUATION_PANEL approval — DESTRUCTIVE reset of the panel back
 * to the DRAFT "Сбор экспертов" ({@code COLLECTING}) state. The product owner
 * explicitly confirmed (with the "scores will be deleted" consequence) that a
 * reject restarts evaluation from scratch.
 *
 * <p>This is the destructive sibling of {@link ReopenPanelUseCase} (CEO
 * CHANGES_REQUESTED → AWAITING_EVALUATIONS, scores PRESERVED). It is invoked from
 * {@code PanelApprovalOutcomeListener}'s REJECTED branch exactly the way
 * {@code onChangesRequested} is invoked from the CHANGES_REQUESTED branch — a
 * system entrypoint that trusts the already-authorized approval-step decision (no
 * UI permission re-check) and is fail-soft (a missing panel returns {@code null}
 * so the approval decision is never rolled back).
 *
 * <p>What it does on {@code SUBMITTED}:
 * <ol>
 *   <li><b>DELETE</b> every per-evaluator sheet AND its {@code evaluation_scores}
 *       — reusing {@link DeleteEvaluationUseCase#deleteForSystem} (scores-first
 *       cascade + per-sheet {@code EVALUATION_DELETED} audit; no hand-rolled
 *       cascade). The {@code panel_assignments.evaluation_id} FK is
 *       {@code ON DELETE SET NULL} (changelog 034), and we also null it explicitly
 *       below.</li>
 *   <li><b>CLEAR</b> the panel aggregates — delete {@code panel_factor_averages}
 *       and null {@code raw_total_score} / {@code displayed_total_score} /
 *       {@code averaged_at} / {@code averaged_by} — REUSING
 *       {@link ReopenPanelUseCase#clearPanelAggregates}.</li>
 *   <li><b>KEEP</b> the roster {@code panel_assignments} (do NOT delete) but reset
 *       each non-withdrawn seat to {@code ASSIGNED} with {@code evaluation_id =
 *       null}. COLLECTING makes the roster mutable
 *       ({@link EvaluationPanelStatus#isRosterMutable()}) so the manager can
 *       re-add/remove experts and re-lock; resetting the seat is required so the
 *       re-lock recreates a fresh DRAFT sheet (its old sheet is gone) and the
 *       active-roster filter / mandatory-role validation see live seats. WITHDRAWN
 *       seats are left as history.</li>
 *   <li><b>SET</b> status {@code SUBMITTED → COLLECTING} (gated by
 *       {@link PanelStatusTransitionPolicy}).</li>
 *   <li><b>AUDIT</b> {@code EVALUATION_PANEL_RESET_TO_DRAFT} — the surviving record
 *       of the rejection now that the score rows are deleted; the reject REASON is
 *       captured at the approval level ({@code APPROVAL_STEP_REJECTED}) and
 *       referenced here.</li>
 * </ol>
 */
@Service
public class ResetPanelToCollectingUseCase {

    private final PanelRepository panels;
    private final EvaluationRepository evaluations;
    private final PanelAssignmentRepository assignments;
    private final DeleteEvaluationUseCase deleteEvaluation;
    private final ReopenPanelUseCase reopenPanel;
    private final PanelStatusTransitionPolicy transitionPolicy;
    private final AuditService audit;

    public ResetPanelToCollectingUseCase(PanelRepository panels,
                                         EvaluationRepository evaluations,
                                         PanelAssignmentRepository assignments,
                                         DeleteEvaluationUseCase deleteEvaluation,
                                         ReopenPanelUseCase reopenPanel,
                                         PanelStatusTransitionPolicy transitionPolicy,
                                         AuditService audit) {
        this.panels = panels;
        this.evaluations = evaluations;
        this.assignments = assignments;
        this.deleteEvaluation = deleteEvaluation;
        this.reopenPanel = reopenPanel;
        this.transitionPolicy = transitionPolicy;
        this.audit = audit;
    }

    /**
     * Approval-coupling entrypoint (CEO REJECT). Fail-soft — a missing panel
     * returns {@code null} so the approval decision is never broken. A panel not in
     * {@code SUBMITTED} is left unchanged (defensive: the reject can only reach a
     * SUBMITTED panel, but a stale double-fire must not corrupt a re-progressed
     * panel).
     *
     * @param tenantId    server-derived active tenant (from the approval engine)
     * @param panelId     the rejected panel (entity_id of the approval request)
     * @param actorUserId the deciding CEO (audit actor)
     */
    @Transactional
    public EvaluationPanel onRejected(UUID tenantId, UUID panelId, UUID actorUserId) {
        EvaluationPanelJpaEntity panel = panels.findByIdAndTenantId(panelId, tenantId)
                .orElse(null);
        if (panel == null) {
            return null; // fail-soft — do not break the approval decision
        }
        EvaluationPanelStatus from = panel.getStatus();
        if (!transitionPolicy.isAllowed(from, PanelStatusTransition.RESET_TO_COLLECTING)) {
            // Only SUBMITTED resets; any other status is a defensive no-op.
            return panel.toDomain();
        }

        // 1) DELETE every per-evaluator sheet + its scores (reused cascade + audit).
        List<EvaluationJpaEntity> sheets =
                evaluations.findAllByTenantIdAndPanelId(tenantId, panelId);
        for (EvaluationJpaEntity sheet : sheets) {
            deleteEvaluation.deleteForSystem(tenantId, actorUserId, sheet,
                    "panel rejected by CEO — reset to COLLECTING (sheets deleted)");
        }

        // 2) RESET (do NOT delete) the roster seats so the manager can re-pick
        //    experts and re-lock. Null evaluation_id (its sheet is gone) and put the
        //    seat back to ASSIGNED; WITHDRAWN seats stay as history.
        List<PanelAssignmentJpaEntity> roster =
                assignments.findAllByTenantIdAndPanelId(tenantId, panelId);
        for (PanelAssignmentJpaEntity seat : roster) {
            if (seat.getAssignmentStatus() == PanelAssignmentStatus.WITHDRAWN) {
                continue;
            }
            seat.setEvaluationId(null);
            seat.setAssignmentStatus(PanelAssignmentStatus.ASSIGNED);
            assignments.save(seat);
        }

        // 3) CLEAR aggregates (reused helper) + 4) flip to COLLECTING.
        reopenPanel.clearPanelAggregates(tenantId, panel);
        panel.setStatus(EvaluationPanelStatus.COLLECTING);
        panels.save(panel);

        // 5) AUDIT — the surviving record of the rejection (scores are now deleted).
        audit.record(AuditEvent.builder()
                .tenantId(tenantId)
                .projectId(panel.getProjectId())
                .actorUserId(actorUserId)
                .action(AuditAction.EVALUATION_PANEL_RESET_TO_DRAFT)
                .entityType("EvaluationPanel")
                .entityId(panelId)
                .reason("CEO rejected; reset to COLLECTING from " + from
                        + "; deleted " + sheets.size() + " evaluator sheet(s) + scores"
                        + " (reject reason at APPROVAL_STEP_REJECTED)")
                .build());
        return panel.toDomain();
    }
}

package uz.hrlab.grading.evaluation.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.evaluation.domain.Evaluation;
import uz.hrlab.grading.evaluation.domain.EvaluationPanel;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.domain.EvaluatorRole;
import uz.hrlab.grading.evaluation.domain.PanelAssignmentStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelAssignmentJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelAssignmentRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelFactorAverageRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

/**
 * Feature 2 — reopen an APPROVED panel to add an ADDITIONAL expert.
 *
 * <p>Confirmed product decisions encoded here:
 * <ul>
 *   <li>Prior evaluators' scores are PRESERVED and edited IN PLACE — this use
 *       case deletes NO {@code evaluation_scores}; the audit log is the change
 *       history. Sheets are simply un-frozen (LOCKED -&gt; COMPLETE) so a later
 *       {@code UpsertEvaluationScore} re-enables (the score-lock trigger keys off
 *       {@code evaluations.status}; COMPLETE re-opens writes automatically).</li>
 *   <li>The additional expert IS included in the recomputed average.</li>
 *   <li>After re-averaging the panel RE-ENTERS the normal approval workflow
 *       (AWAITING_EVALUATIONS -&gt; … -&gt; submit -&gt; CEO approval).</li>
 *   <li>The prior assigned grade is HELD (not cleared) until re-approval — only
 *       the panel aggregates/totals are cleared so a fresh average is required.</li>
 * </ul>
 *
 * <p><b>Atomicity (single {@code @Transactional}).</b> Un-locking the prior
 * sheets and adding the new DRAFT seat MUST happen in ONE transaction to avoid a
 * completion-watcher race: if the panel were flipped to AWAITING_EVALUATIONS and
 * the prior sheets un-locked but the new seat added in a LATER call, the panel
 * would momentarily show ALL active assignments COMPLETE and
 * {@link PanelCompletionWatcher} could auto-re-average (and re-submit) before the
 * expert ever scores. By creating the new seat's DRAFT (incomplete) evaluation in
 * the SAME tx, {@code PanelCompletionWatcher.allActiveComplete} is false, so the
 * panel correctly stays OPEN and will NOT auto-re-average until the expert
 * finishes — which is why the expert is added atomically at reopen rather than in
 * a separate later call.
 *
 * <p>Authorization mirrors {@link DeletePanelUseCase} / {@link ReopenPanelUseCase}:
 * {@code EVALUATION_PANEL_MANAGE}, the ABAC department write-gate via the panel's
 * position, and a non-trivial reason (min length, like delete). A non-APPROVED
 * panel is rejected with a clear domain error.
 */
@Service
public class ReopenApprovedPanelForExpertUseCase {

    private static final int MIN_REASON_LENGTH = 5;
    /** Stable error code surfaced to the FE for a non-APPROVED reopen attempt. */
    static final String NOT_APPROVED_CODE = "PANEL_NOT_APPROVED_FOR_REOPEN";

    private final PanelLoader loader;
    private final PanelRepository panels;
    private final EvaluationRepository evaluations;
    private final PanelAssignmentRepository assignments;
    private final PanelFactorAverageRepository averages;
    private final CreateEvaluationUseCase createEvaluation;
    private final PanelReopenGuc reopenGuc;
    private final AbacGate abacGate;
    private final AuditService audit;

    public ReopenApprovedPanelForExpertUseCase(PanelLoader loader,
                                               PanelRepository panels,
                                               EvaluationRepository evaluations,
                                               PanelAssignmentRepository assignments,
                                               PanelFactorAverageRepository averages,
                                               CreateEvaluationUseCase createEvaluation,
                                               PanelReopenGuc reopenGuc,
                                               AbacGate abacGate,
                                               AuditService audit) {
        this.loader = loader;
        this.panels = panels;
        this.evaluations = evaluations;
        this.assignments = assignments;
        this.averages = averages;
        this.createEvaluation = createEvaluation;
        this.reopenGuc = reopenGuc;
        this.abacGate = abacGate;
        this.audit = audit;
    }

    @Transactional
    public EvaluationPanel reopen(UUID panelId, UUID additionalEvaluatorUserId, String reason) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.EVALUATION_PANEL_MANAGE);
        if (additionalEvaluatorUserId == null) {
            throw new ValidationException("additional_evaluator_user_id is required");
        }
        if (reason == null || reason.trim().length() < MIN_REASON_LENGTH) {
            throw new ValidationException(
                    "reason is required (min " + MIN_REASON_LENGTH + " chars)");
        }
        EvaluationPanelJpaEntity panel = loader.requirePanel(panelId, ctx.tenantId());
        PositionJpaEntity position = loader.requirePosition(panel, ctx.tenantId());
        abacGate.enforceCanWriteInDepartment(
                ctx, panel.getProjectId(), position.getDepartmentId());

        if (panel.getStatus() != EvaluationPanelStatus.APPROVED) {
            throw new ValidationException(NOT_APPROVED_CODE,
                    "Only an APPROVED panel can be reopened to add an additional expert");
        }

        // No-duplicate-evaluator pre-check (honor the existing assign invariant):
        // an evaluator may not hold two active seats on the same panel.
        PanelAssignmentJpaEntity existing = assignments
                .findByTenantIdAndPanelIdAndEvaluatorUserId(
                        ctx.tenantId(), panelId, additionalEvaluatorUserId)
                .orElse(null);
        if (existing != null
                && existing.getAssignmentStatus() != PanelAssignmentStatus.WITHDRAWN) {
            throw new ValidationException(
                    "EVALUATOR_ALREADY_ASSIGNED: this evaluator is already on the panel");
        }

        // STEP 1 — enable the DB carve-out FIRST so the LOCKED -> COMPLETE
        // transitions below are permitted within THIS tx only (SET LOCAL).
        reopenGuc.enableForCurrentTransaction();

        // STEP 2 — un-freeze prior contributing sheets: LOCKED -> COMPLETE. Their
        // evaluation_scores are intentionally NOT touched (preserved + editable
        // once COMPLETE: the score-lock trigger keys off evaluations.status).
        for (EvaluationJpaEntity sheet : evaluations
                .findAllByTenantIdAndPanelId(ctx.tenantId(), panelId)) {
            if (sheet.getStatus() == EvaluationStatus.LOCKED) {
                sheet.setStatus(EvaluationStatus.COMPLETE);
                sheet.setLockedAt(null);
                sheet.setLockedBy(null);
                evaluations.save(sheet);
            }
        }

        // STEP 3 — add the additional expert IN THE SAME TX: roster seat
        // (ADDITIONAL) + an immediate DRAFT evaluation (reuse
        // CreateEvaluationUseCase exactly as LockRosterUseCase does per seat),
        // then link the seat to its evaluation. Re-adds a previously-WITHDRAWN
        // seat by flipping it back to ASSIGNED (satisfies the partial unique
        // index which excludes WITHDRAWN).
        PanelAssignmentJpaEntity seat = existing;
        if (seat == null) {
            seat = new PanelAssignmentJpaEntity(
                    UUID.randomUUID(), ctx.tenantId(), panelId, additionalEvaluatorUserId,
                    EvaluatorRole.ADDITIONAL, PanelAssignmentStatus.ASSIGNED);
        } else {
            seat.setAssignmentStatus(PanelAssignmentStatus.ASSIGNED);
            seat.setEvaluatorRole(EvaluatorRole.ADDITIONAL);
        }
        Evaluation created = createEvaluation.create(CreateEvaluationCommand.forPanelSeat(
                panel.getPositionId(), panel.getMethodologyVersionId(),
                additionalEvaluatorUserId, panelId, EvaluatorRole.ADDITIONAL));
        seat.setEvaluationId(created.id());
        assignments.save(seat);

        // STEP 4 — reopen the panel: APPROVED -> AWAITING_EVALUATIONS and clear
        // ONLY the aggregates/totals (exactly like ReopenPanelUseCase.applyReopen)
        // so a fresh average is required. The assigned grade is intentionally HELD
        // (not cleared) until re-approval. Because the new seat above is DRAFT
        // (incomplete), PanelCompletionWatcher.allActiveComplete is false, so the
        // panel stays OPEN and will NOT auto-re-average until the expert finishes.
        panel.setStatus(EvaluationPanelStatus.AWAITING_EVALUATIONS);
        panel.setRawTotalScore(null);
        panel.setDisplayedTotalScore(null);
        panel.setAveragedAt(null);
        panel.setAveragedBy(null);
        panels.save(panel);
        averages.deleteAllByTenantIdAndPanelId(ctx.tenantId(), panelId);

        audit.record(AuditEvent.builder(ctx)
                .projectId(panel.getProjectId())
                .action(AuditAction.EVALUATION_PANEL_REOPENED_FOR_EXPERT)
                .entityType("EvaluationPanel")
                .entityId(panelId)
                .reason("addedEvaluator=" + additionalEvaluatorUserId
                        + ";role=" + EvaluatorRole.ADDITIONAL + ";reason=" + reason)
                .build());
        return panel.toDomain();
    }
}

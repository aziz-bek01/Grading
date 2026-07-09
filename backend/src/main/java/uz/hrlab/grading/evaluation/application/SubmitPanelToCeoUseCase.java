package uz.hrlab.grading.evaluation.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.approval.application.CreateApprovalRequestUseCase;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.evaluation.domain.EvaluationPanel;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelAssignmentRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * BE-9 — submit a panel's averaged result to the CEO: {@code AVERAGED ->
 * SUBMITTED}, permission {@code EVALUATION_PANEL_MANAGE}.
 *
 * <p>Precondition (REQ-CEO-5, rejected server-side): the panel MUST be
 * {@code AVERAGED} (all assigned complete, average computed). Submitting earlier
 * is rejected. Min-evaluators is re-checked defensively.
 *
 * <p>Opens the CEO approval request via the shared {@link CeoPanelApprovalOpener}
 * (the single home of the EXISTING {@link CreateApprovalRequestUseCase#createSystem}
 * with {@link ApprovalEntityType#EVALUATION_PANEL} + a single step requiring
 * {@code EVALUATION_PANEL_APPROVE}, plus the idempotency guard) — reuse verbatim,
 * no parallel approval engine (REQ-CEO-1). The one-time backfill migration
 * ({@code BackfillPanelApprovalsMigration}) calls the SAME collaborator.
 */
@Service
public class SubmitPanelToCeoUseCase {

    private final PanelLoader loader;
    private final PanelRepository panels;
    private final PanelAssignmentRepository assignments;
    private final CeoPanelApprovalOpener ceoApprovalOpener;
    private final AbacGate abacGate;
    private final AuditService audit;

    public SubmitPanelToCeoUseCase(PanelLoader loader,
                                   PanelRepository panels,
                                   PanelAssignmentRepository assignments,
                                   CeoPanelApprovalOpener ceoApprovalOpener,
                                   AbacGate abacGate,
                                   AuditService audit) {
        this.loader = loader;
        this.panels = panels;
        this.assignments = assignments;
        this.ceoApprovalOpener = ceoApprovalOpener;
        this.abacGate = abacGate;
        this.audit = audit;
    }

    @Transactional
    public EvaluationPanel submit(UUID panelId) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.EVALUATION_PANEL_MANAGE);
        EvaluationPanelJpaEntity panel = loader.requirePanel(panelId, ctx.tenantId());
        PositionJpaEntity position = loader.requirePosition(panel, ctx.tenantId());
        abacGate.enforceCanWriteInDepartment(
                ctx, panel.getProjectId(), position.getDepartmentId());

        if (panel.getStatus() != EvaluationPanelStatus.AVERAGED) {
            throw new ValidationException(
                    "PANEL_NOT_AVERAGED: panel can only be submitted to CEO from AVERAGED");
        }
        // Defensive min-evaluators re-check on the COMPLETED contributing seats.
        long completed = assignments.findAllByTenantIdAndPanelId(ctx.tenantId(), panelId).stream()
                .filter(a -> a.getAssignmentStatus()
                        == uz.hrlab.grading.evaluation.domain.PanelAssignmentStatus.COMPLETED)
                .count();
        if (completed < panel.getMinEvaluators()) {
            throw new ValidationException(
                    "ROSTER_BELOW_FLOOR: fewer than " + panel.getMinEvaluators()
                            + " completed evaluations");
        }

        OffsetDateTime now = OffsetDateTime.now();
        panel.setStatus(EvaluationPanelStatus.SUBMITTED);
        panel.setSubmittedAt(now);
        panel.setSubmittedBy(ctx.userId());
        panels.save(panel);

        UUID approvalRequestId = ceoApprovalOpener.openIfAbsent(ctx.tenantId(), panel);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(panel.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.EVALUATION_PANEL_SUBMITTED_TO_CEO)
                .entityType("EvaluationPanel")
                .entityId(panelId)
                .reason(approvalRequestId == null
                        ? "approvalRequest=existing"
                        : "approvalRequest=" + approvalRequestId)
                .build());
        return panel.toDomain();
    }

    /**
     * SYSTEM variant of {@link #submit(UUID)} — the consolidated panel flow:
     * invoked automatically by {@link PanelCompletionWatcher} the moment a panel
     * reaches {@code AVERAGED} (the last required evaluator completed), so the CEO
     * approval is opened with NO manual button.
     *
     * <p>Differs from {@link #submit(UUID)} ONLY by skipping the user-facing gates
     * (the {@code EVALUATION_PANEL_MANAGE} permission check and the ABAC
     * department write-gate): this is system-initiated inside the last evaluator's
     * transaction, not a manager action, so requiring a manager permission would
     * wrongly fail an ordinary evaluator's submit. Everything else is identical to
     * {@link #submit(UUID)} — the {@code status == AVERAGED} guard, the defensive
     * min-evaluators re-check, the {@code AVERAGED -> SUBMITTED} transition with
     * {@code submittedAt/By}, the EXISTING single-step
     * {@link CreateApprovalRequestUseCase#createSystem} for the CEO
     * ({@link ApprovalEntityType#EVALUATION_PANEL} + {@code EVALUATION_PANEL_APPROVE})
     * with the {@code ANOTHER_PENDING_REQUEST_EXISTS} idempotency swallow, and the
     * {@code EVALUATION_PANEL_SUBMITTED_TO_CEO} audit.
     *
     * <p>Idempotent: if a CEO request already exists (re-run / reopen race) the
     * approval-create swallows {@code ANOTHER_PENDING_REQUEST_EXISTS} and the panel
     * simply stays {@code SUBMITTED}. The caller (watcher) additionally runs this
     * fail-soft so a non-fatal failure here never breaks the evaluator's submit —
     * averaging has already succeeded.
     *
     * <p>Runs in the caller's (watcher's) transaction ({@code REQUIRED}).
     * {@code noRollbackFor = RuntimeException} so the watcher's defensive
     * fail-soft catch does not poison the shared transaction (mark it
     * rollback-only) on a non-fatal auto-submit failure — the already-persisted
     * averaging must survive.
     *
     * @param panelId     the panel that just averaged (tenant-resolved from the
     *                    active context, anti-BOLA)
     * @param actorUserId the acting user (the last evaluator) for submittedBy + audit
     */
    @Transactional(noRollbackFor = RuntimeException.class)
    public EvaluationPanel submitSystem(UUID panelId, UUID actorUserId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        EvaluationPanelJpaEntity panel = loader.requirePanel(panelId, ctx.tenantId());

        if (panel.getStatus() != EvaluationPanelStatus.AVERAGED) {
            throw new ValidationException(
                    "PANEL_NOT_AVERAGED: panel can only be submitted to CEO from AVERAGED");
        }
        // Defensive min-evaluators re-check on the COMPLETED contributing seats.
        long completed = assignments.findAllByTenantIdAndPanelId(ctx.tenantId(), panelId).stream()
                .filter(a -> a.getAssignmentStatus()
                        == uz.hrlab.grading.evaluation.domain.PanelAssignmentStatus.COMPLETED)
                .count();
        if (completed < panel.getMinEvaluators()) {
            throw new ValidationException(
                    "ROSTER_BELOW_FLOOR: fewer than " + panel.getMinEvaluators()
                            + " completed evaluations");
        }

        OffsetDateTime now = OffsetDateTime.now();
        panel.setStatus(EvaluationPanelStatus.SUBMITTED);
        panel.setSubmittedAt(now);
        panel.setSubmittedBy(actorUserId);
        panels.save(panel);

        UUID approvalRequestId = ceoApprovalOpener.openIfAbsent(ctx.tenantId(), panel);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(panel.getProjectId())
                .actorUserId(actorUserId)
                .action(AuditAction.EVALUATION_PANEL_SUBMITTED_TO_CEO)
                .entityType("EvaluationPanel")
                .entityId(panelId)
                .reason(approvalRequestId == null
                        ? "approvalRequest=existing"
                        : "approvalRequest=" + approvalRequestId)
                .build());
        return panel.toDomain();
    }
}

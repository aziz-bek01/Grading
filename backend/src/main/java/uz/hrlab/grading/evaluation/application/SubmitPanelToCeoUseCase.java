package uz.hrlab.grading.evaluation.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.approval.application.CreateApprovalRequestCommand;
import uz.hrlab.grading.approval.application.CreateApprovalRequestUseCase;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.BaseDomainException;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
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
 * <p>Opens the CEO approval request via the EXISTING
 * {@link CreateApprovalRequestUseCase#createSystem} with
 * {@link ApprovalEntityType#EVALUATION_PANEL} + a single step requiring
 * {@code EVALUATION_PANEL_APPROVE} — reuse verbatim, no parallel approval engine
 * (REQ-CEO-1).
 */
@Service
public class SubmitPanelToCeoUseCase {

    private final PanelLoader loader;
    private final PanelRepository panels;
    private final PanelAssignmentRepository assignments;
    private final CreateApprovalRequestUseCase createApprovalRequest;
    private final AbacGate abacGate;
    private final AuditService audit;

    public SubmitPanelToCeoUseCase(PanelLoader loader,
                                   PanelRepository panels,
                                   PanelAssignmentRepository assignments,
                                   CreateApprovalRequestUseCase createApprovalRequest,
                                   AbacGate abacGate,
                                   AuditService audit) {
        this.loader = loader;
        this.panels = panels;
        this.assignments = assignments;
        this.createApprovalRequest = createApprovalRequest;
        this.abacGate = abacGate;
        this.audit = audit;
    }

    @Transactional
    public EvaluationPanel submit(UUID panelId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.EVALUATION_PANEL_MANAGE)) {
            throw new PermissionDeniedException();
        }
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

        // Reuse the existing single-step approval-request path — CEO gate.
        UUID approvalRequestId = null;
        try {
            var req = createApprovalRequest.createSystem(
                    CreateApprovalRequestCommand.singleStep(
                            panel.getProjectId(),
                            ApprovalEntityType.EVALUATION_PANEL,
                            panel.getId(),
                            PermissionCodes.EVALUATION_PANEL_APPROVE));
            approvalRequestId = req == null ? null : req.id();
        } catch (RuntimeException openExisting) {
            if (!"ANOTHER_PENDING_REQUEST_EXISTS".equals(
                    openExisting instanceof BaseDomainException b ? b.getCode() : null)) {
                throw openExisting;
            }
        }

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
}

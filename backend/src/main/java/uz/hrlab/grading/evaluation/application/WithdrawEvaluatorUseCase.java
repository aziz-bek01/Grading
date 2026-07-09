package uz.hrlab.grading.evaluation.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.evaluation.domain.PanelAssignment;
import uz.hrlab.grading.evaluation.domain.PanelAssignmentStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelAssignmentJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelAssignmentRepository;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

/**
 * BE-5 — withdraw an evaluator from a panel's roster (pre-start only per UX
 * doc). Sets {@code assignment_status = WITHDRAWN} so the seat is excluded from
 * the uniqueness index AND the averaging denominator, while remaining as roster
 * history. Permission {@code EVALUATION_PANEL_MANAGE}; only while the panel is
 * {@code COLLECTING}.
 */
@Service
public class WithdrawEvaluatorUseCase {

    private final PanelLoader loader;
    private final PanelAssignmentRepository assignments;
    private final AbacGate abacGate;
    private final AuditService audit;

    public WithdrawEvaluatorUseCase(PanelLoader loader,
                                    PanelAssignmentRepository assignments,
                                    AbacGate abacGate,
                                    AuditService audit) {
        this.loader = loader;
        this.assignments = assignments;
        this.abacGate = abacGate;
        this.audit = audit;
    }

    @Transactional
    public PanelAssignment withdraw(UUID panelId, UUID evaluatorUserId) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.EVALUATION_PANEL_MANAGE);
        if (panelId == null || evaluatorUserId == null) {
            throw new ValidationException("panelId and evaluatorUserId are required");
        }
        EvaluationPanelJpaEntity panel = loader.requirePanel(panelId, ctx.tenantId());
        PositionJpaEntity position = loader.requirePosition(panel, ctx.tenantId());
        abacGate.enforceCanWriteInDepartment(
                ctx, panel.getProjectId(), position.getDepartmentId());

        if (!panel.getStatus().isRosterMutable()) {
            throw new ValidationException(
                    "PANEL_NOT_COLLECTING: evaluators can only be withdrawn while the panel is COLLECTING");
        }

        PanelAssignmentJpaEntity row = assignments
                .findByTenantIdAndPanelIdAndEvaluatorUserId(
                        ctx.tenantId(), panelId, evaluatorUserId)
                .orElseThrow(TenantAccessDeniedException::new);
        if (row.getAssignmentStatus() == PanelAssignmentStatus.WITHDRAWN) {
            return row.toDomain(); // idempotent
        }
        row.setAssignmentStatus(PanelAssignmentStatus.WITHDRAWN);
        assignments.save(row);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(panel.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.EVALUATION_PANEL_EVALUATOR_WITHDRAWN)
                .entityType("EvaluationPanel")
                .entityId(panelId)
                .reason("evaluator=" + evaluatorUserId + ";role=" + row.getEvaluatorRole())
                .build());
        return row.toDomain();
    }
}

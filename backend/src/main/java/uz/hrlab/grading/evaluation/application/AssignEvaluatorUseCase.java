package uz.hrlab.grading.evaluation.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.evaluation.domain.EvaluatorRole;
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
 * BE-5 — add an evaluator (user + role) to a panel's roster.
 *
 * <p>Only an authorized assigner ({@code EVALUATION_PANEL_MANAGE}) may assign;
 * an evaluator can NOT self-assign nor add others (REQ-ISO-7). Assignment is
 * permitted only while the panel is in {@code COLLECTING} (roster mutation
 * window). The uniqueness {@code uq_panel_assignment_per_evaluator} (excludes
 * WITHDRAWN) is the source of truth; this re-adds a withdrawn seat by flipping
 * it back to ASSIGNED rather than violating the index.
 */
@Service
public class AssignEvaluatorUseCase {

    private final PanelLoader loader;
    private final PanelAssignmentRepository assignments;
    private final AbacGate abacGate;
    private final AuditService audit;

    public AssignEvaluatorUseCase(PanelLoader loader,
                                  PanelAssignmentRepository assignments,
                                  AbacGate abacGate,
                                  AuditService audit) {
        this.loader = loader;
        this.assignments = assignments;
        this.abacGate = abacGate;
        this.audit = audit;
    }

    @Transactional
    public PanelAssignment assign(UUID panelId, UUID evaluatorUserId, EvaluatorRole role) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.EVALUATION_PANEL_MANAGE)) {
            throw new PermissionDeniedException();
        }
        if (panelId == null || evaluatorUserId == null || role == null) {
            throw new ValidationException("panelId, evaluator_user_id and evaluator_role are required");
        }
        EvaluationPanelJpaEntity panel = loader.requirePanel(panelId, ctx.tenantId());
        PositionJpaEntity position = loader.requirePosition(panel, ctx.tenantId());
        abacGate.enforceCanWriteInDepartment(
                ctx, panel.getProjectId(), position.getDepartmentId());

        if (!panel.getStatus().isRosterMutable()) {
            throw new ValidationException(
                    "PANEL_NOT_COLLECTING: evaluators can only be assigned while the panel is COLLECTING");
        }

        PanelAssignmentJpaEntity row = assignments
                .findByTenantIdAndPanelIdAndEvaluatorUserId(
                        ctx.tenantId(), panelId, evaluatorUserId)
                .orElse(null);
        if (row != null) {
            if (row.getAssignmentStatus() != PanelAssignmentStatus.WITHDRAWN) {
                throw new ValidationException(
                        "EVALUATOR_ALREADY_ASSIGNED: this evaluator is already on the panel");
            }
            // Re-add a previously withdrawn seat (keeps roster history; satisfies
            // the partial unique index which excludes WITHDRAWN).
            row.setAssignmentStatus(PanelAssignmentStatus.ASSIGNED);
            row.setEvaluatorRole(role);
        } else {
            row = new PanelAssignmentJpaEntity(
                    UUID.randomUUID(), ctx.tenantId(), panelId, evaluatorUserId,
                    role, PanelAssignmentStatus.ASSIGNED);
        }
        assignments.save(row);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(panel.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.EVALUATION_PANEL_EVALUATOR_ASSIGNED)
                .entityType("EvaluationPanel")
                .entityId(panelId)
                .reason("evaluator=" + evaluatorUserId + ";role=" + role)
                .build());
        return row.toDomain();
    }
}

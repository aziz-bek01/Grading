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
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
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
 * an evaluator can NOT self-assign nor add others (REQ-ISO-7). The uniqueness
 * {@code uq_panel_assignment_per_evaluator} (excludes WITHDRAWN) is the source of
 * truth; this re-adds a withdrawn seat by flipping it back to ASSIGNED rather
 * than violating the index.
 *
 * <p>Assignment window (Feature 2 broadening):
 * <ul>
 *   <li>{@code COLLECTING} — initial roster building. Behaviour UNCHANGED: the
 *       seat is created with NO eager Evaluation; the DRAFT sheets are created
 *       later, en masse, by {@link LockRosterUseCase} on roster lock.</li>
 *   <li>{@code AWAITING_EVALUATIONS} — adding a FURTHER expert AFTER the initial
 *       reopen ({@link ReopenApprovedPanelForExpertUseCase} has already moved the
 *       panel here). In this state scoring has already started, so a new seat
 *       must IMMEDIATELY get its own DRAFT Evaluation (mirroring LockRoster's
 *       per-seat create via {@link CreateEvaluationUseCase}); the new
 *       (incomplete) sheet keeps the panel from auto-re-averaging until the
 *       expert finishes.</li>
 *   <li>Any other status (AVERAGED / SUBMITTED / APPROVED / LOCKED / ARCHIVED)
 *       rejects the assignment — the roster is no longer mutable.</li>
 * </ul>
 */
@Service
public class AssignEvaluatorUseCase {

    private final PanelLoader loader;
    private final PanelAssignmentRepository assignments;
    private final CreateEvaluationUseCase createEvaluation;
    private final AbacGate abacGate;
    private final AuditService audit;

    public AssignEvaluatorUseCase(PanelLoader loader,
                                  PanelAssignmentRepository assignments,
                                  CreateEvaluationUseCase createEvaluation,
                                  AbacGate abacGate,
                                  AuditService audit) {
        this.loader = loader;
        this.assignments = assignments;
        this.createEvaluation = createEvaluation;
        this.abacGate = abacGate;
        this.audit = audit;
    }

    @Transactional
    public PanelAssignment assign(UUID panelId, UUID evaluatorUserId, EvaluatorRole role) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.EVALUATION_PANEL_MANAGE);
        if (panelId == null || evaluatorUserId == null || role == null) {
            throw new ValidationException("panelId, evaluator_user_id and evaluator_role are required");
        }
        EvaluationPanelJpaEntity panel = loader.requirePanel(panelId, ctx.tenantId());
        PositionJpaEntity position = loader.requirePosition(panel, ctx.tenantId());
        abacGate.enforceCanWriteInDepartment(
                ctx, panel.getProjectId(), position.getDepartmentId());

        EvaluationPanelStatus status = panel.getStatus();
        boolean collecting = status == EvaluationPanelStatus.COLLECTING;
        boolean awaiting = status == EvaluationPanelStatus.AWAITING_EVALUATIONS;
        if (!collecting && !awaiting) {
            throw new ValidationException(
                    "PANEL_NOT_MUTABLE: evaluators can only be assigned while the panel is "
                            + "COLLECTING or AWAITING_EVALUATIONS");
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

        // AWAITING_EVALUATIONS — scoring has already started, so the new seat
        // needs its DRAFT Evaluation NOW (reuse CreateEvaluationUseCase exactly as
        // LockRoster does per seat). In COLLECTING we leave evaluation_id null:
        // the DRAFT sheets are created en masse at roster lock (behaviour
        // unchanged). A re-added withdrawn seat that already had a (non-archived)
        // evaluation keeps it.
        if (awaiting && row.getEvaluationId() == null) {
            Evaluation created = createEvaluation.create(CreateEvaluationCommand.forPanelSeat(
                    panel.getPositionId(), panel.getMethodologyVersionId(),
                    evaluatorUserId, panelId, role));
            row.setEvaluationId(created.id());
        }
        assignments.save(row);

        audit.record(AuditEvent.builder(ctx)
                .projectId(panel.getProjectId())
                .action(AuditAction.EVALUATION_PANEL_EVALUATOR_ASSIGNED)
                .entityType("EvaluationPanel")
                .entityId(panelId)
                .reason("evaluator=" + evaluatorUserId + ";role=" + role)
                .build());
        return row.toDomain();
    }
}

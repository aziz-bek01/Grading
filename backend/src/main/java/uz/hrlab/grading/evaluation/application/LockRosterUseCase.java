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
import uz.hrlab.grading.evaluation.domain.EvaluatorRole;
import uz.hrlab.grading.evaluation.domain.PanelAssignmentStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelAssignmentJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelAssignmentRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import java.util.UUID;

/**
 * BE-6 — lock the roster and start evaluating: {@code COLLECTING ->
 * AWAITING_EVALUATIONS}, permission {@code EVALUATION_PANEL_MANAGE}.
 *
 * <p>Server-side roster validation (REQ-AVG-5, rejected below floor — NOT in UI):
 * <ul>
 *   <li>count(active assignments) &gt;= {@code panel.min_evaluators}</li>
 *   <li>all three {@link EvaluatorRole#MANDATORY_ROLES} present among active
 *       assignments</li>
 * </ul>
 *
 * <p>On lock it creates one DRAFT evaluation per active assignment via the
 * existing {@link CreateEvaluationUseCase} (reuse — set panel_id +
 * evaluator_role + evaluator_user_id). The relaxed unique index permits N rows
 * per panel.
 */
@Service
public class LockRosterUseCase {

    private final PanelLoader loader;
    private final PanelRepository panels;
    private final PanelAssignmentRepository assignments;
    private final CreateEvaluationUseCase createEvaluation;
    private final AbacGate abacGate;
    private final AuditService audit;

    public LockRosterUseCase(PanelLoader loader,
                             PanelRepository panels,
                             PanelAssignmentRepository assignments,
                             CreateEvaluationUseCase createEvaluation,
                             AbacGate abacGate,
                             AuditService audit) {
        this.loader = loader;
        this.panels = panels;
        this.assignments = assignments;
        this.createEvaluation = createEvaluation;
        this.abacGate = abacGate;
        this.audit = audit;
    }

    @Transactional
    public EvaluationPanel lockRoster(UUID panelId) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.EVALUATION_PANEL_MANAGE);
        EvaluationPanelJpaEntity panel = loader.requirePanel(panelId, ctx.tenantId());
        PositionJpaEntity position = loader.requirePosition(panel, ctx.tenantId());
        abacGate.enforceCanWriteInDepartment(
                ctx, panel.getProjectId(), position.getDepartmentId());

        if (panel.getStatus() != EvaluationPanelStatus.COLLECTING) {
            throw new ValidationException(
                    "PANEL_NOT_COLLECTING: roster can only be locked from COLLECTING");
        }

        List<PanelAssignmentJpaEntity> active = assignments
                .findAllByTenantIdAndPanelId(ctx.tenantId(), panelId).stream()
                .filter(a -> a.getAssignmentStatus().isActive())
                .toList();

        if (active.size() < panel.getMinEvaluators()) {
            throw new ValidationException(
                    "ROSTER_BELOW_FLOOR: panel requires at least " + panel.getMinEvaluators()
                            + " evaluators, found " + active.size());
        }
        Set<EvaluatorRole> rolesPresent = EnumSet.noneOf(EvaluatorRole.class);
        active.forEach(a -> rolesPresent.add(a.getEvaluatorRole()));
        if (!rolesPresent.containsAll(EvaluatorRole.MANDATORY_ROLES)) {
            throw new ValidationException(
                    "MANDATORY_ROLES_MISSING: panel must include HR_DIRECTOR, "
                            + "DEPARTMENT_DIRECTOR and EXTERNAL_EXPERT");
        }

        // Create one DRAFT evaluation per active assignment (reuse CreateEvaluationUseCase).
        for (PanelAssignmentJpaEntity a : active) {
            Evaluation created = createEvaluation.create(CreateEvaluationCommand.forPanelSeat(
                    panel.getPositionId(), panel.getMethodologyVersionId(),
                    a.getEvaluatorUserId(), panel.getId(), a.getEvaluatorRole()));
            a.setEvaluationId(created.id());
            assignments.save(a);
        }

        panel.setStatus(EvaluationPanelStatus.AWAITING_EVALUATIONS);
        panels.save(panel);

        audit.record(AuditEvent.builder(ctx)
                .projectId(panel.getProjectId())
                .action(AuditAction.EVALUATION_PANEL_ROSTER_LOCKED)
                .entityType("EvaluationPanel")
                .entityId(panelId)
                .reason("minEvaluators=" + panel.getMinEvaluators()
                        + ";evaluators=" + active.size()
                        + ";mandatoryRolesSatisfied=true")
                .build());
        return panel.toDomain();
    }
}

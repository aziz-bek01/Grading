package uz.hrlab.grading.evaluation.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.evaluation.domain.Evaluation;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.domain.EvaluationStatusTransitionPolicy;
import uz.hrlab.grading.evaluation.domain.EvaluationTransition;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * SUBMITTED → APPROVED. Permission EVALUATION_APPROVE.
 *
 * <p>Phase 6 integration: after the status flip, the
 * {@link EvaluationGradeAssignmentService} looks up the active grade structure
 * for the project and assigns {@code gradeBandId} + {@code assignedGradeNumber}
 * based on {@code rawTotalScore}. Out-of-range scores leave the fields null +
 * log a warning (manual calibration path).
 */
@Service
public class ApproveEvaluationUseCase {

    private final EvaluationRepository evaluations;
    private final EvaluationContextLoader loader;
    private final EvaluationStatusTransitionPolicy transitionPolicy;
    private final AbacGate abacGate;
    private final AuditService audit;
    private final EvaluationAuditSnapshot snapshot;
    private final EvaluationGradeAssignmentService gradeAssignment;

    public ApproveEvaluationUseCase(EvaluationRepository evaluations,
                                    EvaluationContextLoader loader,
                                    EvaluationStatusTransitionPolicy transitionPolicy,
                                    AbacGate abacGate,
                                    AuditService audit,
                                    EvaluationAuditSnapshot snapshot,
                                    EvaluationGradeAssignmentService gradeAssignment) {
        this.evaluations = evaluations;
        this.loader = loader;
        this.transitionPolicy = transitionPolicy;
        this.abacGate = abacGate;
        this.audit = audit;
        this.snapshot = snapshot;
        this.gradeAssignment = gradeAssignment;
    }

    @Transactional
    public Evaluation approve(UUID evaluationId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.EVALUATION_APPROVE)) {
            throw new PermissionDeniedException();
        }
        EvaluationContext context = loader.load(evaluationId, ctx.tenantId());
        EvaluationJpaEntity evaluation = context.evaluation();
        abacGate.enforceCanWriteInProject(ctx, evaluation.getProjectId());
        transitionPolicy.check(evaluation.getStatus(), EvaluationTransition.APPROVE);

        var beforeJson = snapshot.of(evaluation);
        OffsetDateTime now = OffsetDateTime.now();
        evaluation.setStatus(EvaluationStatus.APPROVED);
        evaluation.setApprovedAt(now);
        evaluation.setApprovedBy(ctx.userId());
        // Phase 6: assign grade based on rawTotalScore. Service emits its own
        // GRADE_ASSIGNED / GRADE_REASSIGNED audit event when a band matches.
        gradeAssignment.assignFromScore(evaluation, ctx.userId());
        evaluations.save(evaluation);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(evaluation.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.EVALUATION_APPROVED)
                .entityType("Evaluation")
                .entityId(evaluation.getId())
                .beforeJson(beforeJson)
                .afterJson(snapshot.of(evaluation))
                .build());
        return evaluation.toDomain();
    }
}

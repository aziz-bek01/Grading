package uz.hrlab.grading.evaluation.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.application.StatusTransitionExecutor;
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
    private final EvaluationAuditSnapshot snapshot;
    private final EvaluationGradeAssignmentService gradeAssignment;
    private final StatusTransitionExecutor transitions;

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
        this.snapshot = snapshot;
        this.gradeAssignment = gradeAssignment;
        this.transitions = new StatusTransitionExecutor(abacGate, audit);
    }

    @Transactional
    public Evaluation approve(UUID evaluationId) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.EVALUATION_APPROVE);
        EvaluationJpaEntity evaluation = loader.load(evaluationId, ctx.tenantId()).evaluation();

        OffsetDateTime now = OffsetDateTime.now();
        transitions.transition(ctx)
                .abacProjectWrite(evaluation.getProjectId())
                .checkTransition(() -> transitionPolicy.check(evaluation.getStatus(), EvaluationTransition.APPROVE))
                .snapshot(() -> snapshot.of(evaluation))
                .mutate(() -> {
                    evaluation.setStatus(EvaluationStatus.APPROVED);
                    evaluation.setApprovedAt(now);
                    evaluation.setApprovedBy(ctx.userId());
                    // Phase 6: assign grade based on rawTotalScore. Service emits its own
                    // GRADE_ASSIGNED / GRADE_REASSIGNED audit event when a band matches —
                    // recorded before the EVALUATION_APPROVED row, as in the original flow.
                    gradeAssignment.assignFromScore(evaluation, ctx.userId());
                })
                .save(() -> evaluations.save(evaluation))
                .audit(AuditAction.EVALUATION_APPROVED, "Evaluation",
                        evaluation.getId(), evaluation.getProjectId())
                .execute();
        return evaluation.toDomain();
    }
}

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
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.domain.EvaluationStatusTransitionPolicy;
import uz.hrlab.grading.evaluation.domain.EvaluationTransition;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

/**
 * SUBMITTED → COMPLETE — request changes. Requires reason ≥ 5 chars (lighter
 * than the calibration 20-char rule; this is a workflow comment).
 *
 * <p>Permission: EVALUATION_APPROVE (same authority that would approve).
 */
@Service
public class RequestEvaluationChangesUseCase {

    private static final int MIN_REASON_LENGTH = 5;

    private final EvaluationRepository evaluations;
    private final EvaluationContextLoader loader;
    private final EvaluationStatusTransitionPolicy transitionPolicy;
    private final AbacGate abacGate;
    private final AuditService audit;
    private final EvaluationAuditSnapshot snapshot;

    public RequestEvaluationChangesUseCase(EvaluationRepository evaluations,
                                           EvaluationContextLoader loader,
                                           EvaluationStatusTransitionPolicy transitionPolicy,
                                           AbacGate abacGate,
                                           AuditService audit,
                                           EvaluationAuditSnapshot snapshot) {
        this.evaluations = evaluations;
        this.loader = loader;
        this.transitionPolicy = transitionPolicy;
        this.abacGate = abacGate;
        this.audit = audit;
        this.snapshot = snapshot;
    }

    @Transactional
    public Evaluation requestChanges(UUID evaluationId, String reason) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.EVALUATION_APPROVE);
        if (reason == null || reason.trim().length() < MIN_REASON_LENGTH) {
            throw new ValidationException(
                    "reason is required (min " + MIN_REASON_LENGTH + " chars)");
        }
        EvaluationContext context = loader.load(evaluationId, ctx.tenantId());
        EvaluationJpaEntity evaluation = context.evaluation();
        abacGate.enforceCanWriteInProject(ctx, evaluation.getProjectId());
        transitionPolicy.check(evaluation.getStatus(), EvaluationTransition.REQUEST_CHANGES);

        var beforeJson = snapshot.of(evaluation);
        evaluation.setStatus(EvaluationStatus.COMPLETE);
        // Clear the submission stamps — a new submit must happen.
        evaluation.setSubmittedAt(null);
        evaluation.setSubmittedBy(null);
        evaluations.save(evaluation);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(evaluation.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.EVALUATION_CHANGES_REQUESTED)
                .entityType("Evaluation")
                .entityId(evaluation.getId())
                .reason(reason)
                .beforeJson(beforeJson)
                .afterJson(snapshot.of(evaluation))
                .build());
        return evaluation.toDomain();
    }
}

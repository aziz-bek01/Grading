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

/** APPROVED → LOCKED. Permission EVALUATION_LOCK. */
@Service
public class LockEvaluationUseCase {

    private final EvaluationRepository evaluations;
    private final EvaluationContextLoader loader;
    private final EvaluationStatusTransitionPolicy transitionPolicy;
    private final EvaluationAuditSnapshot snapshot;
    private final StatusTransitionExecutor transitions;

    public LockEvaluationUseCase(EvaluationRepository evaluations,
                                 EvaluationContextLoader loader,
                                 EvaluationStatusTransitionPolicy transitionPolicy,
                                 AbacGate abacGate,
                                 AuditService audit,
                                 EvaluationAuditSnapshot snapshot) {
        this.evaluations = evaluations;
        this.loader = loader;
        this.transitionPolicy = transitionPolicy;
        this.snapshot = snapshot;
        this.transitions = new StatusTransitionExecutor(abacGate, audit);
    }

    @Transactional
    public Evaluation lock(UUID evaluationId) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.EVALUATION_LOCK);
        EvaluationJpaEntity evaluation = loader.load(evaluationId, ctx.tenantId()).evaluation();

        OffsetDateTime now = OffsetDateTime.now();
        transitions.transition(ctx)
                .abacProjectWrite(evaluation.getProjectId())
                .checkTransition(() -> transitionPolicy.check(evaluation.getStatus(), EvaluationTransition.LOCK))
                .snapshot(() -> snapshot.of(evaluation))
                .mutate(() -> {
                    evaluation.setStatus(EvaluationStatus.LOCKED);
                    evaluation.setLockedAt(now);
                    evaluation.setLockedBy(ctx.userId());
                })
                .save(() -> evaluations.save(evaluation))
                .audit(AuditAction.EVALUATION_LOCKED, "Evaluation",
                        evaluation.getId(), evaluation.getProjectId())
                .execute();
        return evaluation.toDomain();
    }
}

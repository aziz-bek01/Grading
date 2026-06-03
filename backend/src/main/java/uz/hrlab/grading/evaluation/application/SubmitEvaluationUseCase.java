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
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.evaluation.domain.Evaluation;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.domain.EvaluationStatusTransitionPolicy;
import uz.hrlab.grading.evaluation.domain.EvaluationTransition;
import uz.hrlab.grading.evaluation.domain.EvaluationTransitionRejectedException;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * COMPLETE → SUBMITTED. Verifies completeness defensively (no required factor
 * missing) before transitioning.
 */
@Service
public class SubmitEvaluationUseCase {

    private final EvaluationRepository evaluations;
    private final EvaluationScoreRepository scores;
    private final EvaluationContextLoader loader;
    private final EvaluationStatusTransitionPolicy transitionPolicy;
    private final AbacGate abacGate;
    private final AuditService audit;
    private final EvaluationAuditSnapshot snapshot;
    private final CreateApprovalRequestUseCase createApprovalRequest;

    public SubmitEvaluationUseCase(EvaluationRepository evaluations,
                                   EvaluationScoreRepository scores,
                                   EvaluationContextLoader loader,
                                   EvaluationStatusTransitionPolicy transitionPolicy,
                                   AbacGate abacGate,
                                   AuditService audit,
                                   EvaluationAuditSnapshot snapshot,
                                   CreateApprovalRequestUseCase createApprovalRequest) {
        this.evaluations = evaluations;
        this.scores = scores;
        this.loader = loader;
        this.transitionPolicy = transitionPolicy;
        this.abacGate = abacGate;
        this.audit = audit;
        this.snapshot = snapshot;
        this.createApprovalRequest = createApprovalRequest;
    }

    @Transactional
    public Evaluation submit(UUID evaluationId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.EVALUATION_EDIT)) {
            throw new PermissionDeniedException();
        }
        EvaluationContext context = loader.load(evaluationId, ctx.tenantId());
        EvaluationJpaEntity evaluation = context.evaluation();
        abacGate.enforceCanWriteInProject(ctx, evaluation.getProjectId());
        transitionPolicy.check(evaluation.getStatus(), EvaluationTransition.SUBMIT);

        // Defensive completeness re-check (don't trust client state).
        Set<UUID> scored = new HashSet<>();
        scores.findAllByTenantIdAndEvaluationId(ctx.tenantId(), evaluation.getId())
                .forEach(s -> scored.add(s.getFactorId()));
        var missing = context.factors().stream()
                .filter(f -> f.required() && !scored.contains(f.id()))
                .toList();
        if (!missing.isEmpty()) {
            throw new EvaluationTransitionRejectedException(
                    "Cannot submit — " + missing.size() + " required factor(s) not scored");
        }

        var beforeJson = snapshot.of(evaluation);
        OffsetDateTime now = OffsetDateTime.now();
        evaluation.setStatus(EvaluationStatus.SUBMITTED);
        evaluation.setSubmittedAt(now);
        evaluation.setSubmittedBy(ctx.userId());
        evaluations.save(evaluation);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(evaluation.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.EVALUATION_SUBMITTED)
                .entityType("Evaluation")
                .entityId(evaluation.getId())
                .beforeJson(beforeJson)
                .afterJson(snapshot.of(evaluation))
                .build());

        // MVP 2 Phase 1 — open single-step approval request requiring EVALUATION_APPROVE.
        try {
            createApprovalRequest.createSystem(
                    CreateApprovalRequestCommand.singleStep(
                            evaluation.getProjectId(),
                            ApprovalEntityType.EVALUATION,
                            evaluation.getId(),
                            PermissionCodes.EVALUATION_APPROVE));
        } catch (RuntimeException openExisting) {
            if (!"ANOTHER_PENDING_REQUEST_EXISTS".equals(
                    openExisting instanceof uz.hrlab.grading.common.exception.BaseDomainException b
                            ? b.getCode() : null)) {
                throw openExisting;
            }
        }
        return evaluation.toDomain();
    }
}

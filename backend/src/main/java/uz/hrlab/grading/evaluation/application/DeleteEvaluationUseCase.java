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
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.UUID;

/**
 * Hard delete a pre-submission evaluation (Item 1, BE-2).
 *
 * <p>Mirrors {@link ArchiveEvaluationUseCase}: load via
 * {@link EvaluationContextLoader} (tenant-aware {@code findByIdAndTenantId} —
 * cross-tenant ids surface as 404 {@code TenantAccessDeniedException}, no
 * existence reveal), enforce the project write-gate via {@link AbacGate}, take a
 * before-snapshot, then mutate. Unlike archive this physically removes the row.
 *
 * <p><b>Hard guard:</b> only PRE-SUBMISSION statuses may be deleted —
 * {@code DRAFT}, {@code INCOMPLETE}, {@code COMPLETE} (see
 * {@link EvaluationStatus#isDeletable()}). Once an evaluation has been SUBMITTED
 * (SUBMITTED / APPROVED / LOCKED / ARCHIVED) it keeps the soft-delete (ARCHIVE)
 * path and is rejected here with a {@code ValidationException} carrying the
 * stable code {@code EVALUATION_NOT_DELETABLE} (400). This preserves the audit
 * trail for any evaluation that ever entered the approval workflow.
 *
 * <p>Dependent {@code evaluation_scores} rows are deleted FIRST so the parent row
 * never orphans a child FK. INCOMPLETE / COMPLETE evaluations may already carry
 * score rows; they are removed by the same tenant-scoped path used for DRAFT.
 * The audit row records {@code EVALUATION_DELETED} with the before-snapshot and
 * {@code afterJson = null} (the row no longer exists).
 */
@Service
public class DeleteEvaluationUseCase {

    private static final int MIN_REASON_LENGTH = 5;
    /** Stable error code surfaced to the FE for a post-submission delete attempt. */
    static final String NOT_DELETABLE_CODE = "EVALUATION_NOT_DELETABLE";

    private final EvaluationRepository evaluations;
    private final EvaluationScoreRepository scores;
    private final EvaluationContextLoader loader;
    private final AbacGate abacGate;
    private final AuditService audit;
    private final EvaluationAuditSnapshot snapshot;

    public DeleteEvaluationUseCase(EvaluationRepository evaluations,
                                   EvaluationScoreRepository scores,
                                   EvaluationContextLoader loader,
                                   AbacGate abacGate,
                                   AuditService audit,
                                   EvaluationAuditSnapshot snapshot) {
        this.evaluations = evaluations;
        this.scores = scores;
        this.loader = loader;
        this.abacGate = abacGate;
        this.audit = audit;
        this.snapshot = snapshot;
    }

    @Transactional
    public void delete(UUID evaluationId, String reason) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.EVALUATION_EDIT)) {
            throw new PermissionDeniedException();
        }
        if (reason == null || reason.trim().length() < MIN_REASON_LENGTH) {
            throw new ValidationException(
                    "reason is required (min " + MIN_REASON_LENGTH + " chars)");
        }
        EvaluationContext context = loader.load(evaluationId, ctx.tenantId());
        EvaluationJpaEntity evaluation = context.evaluation();
        abacGate.enforceCanWriteInProject(ctx, evaluation.getProjectId());

        if (!evaluation.getStatus().isDeletable()) {
            // SUBMITTED onward keeps its audit trail — use ARCHIVE, not delete.
            throw new ValidationException(NOT_DELETABLE_CODE,
                    "Only pre-submission (DRAFT/INCOMPLETE/COMPLETE) evaluations can be"
                            + " deleted; archive instead");
        }

        var beforeJson = snapshot.of(evaluation);

        // Delete dependent score rows first to avoid FK orphans (tenant-scoped).
        List<EvaluationScoreJpaEntity> dependentScores =
                scores.findAllByTenantIdAndEvaluationId(ctx.tenantId(), evaluation.getId());
        for (EvaluationScoreJpaEntity s : dependentScores) {
            scores.delete(s);
        }
        evaluations.deleteByIdAndTenantId(evaluation.getId(), ctx.tenantId());

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(evaluation.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.EVALUATION_DELETED)
                .entityType("Evaluation")
                .entityId(evaluation.getId())
                .reason(reason)
                .beforeJson(beforeJson)
                .afterJson(null)
                .build());
    }
}

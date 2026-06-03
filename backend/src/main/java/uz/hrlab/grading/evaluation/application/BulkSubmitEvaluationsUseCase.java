package uz.hrlab.grading.evaluation.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.BaseDomainException;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.evaluation.api.BulkOperationResponse;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bulk transition COMPLETE→SUBMITTED across many evaluations. Re-uses
 * {@link SubmitEvaluationUseCase} so completeness validation, transition
 * policy, ABAC, and the standard per-evaluation audit row remain in place.
 *
 * <p>Atomic-best-effort: each id runs in REQUIRES_NEW so a single rejection
 * (incomplete factors, immutable status, approval request collision) does
 * not roll back the others. A summary {@code EVALUATION_BULK_SUBMITTED}
 * audit row is emitted once at the end.
 */
@Service
public class BulkSubmitEvaluationsUseCase {

    private static final Logger log = LoggerFactory.getLogger(BulkSubmitEvaluationsUseCase.class);

    private final SubmitEvaluationUseCase submitUseCase;
    private final AuditService audit;

    public BulkSubmitEvaluationsUseCase(SubmitEvaluationUseCase submitUseCase,
                                        AuditService audit) {
        this.submitUseCase = submitUseCase;
        this.audit = audit;
    }

    public BulkOperationResponse execute(List<UUID> evaluationIds, String reason) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.EVALUATION_EDIT)) {
            throw new PermissionDeniedException();
        }
        if (evaluationIds == null || evaluationIds.isEmpty()) {
            throw new ValidationException("evaluationIds must contain at least one id");
        }

        List<BulkOperationResponse.Failure> failed = new ArrayList<>();
        int updated = 0;
        for (UUID eid : evaluationIds) {
            try {
                submitOne(eid);
                updated++;
            } catch (TenantAccessDeniedException ex) {
                failed.add(new BulkOperationResponse.Failure(eid,
                        "NOT_FOUND_OR_CROSS_TENANT", "evaluation not found"));
            } catch (PermissionDeniedException ex) {
                failed.add(new BulkOperationResponse.Failure(eid,
                        "PERMISSION_DENIED", "missing permission for this evaluation"));
            } catch (BaseDomainException ex) {
                failed.add(new BulkOperationResponse.Failure(eid,
                        ex.getCode() == null ? "DOMAIN_REJECTED" : ex.getCode(),
                        ex.getMessage()));
            } catch (RuntimeException ex) {
                log.warn("Bulk submit: unexpected failure eval={}", eid, ex);
                failed.add(new BulkOperationResponse.Failure(eid,
                        "INTERNAL_ERROR", "unexpected failure; see logs"));
            }
        }

        recordSummary(ctx, updated, failed.size(), reason);
        return new BulkOperationResponse(updated, failed.size(), failed);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void submitOne(UUID evaluationId) {
        submitUseCase.submit(evaluationId);
    }

    private void recordSummary(TenantContext ctx, int updated, int skipped, String reason) {
        try {
            audit.record(AuditEvent.builder()
                    .tenantId(ctx.tenantId())
                    .actorUserId(ctx.userId())
                    .action(AuditAction.EVALUATION_BULK_SUBMITTED)
                    .entityType("Evaluation")
                    .reason(buildReason(updated, skipped, reason))
                    .build());
        } catch (RuntimeException ex) {
            log.error("Failed to write EVALUATION_BULK_SUBMITTED summary audit row", ex);
        }
    }

    private static String buildReason(int updated, int skipped, String reason) {
        StringBuilder sb = new StringBuilder()
                .append("updated=").append(updated)
                .append(" skipped=").append(skipped);
        if (reason != null && !reason.isBlank()) {
            sb.append(" reason=").append(reason);
        }
        return sb.toString();
    }
}

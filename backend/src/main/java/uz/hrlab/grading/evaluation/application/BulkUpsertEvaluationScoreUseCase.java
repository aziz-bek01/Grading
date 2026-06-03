package uz.hrlab.grading.evaluation.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
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
 * Bulk PATCH score across many evaluations for a single factor (Excel K-sheet
 * UX). Re-uses {@link UpsertEvaluationScoreUseCase} per id so all existing
 * security checks (ABAC, immutability policy, tenant scoping, audit per row)
 * remain authoritative — no shortcut path.
 *
 * <p>Failures are <b>collected, not propagated</b>: one bad evaluation must
 * not roll back successful sibling updates. Per-row exceptions are recorded
 * with a stable {@code errorCode} and a sanitized message; the operation
 * still emits one summary {@code EVALUATION_BULK_SCORE_SET} audit row at the
 * end so SIEM can correlate the burst.
 *
 * <p>The summary audit row is NOT transactional with the inner updates — it
 * is best-effort logging (failure to write summary does not roll back work).
 */
@Service
public class BulkUpsertEvaluationScoreUseCase {

    private static final Logger log = LoggerFactory.getLogger(BulkUpsertEvaluationScoreUseCase.class);

    private final UpsertEvaluationScoreUseCase upsertScoreUseCase;
    private final AuditService audit;

    public BulkUpsertEvaluationScoreUseCase(UpsertEvaluationScoreUseCase upsertScoreUseCase,
                                            AuditService audit) {
        this.upsertScoreUseCase = upsertScoreUseCase;
        this.audit = audit;
    }

    /**
     * Apply {@code factorLevelId} for {@code factorId} to every evaluation id
     * in {@code evaluationIds}. Each evaluation runs in its own nested logical
     * try/catch so a single failure does not abort the batch.
     */
    public BulkOperationResponse execute(UUID factorId, UUID factorLevelId,
                                         List<UUID> evaluationIds, String reason) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.EVALUATION_EDIT)) {
            throw new PermissionDeniedException();
        }
        if (factorId == null || factorLevelId == null) {
            throw new ValidationException("factorId and factorLevelId are required");
        }
        if (evaluationIds == null || evaluationIds.isEmpty()) {
            throw new ValidationException("evaluationIds must contain at least one id");
        }

        List<BulkOperationResponse.Failure> failed = new ArrayList<>();
        int updated = 0;
        for (UUID eid : evaluationIds) {
            try {
                applyOne(eid, factorId, factorLevelId, reason);
                updated++;
            } catch (TenantAccessDeniedException ex) {
                // Anti-probe: do not leak whether the id exists in another tenant.
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
                log.warn("Bulk score upsert: unexpected failure eval={}", eid, ex);
                failed.add(new BulkOperationResponse.Failure(eid,
                        "INTERNAL_ERROR", "unexpected failure; see logs"));
            }
        }

        recordSummary(ctx, factorId, factorLevelId, updated, failed.size(), reason);
        return new BulkOperationResponse(updated, failed.size(), failed);
    }

    /**
     * Inner per-id transaction — REQUIRES_NEW so a per-row rollback does not
     * poison the outer (audit) transaction.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void applyOne(UUID evaluationId, UUID factorId, UUID factorLevelId, String reason) {
        upsertScoreUseCase.upsert(new UpsertEvaluationScoreCommand(
                evaluationId, factorId, factorLevelId, reason));
    }

    private void recordSummary(TenantContext ctx, UUID factorId, UUID factorLevelId,
                               int updated, int skipped, String reason) {
        try {
            audit.record(AuditEvent.builder()
                    .tenantId(ctx.tenantId())
                    .actorUserId(ctx.userId())
                    .action(AuditAction.EVALUATION_BULK_SCORE_SET)
                    .entityType("Factor")
                    .entityId(factorId)
                    .reason(buildReason(factorLevelId, updated, skipped, reason))
                    .build());
        } catch (RuntimeException ex) {
            log.error("Failed to write EVALUATION_BULK_SCORE_SET summary audit row", ex);
        }
    }

    private static String buildReason(UUID factorLevelId, int updated, int skipped, String reason) {
        StringBuilder sb = new StringBuilder()
                .append("level=").append(factorLevelId)
                .append(" updated=").append(updated)
                .append(" skipped=").append(skipped);
        if (reason != null && !reason.isBlank()) {
            sb.append(" reason=").append(reason);
        }
        return sb.toString();
    }
}

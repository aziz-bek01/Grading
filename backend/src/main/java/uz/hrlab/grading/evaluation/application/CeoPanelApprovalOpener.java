package uz.hrlab.grading.evaluation.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.approval.application.ApprovalQueries;
import uz.hrlab.grading.approval.application.CreateApprovalRequestCommand;
import uz.hrlab.grading.approval.application.CreateApprovalRequestUseCase;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.common.exception.BaseDomainException;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;

import java.util.UUID;

/**
 * Single source of truth for "open the CEO ({@code EVALUATION_PANEL} +
 * {@code EVALUATION_PANEL_APPROVE}) approval for this panel, idempotently".
 *
 * <p>Extracted from {@code SubmitPanelToCeoUseCase} so the idempotency guard
 * ({@link ApprovalQueries#findActivePendingForEntity}) and the
 * {@code ANOTHER_PENDING_REQUEST_EXISTS} swallow live in exactly ONE place. Both
 * the live submit flow ({@code SubmitPanelToCeoUseCase#submit} /
 * {@code #submitSystem}, reached when the last required evaluator completes) and
 * the one-time backfill migration ({@code BackfillPanelApprovalsMigration}) call
 * this collaborator — never a copy of the body (no duplication).
 *
 * <h3>Why the pre-check (not a try/catch)</h3>
 * A panel can already carry a PENDING {@code EVALUATION_PANEL} request — e.g.
 * after a reopen→re-average, or a manual+auto submit race. The previous code
 * called {@code createSystem} and CAUGHT {@code ANOTHER_PENDING_REQUEST_EXISTS}.
 * But {@code createSystem} is itself {@code @Transactional} (REQUIRED): when it
 * throws, the Spring tx interceptor marks the SHARED transaction rollback-only
 * BEFORE the catch runs, so the swallow could not save it — the outer
 * evaluator-score-save then failed with {@code UnexpectedRollbackException} (the
 * reopen re-average regression). Pre-checking for an existing PENDING request and
 * SKIPPING the create when present means no nested {@code @Transactional} ever
 * throws, so the transaction is never marked rollback-only.
 */
@Component
public class CeoPanelApprovalOpener {

    private final CreateApprovalRequestUseCase createApprovalRequest;
    private final ApprovalQueries approvalQueries;

    public CeoPanelApprovalOpener(CreateApprovalRequestUseCase createApprovalRequest,
                                  ApprovalQueries approvalQueries) {
        this.createApprovalRequest = createApprovalRequest;
        this.approvalQueries = approvalQueries;
    }

    /**
     * Open the single-step CEO approval ({@code EVALUATION_PANEL} +
     * {@code EVALUATION_PANEL_APPROVE}) for the panel — idempotently and WITHOUT
     * poisoning the caller's transaction.
     *
     * <p>Runs in the caller's transaction (no own {@code @Transactional}): the
     * caller establishes the tenant context + tx so the RLS GUC is already bound.
     *
     * @return the new approval request id, or {@code null} when an existing
     *         PENDING request was reused (idempotent no-op)
     */
    public UUID openIfAbsent(UUID tenantId, EvaluationPanelJpaEntity panel) {
        if (approvalQueries.findActivePendingForEntity(
                tenantId, ApprovalEntityType.EVALUATION_PANEL, panel.getId()) != null) {
            // A CEO request is already pending for this panel — reuse it.
            return null;
        }
        try {
            var req = createApprovalRequest.createSystem(
                    CreateApprovalRequestCommand.singleStep(
                            panel.getProjectId(),
                            ApprovalEntityType.EVALUATION_PANEL,
                            panel.getId(),
                            PermissionCodes.EVALUATION_PANEL_APPROVE));
            return req == null ? null : req.id();
        } catch (RuntimeException openExisting) {
            // Defense-in-depth for a concurrent insert that slipped past the
            // pre-check: only the idempotency code is tolerated, anything else
            // propagates (and legitimately rolls back).
            if ("ANOTHER_PENDING_REQUEST_EXISTS".equals(
                    openExisting instanceof BaseDomainException b ? b.getCode() : null)) {
                return null;
            }
            throw openExisting;
        }
    }
}

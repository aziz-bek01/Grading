package uz.hrlab.grading.evaluation.api;

import java.util.UUID;

/**
 * Result of {@code POST /api/v1/panels/reconcile-approvals} — the on-demand
 * trigger for the panel-approval reconciliation against the caller's tenant.
 *
 * <p>Counts mirror {@code BackfillPanelApprovalsMigration.Result}:
 * <ul>
 *   <li>{@code cancelledLegacyApprovals} — stale legacy {@code EVALUATION}
 *       approvals cancelled;</li>
 *   <li>{@code openedPanelApprovals} — missing {@code EVALUATION_PANEL} CEO
 *       approvals opened (also the count of SUBMITTED panels whose empty averages
 *       were recomputed from existing scores). {@code 0} means everything was
 *       already reconciled (the operation is idempotent).</li>
 * </ul>
 */
public record ReconcileApprovalsResponse(
        UUID tenantId,
        int cancelledLegacyApprovals,
        int openedPanelApprovals
) {
}

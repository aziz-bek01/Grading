package uz.hrlab.grading.approval.domain;

/**
 * Approval request state machine (MVP 2 Phase 1).
 *
 * <pre>
 *  PENDING → APPROVED          (last step approves)
 *  PENDING → REJECTED          (any step rejects; terminal)
 *  PENDING → CHANGES_REQUESTED (any step sends back; terminal — new request must be opened)
 *  PENDING → CANCELLED         (requestor cancels)
 * </pre>
 *
 * <p>Terminal statuses are immutable. Only PENDING admits transitions.
 */
public enum ApprovalRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CHANGES_REQUESTED,
    CANCELLED;

    public boolean isTerminal() {
        return this != PENDING;
    }
}

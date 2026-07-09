package uz.hrlab.grading.common.exception;

/**
 * Shared base for every domain state-machine transition rejection (BE-014).
 *
 * <p>A transition-rejection means the request was well-formed but the target
 * entity is in a state that forbids the requested transition — approving a
 * {@code DRAFT}, editing an {@code APPROVED}/locked aggregate, or an illegal
 * FSM edge. That is a <b>conflict with the resource's current state</b>, not a
 * malformed request, so every subclass resolves to HTTP {@code 409 CONFLICT}
 * through a SINGLE {@link uz.hrlab.grading.common.api.GlobalExceptionHandler}
 * handler. Clients can then reliably distinguish "conflict / retry" from
 * "bad request", instead of the previous 400-vs-409 split where only four of
 * the ten sibling exceptions were mapped.
 *
 * <p>Subclasses keep their own stable error {@code code} unchanged — the
 * frontend still switches on the specific code; only the HTTP status is
 * unified here.
 */
public abstract class DomainTransitionRejectedException extends BaseDomainException {

    protected DomainTransitionRejectedException(String code, String safeMessage) {
        super(code, safeMessage);
    }

    protected DomainTransitionRejectedException(String code, String safeMessage, Throwable cause) {
        super(code, safeMessage, cause);
    }
}

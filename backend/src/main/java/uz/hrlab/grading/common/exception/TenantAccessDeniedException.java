package uz.hrlab.grading.common.exception;

/**
 * Thrown when a request resolves to a tenant the caller is not a member of,
 * or when business logic detects a cross-tenant entity reference.
 *
 * <p>Per security-blueprint §5 and PRD AC: API surfaces this as
 * {@code 404 Not Found} (not 403) so existence of cross-tenant objects is
 * not revealed. The exception is still logged as a security event.
 */
public class TenantAccessDeniedException extends BaseDomainException {

    public TenantAccessDeniedException() {
        super("NOT_FOUND", "Resource not found");
    }

    public TenantAccessDeniedException(String safeMessage) {
        super("NOT_FOUND", safeMessage);
    }
}

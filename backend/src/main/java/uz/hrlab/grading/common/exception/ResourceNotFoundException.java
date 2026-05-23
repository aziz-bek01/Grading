package uz.hrlab.grading.common.exception;

/** 404 Not Found — generic. Use {@link TenantAccessDeniedException} for cross-tenant probes. */
public class ResourceNotFoundException extends BaseDomainException {

    public ResourceNotFoundException() {
        super("NOT_FOUND", "Resource not found");
    }

    public ResourceNotFoundException(String safeMessage) {
        super("NOT_FOUND", safeMessage);
    }
}

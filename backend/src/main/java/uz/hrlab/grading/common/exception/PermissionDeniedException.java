package uz.hrlab.grading.common.exception;

/** 403 Forbidden — authenticated user lacks the required permission. */
public class PermissionDeniedException extends BaseDomainException {

    public PermissionDeniedException() {
        super("PERMISSION_DENIED", "Action not permitted");
    }

    public PermissionDeniedException(String safeMessage) {
        super("PERMISSION_DENIED", safeMessage);
    }
}

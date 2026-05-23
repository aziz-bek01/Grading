package uz.hrlab.grading.common.exception;

/** 400 Bad Request — caller-facing validation failure. */
public class ValidationException extends BaseDomainException {

    public ValidationException(String safeMessage) {
        super("VALIDATION_FAILED", safeMessage);
    }

    public ValidationException(String code, String safeMessage) {
        super(code, safeMessage);
    }
}

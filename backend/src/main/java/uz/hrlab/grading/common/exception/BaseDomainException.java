package uz.hrlab.grading.common.exception;

/**
 * Base type for domain-level exceptions. Carries a stable error {@code code}
 * matching the design-foundation §21.6 contract — the same code is mapped to
 * a translation key on the frontend.
 *
 * <p>Subclasses must NEVER expose tenant data, salary data, or PII through
 * {@link #getMessage()}; sanitized messages only.
 */
public abstract class BaseDomainException extends RuntimeException {

    private final String code;

    protected BaseDomainException(String code, String safeMessage) {
        super(safeMessage);
        this.code = code;
    }

    protected BaseDomainException(String code, String safeMessage, Throwable cause) {
        super(safeMessage, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

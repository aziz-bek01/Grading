package uz.hrlab.grading.common.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a DTO field as protected by a permission code
 * (security-blueprint §8, finding F-12).
 *
 * <p>When the registered Jackson {@link SensitiveFieldSerializerModifier}
 * serializes a DTO and the current {@code TenantContext} does NOT carry
 * {@link #requires()}, the annotated field is <b>omitted</b> from the JSON
 * payload entirely — not nulled, not masked with a placeholder, not replaced
 * with a structured marker. This matches the design-foundation §10 handoff:
 * the absence of the field is the signal.
 *
 * <p>Typical use:
 * <pre>{@code
 * public record SalaryRangeResponse(
 *     UUID id,
 *     @Sensitive(requires = "SALARY_VIEW") BigDecimal min,
 *     @Sensitive(requires = "SALARY_VIEW") BigDecimal max
 * ) { }
 * }</pre>
 *
 * <p>The annotation is metadata-only — it is the Jackson module that enforces
 * stripping. Logging is enforced by {@code MaskingPatternLayout} (F-13).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
public @interface Sensitive {

    /**
     * Permission code that the caller must possess for the field to be
     * included in the JSON output. Default is {@code SALARY_VIEW}.
     */
    String requires() default "SALARY_VIEW";

    /**
     * Optional label for forensics / log lines. Never written to the JSON
     * payload — only used by audit redactors.
     */
    String label() default "";
}

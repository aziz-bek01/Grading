package uz.hrlab.grading.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bean Validation constraint: when applied to a
 * {@code Map<String, String>} field, every key must be one of the four
 * supported locale codes (architecture §20.3 / F-308):
 *
 * <ul>
 *   <li>{@code ru-RU}</li>
 *   <li>{@code uz-Cyrl-UZ}</li>
 *   <li>{@code uz-Latn-UZ}</li>
 *   <li>{@code en-US}</li>
 * </ul>
 *
 * <p>{@code null} maps are accepted (compose with {@code @NotNull} if
 * required). Empty maps are accepted.
 */
@Documented
@Constraint(validatedBy = SupportedLocaleKeysValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER,
        ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SupportedLocaleKeys {
    String message() default "INVALID_LOCALE_KEY: only ru-RU, uz-Cyrl-UZ, uz-Latn-UZ, en-US are supported";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

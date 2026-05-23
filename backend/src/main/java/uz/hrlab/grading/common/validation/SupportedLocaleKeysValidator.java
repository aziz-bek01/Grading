package uz.hrlab.grading.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Map;
import java.util.Set;

/**
 * Validator for {@link SupportedLocaleKeys}. Accepts {@code null} (no-op);
 * rejects any map whose keys include a non-allowlisted locale code.
 */
public class SupportedLocaleKeysValidator
        implements ConstraintValidator<SupportedLocaleKeys, Map<String, String>> {

    public static final Set<String> SUPPORTED_LOCALES = Set.of(
            "ru-RU",
            "uz-Cyrl-UZ",
            "uz-Latn-UZ",
            "en-US"
    );

    @Override
    public boolean isValid(Map<String, String> value,
                           ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) return true;
        for (String key : value.keySet()) {
            if (!SUPPORTED_LOCALES.contains(key)) return false;
        }
        return true;
    }
}

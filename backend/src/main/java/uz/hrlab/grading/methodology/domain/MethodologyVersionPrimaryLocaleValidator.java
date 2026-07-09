package uz.hrlab.grading.methodology.domain;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.tenancy.domain.Locale;

import java.util.List;
import java.util.Map;

/**
 * Approve-time check: every factor must have a non-blank value in the
 * canonical primary locale {@code ru-RU} for its {@code name_i18n}
 * (architecture §20.3 / F-308 i18n parity).
 */
@Component
public class MethodologyVersionPrimaryLocaleValidator {

    public static final String PRIMARY_LOCALE = Locale.RU_RU;

    public void validate(List<? extends FactorNameView> factors) {
        if (factors == null) return;
        for (FactorNameView f : factors) {
            Map<String, String> name = f.nameI18n();
            if (name == null || isBlank(name.get(PRIMARY_LOCALE))) {
                throw new MethodologyVersionTransitionRejectedException(
                        "Factor '" + f.code() + "' is missing the primary locale ("
                                + PRIMARY_LOCALE + ") name");
            }
        }
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }

    public interface FactorNameView {
        String code();
        Map<String, String> nameI18n();
    }
}

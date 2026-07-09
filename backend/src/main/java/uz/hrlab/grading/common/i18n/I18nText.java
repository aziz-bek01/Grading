package uz.hrlab.grading.common.i18n;

import uz.hrlab.grading.tenancy.domain.Locale;

import java.util.Map;

/**
 * BE-034 — the single localized-map fallback policy shared across modules.
 *
 * <p>Consolidates the two previously divergent copies (the panel/evaluation
 * {@code pickLocalized} and the report port {@code i18n}) into one rule:
 * requested {@code locale} (if the map carries it) → the canonical primary
 * {@link Locale#RU_RU} → the first available value → {@code fallback}. The
 * {@code "ru-RU"} literal is gone: the primary locale is sourced from the one
 * canonical {@link Locale#RU_RU} constant.
 */
public final class I18nText {

    private I18nText() { }

    /**
     * Fallback-to-{@code null} overload: requested locale → {@link Locale#RU_RU}
     * → first available value → {@code null} (empty/absent map ⇒ {@code null}).
     */
    public static String pick(Map<String, String> i18n, String locale) {
        return pick(i18n, locale, null);
    }

    /**
     * Requested locale → {@link Locale#RU_RU} → first available value →
     * {@code fallback} (returned when the map is null/empty).
     */
    public static String pick(Map<String, String> i18n, String locale, String fallback) {
        if (i18n == null || i18n.isEmpty()) {
            return fallback;
        }
        if (locale != null && i18n.containsKey(locale)) {
            return i18n.get(locale);
        }
        if (i18n.containsKey(Locale.RU_RU)) {
            return i18n.get(Locale.RU_RU);
        }
        return i18n.values().stream().findFirst().orElse(fallback);
    }
}

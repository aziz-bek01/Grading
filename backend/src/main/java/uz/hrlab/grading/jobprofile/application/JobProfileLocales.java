package uz.hrlab.grading.jobprofile.application;

import java.util.List;
import java.util.Map;

/**
 * MVP 1 default primary locale + i18n helpers. {@code ru-RU} is the canonical
 * primary locale; uz-Cyrl-UZ / uz-Latn-UZ / en-US are optional translations
 * (PRD §E11-1, architecture §20.3).
 */
public final class JobProfileLocales {

    public static final String PRIMARY_LOCALE = "ru-RU";

    public static final List<String> ALL_LOCALES =
            List.of("ru-RU", "uz-Cyrl-UZ", "uz-Latn-UZ", "en-US");

    private JobProfileLocales() { }

    /** True if the map contains a non-blank value for the primary locale. */
    public static boolean hasPrimary(Map<String, String> i18n) {
        if (i18n == null) return false;
        String v = i18n.get(PRIMARY_LOCALE);
        return v != null && !v.isBlank();
    }

    /** Returns the primary-locale value or first available non-blank value. */
    public static String pickAny(Map<String, String> i18n) {
        if (i18n == null) return null;
        String v = i18n.get(PRIMARY_LOCALE);
        if (v != null && !v.isBlank()) return v;
        for (String loc : ALL_LOCALES) {
            String alt = i18n.get(loc);
            if (alt != null && !alt.isBlank()) return alt;
        }
        return null;
    }
}

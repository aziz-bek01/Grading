package uz.hrlab.grading.tenancy.domain;

import java.util.Set;

/**
 * Canonical set of supported locales (architecture §20.3, design-foundation §8).
 * Stored as VARCHAR(20) in DB. Use {@link #isSupported(String)} to validate.
 */
public final class Locale {

    public static final String RU_RU      = "ru-RU";
    public static final String UZ_CYRL_UZ = "uz-Cyrl-UZ";
    public static final String UZ_LATN_UZ = "uz-Latn-UZ";
    public static final String EN_US      = "en-US";

    public static final Set<String> SUPPORTED = Set.of(RU_RU, UZ_CYRL_UZ, UZ_LATN_UZ, EN_US);

    public static boolean isSupported(String tag) {
        return tag != null && SUPPORTED.contains(tag);
    }

    private Locale() { }
}

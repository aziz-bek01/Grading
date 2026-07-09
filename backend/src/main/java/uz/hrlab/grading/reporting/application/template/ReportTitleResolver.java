package uz.hrlab.grading.reporting.application.template;

import uz.hrlab.grading.common.i18n.I18nText;
import uz.hrlab.grading.reporting.domain.ReportType;

import java.util.Map;

/**
 * Builds a default human-readable title for a {@link ReportType} in the
 * caller's locale. Used at request time so the {@code reports.title} column
 * is populated before generation starts.
 *
 * <p>i18n note: the four supported locales are mirror the global localization
 * stack (ru-RU, uz-Cyrl-UZ, uz-Latn-UZ, en-US — architecture §13). Strings
 * are intentionally short — frontend may override with the user-chosen title.
 */
public final class ReportTitleResolver {

    private static final Map<ReportType, Map<String, String>> TITLES = Map.of(
            ReportType.GRADE_DISTRIBUTION, Map.of(
                    "ru-RU", "Распределение позиций по грейдам",
                    "uz-Cyrl-UZ", "Лавозимларнинг грейдлар бўйича тақсимоти",
                    "uz-Latn-UZ", "Lavozimlarning greydlar bo‘yicha taqsimoti",
                    "en-US", "Grade distribution"),
            ReportType.POSITION_CATALOG, Map.of(
                    "ru-RU", "Каталог позиций",
                    "uz-Cyrl-UZ", "Лавозимлар каталоги",
                    "uz-Latn-UZ", "Lavozimlar katalogi",
                    "en-US", "Position catalog"),
            ReportType.EVALUATION_SUMMARY, Map.of(
                    "ru-RU", "Сводка оценок",
                    "uz-Cyrl-UZ", "Баҳолашлар хулосаси",
                    "uz-Latn-UZ", "Baholashlar xulosasi",
                    "en-US", "Evaluation summary"),
            ReportType.METHODOLOGY_SPEC, Map.of(
                    "ru-RU", "Спецификация методологии",
                    "uz-Cyrl-UZ", "Методология тавсифи",
                    "uz-Latn-UZ", "Metodologiya tavsifi",
                    "en-US", "Methodology specification"),
            ReportType.AUDIT_SUMMARY, Map.of(
                    "ru-RU", "Сводка аудита",
                    "uz-Cyrl-UZ", "Аудит хулосаси",
                    "uz-Latn-UZ", "Audit xulosasi",
                    "en-US", "Audit summary"),
            ReportType.EXECUTIVE_SUMMARY, Map.of(
                    "ru-RU", "Сводка для руководства",
                    "uz-Cyrl-UZ", "Раҳбарият учун хулоса",
                    "uz-Latn-UZ", "Rahbariyat uchun xulosa",
                    "en-US", "Executive summary"));

    private ReportTitleResolver() { }

    public static String resolve(ReportType type, String locale) {
        Map<String, String> byLocale = TITLES.get(type);
        if (byLocale == null) return type.name();
        return I18nText.pick(byLocale, locale, type.name());
    }
}

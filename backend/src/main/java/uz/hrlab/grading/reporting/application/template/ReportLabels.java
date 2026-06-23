package uz.hrlab.grading.reporting.application.template;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Map;

/**
 * Central localization for report STATIC strings (column headers, section
 * titles, meta-line labels, empty-state paragraphs) and for machine values that
 * reach human output (enum statuses, scoring modes, audit actions).
 *
 * <p>The backend has no {@code MessageSource} / resource-bundle i18n stack, so
 * this per-key map keyed by the four supported locales (ru-RU, uz-Cyrl-UZ,
 * uz-Latn-UZ, en-US — architecture §13) is the single, consistent mechanism the
 * six report templates and {@code DefaultReportDataPort} share. Default locale
 * is {@code ru-RU} (the consultant/admin UI default).
 *
 * <p>Unknown keys / locales fall back to the {@code ru-RU} value, then to the
 * key itself — a report never renders a blank or an exception for a missing
 * translation.
 */
public final class ReportLabels {

    public static final String DEFAULT_LOCALE = "ru-RU";

    private ReportLabels() { }

    // ── Static UI labels (headers, section titles, meta lines) ──────────────

    private static final Map<String, Map<String, String>> LABELS = Map.ofEntries(
            // meta-line labels
            entry("meta.project", "Проект", "Лойиҳа", "Loyiha", "Project"),
            entry("meta.tenant", "Организация", "Ташкилот", "Tashkilot", "Organization"),
            entry("meta.locale", "Язык", "Тил", "Til", "Locale"),
            entry("meta.methodology", "Методология", "Методология", "Metodologiya", "Methodology"),
            entry("meta.version", "Версия", "Версия", "Versiya", "Version"),
            entry("meta.status", "Статус", "Ҳолат", "Holat", "Status"),
            entry("meta.totalPositions", "Всего позиций", "Жами лавозимлар", "Jami lavozimlar", "Total positions"),
            entry("meta.approved", "Утверждено", "Тасдиқланган", "Tasdiqlangan", "Approved"),
            entry("meta.positionCount", "Количество позиций", "Лавозимлар сони", "Lavozimlar soni", "Position count"),
            entry("meta.totalEvents", "Всего событий", "Жами ҳодисалар", "Jami hodisalar", "Total events"),
            entry("meta.period", "Период", "Давр", "Davr", "Period"),
            // EVALUATION_SUMMARY applied-filter meta lines (self-describing export)
            entry("meta.filterPeriod", "Период", "Давр", "Davr", "Period"),
            entry("meta.filterEvaluators", "Оценщики", "Баҳоловчилар", "Baholovchilar", "Evaluators"),
            entry("meta.filterMethodologies", "Методологии", "Методологиялар",
                    "Metodologiyalar", "Methodologies"),
            entry("meta.weight", "Вес", "Вазн", "Vazn", "Weight"),
            entry("meta.maxPoints", "Макс. баллы", "Макс. балл", "Maks. ball", "Max points"),
            entry("meta.scoringMode", "Режим оценки", "Баҳолаш режими", "Baholash rejimi", "Scoring mode"),

            // section titles
            entry("section.distribution", "Распределение", "Тақсимот", "Taqsimot", "Distribution"),
            entry("section.detail", "Детализация", "Тафсилот", "Tafsilot", "Detail"),
            entry("section.keyIndicators", "Ключевые показатели", "Асосий кўрсаткичлар",
                    "Asosiy ko‘rsatkichlar", "Key indicators"),
            entry("section.recentApprovals", "Последние утверждения", "Сўнгги тасдиқлашлар",
                    "So‘nggi tasdiqlashlar", "Recent approvals"),

            // table column headers
            entry("col.positionCode", "Код позиции", "Лавозим коди", "Lavozim kodi", "Position code"),
            entry("col.position", "Позиция", "Лавозим", "Lavozim", "Position"),
            entry("col.department", "Подразделение", "Бўлинма", "Bo‘linma", "Department"),
            entry("col.jobFamily", "Семейство должностей", "Лавозим оиласи", "Lavozim oilasi", "Job family"),
            entry("col.jobLevel", "Уровень должности", "Лавозим даражаси", "Lavozim darajasi", "Job level"),
            entry("col.status", "Статус", "Ҳолат", "Holat", "Status"),
            entry("col.code", "Код", "Код", "Kod", "Code"),
            entry("col.title", "Наименование", "Номланиши", "Nomlanishi", "Title"),
            entry("col.total", "Итого", "Жами", "Jami", "Total"),
            entry("col.grade", "Грейд", "Грейд", "Greyd", "Grade"),
            entry("col.gradeName", "Название грейда", "Грейд номи", "Greyd nomi", "Grade name"),
            entry("col.positions", "Позиции", "Лавозимлар", "Lavozimlar", "Positions"),
            entry("col.level", "Уровень", "Даража", "Daraja", "Level"),
            entry("col.points", "Баллы", "Балл", "Ball", "Points"),
            entry("col.scale", "Шкала", "Шкала", "Shkala", "Scale"),
            entry("col.timestamp", "Время", "Вақт", "Vaqt", "Timestamp"),
            entry("col.action", "Действие", "Амал", "Amal", "Action"),
            entry("col.actor", "Исполнитель", "Бажарувчи", "Bajaruvchi", "Actor"),
            entry("col.entityType", "Тип объекта", "Объект тури", "Obyekt turi", "Entity type"),
            entry("col.entityId", "Идентификатор объекта", "Объект идентификатори",
                    "Obyekt identifikatori", "Entity id"),
            entry("col.reason", "Причина", "Сабаб", "Sabab", "Reason"),
            entry("col.indicator", "Показатель", "Кўрсаткич", "Ko‘rsatkich", "Indicator"),
            entry("col.value", "Значение", "Қиймат", "Qiymat", "Value"),

            // executive KPI indicator labels (column A localized — leak fix)
            entry("kpi.projectName", "Название проекта", "Лойиҳа номи", "Loyiha nomi", "Project name"),
            entry("kpi.projectStatus", "Статус проекта", "Лойиҳа ҳолати", "Loyiha holati", "Project status"),
            entry("kpi.periodFrom", "Период с", "Давр бошланиши", "Davr boshlanishi", "Period from"),
            entry("kpi.periodTo", "Период по", "Давр тугаши", "Davr tugashi", "Period to"),
            entry("kpi.positions", "Позиции", "Лавозимлар", "Lavozimlar", "Positions"),
            entry("kpi.evaluations", "Оценки", "Баҳолашлар", "Baholashlar", "Evaluations"),
            entry("kpi.evaluationsApproved", "Утверждённые оценки", "Тасдиқланган баҳолашлар",
                    "Tasdiqlangan baholashlar", "Approved evaluations"),
            entry("kpi.evaluationsApprovedPct", "Доля утверждённых, %", "Тасдиқланганлар улуши, %",
                    "Tasdiqlanganlar ulushi, %", "Approved share, %"),
            entry("kpi.grades", "Грейды", "Грейдлар", "Greydlar", "Grades"),
            entry("kpi.auditEvents", "События аудита", "Аудит ҳодисалари", "Audit hodisalari", "Audit events"),
            entry("kpi.approvedSuffix", "утверждено", "тасдиқланган", "tasdiqlangan", "approved"),

            // empty-state paragraphs
            entry("empty.positions", "В проекте нет ни одной позиции.", "Лойиҳада лавозимлар йўқ.",
                    "Loyihada lavozimlar yo‘q.", "No positions recorded for this project."),
            entry("empty.evaluations", "В проекте нет ни одной оценки.", "Лойиҳада баҳолашлар йўқ.",
                    "Loyihada baholashlar yo‘q.", "No evaluations recorded for this project."),
            entry("empty.grades", "Грейды ещё не назначены.", "Грейдлар ҳали тайинланмаган.",
                    "Greydlar hali tayinlanmagan.", "No grades assigned yet."),
            entry("empty.factors", "Факторы методологии не определены.", "Методология омиллари аниқланмаган.",
                    "Metodologiya omillari aniqlanmagan.", "No methodology factors defined."),
            entry("empty.audit", "Событий аудита за период не зафиксировано.",
                    "Давр учун аудит ҳодисалари қайд этилмаган.",
                    "Davr uchun audit hodisalari qayd etilmagan.",
                    "No audit events recorded for this period."),
            entry("empty.recentApprovals", "Утверждений по проекту пока нет.",
                    "Лойиҳа бўйича ҳали тасдиқлашлар йўқ.",
                    "Loyiha bo‘yicha hali tasdiqlashlar yo‘q.",
                    "No approvals recorded for this project yet."));

    public static String label(String key, String locale) {
        Map<String, String> byLocale = LABELS.get(key);
        if (byLocale == null) return key;
        return byLocale.getOrDefault(loc(locale), byLocale.getOrDefault(DEFAULT_LOCALE, key));
    }

    // ── Enum-status localization (leak fix — never render raw enum names) ───

    private static final Map<String, Map<String, String>> STATUS = Map.ofEntries(
            entry("DRAFT", "Черновик", "Лойиҳа", "Loyiha", "Draft"),
            entry("INCOMPLETE", "Не завершено", "Тугалланмаган", "Tugallanmagan", "Incomplete"),
            entry("COMPLETE", "Завершено", "Тугалланган", "Tugallangan", "Complete"),
            entry("SUBMITTED", "Отправлено", "Юборилган", "Yuborilgan", "Submitted"),
            entry("APPROVED", "Утверждено", "Тасдиқланган", "Tasdiqlangan", "Approved"),
            entry("LOCKED", "Заблокировано", "Қулфланган", "Qulflangan", "Locked"),
            entry("ARCHIVED", "В архиве", "Архивланган", "Arxivlangan", "Archived"),
            entry("ACTIVE", "Активно", "Фаол", "Faol", "Active"));

    /**
     * Localize a raw Java enum name reaching human output (EvaluationStatus,
     * PositionStatus, MethodologyVersionStatus, ProjectStatus). Blank/unknown
     * names pass through unchanged.
     */
    public static String localizeStatus(String enumName, String locale) {
        if (enumName == null || enumName.isBlank()) return "";
        Map<String, String> byLocale = STATUS.get(enumName);
        if (byLocale == null) return enumName;
        return byLocale.getOrDefault(loc(locale), byLocale.getOrDefault(DEFAULT_LOCALE, enumName));
    }

    private static final Map<String, Map<String, String>> SCORING_MODE = Map.ofEntries(
            entry("DIRECT_POINTS", "Прямые баллы", "Тўғридан-тўғри балл",
                    "To‘g‘ridan-to‘g‘ri ball", "Direct points"),
            entry("WEIGHTED_POINTS", "Взвешенные баллы", "Вазнли балл", "Vaznli ball", "Weighted points"),
            entry("WEIGHTED_SCALE", "Взвешенная шкала", "Вазнли шкала", "Vaznli shkala", "Weighted scale"));

    /** Localize a raw scoring-mode enum name reaching human output. */
    public static String localizeScoringMode(String enumName, String locale) {
        if (enumName == null || enumName.isBlank()) return "";
        Map<String, String> byLocale = SCORING_MODE.get(enumName);
        if (byLocale == null) return enumName;
        return byLocale.getOrDefault(loc(locale), byLocale.getOrDefault(DEFAULT_LOCALE, enumName));
    }

    // ── Audit-action localization (executive recent-approvals) ──────────────

    private static final Map<String, Map<String, String>> ACTION = Map.ofEntries(
            entry("EVALUATION_APPROVED", "Оценка утверждена", "Баҳолаш тасдиқланди",
                    "Baholash tasdiqlandi", "Evaluation approved"),
            entry("EVALUATION_LOCKED", "Оценка заблокирована", "Баҳолаш қулфланди",
                    "Baholash qulflandi", "Evaluation locked"),
            entry("METHODOLOGY_APPROVED", "Методология утверждена", "Методология тасдиқланди",
                    "Metodologiya tasdiqlandi", "Methodology approved"),
            entry("METHODOLOGY_LOCKED", "Методология заблокирована", "Методология қулфланди",
                    "Metodologiya qulflandi", "Methodology locked"),
            entry("METHODOLOGY_VERSION_APPROVED", "Версия методологии утверждена",
                    "Методология версияси тасдиқланди", "Metodologiya versiyasi tasdiqlandi",
                    "Methodology version approved"),
            entry("METHODOLOGY_VERSION_LOCKED", "Версия методологии заблокирована",
                    "Методология версияси қулфланди", "Metodologiya versiyasi qulflandi",
                    "Methodology version locked"),
            entry("GRADE_STRUCTURE_APPROVED", "Грейдовая структура утверждена",
                    "Грейд тузилмаси тасдиқланди", "Greyd tuzilmasi tasdiqlandi",
                    "Grade structure approved"),
            entry("GRADE_STRUCTURE_LOCKED", "Грейдовая структура заблокирована",
                    "Грейд тузилмаси қулфланди", "Greyd tuzilmasi qulflandi",
                    "Grade structure locked"),
            entry("GRADE_ASSIGNED", "Грейд назначен", "Грейд тайинланди",
                    "Greyd tayinlandi", "Grade assigned"),
            entry("EVALUATION_PANEL_APPROVED", "Комиссия утвердила оценку",
                    "Комиссия баҳолашни тасдиқлади", "Komissiya baholashni tasdiqladi",
                    "Panel approved"));

    /**
     * Localize an audit-action code reaching human output. Unknown codes fall
     * back to the stable forensic code itself (it is a documented, searchable
     * string — never a UUID/PII).
     */
    public static String localizeAction(String action, String locale) {
        if (action == null || action.isBlank()) return "";
        Map<String, String> byLocale = ACTION.get(action);
        if (byLocale == null) return action;
        return byLocale.getOrDefault(loc(locale), byLocale.getOrDefault(DEFAULT_LOCALE, action));
    }

    // ── Locale-aware timestamp formatting (human PDF/DOCX — leak fix) ────────

    // BCP-47 report tag → java.util.Locale for date/number formatting. Both
    // Uzbek variants format with the Uzbek locale (Latin script ICU rules);
    // the visible MONTH/AM-PM words still come from the JVM CLDR data.
    private static final Map<String, Locale> JAVA_LOCALE = Map.of(
            "ru-RU", Locale.forLanguageTag("ru-RU"),
            "uz-Cyrl-UZ", Locale.forLanguageTag("uz-Cyrl-UZ"),
            "uz-Latn-UZ", Locale.forLanguageTag("uz-Latn-UZ"),
            "en-US", Locale.forLanguageTag("en-US"));

    /**
     * Localize a timestamp to the report locale (medium date+time, system zone)
     * for the HUMAN PDF/DOCX surfaces — never the raw ISO-8601 string. The XLSX
     * SIEM/data sheets keep ISO on purpose and must NOT call this. Null → empty;
     * any formatting failure falls back to ISO so a report never breaks.
     */
    public static String formatTimestamp(OffsetDateTime ts, String locale) {
        if (ts == null) return "";
        try {
            Locale java = JAVA_LOCALE.getOrDefault(loc(locale), JAVA_LOCALE.get(DEFAULT_LOCALE));
            return DateTimeFormatter
                    .ofLocalizedDateTime(FormatStyle.MEDIUM)
                    .withLocale(java)
                    .format(ts.atZoneSameInstant(ZoneId.systemDefault()));
        } catch (RuntimeException e) {
            return ts.toString();
        }
    }

    private static String loc(String locale) {
        return locale == null ? DEFAULT_LOCALE : locale;
    }

    private static Map.Entry<String, Map<String, String>> entry(
            String key, String ru, String uzCyrl, String uzLatn, String en) {
        return Map.entry(key, Map.of(
                "ru-RU", ru,
                "uz-Cyrl-UZ", uzCyrl,
                "uz-Latn-UZ", uzLatn,
                "en-US", en));
    }
}

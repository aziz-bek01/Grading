package uz.hrlab.grading.integration.imports.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.integration.excel.GuideSheet;
import uz.hrlab.grading.integration.excel.GuideSheet.GuideRow;
import uz.hrlab.grading.integration.imports.domain.ImportTemplateCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the bilingual (Uzbek-Cyrillic + Russian) "guide" sheet that is
 * appended as sheet #2 to every downloadable import template.
 *
 * <p><b>Source of truth for the column rules is the actual importer.</b> Each
 * per-column row below is derived directly from what the parser
 * ({@code ExcelParser}), the validator ({@code ImportValidator} levels 2-3),
 * and the per-template {@code *RowCommitter} truly require — required vs
 * optional, the exact enum codes they accept, decimal / integer / date
 * expectations, and the cross-reference rules. If a committer changes an enum
 * or a required field, this class must change with it (covered by
 * {@code ImportTemplateGuideTest}).
 *
 * <p>The guide is informational only: it is never parsed on upload. The
 * importer always targets the DATA sheet (index 0 / by name), and the guide
 * sheet is ignored — see {@code ExcelParser}.
 */
@Component
public class ImportTemplateGuide {

    /** Sheet name for the guide tab (≤ 31 chars, Excel limit). */
    public static final String GUIDE_SHEET_NAME = "Йўриқнома-Инструкция";

    /** Column headers of the guide table itself (bilingual). */
    private static final String[] GUIDE_TABLE_HEADER = {
            "Устун / Колонка",
            "Мажбурий? / Обязательно?",
            "Формат / Тип (Формат / Тип)",
            "Қабул қилинадиган қийматлар / Допустимые значения",
            "Мисол / Пример",
            "Кўп учрайдиган хато / Частая ошибка",
    };

    /** Width (chars) for the 6 guide columns. */
    private static final int[] GUIDE_WIDTHS = { 26, 22, 34, 44, 22, 50 };

    private static final String REQUIRED = "Ҳа / Да";
    private static final String OPTIONAL = "Йўқ / Нет";

    /**
     * Build the guide sheet for the given template, or {@code null} when the
     * template code is unknown (caller then falls back to a data-only
     * workbook). The data-sheet name is passed so the general notes can name
     * the exact tab the user must not rename.
     */
    public GuideSheet buildFor(String templateCode, String dataSheetName) {
        List<ColumnSpec> columns = columnsFor(templateCode);
        if (columns == null) {
            return null;
        }
        List<GuideRow> rows = new ArrayList<>();

        // Title.
        rows.add(GuideRow.header(
                "ЙЎРИҚНОМА — Қандай тўлдириш керак / ИНСТРУКЦИЯ — Как заполнять"));

        // General notes block.
        for (String note : generalNotes(dataSheetName)) {
            rows.add(GuideRow.body(note));
        }
        rows.add(GuideRow.body("")); // spacer

        // Per-column table header.
        rows.add(GuideRow.header(GUIDE_TABLE_HEADER));

        // Per-column rows.
        for (ColumnSpec c : columns) {
            rows.add(GuideRow.body(
                    c.column(),
                    c.required() ? REQUIRED : OPTIONAL,
                    c.format(),
                    c.allowed(),
                    c.example(),
                    c.commonMistake()));
        }

        return new GuideSheet(GUIDE_SHEET_NAME, GUIDE_WIDTHS, rows);
    }

    // -----------------------------------------------------------------
    // General notes (top of every guide)
    // -----------------------------------------------------------------

    private static List<String> generalNotes(String dataSheetName) {
        return List.of(
                "1) Маълумотларни биринчи варақ («" + dataSheetName + "») га киритинг. "
                        + "Данные вводите ТОЛЬКО на первом листе («" + dataSheetName + "»).",
                "2) Сарлавҳа (биринчи) қаторни ўзгартирманг, ўрнини алмаштирманг ва ўчирманг. "
                        + "Не переименовывайте, не меняйте порядок и не удаляйте строку заголовков.",
                "3) Варақ номини ўзгартирманг. Не меняйте имя листа с данными.",
                "4) Ҳар бир қаторда — битта ёзув. Одна запись = одна строка.",
                "5) DEMO банер қаторини ўчириб ташланг ва ўз маълумотингизни ёзинг. "
                        + "Удалите строку с пометкой DEMO и впишите свои данные.",
                "6) Сонларни нуқта (.) билан ёзинг, минг ажратгич ишлатманг (масалан 1234.50). "
                        + "Числа — с точкой как разделителем дробной части, без разделителей тысяч (например 1234.50).",
                "7) Саналар YYYY-MM-DD кўринишида (масалан 2026-01-15). "
                        + "Даты в формате ГГГГ-ММ-ДД (например 2026-01-15).",
                "8) Код устунлари (масалан external_id, parent_external_id, department_external_id) "
                        + "лойиҳада мавжуд кодларга мос келиши шарт. "
                        + "Колонки с кодами должны ссылаться на коды, уже существующие в проекте.",
                "9) Ҳужайра = , + , - , @ белгилари билан бошланмасин (формула киритиш хавфи). "
                        + "Ячейка не должна начинаться с = , + , - , @ (защита от формул).");
    }

    // -----------------------------------------------------------------
    // Per-template column specs — derived from the real importer rules
    // -----------------------------------------------------------------

    private static List<ColumnSpec> columnsFor(String templateCode) {
        return switch (templateCode) {
            case ImportTemplateCode.ORG_STRUCTURE_V1       -> orgStructureColumns();
            case ImportTemplateCode.POSITION_CATALOG_V1    -> positionCatalogColumns();
            case ImportTemplateCode.JOB_PROFILE_V1         -> jobProfileColumns();
            case ImportTemplateCode.METHODOLOGY_FACTORS_V1 -> methodologyColumns();
            case ImportTemplateCode.GRADE_BANDS_V1         -> gradeBandsColumns();
            default -> null;
        };
    }

    /** ORG_STRUCTURE_V1 — see {@code OrgStructureRowCommitter}. */
    private static List<ColumnSpec> orgStructureColumns() {
        return List.of(
                new ColumnSpec("external_id", true,
                        "Матн / Текст. Лойиҳа ичида ноёб. Уникален в проекте.",
                        "Ихтиёрий код / Любой код",
                        "CFO-OFFICE",
                        "Дубликат код — қатор рад этилади. Дубликат кода — строка отклоняется."),
                new ColumnSpec("name", true,
                        "Матн / Текст",
                        "Эркин матн / Свободный текст",
                        "Молия бошқармаси",
                        "Бўш қолдириш мумкин эмас. Нельзя оставлять пустым."),
                new ColumnSpec("parent_external_id", false,
                        "Матн / Текст. Юқори бўлинманинг external_id'си. external_id родителя.",
                        "Ушбу файл ёки лойиҳадаги мавжуд external_id. "
                                + "Существующий external_id из этого файла или проекта. "
                                + "Илдиз учун бўш қолдиринг. Для корня — пусто.",
                        "CEO-OFFICE",
                        "Мавжуд бўлмаган ота-код → MISSING_PARENT. "
                                + "Несуществующий код родителя → MISSING_PARENT."),
                new ColumnSpec("level", false,
                        "Бутун сон / Целое число (маълумот учун / справочно)",
                        "1, 2, 3, ...",
                        "2",
                        "Иерархия parent_external_id орқали қурилади, level эмас. "
                                + "Иерархия строится по parent_external_id, а не по level."),
                new ColumnSpec("type", false,
                        "Код (катта ҳарф) / Код (верхний регистр). "
                                + "Бўш бўлса DEPARTMENT. Пусто → DEPARTMENT.",
                        "BRANCH, DEPARTMENT, DIVISION, UNIT",
                        "DEPARTMENT",
                        "Нотўғри қиймат (масалан OFFICE) → қатор рад этилади "
                                + "(INVALID_DEPARTMENT_TYPE), рухсат этилган қийматлар: "
                                + "BRANCH, DEPARTMENT, DIVISION, UNIT. "
                                + "Недопустимое значение (например OFFICE) → строка "
                                + "отклоняется (INVALID_DEPARTMENT_TYPE); допустимы: "
                                + "BRANCH, DEPARTMENT, DIVISION, UNIT."),
                new ColumnSpec("location_code", false,
                        "Матн / Текст (маълумот учун / справочно)",
                        "Эркин код / Свободный код",
                        "HQ",
                        "—"),
                new ColumnSpec("cost_center_code", false,
                        "Матн / Текст (маълумот учун / справочно)",
                        "Эркин код / Свободный код",
                        "CC-110",
                        "—"),
                new ColumnSpec("description", false,
                        "Матн / Текст (маълумот учун / справочно)",
                        "Эркин матн / Свободный текст",
                        "Молиявий режалаштириш",
                        "—"));
    }

    /** POSITION_CATALOG_V1 — see {@code PositionCatalogRowCommitter}. */
    private static List<ColumnSpec> positionCatalogColumns() {
        return List.of(
                new ColumnSpec("external_id", true,
                        "Матн / Текст. Лойиҳа ичида ноёб. Уникален в проекте.",
                        "Ихтиёрий код / Любой код",
                        "POS-CFO",
                        "Дубликат код — қатор рад этилади. Дубликат кода — строка отклоняется."),
                new ColumnSpec("title", true,
                        "Матн / Текст",
                        "Эркин матн / Свободный текст",
                        "Молия директори",
                        "Бўш қолдириш мумкин эмас. Нельзя оставлять пустым."),
                new ColumnSpec("department_external_id", true,
                        "Матн / Текст. Бўлинманинг external_id'си. external_id подразделения.",
                        "Лойиҳада мавжуд бўлинма external_id. "
                                + "Существующий external_id подразделения в проекте.",
                        "CFO-OFFICE",
                        "Топилмаган бўлинма → MISSING_DEPARTMENT. Сначала импортируйте оргструктуру."),
                new ColumnSpec("status", false,
                        "Код (катта ҳарф) / Код (верхний регистр). "
                                + "Бўш бўлса ACTIVE. Пусто → ACTIVE.",
                        "DRAFT, ACTIVE, ARCHIVED",
                        "ACTIVE",
                        "Нотўғри қиймат → қатор рад этилади "
                                + "(INVALID_POSITION_STATUS), рухсат этилган қийматлар: "
                                + "DRAFT, ACTIVE, ARCHIVED. "
                                + "Недопустимое значение → строка отклоняется "
                                + "(INVALID_POSITION_STATUS); допустимы: "
                                + "DRAFT, ACTIVE, ARCHIVED."),
                new ColumnSpec("function", false,
                        "Матн / Текст",
                        "Эркин матн / Свободный текст",
                        "Finance",
                        "—"),
                new ColumnSpec("category", false,
                        "Матн / Текст",
                        "Эркин матн / Свободный текст (масалан MANAGER, SPECIALIST)",
                        "MANAGER",
                        "—"),
                new ColumnSpec("job_family", false,
                        "Матн / Текст",
                        "Эркин матн / Свободный текст",
                        "Finance",
                        "—"),
                new ColumnSpec("job_level", false,
                        "Матн / Текст",
                        "Эркин матн / Свободный текст",
                        "Senior",
                        "—"),
                new ColumnSpec("grade_code", false,
                        "Матн / Текст (маълумот учун / справочно)",
                        "Эркин код / Свободный код",
                        "G12",
                        "Ҳозирча импортда грейд боғланмайди. Привязка к грейду пока не выполняется."),
                new ColumnSpec("headcount", false,
                        "Бутун сон / Целое число (маълумот учун / справочно)",
                        "0, 1, 2, ...",
                        "1",
                        "—"));
    }

    /**
     * JOB_PROFILE_V1 — see {@code JobProfileRowCommitter}. Each row creates a
     * DRAFT job profile (version 1) for the referenced position. Required:
     * {@code position_external_id}, {@code purpose}. Other rich columns map to
     * extra i18n fields and are optional (a DRAFT may be partial). The position
     * MUST already exist (import positions first) and only ONE active profile
     * per position is allowed.
     */
    private static List<ColumnSpec> jobProfileColumns() {
        return List.of(
                new ColumnSpec("position_external_id", true,
                        "Матн / Текст. Лавозимнинг external_id'си. external_id должности. "
                                + "→ профиль шу лавозимга боғланади. → профиль привязывается к этой должности.",
                        "Лойиҳада АВВАЛ импорт қилинган лавозим коди (импорт тартиби: "
                                + "оргструктура → лавозимлар → лавозим профиллари). "
                                + "Код должности, импортированной РАНЕЕ (порядок импорта: "
                                + "оргструктура → должности → профили должностей).",
                        "POS-CFO",
                        "Лавозим топилмаса → MISSING_POSITION. Олдин лавозимларни импорт қилинг. "
                                + "Если должность не найдена → MISSING_POSITION. Сначала импортируйте должности. "
                                + "Бир лавозимда фаол профиль битта бўлади (такрор → PROFILE_ALREADY_EXISTS). "
                                + "У должности может быть только один активный профиль (дубль → PROFILE_ALREADY_EXISTS)."),
                new ColumnSpec("purpose", true,
                        "Матн / Текст → purpose (мақсад) майдонига. Текст → поле purpose (цель).",
                        "Эркин матн / Свободный текст",
                        "Холдинг молиявий стратегиясини бошқариш",
                        "Бўш қолдириш мумкин эмас (мажбурий). Нельзя оставлять пустым (обязательно)."),
                new ColumnSpec("responsibilities", false,
                        "Матн / Текст → main_duties (асосий вазифалар) майдонига. "
                                + "Текст → поле main_duties (основные обязанности).",
                        "Эркин матн / Свободный текст",
                        "Бюджетни бошқариш; аудитни юритиш",
                        "Бўш бўлса профиль барибир яратилади (DRAFT). "
                                + "Если пусто — профиль всё равно создаётся (DRAFT)."),
                new ColumnSpec("requirements", false,
                        "Матн / Текст → knowledge_skills (билим/кўникма) майдонига. "
                                + "Текст → поле knowledge_skills (знания/навыки).",
                        "Эркин матн / Свободный текст",
                        "10+ йил молия соҳасида тажриба",
                        "Бўш бўлса профиль барибир яратилади (DRAFT). "
                                + "Если пусто — профиль всё равно создаётся (DRAFT)."),
                new ColumnSpec("main_duties", false,
                        "Матн / Текст", "Эркин матн / Свободный текст",
                        "Бюджетни эгаллаш; кенгашга ҳисобот", "—"),
                new ColumnSpec("responsibility_area", false,
                        "Матн / Текст", "Эркин матн / Свободный текст",
                        "Молиявий саломатлик", "—"),
                new ColumnSpec("authority", false,
                        "Матн / Текст", "Эркин матн / Свободный текст",
                        "5 млн USD гача бюджетни тасдиқлаш", "—"),
                new ColumnSpec("kpi_expected_results", false,
                        "Матн / Текст", "Эркин матн / Свободный текст",
                        "Ойлик ёпилиш ўз вақтида", "—"),
                new ColumnSpec("education_requirements", false,
                        "Матн / Текст", "Эркин матн / Свободный текст",
                        "MBA ёки молия магистри", "—"),
                new ColumnSpec("experience_requirements", false,
                        "Матн / Текст", "Эркин матн / Свободный текст",
                        "10+ йил раҳбарлик", "—"),
                new ColumnSpec("knowledge_skills", false,
                        "Матн / Текст", "Эркин матн / Свободный текст",
                        "IFRS; SAP S/4; риск-менежмент", "—"),
                new ColumnSpec("working_conditions", false,
                        "Матн / Текст", "Эркин матн / Свободный текст",
                        "Бош офис; ~40 соат/ҳафта", "—"),
                new ColumnSpec("actualization_date", false,
                        "Сана / Дата — YYYY-MM-DD",
                        "Масалан 2026-01-15 / Например 2026-01-15",
                        "2026-01-15",
                        "Бошқа форматда сана ёзманг. Не используйте иной формат даты."));
    }

    /**
     * METHODOLOGY_FACTORS_V1 — see {@code MethodologyFactorsRowCommitter}. The
     * file carries ONE methodology: the methodology-level metadata columns
     * ({@code methodology_code}, {@code methodology_name}, {@code methodology_type},
     * {@code scoring_mode}, {@code target_total_points}) are repeated identically
     * on every row, plus one row per {@code factor + level}. Commit is
     * create-or-upsert into the project's DRAFT methodology of that code.
     */
    private static List<ColumnSpec> methodologyColumns() {
        String sameOnEveryRow = "Ҳар бир қаторда бир хил ёзилсин (файлда битта методология). "
                + "Указывайте одинаково в каждой строке (одна методология на файл).";
        return List.of(
                new ColumnSpec("methodology_code", true,
                        "Матн / Текст. Лойиҳа ичида методология коди. Код методологии в проекте.",
                        "Ихтиёрий код / Любой код (масалан ACME-GRADING)",
                        "ACME-GRADING",
                        "Мавжуд методология DRAFT бўлмаса қатор рад этилади "
                                + "(METHODOLOGY_NOT_DRAFT). Если методология не в статусе DRAFT — "
                                + "строка отклоняется (METHODOLOGY_NOT_DRAFT). " + sameOnEveryRow),
                new ColumnSpec("methodology_name", true,
                        "Матн / Текст (асосий тил ru-RU). Текст (основной язык ru-RU).",
                        "Эркин матн / Свободный текст",
                        "Методология грейдирования ACME",
                        "Бўш қолдириш мумкин эмас. Нельзя оставлять пустым. " + sameOnEveryRow),
                new ColumnSpec("methodology_type", true,
                        "Код (катта ҳарф) / Код (верхний регистр).",
                        "CLASSIC_8_FACTOR, EXTENDED_11_CRITERIA, CUSTOM",
                        "CLASSIC_8_FACTOR",
                        "Нотўғри қиймат → INVALID_METHODOLOGY_TYPE. "
                                + "Недопустимое значение → INVALID_METHODOLOGY_TYPE. " + sameOnEveryRow),
                new ColumnSpec("scoring_mode", true,
                        "Код (катта ҳарф) / Код (верхний регистр).",
                        "DIRECT_POINTS, WEIGHTED_POINTS, WEIGHTED_SCALE",
                        "WEIGHTED_POINTS",
                        "Нотўғри қиймат → INVALID_SCORING_MODE. "
                                + "Недопустимое значение → INVALID_SCORING_MODE. " + sameOnEveryRow),
                new ColumnSpec("target_total_points", true,
                        "Сон / Число (нуқта билан / с точкой)",
                        "Мусбат сон / Положительное число",
                        "1000",
                        "Матн эмас, сон. Не текст, а число → INVALID_TOTAL_POINTS. " + sameOnEveryRow),
                new ColumnSpec("factor_code", true,
                        "Матн / Текст (катта ҳарф тавсия этилади). Текст (рекомендуется верхний регистр).",
                        "Масалан KNOWLEDGE, EXPERIENCE / Например KNOWLEDGE, EXPERIENCE",
                        "KNOWLEDGE",
                        "Бир файлда бир фактор коди такрорланиб, ҳар хил даражаларга эга бўлади. "
                                + "Один код фактора повторяется на несколько уровней в файле."),
                new ColumnSpec("factor_name", true,
                        "Матн / Текст",
                        "Эркин матн / Свободный текст",
                        "Билим",
                        "Бўш қолдириш мумкин эмас. Нельзя оставлять пустым."),
                new ColumnSpec("level_code", true,
                        "Матн / Текст",
                        "Масалан L1, L2, L3 / Например L1, L2, L3",
                        "L1",
                        "Ҳар бир фактор учун даражалар такрорланмасин. Уровни в рамках фактора не дублировать."),
                new ColumnSpec("level_name", true,
                        "Матн / Текст",
                        "Эркин матн / Свободный текст",
                        "Бошланғич",
                        "Бўш қолдириш мумкин эмас. Нельзя оставлять пустым."),
                new ColumnSpec("weight", true,
                        "Ўнли сон / Десятичное число (нуқта билан / с точкой)",
                        "0–100 (фоиз / процент)",
                        "12.5",
                        "Вергул эмас, нуқта ишлатинг (12,5 эмас 12.5). Точка, не запятая (12.5)."),
                new ColumnSpec("score", true,
                        "Сон / Число",
                        "Мусбат сон / Положительное число",
                        "40",
                        "Матн киритманг, фақат сон. Не вводите текст, только число."),
                new ColumnSpec("factor_weight", false,
                        "Ўнли сон / Десятичное число (дубликат маълумот / дублирующее поле)",
                        "0–100",
                        "12.5",
                        "weight билан бир хил қийматни ёзинг. Заполняйте тем же значением, что и weight."),
                new ColumnSpec("level_points", false,
                        "Сон / Число (дубликат маълумот / дублирующее поле)",
                        "Мусбат сон / Положительное число",
                        "40",
                        "score билан мос келсин. Должно совпадать со score."),
                new ColumnSpec("level_order", false,
                        "Бутун сон / Целое число",
                        "1, 2, 3, ...",
                        "1",
                        "—"),
                new ColumnSpec("level_description", false,
                        "Матн / Текст",
                        "Эркин матн / Свободный текст",
                        "Билим — бошланғич даража",
                        "—"));
    }

    /** GRADE_BANDS_V1 — see {@code GradeBandsRowCommitter}. */
    private static List<ColumnSpec> gradeBandsColumns() {
        return List.of(
                new ColumnSpec("grade_code", true,
                        "Мусбат бутун сон / Положительное целое число. "
                                + "grade_number сифатида ўқилади. Читается как grade_number.",
                        "1, 2, 3 ... (масалан G7 ЭМАС, балки 7). "
                                + "Только цифры (например 7, а НЕ G7).",
                        "7",
                        "G7 каби ҳарфли код INVALID_GRADE_NUMBER беради. "
                                + "Буквенный код вроде G7 даёт INVALID_GRADE_NUMBER — пишите 7."),
                new ColumnSpec("min_score", true,
                        "Ўнли сон / Десятичное число (нуқта билан / с точкой)",
                        "min_score ≤ max_score",
                        "300.0001",
                        "min_score > max_score → INVALID_BAND_RANGE."),
                new ColumnSpec("max_score", true,
                        "Ўнли сон / Десятичное число (нуқта билан / с точкой)",
                        "max_score ≥ min_score",
                        "350",
                        "Вергул эмас, нуқта ишлатинг. Точка вместо запятой."),
                new ColumnSpec("label", false,
                        "Матн / Текст. Бўш бўлса 'Grade N'. Пусто → 'Grade N'.",
                        "Эркин матн / Свободный текст",
                        "Team Lead",
                        "—"),
                new ColumnSpec("description", false,
                        "Матн / Текст (маълумот учун / справочно)",
                        "Эркин матн / Свободный текст",
                        "Grade 7 - Team Lead",
                        "—"));
    }

    /** One per-column guidance row (bilingual, derived from importer rules). */
    private record ColumnSpec(
            String column,
            boolean required,
            String format,
            String allowed,
            String example,
            String commonMistake) { }
}

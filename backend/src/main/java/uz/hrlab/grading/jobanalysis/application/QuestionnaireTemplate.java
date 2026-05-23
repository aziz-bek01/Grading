package uz.hrlab.grading.jobanalysis.application;

import uz.hrlab.grading.jobanalysis.domain.JobAnalysisQuestion;
import uz.hrlab.grading.jobanalysis.domain.QuestionType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MVP 1 questionnaire template catalogue. Two seeded templates per task —
 * STANDARD_V1 covers individual contributor roles; EXECUTIVE_V1 adds
 * leadership and decision-authority questions.
 *
 * <p>In MVP 2 templates will be stored in the control-plane database and
 * loaded from {@code methodology_templates} (database-blueprint §3.1). For
 * MVP 1 we ship them as code so the questionnaire can be instantiated
 * deterministically without an additional schema dependency.
 */
public final class QuestionnaireTemplate {

    public static final String STANDARD_V1 = "STANDARD_V1";
    public static final String EXECUTIVE_V1 = "EXECUTIVE_V1";

    private QuestionnaireTemplate() { }

    public static Map<String, String> nameI18nFor(String templateCode) {
        return switch (templateCode) {
            case STANDARD_V1 -> Map.of(
                    "ru-RU", "Стандартный анализ должности (v1)",
                    "uz-Cyrl-UZ", "Стандарт лавозим таҳлили (v1)",
                    "uz-Latn-UZ", "Standart lavozim tahlili (v1)",
                    "en-US", "Standard job analysis (v1)");
            case EXECUTIVE_V1 -> Map.of(
                    "ru-RU", "Анализ руководящей должности (v1)",
                    "uz-Cyrl-UZ", "Раҳбарлик лавозими таҳлили (v1)",
                    "uz-Latn-UZ", "Rahbarlik lavozimi tahlili (v1)",
                    "en-US", "Executive job analysis (v1)");
            default -> throw new IllegalArgumentException(
                    "Unknown questionnaire template: " + templateCode);
        };
    }

    public static List<JobAnalysisQuestion> questionsFor(String templateCode) {
        return switch (templateCode) {
            case STANDARD_V1 -> standardQuestions();
            case EXECUTIVE_V1 -> executiveQuestions();
            default -> throw new IllegalArgumentException(
                    "Unknown questionnaire template: " + templateCode);
        };
    }

    private static List<JobAnalysisQuestion> standardQuestions() {
        return List.of(
                question("Q_PURPOSE", "Какова основная цель должности?",
                        QuestionType.LONG_TEXT, null, true, 10),
                question("Q_MAIN_DUTIES", "Перечислите основные обязанности.",
                        QuestionType.LONG_TEXT, null, true, 20),
                question("Q_RESPONSIBILITY_AREA", "За что должность несёт ответственность?",
                        QuestionType.LONG_TEXT, null, true, 30),
                question("Q_KPI", "Каковы ожидаемые результаты / KPI?",
                        QuestionType.LONG_TEXT, null, true, 40),
                question("Q_REPORTS_TO", "Кому подчиняется (название должности)?",
                        QuestionType.TEXT, null, true, 50),
                question("Q_DIRECT_REPORTS", "Сколько прямых подчинённых?",
                        QuestionType.NUMBER, null, false, 60),
                question("Q_DECISION_AUTHORITY",
                        "Какие решения принимает должность самостоятельно?",
                        QuestionType.SINGLE_CHOICE,
                        List.of("OPERATIONAL", "TACTICAL", "STRATEGIC"),
                        true, 70),
                question("Q_INTERNAL_INTERACTIONS",
                        "С какими внутренними подразделениями взаимодействует?",
                        QuestionType.LONG_TEXT, null, false, 80),
                question("Q_EXTERNAL_INTERACTIONS",
                        "С какими внешними сторонами взаимодействует?",
                        QuestionType.LONG_TEXT, null, false, 90),
                question("Q_COMPLEXITY_RATING",
                        "Оцените сложность работы (1 — простая, 5 — очень сложная)",
                        QuestionType.RATING_SCALE,
                        List.of("1", "2", "3", "4", "5"), true, 100)
        );
    }

    private static List<JobAnalysisQuestion> executiveQuestions() {
        return List.of(
                question("Q_PURPOSE", "Какова основная цель руководящей должности?",
                        QuestionType.LONG_TEXT, null, true, 10),
                question("Q_STRATEGIC_SCOPE", "Опишите стратегический объём ответственности.",
                        QuestionType.LONG_TEXT, null, true, 20),
                question("Q_REPORTS_TO", "Кому подчиняется?",
                        QuestionType.TEXT, null, true, 30),
                question("Q_DIRECT_REPORTS", "Количество прямых подчинённых",
                        QuestionType.NUMBER, null, true, 40),
                question("Q_TOTAL_REPORTS", "Общее количество подчинённых (с учётом косвенных)",
                        QuestionType.NUMBER, null, true, 50),
                question("Q_BUDGET_AUTHORITY",
                        "В рамках какого бюджета принимает решения?",
                        QuestionType.SINGLE_CHOICE,
                        List.of("NONE", "DEPARTMENT", "DIVISION", "COMPANY"),
                        true, 60),
                question("Q_DECISION_AUTHORITY",
                        "Какие стратегические решения принимает самостоятельно?",
                        QuestionType.LONG_TEXT, null, true, 70),
                question("Q_KPI", "Каковы ключевые KPI руководителя?",
                        QuestionType.LONG_TEXT, null, true, 80),
                question("Q_INTERNAL_INTERACTIONS",
                        "Ключевые внутренние стейкхолдеры",
                        QuestionType.LONG_TEXT, null, true, 90),
                question("Q_EXTERNAL_INTERACTIONS",
                        "Ключевые внешние стейкхолдеры (регуляторы, ассоциации, клиенты)",
                        QuestionType.LONG_TEXT, null, false, 100),
                question("Q_COMPLEXITY_RATING",
                        "Оцените сложность роли (1 — простая, 5 — очень сложная)",
                        QuestionType.RATING_SCALE,
                        List.of("1", "2", "3", "4", "5"), true, 110)
        );
    }

    private static JobAnalysisQuestion question(String code, String promptRu,
                                                QuestionType type, List<String> options,
                                                boolean required, int sortOrder) {
        return new JobAnalysisQuestion(
                UUID.randomUUID(),
                code,
                Map.of("ru-RU", promptRu),
                Map.of(),
                type,
                options,
                required,
                sortOrder);
    }
}

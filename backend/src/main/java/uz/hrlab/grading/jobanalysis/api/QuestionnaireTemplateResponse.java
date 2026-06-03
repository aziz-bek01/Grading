package uz.hrlab.grading.jobanalysis.api;

import java.util.Map;

/**
 * One entry in the questionnaire-template catalogue. Serializes (global
 * SNAKE_CASE) as {@code {"code": "...", "name_i18n": {...}, "question_count": N}}.
 */
public record QuestionnaireTemplateResponse(
        String code,
        Map<String, String> nameI18n,
        int questionCount
) {
}

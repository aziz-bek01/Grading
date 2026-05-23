package uz.hrlab.grading.jobanalysis.api;

import uz.hrlab.grading.jobanalysis.domain.JobAnalysisQuestion;
import uz.hrlab.grading.jobanalysis.domain.QuestionType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record JobAnalysisQuestionResponse(
        UUID id,
        String code,
        Map<String, String> promptI18n,
        Map<String, String> helpTextI18n,
        QuestionType questionType,
        List<String> options,
        boolean required,
        int sortOrder
) {
    public static JobAnalysisQuestionResponse from(JobAnalysisQuestion q) {
        return new JobAnalysisQuestionResponse(
                q.getId(), q.getCode(), q.getPromptI18n(), q.getHelpTextI18n(),
                q.getQuestionType(), q.getOptions(), q.isRequired(), q.getSortOrder());
    }
}

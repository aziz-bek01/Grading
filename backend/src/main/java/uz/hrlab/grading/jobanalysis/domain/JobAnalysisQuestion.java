package uz.hrlab.grading.jobanalysis.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Embedded question value object — stored as a JSONB array on the
 * questionnaire entity. Identity ({@code id}) is generated server-side and
 * stable across the lifetime of the questionnaire (answers reference it).
 */
public final class JobAnalysisQuestion {

    private UUID id;
    private String code;
    private Map<String, String> promptI18n;
    private Map<String, String> helpTextI18n;
    private QuestionType questionType;
    private List<String> options;
    private boolean required;
    private int sortOrder;

    /** Jackson reflective constructor. */
    public JobAnalysisQuestion() { }

    public JobAnalysisQuestion(UUID id, String code, Map<String, String> promptI18n,
                               Map<String, String> helpTextI18n,
                               QuestionType questionType, List<String> options,
                               boolean required, int sortOrder) {
        this.id = id;
        this.code = code;
        this.promptI18n = promptI18n == null ? new HashMap<>() : new HashMap<>(promptI18n);
        this.helpTextI18n = helpTextI18n == null ? new HashMap<>() : new HashMap<>(helpTextI18n);
        this.questionType = questionType;
        this.options = options;
        this.required = required;
        this.sortOrder = sortOrder;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public Map<String, String> getPromptI18n() { return promptI18n; }
    public Map<String, String> getHelpTextI18n() { return helpTextI18n; }
    public QuestionType getQuestionType() { return questionType; }
    public List<String> getOptions() { return options; }
    public boolean isRequired() { return required; }
    public int getSortOrder() { return sortOrder; }

    public void setId(UUID id) { this.id = id; }
    public void setCode(String code) { this.code = code; }
    public void setPromptI18n(Map<String, String> promptI18n) { this.promptI18n = promptI18n; }
    public void setHelpTextI18n(Map<String, String> helpTextI18n) { this.helpTextI18n = helpTextI18n; }
    public void setQuestionType(QuestionType questionType) { this.questionType = questionType; }
    public void setOptions(List<String> options) { this.options = options; }
    public void setRequired(boolean required) { this.required = required; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}

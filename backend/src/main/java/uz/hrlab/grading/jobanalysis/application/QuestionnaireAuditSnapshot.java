package uz.hrlab.grading.jobanalysis.application;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import uz.hrlab.grading.audit.application.AuditJsonRedactor;
import uz.hrlab.grading.jobanalysis.infrastructure.JobAnalysisAnswerJpaEntity;
import uz.hrlab.grading.jobanalysis.infrastructure.JobAnalysisQuestionnaireJpaEntity;

/**
 * Builds redacted before/after JSON snapshots for questionnaire + answer
 * entities. Policy mirrors {@link uz.hrlab.grading.audit.application.AuditJsonRedactor}:
 * no salary fields, no answer-text body verbatim — only previews and counts.
 */
@Component
public class QuestionnaireAuditSnapshot {

    private final AuditJsonRedactor redactor;

    public QuestionnaireAuditSnapshot(AuditJsonRedactor redactor) {
        this.redactor = redactor;
    }

    public JsonNode of(JobAnalysisQuestionnaireJpaEntity q) {
        if (q == null) return null;
        return redactor.builder()
                .put("id", q.getId())
                .put("tenantId", q.getTenantId())
                .put("projectId", q.getProjectId())
                .put("positionId", q.getPositionId())
                .put("templateCode", q.getTemplateCode())
                .put("status", q.getStatus() == null ? null : q.getStatus().name())
                .putRaw("questionnaireVersion", q.getQuestionnaireVersion())
                .putRaw("questionCount", q.getQuestions() == null ? 0 : q.getQuestions().size())
                .addI18nPreviews("nameI18n", q.getNameI18n())
                .build();
    }

    public JsonNode of(JobAnalysisAnswerJpaEntity a) {
        if (a == null) return null;
        String text = a.getAnswerText();
        return redactor.builder()
                .put("id", a.getId())
                .put("tenantId", a.getTenantId())
                .put("projectId", a.getProjectId())
                .put("questionnaireId", a.getQuestionnaireId())
                .put("questionId", a.getQuestionId())
                .put("respondentUserId", a.getRespondentUserId())
                .put("answerText.preview", text == null ? null
                        : (text.length() <= AuditJsonRedactor.PREVIEW_CHARS
                            ? text
                            : text.substring(0, AuditJsonRedactor.PREVIEW_CHARS) + "..."))
                .putRaw("answerText.length", text == null ? 0 : text.length())
                .putRaw("answerChoices.count", a.getAnswerChoices() == null ? 0
                        : a.getAnswerChoices().size())
                .put("answerNumber", a.getAnswerNumber() == null ? null
                        : a.getAnswerNumber().toPlainString())
                .build();
    }
}

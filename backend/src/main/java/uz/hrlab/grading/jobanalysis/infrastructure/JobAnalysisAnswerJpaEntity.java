package uz.hrlab.grading.jobanalysis.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;
import uz.hrlab.grading.jobanalysis.domain.JobAnalysisAnswer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "job_analysis_answers")
public class JobAnalysisAnswerJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "questionnaire_id", nullable = false, updatable = false)
    private UUID questionnaireId;

    @Column(name = "question_id", nullable = false, updatable = false)
    private UUID questionId;

    @Column(name = "answer_text", length = 20000)
    private String answerText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer_choices", columnDefinition = "jsonb")
    private List<String> answerChoices;

    @Column(name = "answer_number", precision = 18, scale = 4)
    private BigDecimal answerNumber;

    @Column(name = "respondent_user_id", nullable = false, updatable = false)
    private UUID respondentUserId;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    protected JobAnalysisAnswerJpaEntity() { }

    public JobAnalysisAnswerJpaEntity(UUID id, UUID tenantId, UUID projectId,
                                      UUID questionnaireId, UUID questionId,
                                      UUID respondentUserId) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.questionnaireId = questionnaireId;
        this.questionId = questionId;
        this.respondentUserId = respondentUserId;
    }

    public JobAnalysisAnswer toDomain() {
        return new JobAnalysisAnswer(id, tenantId, projectId, questionnaireId, questionId,
                answerText, answerChoices, answerNumber, respondentUserId, submittedAt);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public UUID getQuestionnaireId() { return questionnaireId; }
    public UUID getQuestionId() { return questionId; }
    public String getAnswerText() { return answerText; }
    public List<String> getAnswerChoices() { return answerChoices; }
    public BigDecimal getAnswerNumber() { return answerNumber; }
    public UUID getRespondentUserId() { return respondentUserId; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }

    public void setAnswerText(String v) { this.answerText = v; }
    public void setAnswerChoices(List<String> v) {
        this.answerChoices = v == null ? null : new ArrayList<>(v);
    }
    public void setAnswerNumber(BigDecimal v) { this.answerNumber = v; }
    public void setSubmittedAt(OffsetDateTime v) { this.submittedAt = v; }
}

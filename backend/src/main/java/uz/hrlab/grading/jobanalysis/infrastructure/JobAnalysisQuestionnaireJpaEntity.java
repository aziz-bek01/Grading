package uz.hrlab.grading.jobanalysis.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;
import uz.hrlab.grading.jobanalysis.domain.JobAnalysisQuestion;
import uz.hrlab.grading.jobanalysis.domain.JobAnalysisQuestionnaire;
import uz.hrlab.grading.jobanalysis.domain.QuestionnaireStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "job_analysis_questionnaires")
public class JobAnalysisQuestionnaireJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "position_id", nullable = false, updatable = false)
    private UUID positionId;

    @Column(name = "template_code", nullable = false, length = 64, updatable = false)
    private String templateCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "name_i18n", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> nameI18n = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private QuestionnaireStatus status;

    @Column(name = "questionnaire_version", nullable = false)
    private int questionnaireVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "questions", nullable = false, columnDefinition = "jsonb")
    private List<JobAnalysisQuestion> questions = new ArrayList<>();

    protected JobAnalysisQuestionnaireJpaEntity() { }

    public JobAnalysisQuestionnaireJpaEntity(UUID id, UUID tenantId, UUID projectId,
                                             UUID positionId, String templateCode,
                                             Map<String, String> nameI18n,
                                             QuestionnaireStatus status,
                                             int questionnaireVersion,
                                             List<JobAnalysisQuestion> questions) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.positionId = positionId;
        this.templateCode = templateCode;
        this.nameI18n = nameI18n == null ? new HashMap<>() : new HashMap<>(nameI18n);
        this.status = status;
        this.questionnaireVersion = questionnaireVersion;
        this.questions = questions == null ? new ArrayList<>() : new ArrayList<>(questions);
    }

    public JobAnalysisQuestionnaire toDomain() {
        return new JobAnalysisQuestionnaire(id, tenantId, projectId, positionId,
                templateCode, nameI18n, status, questionnaireVersion, questions);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public UUID getPositionId() { return positionId; }
    public String getTemplateCode() { return templateCode; }
    public Map<String, String> getNameI18n() { return nameI18n; }
    public QuestionnaireStatus getStatus() { return status; }
    public int getQuestionnaireVersion() { return questionnaireVersion; }
    public List<JobAnalysisQuestion> getQuestions() { return questions; }

    public void setStatus(QuestionnaireStatus status) { this.status = status; }
    public void setQuestions(List<JobAnalysisQuestion> questions) {
        this.questions = questions == null ? new ArrayList<>() : new ArrayList<>(questions);
    }
}

package uz.hrlab.grading.jobanalysis.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class JobAnalysisAnswer {

    private final UUID id;
    private final UUID tenantId;
    private final UUID projectId;
    private final UUID questionnaireId;
    private final UUID questionId;
    private final String answerText;
    private final List<String> answerChoices;
    private final BigDecimal answerNumber;
    private final UUID respondentUserId;
    private final OffsetDateTime submittedAt;

    public JobAnalysisAnswer(UUID id, UUID tenantId, UUID projectId, UUID questionnaireId,
                             UUID questionId, String answerText, List<String> answerChoices,
                             BigDecimal answerNumber, UUID respondentUserId,
                             OffsetDateTime submittedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.questionnaireId = questionnaireId;
        this.questionId = questionId;
        this.answerText = answerText;
        this.answerChoices = answerChoices == null ? null : List.copyOf(answerChoices);
        this.answerNumber = answerNumber;
        this.respondentUserId = respondentUserId;
        this.submittedAt = submittedAt;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID projectId() { return projectId; }
    public UUID questionnaireId() { return questionnaireId; }
    public UUID questionId() { return questionId; }
    public String answerText() { return answerText; }
    public List<String> answerChoices() { return answerChoices; }
    public BigDecimal answerNumber() { return answerNumber; }
    public UUID respondentUserId() { return respondentUserId; }
    public OffsetDateTime submittedAt() { return submittedAt; }
}

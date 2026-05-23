package uz.hrlab.grading.jobanalysis.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Domain projection for a questionnaire (no persistence concerns). */
public final class JobAnalysisQuestionnaire {

    private final UUID id;
    private final UUID tenantId;
    private final UUID projectId;
    private final UUID positionId;
    private final String templateCode;
    private final Map<String, String> nameI18n;
    private final QuestionnaireStatus status;
    private final int version;
    private final List<JobAnalysisQuestion> questions;

    public JobAnalysisQuestionnaire(UUID id, UUID tenantId, UUID projectId,
                                    UUID positionId, String templateCode,
                                    Map<String, String> nameI18n,
                                    QuestionnaireStatus status, int version,
                                    List<JobAnalysisQuestion> questions) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.positionId = positionId;
        this.templateCode = templateCode;
        this.nameI18n = nameI18n == null ? new HashMap<>() : new HashMap<>(nameI18n);
        this.status = status;
        this.version = version;
        this.questions = questions == null ? List.of() : List.copyOf(questions);
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID projectId() { return projectId; }
    public UUID positionId() { return positionId; }
    public String templateCode() { return templateCode; }
    public Map<String, String> nameI18n() { return nameI18n; }
    public QuestionnaireStatus status() { return status; }
    public int version() { return version; }
    public List<JobAnalysisQuestion> questions() { return questions; }
}

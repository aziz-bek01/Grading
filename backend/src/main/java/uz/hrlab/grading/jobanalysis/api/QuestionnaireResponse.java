package uz.hrlab.grading.jobanalysis.api;

import uz.hrlab.grading.jobanalysis.domain.JobAnalysisQuestionnaire;
import uz.hrlab.grading.jobanalysis.domain.QuestionnaireStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record QuestionnaireResponse(
        UUID id,
        UUID projectId,
        UUID positionId,
        String templateCode,
        Map<String, String> nameI18n,
        QuestionnaireStatus status,
        int version,
        List<JobAnalysisQuestionResponse> questions
) {
    public static QuestionnaireResponse from(JobAnalysisQuestionnaire q) {
        return new QuestionnaireResponse(
                q.id(), q.projectId(), q.positionId(), q.templateCode(),
                q.nameI18n(), q.status(), q.version(),
                q.questions().stream().map(JobAnalysisQuestionResponse::from).toList());
    }
}

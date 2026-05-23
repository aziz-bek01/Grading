package uz.hrlab.grading.jobanalysis.api;

import uz.hrlab.grading.jobanalysis.domain.JobAnalysisAnswer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AnswerResponse(
        UUID id,
        UUID questionnaireId,
        UUID questionId,
        String answerText,
        List<String> answerChoices,
        BigDecimal answerNumber,
        UUID respondentUserId,
        OffsetDateTime submittedAt
) {
    public static AnswerResponse from(JobAnalysisAnswer a) {
        return new AnswerResponse(a.id(), a.questionnaireId(), a.questionId(),
                a.answerText(), a.answerChoices(), a.answerNumber(),
                a.respondentUserId(), a.submittedAt());
    }
}

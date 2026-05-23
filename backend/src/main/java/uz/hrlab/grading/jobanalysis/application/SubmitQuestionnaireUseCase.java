package uz.hrlab.grading.jobanalysis.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.jobanalysis.domain.JobAnalysisAnswer;
import uz.hrlab.grading.jobanalysis.domain.JobAnalysisQuestion;
import uz.hrlab.grading.jobanalysis.domain.JobAnalysisQuestionnaire;
import uz.hrlab.grading.jobanalysis.domain.QuestionnaireStatus;
import uz.hrlab.grading.jobanalysis.domain.QuestionnaireStatusTransitionPolicy;
import uz.hrlab.grading.jobanalysis.domain.QuestionnaireTransition;
import uz.hrlab.grading.jobanalysis.domain.QuestionnaireTransitionRejectedException;
import uz.hrlab.grading.jobanalysis.infrastructure.JobAnalysisAnswerJpaEntity;
import uz.hrlab.grading.jobanalysis.infrastructure.JobAnalysisAnswerRepository;
import uz.hrlab.grading.jobanalysis.infrastructure.JobAnalysisQuestionnaireJpaEntity;
import uz.hrlab.grading.jobanalysis.infrastructure.JobAnalysisQuestionnaireRepository;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * DRAFT → COMPLETED. Validates that every required question has at least one
 * answer (any respondent). MVP 1: "answered" = any non-null/non-empty field.
 */
@Service
public class SubmitQuestionnaireUseCase {

    private final JobAnalysisQuestionnaireRepository questionnaires;
    private final JobAnalysisAnswerRepository answers;
    private final PositionRepository positions;
    private final AuditService audit;
    private final AbacGate abacGate;
    private final QuestionnaireAuditSnapshot snapshot;
    private final QuestionnaireStatusTransitionPolicy transitionPolicy;

    public SubmitQuestionnaireUseCase(JobAnalysisQuestionnaireRepository questionnaires,
                                      JobAnalysisAnswerRepository answers,
                                      PositionRepository positions,
                                      AuditService audit,
                                      AbacGate abacGate,
                                      QuestionnaireAuditSnapshot snapshot,
                                      QuestionnaireStatusTransitionPolicy transitionPolicy) {
        this.questionnaires = questionnaires;
        this.answers = answers;
        this.positions = positions;
        this.audit = audit;
        this.abacGate = abacGate;
        this.snapshot = snapshot;
        this.transitionPolicy = transitionPolicy;
    }

    @Transactional
    public JobAnalysisQuestionnaire submit(UUID questionnaireId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        JobAnalysisQuestionnaireJpaEntity questionnaire = questionnaires
                .findByIdAndTenantId(questionnaireId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        PositionJpaEntity position = positions
                .findByIdAndTenantId(questionnaire.getPositionId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        abacGate.enforceCanWriteInProject(ctx, questionnaire.getProjectId());
        abacGate.enforceCanWriteInDepartment(ctx, questionnaire.getProjectId(),
                position.getDepartmentId());

        transitionPolicy.check(questionnaire.getStatus(), QuestionnaireTransition.SUBMIT);

        List<JobAnalysisAnswerJpaEntity> all = answers
                .findAllByTenantIdAndQuestionnaireId(ctx.tenantId(), questionnaireId);
        Set<UUID> answeredQuestions = all.stream()
                .filter(SubmitQuestionnaireUseCase::isAnswered)
                .map(JobAnalysisAnswerJpaEntity::getQuestionId)
                .collect(Collectors.toCollection(HashSet::new));

        List<String> missing = questionnaire.getQuestions().stream()
                .filter(JobAnalysisQuestion::isRequired)
                .filter(q -> !answeredQuestions.contains(q.getId()))
                .map(JobAnalysisQuestion::getCode)
                .toList();
        if (!missing.isEmpty()) {
            throw new QuestionnaireTransitionRejectedException(
                    "QUESTIONNAIRE_INCOMPLETE",
                    "Required questions unanswered: " + String.join(",", missing));
        }

        var beforeJson = snapshot.of(questionnaire);
        OffsetDateTime now = OffsetDateTime.now();
        questionnaire.setStatus(QuestionnaireStatus.COMPLETED);
        all.forEach(a -> {
            if (a.getSubmittedAt() == null) a.setSubmittedAt(now);
            answers.save(a);
        });
        questionnaires.save(questionnaire);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(questionnaire.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.JOB_ANALYSIS_SUBMITTED)
                .entityType("JobAnalysisQuestionnaire")
                .entityId(questionnaireId)
                .beforeJson(beforeJson)
                .afterJson(snapshot.of(questionnaire))
                .build());
        return questionnaire.toDomain();
    }

    private static boolean isAnswered(JobAnalysisAnswerJpaEntity a) {
        return (a.getAnswerText() != null && !a.getAnswerText().isBlank())
                || (a.getAnswerChoices() != null && !a.getAnswerChoices().isEmpty())
                || a.getAnswerNumber() != null;
    }
}

package uz.hrlab.grading.jobanalysis.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.application.StatusTransitionExecutor;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
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
    private final QuestionnaireAuditSnapshot snapshot;
    private final QuestionnaireStatusTransitionPolicy transitionPolicy;
    private final StatusTransitionExecutor transitions;

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
        this.snapshot = snapshot;
        this.transitionPolicy = transitionPolicy;
        this.transitions = new StatusTransitionExecutor(abacGate, audit);
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

        // Answers are loaded post-transition-check (see beforeMutate) and reused by
        // the mutation, so they must survive between the two hooks.
        final var loaded = new Object() {
            List<JobAnalysisAnswerJpaEntity> all;
        };
        OffsetDateTime now = OffsetDateTime.now();

        transitions.transition(ctx)
                .abacProjectAndDepartmentWrite(questionnaire.getProjectId(), position.getDepartmentId())
                .checkTransition(() -> transitionPolicy.check(questionnaire.getStatus(), QuestionnaireTransition.SUBMIT))
                .beforeMutate(() -> {
                    loaded.all = answers.findAllByTenantIdAndQuestionnaireId(ctx.tenantId(), questionnaireId);
                    requireAllRequiredQuestionsAnswered(questionnaire, loaded.all);
                })
                .snapshot(() -> snapshot.of(questionnaire))
                .mutate(() -> {
                    questionnaire.setStatus(QuestionnaireStatus.COMPLETED);
                    loaded.all.forEach(a -> {
                        if (a.getSubmittedAt() == null) a.setSubmittedAt(now);
                        answers.save(a);
                    });
                })
                .save(() -> questionnaires.save(questionnaire))
                .audit(AuditAction.JOB_ANALYSIS_SUBMITTED, "JobAnalysisQuestionnaire",
                        questionnaireId, questionnaire.getProjectId())
                .execute();
        return questionnaire.toDomain();
    }

    private static void requireAllRequiredQuestionsAnswered(JobAnalysisQuestionnaireJpaEntity questionnaire,
                                                            List<JobAnalysisAnswerJpaEntity> all) {
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
    }

    private static boolean isAnswered(JobAnalysisAnswerJpaEntity a) {
        return (a.getAnswerText() != null && !a.getAnswerText().isBlank())
                || (a.getAnswerChoices() != null && !a.getAnswerChoices().isEmpty())
                || a.getAnswerNumber() != null;
    }
}

package uz.hrlab.grading.jobanalysis.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.jobanalysis.domain.JobAnalysisAnswer;
import uz.hrlab.grading.jobanalysis.domain.JobAnalysisQuestion;
import uz.hrlab.grading.jobanalysis.domain.QuestionType;
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

import java.util.UUID;

/**
 * Idempotent upsert of one answer by (questionnaireId, questionId,
 * respondentUserId). Answer type is validated against the embedded question
 * definition — caller cannot e.g. supply {@code answerNumber} for a TEXT
 * question.
 */
@Service
public class UpdateAnswerUseCase {

    private final JobAnalysisAnswerRepository answers;
    private final JobAnalysisQuestionnaireRepository questionnaires;
    private final PositionRepository positions;
    private final AuditService audit;
    private final AbacGate abacGate;
    private final QuestionnaireAuditSnapshot snapshot;
    private final QuestionnaireStatusTransitionPolicy transitionPolicy;

    public UpdateAnswerUseCase(JobAnalysisAnswerRepository answers,
                               JobAnalysisQuestionnaireRepository questionnaires,
                               PositionRepository positions,
                               AuditService audit,
                               AbacGate abacGate,
                               QuestionnaireAuditSnapshot snapshot,
                               QuestionnaireStatusTransitionPolicy transitionPolicy) {
        this.answers = answers;
        this.questionnaires = questionnaires;
        this.positions = positions;
        this.audit = audit;
        this.abacGate = abacGate;
        this.snapshot = snapshot;
        this.transitionPolicy = transitionPolicy;
    }

    @Transactional
    public JobAnalysisAnswer upsert(UUID questionnaireId, UUID questionId,
                                    UpdateAnswerCommand cmd) {
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

        if (questionnaire.getStatus() != QuestionnaireStatus.DRAFT
                && questionnaire.getStatus() != QuestionnaireStatus.IN_PROGRESS) {
            throw new QuestionnaireTransitionRejectedException(
                    "QUESTIONNAIRE_NOT_EDITABLE",
                    "Questionnaire is " + questionnaire.getStatus()
                            + "; cannot edit answers");
        }

        JobAnalysisQuestion question = questionnaire.getQuestions().stream()
                .filter(q -> q.getId() != null && q.getId().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new ValidationException("QUESTION_NOT_FOUND",
                        "Question does not belong to this questionnaire"));
        validateAnswerShape(question, cmd);

        UUID respondentId = ctx.userId();
        var existing = answers
                .findByTenantIdAndQuestionnaireIdAndQuestionIdAndRespondentUserId(
                        ctx.tenantId(), questionnaireId, questionId, respondentId);
        JobAnalysisAnswerJpaEntity entity = existing
                .orElseGet(() -> new JobAnalysisAnswerJpaEntity(
                        UUID.randomUUID(), ctx.tenantId(), questionnaire.getProjectId(),
                        questionnaireId, questionId, respondentId));
        var beforeJson = existing.isPresent() ? snapshot.of(entity) : null;
        entity.setAnswerText(cmd.answerText());
        entity.setAnswerChoices(cmd.answerChoices());
        entity.setAnswerNumber(cmd.answerNumber());
        answers.save(entity);

        // First answer on a DRAFT questionnaire transitions to IN_PROGRESS
        // (PRD §E7 / D-308 — backend now mirrors the frontend status set).
        if (questionnaire.getStatus() == QuestionnaireStatus.DRAFT) {
            transitionPolicy.check(questionnaire.getStatus(),
                    QuestionnaireTransition.START_ANSWERING);
            questionnaire.setStatus(QuestionnaireStatus.IN_PROGRESS);
            questionnaires.save(questionnaire);
        }

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(questionnaire.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.JOB_ANALYSIS_ANSWER_UPDATED)
                .entityType("JobAnalysisAnswer")
                .entityId(entity.getId())
                .reason("question=" + question.getCode())
                .beforeJson(beforeJson)
                .afterJson(snapshot.of(entity))
                .build());
        return entity.toDomain();
    }

    private static void validateAnswerShape(JobAnalysisQuestion q, UpdateAnswerCommand c) {
        switch (q.getQuestionType()) {
            case TEXT, LONG_TEXT -> {
                if (c.answerText() == null) {
                    throw new ValidationException("ANSWER_TYPE_MISMATCH",
                            "Text answer required for question " + q.getCode());
                }
            }
            case NUMBER -> {
                if (c.answerNumber() == null) {
                    throw new ValidationException("ANSWER_TYPE_MISMATCH",
                            "Numeric answer required for question " + q.getCode());
                }
            }
            case RATING_SCALE -> {
                if (c.answerNumber() == null && (c.answerChoices() == null
                        || c.answerChoices().isEmpty())) {
                    throw new ValidationException("ANSWER_TYPE_MISMATCH",
                            "Rating answer required for question " + q.getCode());
                }
            }
            case SINGLE_CHOICE -> {
                if (c.answerChoices() == null || c.answerChoices().size() != 1) {
                    throw new ValidationException("ANSWER_TYPE_MISMATCH",
                            "Exactly one choice required for question " + q.getCode());
                }
                if (q.getOptions() != null
                        && !q.getOptions().contains(c.answerChoices().get(0))) {
                    throw new ValidationException("ANSWER_CHOICE_INVALID",
                            "Choice not allowed for question " + q.getCode());
                }
            }
            case MULTI_CHOICE -> {
                if (c.answerChoices() == null || c.answerChoices().isEmpty()) {
                    throw new ValidationException("ANSWER_TYPE_MISMATCH",
                            "At least one choice required for question " + q.getCode());
                }
                if (q.getOptions() != null) {
                    for (String choice : c.answerChoices()) {
                        if (!q.getOptions().contains(choice)) {
                            throw new ValidationException("ANSWER_CHOICE_INVALID",
                                    "Choice '" + choice + "' not allowed for question "
                                            + q.getCode());
                        }
                    }
                }
            }
            // exhaustive — QuestionType is sealed by enum exhaustiveness
        }
        // additional guard: don't accept stray fields beyond what the question expects
        if (q.getQuestionType() == QuestionType.TEXT
                || q.getQuestionType() == QuestionType.LONG_TEXT) {
            if (c.answerNumber() != null || (c.answerChoices() != null
                    && !c.answerChoices().isEmpty())) {
                throw new ValidationException("ANSWER_TYPE_MISMATCH",
                        "Only text answer allowed for question " + q.getCode());
            }
        }
    }
}

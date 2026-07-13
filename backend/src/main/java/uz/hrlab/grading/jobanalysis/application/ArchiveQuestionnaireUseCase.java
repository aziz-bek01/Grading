package uz.hrlab.grading.jobanalysis.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditJsonRedactor;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.application.StatusTransitionExecutor;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.jobanalysis.domain.JobAnalysisQuestionnaire;
import uz.hrlab.grading.jobanalysis.domain.QuestionnaireStatus;
import uz.hrlab.grading.jobanalysis.domain.QuestionnaireStatusTransitionPolicy;
import uz.hrlab.grading.jobanalysis.domain.QuestionnaireTransition;
import uz.hrlab.grading.jobanalysis.infrastructure.JobAnalysisQuestionnaireJpaEntity;
import uz.hrlab.grading.jobanalysis.infrastructure.JobAnalysisQuestionnaireRepository;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

@Service
public class ArchiveQuestionnaireUseCase {

    private static final int MIN_REASON_LENGTH = 10;

    private final JobAnalysisQuestionnaireRepository questionnaires;
    private final PositionRepository positions;
    private final QuestionnaireAuditSnapshot snapshot;
    private final AuditJsonRedactor redactor;
    private final QuestionnaireStatusTransitionPolicy transitionPolicy;
    private final StatusTransitionExecutor transitions;

    public ArchiveQuestionnaireUseCase(JobAnalysisQuestionnaireRepository questionnaires,
                                       PositionRepository positions,
                                       AuditService audit,
                                       AbacGate abacGate,
                                       QuestionnaireAuditSnapshot snapshot,
                                       AuditJsonRedactor redactor,
                                       QuestionnaireStatusTransitionPolicy transitionPolicy) {
        this.questionnaires = questionnaires;
        this.positions = positions;
        this.snapshot = snapshot;
        this.redactor = redactor;
        this.transitionPolicy = transitionPolicy;
        this.transitions = new StatusTransitionExecutor(abacGate, audit);
    }

    @Transactional
    public JobAnalysisQuestionnaire archive(UUID id, String reason) {
        if (reason == null || reason.trim().length() < MIN_REASON_LENGTH) {
            throw new ValidationException("REASON_REQUIRED",
                    "A reason of at least " + MIN_REASON_LENGTH + " characters is required");
        }
        TenantContext ctx = TenantContextHolder.requireActive();
        JobAnalysisQuestionnaireJpaEntity questionnaire = questionnaires
                .findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        PositionJpaEntity position = positions
                .findByIdAndTenantId(questionnaire.getPositionId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        transitions.transition(ctx)
                .abacProjectAndDepartmentWrite(questionnaire.getProjectId(), position.getDepartmentId())
                .checkTransition(() -> transitionPolicy.check(questionnaire.getStatus(), QuestionnaireTransition.ARCHIVE))
                .snapshot(() -> snapshot.of(questionnaire))
                .mutate(() -> questionnaire.setStatus(QuestionnaireStatus.ARCHIVED))
                .save(() -> questionnaires.save(questionnaire))
                .reason(redactor.redactReason(reason))
                .audit(AuditAction.JOB_ANALYSIS_ARCHIVED, "JobAnalysisQuestionnaire",
                        id, questionnaire.getProjectId())
                .execute();
        return questionnaire.toDomain();
    }
}

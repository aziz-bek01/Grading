package uz.hrlab.grading.jobanalysis.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditJsonRedactor;
import uz.hrlab.grading.audit.application.AuditService;
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
    private final AuditService audit;
    private final AbacGate abacGate;
    private final QuestionnaireAuditSnapshot snapshot;
    private final AuditJsonRedactor redactor;
    private final QuestionnaireStatusTransitionPolicy transitionPolicy;

    public ArchiveQuestionnaireUseCase(JobAnalysisQuestionnaireRepository questionnaires,
                                       PositionRepository positions,
                                       AuditService audit,
                                       AbacGate abacGate,
                                       QuestionnaireAuditSnapshot snapshot,
                                       AuditJsonRedactor redactor,
                                       QuestionnaireStatusTransitionPolicy transitionPolicy) {
        this.questionnaires = questionnaires;
        this.positions = positions;
        this.audit = audit;
        this.abacGate = abacGate;
        this.snapshot = snapshot;
        this.redactor = redactor;
        this.transitionPolicy = transitionPolicy;
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

        abacGate.enforceCanWriteInProject(ctx, questionnaire.getProjectId());
        abacGate.enforceCanWriteInDepartment(ctx, questionnaire.getProjectId(),
                position.getDepartmentId());

        transitionPolicy.check(questionnaire.getStatus(), QuestionnaireTransition.ARCHIVE);

        var beforeJson = snapshot.of(questionnaire);
        questionnaire.setStatus(QuestionnaireStatus.ARCHIVED);
        questionnaires.save(questionnaire);

        audit.record(AuditEvent.builder(ctx)
                .projectId(questionnaire.getProjectId())
                .action(AuditAction.JOB_ANALYSIS_ARCHIVED)
                .entityType("JobAnalysisQuestionnaire")
                .entityId(id)
                .reason(redactor.redactReason(reason))
                .beforeJson(beforeJson)
                .afterJson(snapshot.of(questionnaire))
                .build());
        return questionnaire.toDomain();
    }
}

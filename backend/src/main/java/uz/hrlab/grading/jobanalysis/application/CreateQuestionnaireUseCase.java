package uz.hrlab.grading.jobanalysis.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.jobanalysis.domain.JobAnalysisQuestion;
import uz.hrlab.grading.jobanalysis.domain.JobAnalysisQuestionnaire;
import uz.hrlab.grading.jobanalysis.domain.QuestionnaireStatus;
import uz.hrlab.grading.jobanalysis.infrastructure.JobAnalysisQuestionnaireJpaEntity;
import uz.hrlab.grading.jobanalysis.infrastructure.JobAnalysisQuestionnaireRepository;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.project.application.ProjectAccess;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.UUID;

/**
 * Instantiate a new questionnaire from a template (STANDARD_V1 /
 * EXECUTIVE_V1). The questions are materialised at creation time so that
 * later changes to the template code never retro-actively mutate an existing
 * questionnaire (immutability of historical record).
 */
@Service
public class CreateQuestionnaireUseCase {

    private final JobAnalysisQuestionnaireRepository questionnaires;
    private final PositionRepository positions;
    private final ProjectAccess projectAccess;
    private final AuditService audit;
    private final AbacGate abacGate;
    private final QuestionnaireAuditSnapshot snapshot;

    public CreateQuestionnaireUseCase(JobAnalysisQuestionnaireRepository questionnaires,
                                      PositionRepository positions,
                                      ProjectAccess projectAccess,
                                      AuditService audit,
                                      AbacGate abacGate,
                                      QuestionnaireAuditSnapshot snapshot) {
        this.questionnaires = questionnaires;
        this.positions = positions;
        this.projectAccess = projectAccess;
        this.audit = audit;
        this.abacGate = abacGate;
        this.snapshot = snapshot;
    }

    @Transactional
    public JobAnalysisQuestionnaire create(CreateQuestionnaireCommand cmd) {
        TenantContext ctx = TenantContextHolder.requireActive();

        PositionJpaEntity position = positions
                .findByIdAndTenantId(cmd.positionId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        abacGate.enforceCanWriteInProject(ctx, position.getProjectId());
        abacGate.enforceCanWriteInDepartment(ctx, position.getProjectId(),
                position.getDepartmentId());

        projectAccess.requireWritable(ctx, position.getProjectId());

        List<JobAnalysisQuestion> questions;
        try {
            questions = QuestionnaireTemplate.questionsFor(cmd.templateCode());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("QUESTIONNAIRE_TEMPLATE_UNKNOWN",
                    "Unknown questionnaire template");
        }

        UUID id = UUID.randomUUID();
        JobAnalysisQuestionnaireJpaEntity entity = new JobAnalysisQuestionnaireJpaEntity(
                id, ctx.tenantId(), position.getProjectId(), position.getId(),
                cmd.templateCode(),
                QuestionnaireTemplate.nameI18nFor(cmd.templateCode()),
                QuestionnaireStatus.DRAFT,
                1,
                questions);
        questionnaires.save(entity);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(position.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.JOB_ANALYSIS_QUESTIONNAIRE_CREATED)
                .entityType("JobAnalysisQuestionnaire")
                .entityId(id)
                .reason("template=" + cmd.templateCode())
                .afterJson(snapshot.of(entity))
                .build());
        return entity.toDomain();
    }
}

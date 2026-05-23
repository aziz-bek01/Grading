package uz.hrlab.grading.jobanalysis.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.jobanalysis.domain.JobAnalysisAnswer;
import uz.hrlab.grading.jobanalysis.domain.JobAnalysisQuestionnaire;
import uz.hrlab.grading.jobanalysis.infrastructure.JobAnalysisAnswerJpaEntity;
import uz.hrlab.grading.jobanalysis.infrastructure.JobAnalysisAnswerRepository;
import uz.hrlab.grading.jobanalysis.infrastructure.JobAnalysisQuestionnaireJpaEntity;
import uz.hrlab.grading.jobanalysis.infrastructure.JobAnalysisQuestionnaireRepository;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.UUID;

@Service
public class FindQuestionnaireQuery {

    private final JobAnalysisQuestionnaireRepository questionnaires;
    private final JobAnalysisAnswerRepository answers;
    private final PositionRepository positions;
    private final AbacGate abacGate;

    public FindQuestionnaireQuery(JobAnalysisQuestionnaireRepository questionnaires,
                                  JobAnalysisAnswerRepository answers,
                                  PositionRepository positions,
                                  AbacGate abacGate) {
        this.questionnaires = questionnaires;
        this.answers = answers;
        this.positions = positions;
        this.abacGate = abacGate;
    }

    @Transactional(readOnly = true)
    public JobAnalysisQuestionnaire findById(UUID id) {
        TenantContext ctx = TenantContextHolder.requireActive();
        JobAnalysisQuestionnaireJpaEntity entity = questionnaires
                .findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        PositionJpaEntity position = positions
                .findByIdAndTenantId(entity.getPositionId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        abacGate.enforceCanReadPosition(ctx, position.getId(), position.getProjectId(),
                position.getDepartmentId(), entity.getStatus());
        return entity.toDomain();
    }

    @Transactional(readOnly = true)
    public List<JobAnalysisQuestionnaire> listByPositionId(UUID positionId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        PositionJpaEntity position = positions
                .findByIdAndTenantId(positionId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        abacGate.enforceCanReadPosition(ctx, position.getId(), position.getProjectId(),
                position.getDepartmentId(), null);
        return questionnaires
                .findAllByTenantIdAndProjectIdAndPositionIdOrderByCreatedAtDesc(
                        ctx.tenantId(), position.getProjectId(), position.getId())
                .stream()
                .map(JobAnalysisQuestionnaireJpaEntity::toDomain)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<JobAnalysisAnswer> listAnswers(UUID questionnaireId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        JobAnalysisQuestionnaireJpaEntity entity = questionnaires
                .findByIdAndTenantId(questionnaireId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        PositionJpaEntity position = positions
                .findByIdAndTenantId(entity.getPositionId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        abacGate.enforceCanReadPosition(ctx, position.getId(), position.getProjectId(),
                position.getDepartmentId(), entity.getStatus());
        return answers.findAllByTenantIdAndQuestionnaireId(ctx.tenantId(), questionnaireId)
                .stream().map(JobAnalysisAnswerJpaEntity::toDomain).toList();
    }
}

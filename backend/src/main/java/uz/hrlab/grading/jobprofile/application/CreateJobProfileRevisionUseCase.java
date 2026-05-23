package uz.hrlab.grading.jobprofile.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.jobprofile.domain.JobProfile;
import uz.hrlab.grading.jobprofile.domain.JobProfileStatus;
import uz.hrlab.grading.jobprofile.domain.JobProfileStatusTransitionPolicy;
import uz.hrlab.grading.jobprofile.domain.JobProfileTransition;
import uz.hrlab.grading.jobprofile.infrastructure.JobProfileJpaEntity;
import uz.hrlab.grading.jobprofile.infrastructure.JobProfileRepository;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

/**
 * APPROVED → new DRAFT (PRD §E6 hard rule). The source row is NOT mutated;
 * a new {@link JobProfileJpaEntity} is created with
 * {@code revisionNumber = source.revisionNumber + 1} and
 * {@code previousRevisionId = source.id}. Content is deep-copied so the new
 * revision starts from the approved content.
 */
@Service
public class CreateJobProfileRevisionUseCase {

    private final JobProfileRepository profiles;
    private final PositionRepository positions;
    private final AuditService audit;
    private final AbacGate abacGate;
    private final JobProfileStatusTransitionPolicy transitionPolicy;
    private final JobProfileAuditSnapshot snapshot;

    public CreateJobProfileRevisionUseCase(JobProfileRepository profiles,
                                           PositionRepository positions,
                                           AuditService audit,
                                           AbacGate abacGate,
                                           JobProfileStatusTransitionPolicy transitionPolicy,
                                           JobProfileAuditSnapshot snapshot) {
        this.profiles = profiles;
        this.positions = positions;
        this.audit = audit;
        this.abacGate = abacGate;
        this.transitionPolicy = transitionPolicy;
        this.snapshot = snapshot;
    }

    @Transactional
    public JobProfile createRevision(UUID sourceId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        JobProfileJpaEntity source = profiles.findByIdAndTenantId(sourceId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        PositionJpaEntity position = positions
                .findByIdAndTenantId(source.getPositionId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        abacGate.enforceCanWriteInProject(ctx, source.getProjectId());
        abacGate.enforceCanWriteInDepartment(ctx, source.getProjectId(),
                position.getDepartmentId());

        transitionPolicy.check(source.getStatus(), JobProfileTransition.CREATE_REVISION);

        UUID newId = UUID.randomUUID();
        JobProfileJpaEntity revision = new JobProfileJpaEntity(
                newId, ctx.tenantId(), source.getProjectId(), source.getPositionId(),
                JobProfileStatus.DRAFT,
                source.getRevisionNumber() + 1,
                source.getId());
        // Deep-copy content so the new revision is a working draft.
        revision.setPurposeI18n(source.getPurposeI18n());
        revision.setMainDutiesI18n(source.getMainDutiesI18n());
        revision.setResponsibilityAreaI18n(source.getResponsibilityAreaI18n());
        revision.setAuthorityI18n(source.getAuthorityI18n());
        revision.setKpiExpectedResultsI18n(source.getKpiExpectedResultsI18n());
        revision.setEducationRequirementsI18n(source.getEducationRequirementsI18n());
        revision.setExperienceRequirementsI18n(source.getExperienceRequirementsI18n());
        revision.setKnowledgeSkillsI18n(source.getKnowledgeSkillsI18n());
        revision.setInternalInteractionsI18n(source.getInternalInteractionsI18n());
        revision.setExternalInteractionsI18n(source.getExternalInteractionsI18n());
        revision.setWorkingConditionsI18n(source.getWorkingConditionsI18n());
        revision.setDocumentsRegulationsI18n(source.getDocumentsRegulationsI18n());
        revision.setActualizationDate(source.getActualizationDate());
        profiles.save(revision);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(source.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.JOB_PROFILE_REVISION_CREATED)
                .entityType("JobProfile")
                .entityId(newId)
                .reason("Revision of " + source.getId())
                .beforeJson(snapshot.of(source))
                .afterJson(snapshot.of(revision))
                .build());
        return revision.toDomain();
    }
}

package uz.hrlab.grading.jobprofile.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.jobprofile.domain.JobProfile;
import uz.hrlab.grading.jobprofile.domain.JobProfileImmutabilityPolicy;
import uz.hrlab.grading.jobprofile.infrastructure.JobProfileJpaEntity;
import uz.hrlab.grading.jobprofile.infrastructure.JobProfileRepository;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.project.application.ProjectAccess;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

/**
 * Mutate a DRAFT profile (PRD §E6 — APPROVED rejects 409). UNDER_REVIEW
 * profiles are likewise read-only; content edits require REQUEST_CHANGES
 * first to return them to DRAFT.
 */
@Service
public class UpdateJobProfileUseCase {

    private final JobProfileRepository profiles;
    private final PositionRepository positions;
    private final ProjectAccess projectAccess;
    private final AuditService audit;
    private final AbacGate abacGate;
    private final JobProfileImmutabilityPolicy immutabilityPolicy;
    private final JobProfileAuditSnapshot snapshot;

    public UpdateJobProfileUseCase(JobProfileRepository profiles,
                                   PositionRepository positions,
                                   ProjectAccess projectAccess,
                                   AuditService audit,
                                   AbacGate abacGate,
                                   JobProfileImmutabilityPolicy immutabilityPolicy,
                                   JobProfileAuditSnapshot snapshot) {
        this.profiles = profiles;
        this.positions = positions;
        this.projectAccess = projectAccess;
        this.audit = audit;
        this.abacGate = abacGate;
        this.immutabilityPolicy = immutabilityPolicy;
        this.snapshot = snapshot;
    }

    @Transactional
    public JobProfile update(UUID id, UpdateJobProfileCommand cmd) {
        TenantContext ctx = TenantContextHolder.requireActive();
        JobProfileJpaEntity entity = profiles.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        PositionJpaEntity position = positions
                .findByIdAndTenantId(entity.getPositionId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        abacGate.enforceCanWriteInProject(ctx, entity.getProjectId());
        abacGate.enforceCanWriteInDepartment(ctx, entity.getProjectId(),
                position.getDepartmentId());

        projectAccess.requireWritable(ctx, entity.getProjectId());

        // Hard rule: APPROVED + ARCHIVED + UNDER_REVIEW are not editable.
        immutabilityPolicy.enforceEditable(entity.getStatus());

        var beforeJson = snapshot.of(entity);

        if (cmd.purposeI18n() != null) entity.setPurposeI18n(cmd.purposeI18n());
        if (cmd.mainDutiesI18n() != null) entity.setMainDutiesI18n(cmd.mainDutiesI18n());
        if (cmd.responsibilityAreaI18n() != null) entity.setResponsibilityAreaI18n(cmd.responsibilityAreaI18n());
        if (cmd.authorityI18n() != null) entity.setAuthorityI18n(cmd.authorityI18n());
        if (cmd.kpiExpectedResultsI18n() != null) entity.setKpiExpectedResultsI18n(cmd.kpiExpectedResultsI18n());
        if (cmd.educationRequirementsI18n() != null) entity.setEducationRequirementsI18n(cmd.educationRequirementsI18n());
        if (cmd.experienceRequirementsI18n() != null) entity.setExperienceRequirementsI18n(cmd.experienceRequirementsI18n());
        if (cmd.knowledgeSkillsI18n() != null) entity.setKnowledgeSkillsI18n(cmd.knowledgeSkillsI18n());
        if (cmd.internalInteractionsI18n() != null) entity.setInternalInteractionsI18n(cmd.internalInteractionsI18n());
        if (cmd.externalInteractionsI18n() != null) entity.setExternalInteractionsI18n(cmd.externalInteractionsI18n());
        if (cmd.workingConditionsI18n() != null) entity.setWorkingConditionsI18n(cmd.workingConditionsI18n());
        if (cmd.documentsRegulationsI18n() != null) entity.setDocumentsRegulationsI18n(cmd.documentsRegulationsI18n());
        if (cmd.actualizationDate() != null) entity.setActualizationDate(cmd.actualizationDate());

        profiles.save(entity);

        audit.record(AuditEvent.builder(ctx)
                .projectId(entity.getProjectId())
                .action(AuditAction.JOB_PROFILE_UPDATED)
                .entityType("JobProfile")
                .entityId(id)
                .beforeJson(beforeJson)
                .afterJson(snapshot.of(entity))
                .build());
        return entity.toDomain();
    }
}

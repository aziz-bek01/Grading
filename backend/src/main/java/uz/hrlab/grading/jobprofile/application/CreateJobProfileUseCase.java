package uz.hrlab.grading.jobprofile.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.jobprofile.domain.JobProfile;
import uz.hrlab.grading.jobprofile.domain.JobProfileStatus;
import uz.hrlab.grading.jobprofile.infrastructure.JobProfileJpaEntity;
import uz.hrlab.grading.jobprofile.infrastructure.JobProfileRepository;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.project.application.ProjectLockedException;
import uz.hrlab.grading.project.domain.ProjectStatus;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

/**
 * Create the FIRST DRAFT job profile for a position (PRD §E6-1). If an
 * active (non-ARCHIVED) profile already exists, return 409 PROFILE_ALREADY_EXISTS.
 */
@Service
public class CreateJobProfileUseCase {

    private final JobProfileRepository profiles;
    private final PositionRepository positions;
    private final ProjectRepository projects;
    private final AuditService audit;
    private final AbacGate abacGate;
    private final JobProfileAuditSnapshot snapshot;

    public CreateJobProfileUseCase(JobProfileRepository profiles,
                                   PositionRepository positions,
                                   ProjectRepository projects,
                                   AuditService audit,
                                   AbacGate abacGate,
                                   JobProfileAuditSnapshot snapshot) {
        this.profiles = profiles;
        this.positions = positions;
        this.projects = projects;
        this.audit = audit;
        this.abacGate = abacGate;
        this.snapshot = snapshot;
    }

    @Transactional
    public JobProfile create(CreateJobProfileCommand cmd) {
        TenantContext ctx = TenantContextHolder.requireActive();

        PositionJpaEntity position = positions
                .findByIdAndTenantId(cmd.positionId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        // ABAC — project + department membership on write
        abacGate.enforceCanWriteInProject(ctx, position.getProjectId());
        abacGate.enforceCanWriteInDepartment(ctx, position.getProjectId(),
                position.getDepartmentId());

        var project = projects.findByIdAndTenantId(position.getProjectId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (project.getStatus() == ProjectStatus.LOCKED
                || project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new ProjectLockedException();
        }

        if (profiles.existsByTenantIdAndProjectIdAndPositionIdAndStatusNot(
                ctx.tenantId(), position.getProjectId(), position.getId(),
                JobProfileStatus.ARCHIVED)) {
            throw new ValidationException("PROFILE_ALREADY_EXISTS",
                    "An active profile already exists for this position");
        }

        UUID id = UUID.randomUUID();
        JobProfileJpaEntity entity = new JobProfileJpaEntity(
                id, ctx.tenantId(), position.getProjectId(), position.getId(),
                JobProfileStatus.DRAFT, 1, null);
        applyCreateBody(entity, cmd);
        profiles.save(entity);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(position.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.JOB_PROFILE_CREATED)
                .entityType("JobProfile")
                .entityId(id)
                .afterJson(snapshot.of(entity))
                .build());
        return entity.toDomain();
    }

    private static void applyCreateBody(JobProfileJpaEntity e, CreateJobProfileCommand c) {
        e.setPurposeI18n(c.purposeI18n());
        e.setMainDutiesI18n(c.mainDutiesI18n());
        e.setResponsibilityAreaI18n(c.responsibilityAreaI18n());
        e.setAuthorityI18n(c.authorityI18n());
        e.setKpiExpectedResultsI18n(c.kpiExpectedResultsI18n());
        e.setEducationRequirementsI18n(c.educationRequirementsI18n());
        e.setExperienceRequirementsI18n(c.experienceRequirementsI18n());
        e.setKnowledgeSkillsI18n(c.knowledgeSkillsI18n());
        e.setInternalInteractionsI18n(c.internalInteractionsI18n());
        e.setExternalInteractionsI18n(c.externalInteractionsI18n());
        e.setWorkingConditionsI18n(c.workingConditionsI18n());
        e.setDocumentsRegulationsI18n(c.documentsRegulationsI18n());
        e.setActualizationDate(c.actualizationDate());
    }
}

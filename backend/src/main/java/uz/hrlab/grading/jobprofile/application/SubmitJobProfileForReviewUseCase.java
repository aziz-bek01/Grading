package uz.hrlab.grading.jobprofile.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.approval.application.CreateApprovalRequestCommand;
import uz.hrlab.grading.approval.application.CreateApprovalRequestUseCase;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.jobprofile.domain.JobProfile;
import uz.hrlab.grading.jobprofile.domain.JobProfileStatus;
import uz.hrlab.grading.jobprofile.domain.JobProfileStatusTransitionPolicy;
import uz.hrlab.grading.jobprofile.domain.JobProfileTransition;
import uz.hrlab.grading.jobprofile.domain.JobProfileTransitionRejectedException;
import uz.hrlab.grading.jobprofile.infrastructure.JobProfileJpaEntity;
import uz.hrlab.grading.jobprofile.infrastructure.JobProfileRepository;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DRAFT → UNDER_REVIEW. Validates that the primary locale (ru-RU) is present
 * for every required long-text field (purpose, mainDuties, responsibilityArea,
 * KPI, education, experience). PRD §E6.
 */
@Service
public class SubmitJobProfileForReviewUseCase {

    private final JobProfileRepository profiles;
    private final PositionRepository positions;
    private final AuditService audit;
    private final AbacGate abacGate;
    private final JobProfileStatusTransitionPolicy transitionPolicy;
    private final JobProfileAuditSnapshot snapshot;
    private final CreateApprovalRequestUseCase createApprovalRequest;

    public SubmitJobProfileForReviewUseCase(JobProfileRepository profiles,
                                            PositionRepository positions,
                                            AuditService audit,
                                            AbacGate abacGate,
                                            JobProfileStatusTransitionPolicy transitionPolicy,
                                            JobProfileAuditSnapshot snapshot,
                                            CreateApprovalRequestUseCase createApprovalRequest) {
        this.profiles = profiles;
        this.positions = positions;
        this.audit = audit;
        this.abacGate = abacGate;
        this.transitionPolicy = transitionPolicy;
        this.snapshot = snapshot;
        this.createApprovalRequest = createApprovalRequest;
    }

    @Transactional
    public JobProfile submit(UUID id) {
        TenantContext ctx = TenantContextHolder.requireActive();
        JobProfileJpaEntity entity = profiles.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        PositionJpaEntity position = positions
                .findByIdAndTenantId(entity.getPositionId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        abacGate.enforceCanWriteInProject(ctx, entity.getProjectId());
        abacGate.enforceCanWriteInDepartment(ctx, entity.getProjectId(),
                position.getDepartmentId());

        transitionPolicy.check(entity.getStatus(), JobProfileTransition.SUBMIT_FOR_REVIEW);
        requirePrimaryLocaleFields(entity);

        var beforeJson = snapshot.of(entity);
        entity.setStatus(JobProfileStatus.UNDER_REVIEW);
        entity.setSubmittedAt(OffsetDateTime.now());
        entity.setSubmittedBy(ctx.userId());
        profiles.save(entity);

        audit.record(AuditEvent.builder(ctx)
                .projectId(entity.getProjectId())
                .action(AuditAction.JOB_PROFILE_SUBMITTED)
                .entityType("JobProfile")
                .entityId(id)
                .beforeJson(beforeJson)
                .afterJson(snapshot.of(entity))
                .build());

        // MVP 2 Phase 1 — open a single-step approval request requiring
        // JOB_PROFILE_APPROVE. The originating user already passed JOB_PROFILE_EDIT;
        // we use createSystem to skip the APPROVAL_REQUEST_CREATE recheck.
        try {
            createApprovalRequest.createSystem(
                    CreateApprovalRequestCommand.singleStep(
                            entity.getProjectId(),
                            ApprovalEntityType.JOB_PROFILE,
                            entity.getId(),
                            PermissionCodes.JOB_PROFILE_APPROVE));
        } catch (RuntimeException openExisting) {
            // Idempotent — if a PENDING request already exists for this profile,
            // re-submission must not raise. Other failures should propagate.
            if (!"ANOTHER_PENDING_REQUEST_EXISTS".equals(
                    openExisting instanceof uz.hrlab.grading.common.exception.BaseDomainException b
                            ? b.getCode() : null)) {
                throw openExisting;
            }
        }
        return entity.toDomain();
    }

    private static void requirePrimaryLocaleFields(JobProfileJpaEntity e) {
        require(e.getPurposeI18n(), "purposeI18n");
        require(e.getMainDutiesI18n(), "mainDutiesI18n");
        require(e.getResponsibilityAreaI18n(), "responsibilityAreaI18n");
        require(e.getKpiExpectedResultsI18n(), "kpiExpectedResultsI18n");
        require(e.getEducationRequirementsI18n(), "educationRequirementsI18n");
        require(e.getExperienceRequirementsI18n(), "experienceRequirementsI18n");
    }

    private static void require(java.util.Map<String, String> i18n, String fieldName) {
        if (!JobProfileLocales.hasPrimary(i18n)) {
            throw new JobProfileTransitionRejectedException(
                    "PRIMARY_LOCALE_MISSING",
                    "Field '" + fieldName + "' must have a value in the primary locale ("
                            + JobProfileLocales.PRIMARY_LOCALE + ") before submit-for-review");
        }
    }
}

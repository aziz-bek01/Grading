package uz.hrlab.grading.jobprofile.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditJsonRedactor;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.application.StatusTransitionExecutor;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
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
 * UNDER_REVIEW → DRAFT with a mandatory reason (audit trail requirement).
 * Requires {@link PermissionCodes#JOB_PROFILE_APPROVE} (same role that can
 * approve can also reject — Spring's @PreAuthorize gates on EDIT, this use
 * case adds the APPROVE check).
 */
@Service
public class RequestJobProfileChangesUseCase {

    private static final int MIN_REASON_LENGTH = 10;

    private final JobProfileRepository profiles;
    private final PositionRepository positions;
    private final JobProfileStatusTransitionPolicy transitionPolicy;
    private final JobProfileAuditSnapshot snapshot;
    private final AuditJsonRedactor redactor;
    private final StatusTransitionExecutor transitions;

    public RequestJobProfileChangesUseCase(JobProfileRepository profiles,
                                           PositionRepository positions,
                                           AuditService audit,
                                           AbacGate abacGate,
                                           JobProfileStatusTransitionPolicy transitionPolicy,
                                           JobProfileAuditSnapshot snapshot,
                                           AuditJsonRedactor redactor) {
        this.profiles = profiles;
        this.positions = positions;
        this.transitionPolicy = transitionPolicy;
        this.snapshot = snapshot;
        this.redactor = redactor;
        this.transitions = new StatusTransitionExecutor(abacGate, audit);
    }

    @Transactional
    public JobProfile requestChanges(UUID id, String reason) {
        if (reason == null || reason.trim().length() < MIN_REASON_LENGTH) {
            throw new ValidationException("REASON_REQUIRED",
                    "A reason of at least " + MIN_REASON_LENGTH + " characters is required");
        }
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.JOB_PROFILE_APPROVE);
        JobProfileJpaEntity entity = profiles.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        PositionJpaEntity position = positions
                .findByIdAndTenantId(entity.getPositionId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        transitions.transition(ctx)
                .abacProjectAndDepartmentWrite(entity.getProjectId(), position.getDepartmentId())
                .checkTransition(() -> transitionPolicy.check(entity.getStatus(), JobProfileTransition.REQUEST_CHANGES))
                .snapshot(() -> snapshot.of(entity))
                .mutate(() -> {
                    entity.setStatus(JobProfileStatus.DRAFT);
                    entity.setSubmittedAt(null);
                    entity.setSubmittedBy(null);
                })
                .save(() -> profiles.save(entity))
                .reason(redactor.redactReason(reason))
                .audit(AuditAction.JOB_PROFILE_CHANGES_REQUESTED, "JobProfile", id, entity.getProjectId())
                .execute();
        return entity.toDomain();
    }
}

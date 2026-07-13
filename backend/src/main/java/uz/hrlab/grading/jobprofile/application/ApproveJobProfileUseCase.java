package uz.hrlab.grading.jobprofile.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.application.StatusTransitionExecutor;
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

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * UNDER_REVIEW → APPROVED. Approval is gated by
 * {@link PermissionCodes#JOB_PROFILE_APPROVE} in addition to the controller's
 * {@code @PreAuthorize}; the use-case re-checks the permission server-side so
 * a misconfigured controller annotation can never silently escalate.
 */
@Service
public class ApproveJobProfileUseCase {

    private final JobProfileRepository profiles;
    private final PositionRepository positions;
    private final JobProfileStatusTransitionPolicy transitionPolicy;
    private final JobProfileAuditSnapshot snapshot;
    private final StatusTransitionExecutor transitions;

    public ApproveJobProfileUseCase(JobProfileRepository profiles,
                                    PositionRepository positions,
                                    AuditService audit,
                                    AbacGate abacGate,
                                    JobProfileStatusTransitionPolicy transitionPolicy,
                                    JobProfileAuditSnapshot snapshot) {
        this.profiles = profiles;
        this.positions = positions;
        this.transitionPolicy = transitionPolicy;
        this.snapshot = snapshot;
        this.transitions = new StatusTransitionExecutor(abacGate, audit);
    }

    @Transactional
    public JobProfile approve(UUID id) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.JOB_PROFILE_APPROVE);
        JobProfileJpaEntity entity = profiles.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        PositionJpaEntity position = positions
                .findByIdAndTenantId(entity.getPositionId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        OffsetDateTime now = OffsetDateTime.now();
        transitions.transition(ctx)
                .abacProjectAndDepartmentWrite(entity.getProjectId(), position.getDepartmentId())
                .checkTransition(() -> transitionPolicy.check(entity.getStatus(), JobProfileTransition.APPROVE))
                .snapshot(() -> snapshot.of(entity))
                .mutate(() -> {
                    entity.setStatus(JobProfileStatus.APPROVED);
                    entity.setApprovedAt(now);
                    entity.setApprovedBy(ctx.userId());
                    entity.setLockedAt(now);
                })
                .save(() -> profiles.save(entity))
                .audit(AuditAction.JOB_PROFILE_APPROVED, "JobProfile", id, entity.getProjectId())
                .execute();
        return entity.toDomain();
    }
}

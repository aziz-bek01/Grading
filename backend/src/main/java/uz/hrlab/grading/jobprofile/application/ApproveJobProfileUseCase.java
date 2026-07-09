package uz.hrlab.grading.jobprofile.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
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
    private final AuditService audit;
    private final AbacGate abacGate;
    private final JobProfileStatusTransitionPolicy transitionPolicy;
    private final JobProfileAuditSnapshot snapshot;

    public ApproveJobProfileUseCase(JobProfileRepository profiles,
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
    public JobProfile approve(UUID id) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.JOB_PROFILE_APPROVE);
        JobProfileJpaEntity entity = profiles.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        PositionJpaEntity position = positions
                .findByIdAndTenantId(entity.getPositionId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        abacGate.enforceCanWriteInProject(ctx, entity.getProjectId());
        abacGate.enforceCanWriteInDepartment(ctx, entity.getProjectId(),
                position.getDepartmentId());

        transitionPolicy.check(entity.getStatus(), JobProfileTransition.APPROVE);

        var beforeJson = snapshot.of(entity);
        OffsetDateTime now = OffsetDateTime.now();
        entity.setStatus(JobProfileStatus.APPROVED);
        entity.setApprovedAt(now);
        entity.setApprovedBy(ctx.userId());
        entity.setLockedAt(now);
        profiles.save(entity);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(entity.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.JOB_PROFILE_APPROVED)
                .entityType("JobProfile")
                .entityId(id)
                .beforeJson(beforeJson)
                .afterJson(snapshot.of(entity))
                .build());
        return entity.toDomain();
    }
}

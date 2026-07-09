package uz.hrlab.grading.jobprofile.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditJsonRedactor;
import uz.hrlab.grading.audit.application.AuditService;
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
 * → ARCHIVED. Allowed from DRAFT or APPROVED. Reason required for audit.
 */
@Service
public class ArchiveJobProfileUseCase {

    private static final int MIN_REASON_LENGTH = 10;

    private final JobProfileRepository profiles;
    private final PositionRepository positions;
    private final AuditService audit;
    private final AbacGate abacGate;
    private final JobProfileStatusTransitionPolicy transitionPolicy;
    private final JobProfileAuditSnapshot snapshot;
    private final AuditJsonRedactor redactor;

    public ArchiveJobProfileUseCase(JobProfileRepository profiles,
                                    PositionRepository positions,
                                    AuditService audit,
                                    AbacGate abacGate,
                                    JobProfileStatusTransitionPolicy transitionPolicy,
                                    JobProfileAuditSnapshot snapshot,
                                    AuditJsonRedactor redactor) {
        this.profiles = profiles;
        this.positions = positions;
        this.audit = audit;
        this.abacGate = abacGate;
        this.transitionPolicy = transitionPolicy;
        this.snapshot = snapshot;
        this.redactor = redactor;
    }

    @Transactional
    public JobProfile archive(UUID id, String reason) {
        if (reason == null || reason.trim().length() < MIN_REASON_LENGTH) {
            throw new ValidationException("REASON_REQUIRED",
                    "A reason of at least " + MIN_REASON_LENGTH + " characters is required");
        }
        TenantContext ctx = TenantContextHolder.requireActive();
        JobProfileJpaEntity entity = profiles.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        PositionJpaEntity position = positions
                .findByIdAndTenantId(entity.getPositionId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        abacGate.enforceCanWriteInProject(ctx, entity.getProjectId());
        abacGate.enforceCanWriteInDepartment(ctx, entity.getProjectId(),
                position.getDepartmentId());

        transitionPolicy.check(entity.getStatus(), JobProfileTransition.ARCHIVE);

        var beforeJson = snapshot.of(entity);
        entity.setStatus(JobProfileStatus.ARCHIVED);
        profiles.save(entity);

        audit.record(AuditEvent.builder(ctx)
                .projectId(entity.getProjectId())
                .action(AuditAction.JOB_PROFILE_ARCHIVED)
                .entityType("JobProfile")
                .entityId(id)
                .reason(redactor.redactReason(reason))
                .beforeJson(beforeJson)
                .afterJson(snapshot.of(entity))
                .build());
        return entity.toDomain();
    }
}

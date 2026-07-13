package uz.hrlab.grading.methodology.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.application.StatusTransitionExecutor;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.methodology.domain.MethodologyVersion;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatusTransitionPolicy;
import uz.hrlab.grading.methodology.domain.MethodologyVersionTransition;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * APPROVED → LOCKED. Requires {@code METHODOLOGY_LOCK} permission
 * (HRLab Super Admin or Project Manager only).
 */
@Service
public class LockMethodologyVersionUseCase {

    private final MethodologyRepository methodologies;
    private final MethodologyVersionRepository versions;
    private final MethodologyVersionStatusTransitionPolicy transitionPolicy;
    private final MethodologyAuditSnapshot snapshot;
    private final StatusTransitionExecutor transitions;

    public LockMethodologyVersionUseCase(MethodologyRepository methodologies,
                                         MethodologyVersionRepository versions,
                                         AbacGate abacGate,
                                         MethodologyVersionStatusTransitionPolicy transitionPolicy,
                                         AuditService audit,
                                         MethodologyAuditSnapshot snapshot) {
        this.methodologies = methodologies;
        this.versions = versions;
        this.transitionPolicy = transitionPolicy;
        this.snapshot = snapshot;
        this.transitions = new StatusTransitionExecutor(abacGate, audit);
    }

    @Transactional
    public MethodologyVersion lock(UUID versionId) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.METHODOLOGY_LOCK);
        MethodologyVersionJpaEntity v = versions.findByIdAndTenantId(versionId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        MethodologyJpaEntity m = methodologies.findByIdAndTenantId(v.getMethodologyId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        OffsetDateTime now = OffsetDateTime.now();
        transitions.transition(ctx)
                .abacProjectWrite(m.getProjectId())
                .checkTransition(() -> transitionPolicy.check(v.getStatus(), MethodologyVersionTransition.LOCK))
                .snapshot(() -> snapshot.of(v))
                .mutate(() -> {
                    v.setStatus(MethodologyVersionStatus.LOCKED);
                    v.setLockedAt(now);
                    v.setLockedBy(ctx.userId());
                })
                .save(() -> versions.save(v))
                .audit(AuditAction.METHODOLOGY_VERSION_LOCKED, "MethodologyVersion",
                        versionId, m.getProjectId())
                .execute();
        return v.toDomain();
    }
}

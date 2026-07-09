package uz.hrlab.grading.methodology.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
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
    private final AbacGate abacGate;
    private final MethodologyVersionStatusTransitionPolicy transitionPolicy;
    private final AuditService audit;
    private final MethodologyAuditSnapshot snapshot;

    public LockMethodologyVersionUseCase(MethodologyRepository methodologies,
                                         MethodologyVersionRepository versions,
                                         AbacGate abacGate,
                                         MethodologyVersionStatusTransitionPolicy transitionPolicy,
                                         AuditService audit,
                                         MethodologyAuditSnapshot snapshot) {
        this.methodologies = methodologies;
        this.versions = versions;
        this.abacGate = abacGate;
        this.transitionPolicy = transitionPolicy;
        this.audit = audit;
        this.snapshot = snapshot;
    }

    @Transactional
    public MethodologyVersion lock(UUID versionId) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.METHODOLOGY_LOCK);
        MethodologyVersionJpaEntity v = versions.findByIdAndTenantId(versionId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        MethodologyJpaEntity m = methodologies.findByIdAndTenantId(v.getMethodologyId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (m.getProjectId() != null) {
            abacGate.enforceCanWriteInProject(ctx, m.getProjectId());
        }
        transitionPolicy.check(v.getStatus(), MethodologyVersionTransition.LOCK);

        var beforeJson = snapshot.of(v);
        OffsetDateTime now = OffsetDateTime.now();
        v.setStatus(MethodologyVersionStatus.LOCKED);
        v.setLockedAt(now);
        v.setLockedBy(ctx.userId());
        versions.save(v);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(m.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.METHODOLOGY_VERSION_LOCKED)
                .entityType("MethodologyVersion")
                .entityId(versionId)
                .beforeJson(beforeJson)
                .afterJson(snapshot.of(v))
                .build());
        return v.toDomain();
    }
}

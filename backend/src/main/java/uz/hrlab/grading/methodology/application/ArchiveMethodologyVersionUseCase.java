package uz.hrlab.grading.methodology.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditJsonRedactor;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
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

import java.util.UUID;

/** Archives a methodology version (DRAFT/APPROVED/LOCKED → ARCHIVED). */
@Service
public class ArchiveMethodologyVersionUseCase {

    private final MethodologyRepository methodologies;
    private final MethodologyVersionRepository versions;
    private final AbacGate abacGate;
    private final MethodologyVersionStatusTransitionPolicy transitionPolicy;
    private final AuditService audit;
    private final MethodologyAuditSnapshot snapshot;
    private final AuditJsonRedactor redactor;

    public ArchiveMethodologyVersionUseCase(MethodologyRepository methodologies,
                                            MethodologyVersionRepository versions,
                                            AbacGate abacGate,
                                            MethodologyVersionStatusTransitionPolicy transitionPolicy,
                                            AuditService audit,
                                            MethodologyAuditSnapshot snapshot,
                                            AuditJsonRedactor redactor) {
        this.methodologies = methodologies;
        this.versions = versions;
        this.abacGate = abacGate;
        this.transitionPolicy = transitionPolicy;
        this.audit = audit;
        this.snapshot = snapshot;
        this.redactor = redactor;
    }

    @Transactional
    public MethodologyVersion archive(UUID versionId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("REASON_REQUIRED", "Archive reason is required");
        }
        TenantContext ctx = TenantContextHolder.requireActive();
        // F-402: defense-in-depth RBAC re-check (matches Approve/Lock/Create pattern).
        ctx.require(PermissionCodes.METHODOLOGY_EDIT);
        MethodologyVersionJpaEntity v = versions.findByIdAndTenantId(versionId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        MethodologyJpaEntity m = methodologies.findByIdAndTenantId(v.getMethodologyId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (m.getProjectId() != null) {
            abacGate.enforceCanWriteInProject(ctx, m.getProjectId());
        }
        transitionPolicy.check(v.getStatus(), MethodologyVersionTransition.ARCHIVE);

        var beforeJson = snapshot.of(v);
        v.setStatus(MethodologyVersionStatus.ARCHIVED);
        versions.save(v);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(m.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.METHODOLOGY_VERSION_ARCHIVED)
                .entityType("MethodologyVersion")
                .entityId(versionId)
                .reason(redactor.redactReason(reason))
                .beforeJson(beforeJson)
                .afterJson(snapshot.of(v))
                .build());
        return v.toDomain();
    }
}

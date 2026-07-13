package uz.hrlab.grading.methodology.application;

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
    private final MethodologyVersionStatusTransitionPolicy transitionPolicy;
    private final MethodologyAuditSnapshot snapshot;
    private final AuditJsonRedactor redactor;
    private final StatusTransitionExecutor transitions;

    public ArchiveMethodologyVersionUseCase(MethodologyRepository methodologies,
                                            MethodologyVersionRepository versions,
                                            AbacGate abacGate,
                                            MethodologyVersionStatusTransitionPolicy transitionPolicy,
                                            AuditService audit,
                                            MethodologyAuditSnapshot snapshot,
                                            AuditJsonRedactor redactor) {
        this.methodologies = methodologies;
        this.versions = versions;
        this.transitionPolicy = transitionPolicy;
        this.snapshot = snapshot;
        this.redactor = redactor;
        this.transitions = new StatusTransitionExecutor(abacGate, audit);
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

        transitions.transition(ctx)
                .abacProjectWrite(m.getProjectId())
                .checkTransition(() -> transitionPolicy.check(v.getStatus(), MethodologyVersionTransition.ARCHIVE))
                .snapshot(() -> snapshot.of(v))
                .mutate(() -> v.setStatus(MethodologyVersionStatus.ARCHIVED))
                .save(() -> versions.save(v))
                .reason(redactor.redactReason(reason))
                .audit(AuditAction.METHODOLOGY_VERSION_ARCHIVED, "MethodologyVersion",
                        versionId, m.getProjectId())
                .execute();
        return v.toDomain();
    }
}

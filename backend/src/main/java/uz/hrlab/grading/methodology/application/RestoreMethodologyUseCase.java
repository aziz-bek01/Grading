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
import uz.hrlab.grading.methodology.domain.Methodology;
import uz.hrlab.grading.methodology.domain.MethodologyStatus;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

/**
 * Restores an ARCHIVED methodology container (status ARCHIVED → ACTIVE). The
 * inverse of {@link ArchiveMethodologyUseCase}; the container has only the two
 * states ACTIVE / ARCHIVED, so restore is just the reverse transition (no new
 * state). Version-level state (DRAFT/APPROVED/LOCKED/ARCHIVED) is untouched.
 */
@Service
public class RestoreMethodologyUseCase {

    private final MethodologyRepository methodologies;
    private final AbacGate abacGate;
    private final AuditService audit;
    private final MethodologyAuditSnapshot snapshot;
    private final AuditJsonRedactor redactor;

    public RestoreMethodologyUseCase(MethodologyRepository methodologies,
                                     AbacGate abacGate, AuditService audit,
                                     MethodologyAuditSnapshot snapshot,
                                     AuditJsonRedactor redactor) {
        this.methodologies = methodologies;
        this.abacGate = abacGate;
        this.audit = audit;
        this.snapshot = snapshot;
        this.redactor = redactor;
    }

    @Transactional
    public Methodology restore(UUID id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("REASON_REQUIRED", "Restore reason is required");
        }
        TenantContext ctx = TenantContextHolder.requireActive();
        // Defense-in-depth RBAC re-check (matches Archive/Approve/Lock/Create).
        ctx.require(PermissionCodes.METHODOLOGY_EDIT);
        // BOLA: load by id + tenant so a cross-tenant id surfaces as 404.
        MethodologyJpaEntity m = methodologies.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (m.getProjectId() != null) {
            abacGate.enforceCanWriteInProject(ctx, m.getProjectId());
        }
        // Guard: only an ARCHIVED container can be restored (no-op / wrong-state
        // edits are rejected with a stable code).
        if (m.getStatus() != MethodologyStatus.ARCHIVED) {
            throw new ValidationException("METHODOLOGY_NOT_ARCHIVED",
                    "Only an archived methodology can be restored");
        }
        var beforeJson = snapshot.of(m);
        m.setStatus(MethodologyStatus.ACTIVE);
        methodologies.save(m);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(m.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.METHODOLOGY_RESTORED)
                .entityType("Methodology")
                .entityId(id)
                .reason(redactor.redactReason(reason))
                .beforeJson(beforeJson)
                .afterJson(snapshot.of(m))
                .build());
        return m.toDomain();
    }
}

package uz.hrlab.grading.methodology.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditJsonRedactor;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.methodology.domain.Methodology;
import uz.hrlab.grading.methodology.domain.MethodologyStatus;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

/** Archives a methodology container (status → ARCHIVED). */
@Service
public class ArchiveMethodologyUseCase {

    private final MethodologyRepository methodologies;
    private final AbacGate abacGate;
    private final AuditService audit;
    private final MethodologyAuditSnapshot snapshot;
    private final AuditJsonRedactor redactor;

    public ArchiveMethodologyUseCase(MethodologyRepository methodologies,
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
    public Methodology archive(UUID id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("REASON_REQUIRED", "Archive reason is required");
        }
        TenantContext ctx = TenantContextHolder.requireActive();
        // F-402: defense-in-depth RBAC re-check (matches Approve/Lock/Create pattern).
        if (!ctx.hasPermission(PermissionCodes.METHODOLOGY_EDIT)) {
            throw new PermissionDeniedException();
        }
        MethodologyJpaEntity m = methodologies.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (m.getProjectId() != null) {
            abacGate.enforceCanWriteInProject(ctx, m.getProjectId());
        }
        var beforeJson = snapshot.of(m);
        m.setStatus(MethodologyStatus.ARCHIVED);
        methodologies.save(m);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(m.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.METHODOLOGY_ARCHIVED)
                .entityType("Methodology")
                .entityId(id)
                .reason(redactor.redactReason(reason))
                .beforeJson(beforeJson)
                .afterJson(snapshot.of(m))
                .build());
        return m.toDomain();
    }
}

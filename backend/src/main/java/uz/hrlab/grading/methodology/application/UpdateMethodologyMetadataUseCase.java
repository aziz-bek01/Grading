package uz.hrlab.grading.methodology.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.methodology.domain.Methodology;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.Map;
import java.util.UUID;

/**
 * Update methodology container metadata only (name + description). Works
 * regardless of any version status — the Methodology row is just the
 * container. Factor/level/version edits go through different use cases.
 */
@Service
public class UpdateMethodologyMetadataUseCase {

    private final MethodologyRepository methodologies;
    private final AbacGate abacGate;
    private final AuditService audit;
    private final MethodologyAuditSnapshot snapshot;

    public UpdateMethodologyMetadataUseCase(MethodologyRepository methodologies,
                                            AbacGate abacGate,
                                            AuditService audit,
                                            MethodologyAuditSnapshot snapshot) {
        this.methodologies = methodologies;
        this.abacGate = abacGate;
        this.audit = audit;
        this.snapshot = snapshot;
    }

    @Transactional
    public Methodology update(UUID id, Map<String, String> nameI18n,
                              Map<String, String> descriptionI18n) {
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
        if (nameI18n != null) m.setNameI18n(nameI18n);
        if (descriptionI18n != null) m.setDescriptionI18n(descriptionI18n);
        methodologies.save(m);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(m.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.METHODOLOGY_UPDATED)
                .entityType("Methodology")
                .entityId(id)
                .beforeJson(beforeJson)
                .afterJson(snapshot.of(m))
                .build());
        return m.toDomain();
    }
}

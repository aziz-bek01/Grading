package uz.hrlab.grading.methodology.application;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.methodology.domain.MethodologyTemplateStatus;
import uz.hrlab.grading.methodology.infrastructure.CustomMethodologyTemplateRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyTemplateJpaEntity;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

/**
 * Archive a tenant CUSTOM template (Epic E) — soft delete: status → ARCHIVED so
 * the row drops out of the picker but its frozen {@code factors_snapshot} is
 * retained for audit/forensics, and methodologies already instantiated from it
 * are unaffected (the snapshot was deep-copied, not referenced).
 *
 * <p>Custom-only: a built-in code (no DB row) or any cross-tenant id resolves to
 * 404 via {@code findByIdAndTenantId} — built-ins can never be archived. Gated
 * by {@code METHODOLOGY_EDIT}.
 */
@Service
public class ArchiveCustomTemplateUseCase {

    private final CustomMethodologyTemplateRepository customTemplates;
    private final AuditService audit;
    private final MethodologyAuditSnapshot snapshot;

    public ArchiveCustomTemplateUseCase(CustomMethodologyTemplateRepository customTemplates,
                                        AuditService audit,
                                        MethodologyAuditSnapshot snapshot) {
        this.customTemplates = customTemplates;
        this.audit = audit;
        this.snapshot = snapshot;
    }

    @Transactional
    public void archive(UUID templateId) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.METHODOLOGY_EDIT);
        MethodologyTemplateJpaEntity entity = customTemplates
                .findByIdAndTenantId(templateId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        // Idempotent: already-archived stays archived (no-op audit still helps
        // forensics show the intent, but we keep before/after honest).
        JsonNode before = snapshot.of(entity);
        entity.setStatus(MethodologyTemplateStatus.ARCHIVED);
        customTemplates.save(entity);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .actorUserId(ctx.userId())
                .action(AuditAction.METHODOLOGY_TEMPLATE_ARCHIVED)
                .entityType("MethodologyTemplate")
                .entityId(templateId)
                .beforeJson(before)
                .afterJson(snapshot.of(entity))
                .build());
    }
}

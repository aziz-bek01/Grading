package uz.hrlab.grading.methodology.application;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.methodology.infrastructure.CustomMethodologyTemplateRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyTemplateJpaEntity;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.Map;
import java.util.UUID;

/**
 * Rename a tenant CUSTOM template (Epic E) — name/description i18n only. The
 * code, methodology type, scoring mode and frozen {@code factors_snapshot} are
 * immutable (the JPA entity marks them {@code updatable=false}); a different
 * factor skeleton requires saving a NEW template.
 *
 * <p>Custom-only: built-ins have no DB row, so a built-in code (or any
 * cross-tenant id) resolves to 404 via {@code findByIdAndTenantId} — built-ins
 * can never be edited. Gated by {@code METHODOLOGY_EDIT}.
 */
@Service
public class UpdateCustomTemplateUseCase {

    private final CustomMethodologyTemplateRepository customTemplates;
    private final AuditService audit;
    private final MethodologyAuditSnapshot snapshot;

    public UpdateCustomTemplateUseCase(CustomMethodologyTemplateRepository customTemplates,
                                       AuditService audit,
                                       MethodologyAuditSnapshot snapshot) {
        this.customTemplates = customTemplates;
        this.audit = audit;
        this.snapshot = snapshot;
    }

    @Transactional
    public UUID rename(UUID templateId, Map<String, String> nameI18n,
                       Map<String, String> descriptionI18n) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.METHODOLOGY_EDIT);
        MethodologyTemplateJpaEntity entity = customTemplates
                .findByIdAndTenantId(templateId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        JsonNode before = snapshot.of(entity);
        if (nameI18n != null) {
            entity.setNameI18n(nameI18n);
        }
        if (descriptionI18n != null) {
            entity.setDescriptionI18n(descriptionI18n);
        }
        customTemplates.save(entity);

        audit.record(AuditEvent.builder(ctx)
                .action(AuditAction.METHODOLOGY_TEMPLATE_UPDATED)
                .entityType("MethodologyTemplate")
                .entityId(templateId)
                .beforeJson(before)
                .afterJson(snapshot.of(entity))
                .build());
        return templateId;
    }
}

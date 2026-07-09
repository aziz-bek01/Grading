package uz.hrlab.grading.gradestructure.application;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.gradestructure.infrastructure.CustomGradeTemplateRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeTemplateJpaEntity;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.Map;
import java.util.UUID;

/**
 * Rename a tenant CUSTOM grade template (BE-8) — name/description i18n only. The
 * code, structure type and frozen {@code grades_snapshot} are immutable (the JPA
 * entity marks them {@code updatable=false}); a different grade skeleton requires
 * saving a NEW template. Mirrors the methodology {@code UpdateCustomTemplateUseCase}.
 *
 * <p>Custom-only: built-ins have no DB row, so a built-in code (or any
 * cross-tenant id) resolves to 404 via {@code findByIdAndTenantId} — built-ins
 * can never be edited. Gated by {@code GRADE_EDIT}.
 */
@Service
public class UpdateGradeTemplateUseCase {

    private final CustomGradeTemplateRepository customTemplates;
    private final AuditService audit;
    private final GradeStructureAuditSnapshot snapshot;

    public UpdateGradeTemplateUseCase(CustomGradeTemplateRepository customTemplates,
                                      AuditService audit,
                                      GradeStructureAuditSnapshot snapshot) {
        this.customTemplates = customTemplates;
        this.audit = audit;
        this.snapshot = snapshot;
    }

    @Transactional
    public UUID rename(UUID templateId, Map<String, String> nameI18n,
                       Map<String, String> descriptionI18n) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.GRADE_EDIT);
        GradeTemplateJpaEntity entity = customTemplates
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

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .actorUserId(ctx.userId())
                .action(AuditAction.GRADE_TEMPLATE_UPDATED)
                .entityType("GradeTemplate")
                .entityId(templateId)
                .beforeJson(before)
                .afterJson(snapshot.of(entity))
                .build());
        return templateId;
    }
}

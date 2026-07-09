package uz.hrlab.grading.gradestructure.application;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.gradestructure.domain.GradeTemplateStatus;
import uz.hrlab.grading.gradestructure.infrastructure.CustomGradeTemplateRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeTemplateJpaEntity;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

/**
 * Archive a tenant CUSTOM grade template (BE-8) — soft delete: status → ARCHIVED
 * so the row drops out of the picker but its frozen {@code grades_snapshot} is
 * retained for audit/forensics, and structures already instantiated from it are
 * unaffected (the snapshot was deep-copied, not referenced). Mirrors the
 * methodology {@code ArchiveCustomTemplateUseCase}.
 *
 * <p>Custom-only: a built-in code (no DB row) or any cross-tenant id resolves to
 * 404 via {@code findByIdAndTenantId} — built-ins can never be archived. Gated
 * by {@code GRADE_EDIT}.
 */
@Service
public class ArchiveGradeTemplateUseCase {

    private final CustomGradeTemplateRepository customTemplates;
    private final AuditService audit;
    private final GradeStructureAuditSnapshot snapshot;

    public ArchiveGradeTemplateUseCase(CustomGradeTemplateRepository customTemplates,
                                       AuditService audit,
                                       GradeStructureAuditSnapshot snapshot) {
        this.customTemplates = customTemplates;
        this.audit = audit;
        this.snapshot = snapshot;
    }

    @Transactional
    public void archive(UUID templateId) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.GRADE_EDIT);
        GradeTemplateJpaEntity entity = customTemplates
                .findByIdAndTenantId(templateId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        JsonNode before = snapshot.of(entity);
        entity.setStatus(GradeTemplateStatus.ARCHIVED);
        customTemplates.save(entity);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .actorUserId(ctx.userId())
                .action(AuditAction.GRADE_TEMPLATE_ARCHIVED)
                .entityType("GradeTemplate")
                .entityId(templateId)
                .beforeJson(before)
                .afterJson(snapshot.of(entity))
                .build());
    }
}

package uz.hrlab.grading.integration.imports.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.integration.imports.domain.ImportTemplateCode;
import uz.hrlab.grading.organization.domain.DepartmentStatus;
import uz.hrlab.grading.organization.domain.DepartmentType;
import uz.hrlab.grading.organization.infrastructure.DepartmentJpaEntity;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * PO-11 — ORG_STRUCTURE_V1 commit DAO. Each parsed row materializes one
 * {@link uz.hrlab.grading.organization.domain.Department} aggregate via
 * {@link DepartmentRepository}.
 *
 * <p>Multi-pass parent resolution is handled at the {@link
 * CommitImportBatchUseCase} level — by the time {@link #commit} runs, the
 * row's {@code parent_external_id} either resolves to a previously committed
 * department in this same batch, an already-existing department in the
 * project, or is rejected as MISSING_PARENT.
 *
 * <p>Cross-tenant safety: tenant_id is sourced from {@code ctx.tenantId()};
 * parent lookup uses {@code findByTenantIdAndProjectIdAndCode}. A parent code
 * that exists in a different tenant CANNOT be resolved.
 */
@Component
public class OrgStructureRowCommitter implements ImportRowCommitter {

    private final DepartmentRepository departments;
    private final AuditService audit;

    public OrgStructureRowCommitter(DepartmentRepository departments, AuditService audit) {
        this.departments = departments;
        this.audit = audit;
    }

    @Override
    public String templateCode() {
        return ImportTemplateCode.ORG_STRUCTURE_V1;
    }

    @Override
    public CommitResult commit(Map<String, String> row, ImportRowCommitContext ctx) {
        if (ctx.projectId() == null) {
            throw new ImportRowCommitException("PROJECT_REQUIRED",
                    "ORG_STRUCTURE_V1 commit requires a project");
        }

        String externalId = req(row, "external_id");
        String name = req(row, "name");

        // Duplicate guard (same project + same code already committed).
        if (departments.existsByTenantIdAndProjectIdAndCode(ctx.tenantId(),
                ctx.projectId(), externalId)) {
            throw new ImportRowCommitException("DUPLICATE_CODE",
                    "Department with external_id '" + externalId + "' already exists in project");
        }

        UUID parentId = null;
        String parentExternalId = row.get("parent_external_id");
        if (parentExternalId != null && !parentExternalId.isBlank()) {
            parentId = departments
                    .findByTenantIdAndProjectIdAndCode(ctx.tenantId(),
                            ctx.projectId(), parentExternalId.trim())
                    .map(DepartmentJpaEntity::getId)
                    .orElseThrow(() -> new ImportRowCommitException(
                            "MISSING_PARENT",
                            "Parent department with external_id '"
                                    + parentExternalId + "' was not found in this project"));
        }

        DepartmentType type = parseType(row.get("type"));
        Map<String, String> nameI18n = new HashMap<>();
        // Primary locale fallback — the i18n columns may be enriched by a
        // future template version; today we default the primary locale value.
        nameI18n.put("ru-RU", name);

        UUID id = UUID.randomUUID();
        DepartmentJpaEntity entity = new DepartmentJpaEntity(
                id, ctx.tenantId(), ctx.projectId(), parentId, externalId,
                nameI18n, type, DepartmentStatus.ACTIVE);
        departments.save(entity);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(ctx.projectId())
                .actorUserId(ctx.actorUserId())
                .action(AuditAction.DEPARTMENT_CREATED)
                .entityType("Department")
                .entityId(id)
                .reason("import_batch=" + ctx.importBatchId())
                .build());

        return new CommitResult("Department", id, AuditAction.DEPARTMENT_CREATED);
    }

    private static String req(Map<String, String> row, String key) {
        String v = row.get(key);
        if (v == null || v.isBlank()) {
            throw new ImportRowCommitException("MISSING_REQUIRED_FIELD",
                    "Required field missing: " + key);
        }
        return v.trim();
    }

    private static DepartmentType parseType(String raw) {
        // Blank intentionally defaults to DEPARTMENT (type is an optional
        // column). An unknown/typo value is rejected with a clear row error
        // instead of being silently coerced.
        if (raw == null || raw.isBlank()) return DepartmentType.DEPARTMENT;
        try {
            return DepartmentType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ImportRowCommitException("INVALID_DEPARTMENT_TYPE",
                    "Invalid type '" + raw.trim() + "'. Allowed values: "
                            + allowedTypes());
        }
    }

    private static String allowedTypes() {
        return String.join(", ",
                java.util.Arrays.stream(DepartmentType.values())
                        .map(Enum::name).toArray(String[]::new));
    }
}

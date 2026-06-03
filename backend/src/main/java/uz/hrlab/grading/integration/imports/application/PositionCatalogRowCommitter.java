package uz.hrlab.grading.integration.imports.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.integration.imports.domain.ImportTemplateCode;
import uz.hrlab.grading.organization.infrastructure.DepartmentJpaEntity;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;
import uz.hrlab.grading.position.domain.PositionStatus;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * PO-11 — POSITION_CATALOG_V1 commit DAO. Materializes one Position per
 * parsed row, resolving {@code department_external_id} → Department via
 * {@link DepartmentRepository}.
 *
 * <p>Cross-tenant safety: department lookup is scoped to (tenant_id,
 * project_id, code) — a department in a different tenant or different
 * project cannot resolve.
 */
@Component
public class PositionCatalogRowCommitter implements ImportRowCommitter {

    private final PositionRepository positions;
    private final DepartmentRepository departments;
    private final AuditService audit;

    public PositionCatalogRowCommitter(PositionRepository positions,
                                       DepartmentRepository departments,
                                       AuditService audit) {
        this.positions = positions;
        this.departments = departments;
        this.audit = audit;
    }

    @Override
    public String templateCode() {
        return ImportTemplateCode.POSITION_CATALOG_V1;
    }

    @Override
    public CommitResult commit(Map<String, String> row, ImportRowCommitContext ctx) {
        if (ctx.projectId() == null) {
            throw new ImportRowCommitException("PROJECT_REQUIRED",
                    "POSITION_CATALOG_V1 commit requires a project");
        }
        String externalId = req(row, "external_id");
        String title = req(row, "title");
        String departmentExternalId = req(row, "department_external_id");

        DepartmentJpaEntity dept = departments
                .findByTenantIdAndProjectIdAndCode(ctx.tenantId(), ctx.projectId(),
                        departmentExternalId)
                .orElseThrow(() -> new ImportRowCommitException("MISSING_DEPARTMENT",
                        "Department with external_id '" + departmentExternalId
                                + "' was not found in this project"));

        if (positions.existsByTenantIdAndProjectIdAndCode(ctx.tenantId(),
                ctx.projectId(), externalId)) {
            throw new ImportRowCommitException("DUPLICATE_CODE",
                    "Position with external_id '" + externalId + "' already exists in project");
        }

        PositionStatus status = parseStatus(row.get("status"));
        Map<String, String> titleI18n = new HashMap<>();
        titleI18n.put("ru-RU", title);

        UUID id = UUID.randomUUID();
        PositionJpaEntity entity = new PositionJpaEntity(
                id, ctx.tenantId(), ctx.projectId(), dept.getId(), externalId,
                titleI18n,
                emptyToNull(row.get("function")),
                emptyToNull(row.get("category")),
                emptyToNull(row.get("job_family")),
                emptyToNull(row.get("job_level")),
                status);
        positions.save(entity);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(ctx.projectId())
                .actorUserId(ctx.actorUserId())
                .action(AuditAction.POSITION_CREATED)
                .entityType("Position")
                .entityId(id)
                .reason("import_batch=" + ctx.importBatchId())
                .build());

        return new CommitResult("Position", id, AuditAction.POSITION_CREATED);
    }

    private static String req(Map<String, String> row, String key) {
        String v = row.get(key);
        if (v == null || v.isBlank()) {
            throw new ImportRowCommitException("MISSING_REQUIRED_FIELD",
                    "Required field missing: " + key);
        }
        return v.trim();
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static PositionStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) return PositionStatus.ACTIVE;
        try {
            return PositionStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return PositionStatus.ACTIVE;
        }
    }
}

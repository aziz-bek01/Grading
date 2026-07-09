package uz.hrlab.grading.gradestructure.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.ConflictException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.gradestructure.domain.GradeTemplateStatus;
import uz.hrlab.grading.gradestructure.infrastructure.CustomGradeTemplateRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeTemplateJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeTemplateSnapshot;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Save grade structure as reusable template" (BE-8/9). Freezes a structure's
 * grades + bands into a tenant CUSTOM template ({@code grade_templates}). The
 * frozen {@code grades_snapshot} is a deep copy (no FKs) so the template
 * reproduces a brand-new DRAFT structure even if the source is later edited.
 * Mirrors the methodology {@code SaveAsTemplateUseCase} one-for-one.
 *
 * <p>Gated by {@code GRADE_EDIT}. The tenant is taken from {@code TenantContext};
 * the snapshot is read tenant-scoped, the row is written {@code tenant_id = ctx}.
 * RLS is defense-in-depth on top of the explicit predicates.
 */
@Service
public class SaveAsGradeTemplateUseCase {

    private final GradeTemplateCatalog templateCatalog;
    private final CustomGradeTemplateRepository customTemplates;
    private final GradeStructureRepository structures;
    private final GradeRepository grades;
    private final GradeBandRepository bands;
    private final AbacGate abacGate;
    private final AuditService audit;
    private final GradeStructureAuditSnapshot snapshot;

    public SaveAsGradeTemplateUseCase(GradeTemplateCatalog templateCatalog,
                                      CustomGradeTemplateRepository customTemplates,
                                      GradeStructureRepository structures,
                                      GradeRepository grades,
                                      GradeBandRepository bands,
                                      AbacGate abacGate,
                                      AuditService audit,
                                      GradeStructureAuditSnapshot snapshot) {
        this.templateCatalog = templateCatalog;
        this.customTemplates = customTemplates;
        this.structures = structures;
        this.grades = grades;
        this.bands = bands;
        this.abacGate = abacGate;
        this.audit = audit;
        this.snapshot = snapshot;
    }

    @Transactional
    public UUID saveAsTemplate(UUID structureId, String code,
                               Map<String, String> nameI18n,
                               Map<String, String> descriptionI18n) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.GRADE_EDIT);

        // Reserved-vs-builtin namespace collision → 409 (a custom code may not
        // shadow a built-in registry code).
        if (templateCatalog.isBuiltinCode(code)) {
            throw new ConflictException("GRADE_TEMPLATE_CODE_EXISTS",
                    "Template code is reserved by a built-in template");
        }
        // Duplicate per-tenant code → 409 (UNIQUE (tenant_id, code)).
        if (customTemplates.existsByTenantIdAndCode(ctx.tenantId(), code)) {
            throw new ConflictException("GRADE_TEMPLATE_CODE_EXISTS",
                    "Template code already exists in this tenant");
        }

        // Load the source structure (tenant-scoped, must exist). Cross-tenant → 404.
        GradeStructureJpaEntity structure = structures
                .findByIdAndTenantId(structureId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (structure.getProjectId() != null) {
            // Saving a template reads the source — list-scope ABAC is sufficient.
            abacGate.enforceCanListInProject(ctx, structure.getProjectId());
        }

        GradeTemplateSnapshot gradesSnapshot = buildSnapshot(ctx.tenantId(), structureId);

        UUID templateId = UUID.randomUUID();
        GradeTemplateJpaEntity entity = new GradeTemplateJpaEntity(
                templateId, ctx.tenantId(), code,
                structure.getStructureType(), gradesSnapshot,
                GradeTemplateStatus.ACTIVE);
        entity.setNameI18n(nameI18n);
        entity.setDescriptionI18n(descriptionI18n);
        customTemplates.save(entity);

        audit.record(AuditEvent.builder(ctx)
                .projectId(structure.getProjectId())
                .action(AuditAction.GRADE_TEMPLATE_CREATED)
                .entityType("GradeTemplate")
                .entityId(templateId)
                .reason("save-as-template from structure " + structure.getCode())
                .afterJson(snapshot.of(entity))
                .build());

        return templateId;
    }

    /**
     * Reads the structure's grades + bands (tenant-scoped, ordered) and freezes
     * them into the jsonb snapshot shape. No ids/tenant/audit columns captured.
     */
    private GradeTemplateSnapshot buildSnapshot(UUID tenantId, UUID structureId) {
        List<GradeJpaEntity> srcGrades = grades
                .findAllByTenantIdAndGradeStructureIdOrderBySortOrderAsc(tenantId, structureId);
        List<GradeTemplateSnapshot.GradeSnapshot> gradeSnaps = new ArrayList<>();
        for (GradeJpaEntity g : srcGrades) {
            GradeBandJpaEntity band = bands.findByTenantIdAndGradeId(tenantId, g.getId())
                    .orElse(null);
            gradeSnaps.add(new GradeTemplateSnapshot.GradeSnapshot(
                    g.getGradeNumber(), g.getSortOrder(),
                    copy(g.getNameI18n()), copy(g.getDescriptionI18n()),
                    band == null ? null : band.getMinScore(),
                    band == null ? null : band.getMaxScore()));
        }
        return new GradeTemplateSnapshot(gradeSnaps);
    }

    private static Map<String, String> copy(Map<String, String> in) {
        return in == null || in.isEmpty() ? null : new LinkedHashMap<>(in);
    }
}

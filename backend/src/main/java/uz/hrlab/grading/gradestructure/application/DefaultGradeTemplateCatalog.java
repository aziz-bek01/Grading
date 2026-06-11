package uz.hrlab.grading.gradestructure.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.common.exception.ResourceNotFoundException;
import uz.hrlab.grading.gradestructure.domain.GradeTemplateStatus;
import uz.hrlab.grading.gradestructure.infrastructure.CustomGradeTemplateRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeTemplateJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeTemplateSnapshot;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Default {@link GradeTemplateCatalog} (BE-8) — UNIONs the global built-in
 * registry with the tenant's DB-backed custom grade templates, and resolves
 * either source into the SAME {@link GradeStructureTemplate} instantiation shape
 * consumed by {@code CreateGradeStructureFromTemplateUseCase}. This is the single
 * seam that removes the need to duplicate deep-copy / instantiation logic per
 * source. EXACT mirror of the methodology {@code DefaultTemplateCatalog}.
 *
 * <p>Tenant scoping is explicit on every custom-template read
 * ({@code findByTenantIdAndStatus} / {@code findByTenantIdAndCode}); the RLS
 * policy on {@code grade_templates} is defense-in-depth on top.
 */
@Component
public class DefaultGradeTemplateCatalog implements GradeTemplateCatalog {

    private final GradeStructureTemplateRegistry registry;
    private final CustomGradeTemplateRepository customTemplates;

    public DefaultGradeTemplateCatalog(GradeStructureTemplateRegistry registry,
                                       CustomGradeTemplateRepository customTemplates) {
        this.registry = registry;
        this.customTemplates = customTemplates;
    }

    @Override
    public List<GradeTemplateCatalogEntry> list(UUID tenantId) {
        List<GradeTemplateCatalogEntry> out = new ArrayList<>();
        // Built-ins first, in their stable registry order, read-only + global.
        for (GradeStructureTemplate t : registry.list()) {
            out.add(new GradeTemplateCatalogEntry(
                    null, t.code(), GradeTemplateSource.BUILTIN, true,
                    t.nameI18n(), t.descriptionI18n(),
                    t.structureType(),
                    t.grades() == null ? 0 : t.grades().size()));
        }
        // Then the tenant's ACTIVE custom templates (cross-tenant rows never
        // appear — the query is tenant-bound and RLS enforces the same).
        for (GradeTemplateJpaEntity e :
                customTemplates.findByTenantIdAndStatus(tenantId, GradeTemplateStatus.ACTIVE)) {
            out.add(new GradeTemplateCatalogEntry(
                    e.getId(), e.getCode(), GradeTemplateSource.CUSTOM, false,
                    e.getNameI18n(), e.getDescriptionI18n(),
                    e.getStructureType(),
                    gradeCount(e.getGradesSnapshot())));
        }
        return out;
    }

    @Override
    public GradeStructureTemplate findInstantiable(UUID tenantId, String code) {
        // Built-in wins — its code namespace is reserved, so a custom row can
        // never have shadowed it (save-as-template rejects such a code).
        var builtin = registry.findByCode(code);
        if (builtin.isPresent()) {
            return builtin.get();
        }
        // Custom: only ACTIVE rows of THIS tenant are instantiable. An archived
        // or cross-tenant code resolves to empty → 404 (no existence reveal).
        return customTemplates.findByTenantIdAndCode(tenantId, code)
                .filter(e -> e.getStatus() == GradeTemplateStatus.ACTIVE)
                .map(DefaultGradeTemplateCatalog::toInstantiationShape)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Grade structure template not found: " + code));
    }

    @Override
    public boolean isBuiltinCode(String code) {
        return registry.isBuiltinCode(code);
    }

    private static int gradeCount(GradeTemplateSnapshot snapshot) {
        return snapshot == null || snapshot.grades() == null ? 0 : snapshot.grades().size();
    }

    /**
     * Maps a frozen {@link GradeTemplateSnapshot} (jsonb persistence shape) back
     * into the common {@link GradeStructureTemplate} instantiation shape so the
     * create-from-template use case copies custom and built-in templates through
     * one code path. The snapshot's grade/band field names mirror the
     * instantiation record 1:1, so this is a structural copy.
     */
    static GradeStructureTemplate toInstantiationShape(GradeTemplateJpaEntity e) {
        GradeTemplateSnapshot snapshot = e.getGradesSnapshot();
        List<GradeStructureTemplate.GradeRowTemplate> grades = new ArrayList<>();
        BigDecimal min = null;
        BigDecimal max = null;
        if (snapshot != null && snapshot.grades() != null) {
            for (GradeTemplateSnapshot.GradeSnapshot gs : snapshot.grades()) {
                grades.add(new GradeStructureTemplate.GradeRowTemplate(
                        gs.gradeNumber(), gs.sortOrder(), gs.nameI18n(),
                        gs.minScore(), gs.maxScore()));
                if (gs.minScore() != null && (min == null || gs.minScore().compareTo(min) < 0)) {
                    min = gs.minScore();
                }
                if (gs.maxScore() != null && (max == null || gs.maxScore().compareTo(max) > 0)) {
                    max = gs.maxScore();
                }
            }
        }
        return new GradeStructureTemplate(
                e.getCode(), e.getStructureType(), grades.size(),
                min, max, e.getNameI18n(), e.getDescriptionI18n(), grades);
    }
}

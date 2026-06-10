package uz.hrlab.grading.methodology.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.common.exception.ResourceNotFoundException;
import uz.hrlab.grading.methodology.domain.MethodologyTemplateStatus;
import uz.hrlab.grading.methodology.infrastructure.CustomMethodologyTemplateRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyTemplateJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyTemplateSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Default {@link TemplateCatalog} — UNIONs the global built-in registry with the
 * tenant's DB-backed custom templates, and resolves either source into the SAME
 * {@link MethodologyTemplate} instantiation shape consumed by
 * {@code CreateMethodologyFromTemplateUseCase}. This is the single seam that
 * removes the need to duplicate deep-copy / instantiation logic per source.
 *
 * <p>Tenant scoping is explicit on every custom-template read
 * ({@code findByTenantIdAndStatus} / {@code findByTenantIdAndCode}); the RLS
 * policy on {@code methodology_templates} is defense-in-depth on top.
 */
@Component
public class DefaultTemplateCatalog implements TemplateCatalog {

    private final MethodologyTemplateRegistry registry;
    private final CustomMethodologyTemplateRepository customTemplates;

    public DefaultTemplateCatalog(MethodologyTemplateRegistry registry,
                                  CustomMethodologyTemplateRepository customTemplates) {
        this.registry = registry;
        this.customTemplates = customTemplates;
    }

    @Override
    public List<TemplateCatalogEntry> list(UUID tenantId) {
        List<TemplateCatalogEntry> out = new ArrayList<>();
        // Built-ins first, in their stable registry order, read-only + global.
        for (MethodologyTemplate t : registry.list()) {
            out.add(new TemplateCatalogEntry(
                    null, t.code(), TemplateSource.BUILTIN, true,
                    t.nameI18n(), t.descriptionI18n(),
                    t.methodologyType(), t.scoringMode(),
                    t.factors() == null ? 0 : t.factors().size()));
        }
        // Then the tenant's ACTIVE custom templates (cross-tenant rows never
        // appear — the query is tenant-bound and RLS enforces the same).
        for (MethodologyTemplateJpaEntity e :
                customTemplates.findByTenantIdAndStatus(tenantId, MethodologyTemplateStatus.ACTIVE)) {
            out.add(new TemplateCatalogEntry(
                    e.getId(), e.getCode(), TemplateSource.CUSTOM, false,
                    e.getNameI18n(), e.getDescriptionI18n(),
                    e.getMethodologyType(), e.getScoringMode(),
                    factorCount(e.getFactorsSnapshot())));
        }
        return out;
    }

    @Override
    public MethodologyTemplate findInstantiable(UUID tenantId, String code) {
        // Built-in wins — its code namespace is reserved, so a custom row can
        // never have shadowed it (save-as-template rejects such a code).
        var builtin = registry.findByCode(code);
        if (builtin.isPresent()) {
            return builtin.get();
        }
        // Custom: only ACTIVE rows of THIS tenant are instantiable. An archived
        // or cross-tenant code resolves to empty → 404 (no existence reveal).
        return customTemplates.findByTenantIdAndCode(tenantId, code)
                .filter(e -> e.getStatus() == MethodologyTemplateStatus.ACTIVE)
                .map(DefaultTemplateCatalog::toInstantiationShape)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Methodology template not found: " + code));
    }

    @Override
    public boolean isBuiltinCode(String code) {
        return registry.isBuiltinCode(code);
    }

    private static int factorCount(MethodologyTemplateSnapshot snapshot) {
        return snapshot == null || snapshot.factors() == null ? 0 : snapshot.factors().size();
    }

    /**
     * Maps a frozen {@link MethodologyTemplateSnapshot} (jsonb persistence shape)
     * back into the common {@link MethodologyTemplate} instantiation shape so the
     * create-from-template use case copies custom and built-in templates through
     * one code path. The snapshot's factor/level field names mirror the
     * instantiation record 1:1, so this is a structural copy.
     */
    static MethodologyTemplate toInstantiationShape(MethodologyTemplateJpaEntity e) {
        MethodologyTemplateSnapshot snapshot = e.getFactorsSnapshot();
        List<MethodologyTemplate.FactorTemplate> factors = new ArrayList<>();
        if (snapshot != null && snapshot.factors() != null) {
            for (MethodologyTemplateSnapshot.FactorSnapshot fs : snapshot.factors()) {
                List<MethodologyTemplate.LevelTemplate> levels = new ArrayList<>();
                if (fs.levels() != null) {
                    for (MethodologyTemplateSnapshot.LevelSnapshot ls : fs.levels()) {
                        levels.add(new MethodologyTemplate.LevelTemplate(
                                ls.code(), ls.levelOrder(), ls.points(), ls.scaleValue(),
                                ls.labelI18n(), ls.descriptionI18n()));
                    }
                }
                factors.add(new MethodologyTemplate.FactorTemplate(
                        fs.code(), fs.nameI18n(), fs.descriptionI18n(),
                        fs.weight(), fs.maxPoints(),
                        fs.sortOrder(), fs.required(), levels));
            }
        }
        return new MethodologyTemplate(
                e.getCode(), e.getMethodologyType(), e.getScoringMode(),
                e.getTargetTotalPoints(), e.getNameI18n(), e.getDescriptionI18n(),
                factors);
    }
}

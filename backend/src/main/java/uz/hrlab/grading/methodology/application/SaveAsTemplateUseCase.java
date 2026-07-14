package uz.hrlab.grading.methodology.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.ConflictException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.methodology.domain.MethodologyTemplateStatus;
import uz.hrlab.grading.methodology.infrastructure.CustomMethodologyTemplateRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyTemplateJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyTemplateSnapshot;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * "Save methodology version as reusable template" (Epic E). Freezes a version's
 * factors + levels into a tenant CUSTOM template
 * ({@code methodology_templates}). The frozen {@code factors_snapshot} is a deep
 * copy (no FKs) so the template reproduces a brand-new methodology v1 even if
 * the source version is later edited.
 *
 * <p>Gated by {@code METHODOLOGY_CREATE} (reuses the create permission — no new
 * permission/seed). The tenant is taken from {@code TenantContext}; the snapshot
 * is read tenant-scoped, the row is written {@code tenant_id = ctx}. RLS is
 * defense-in-depth on top of the explicit predicates.
 */
@Service
public class SaveAsTemplateUseCase {

    private final TemplateCatalog templateCatalog;
    private final CustomMethodologyTemplateRepository customTemplates;
    private final MethodologyRepository methodologies;
    private final MethodologyVersionRepository versions;
    private final FactorRepository factors;
    private final FactorLevelRepository levels;
    private final AbacGate abacGate;
    private final AuditService audit;
    private final MethodologyAuditSnapshot snapshot;

    public SaveAsTemplateUseCase(TemplateCatalog templateCatalog,
                                 CustomMethodologyTemplateRepository customTemplates,
                                 MethodologyRepository methodologies,
                                 MethodologyVersionRepository versions,
                                 FactorRepository factors,
                                 FactorLevelRepository levels,
                                 AbacGate abacGate,
                                 AuditService audit,
                                 MethodologyAuditSnapshot snapshot) {
        this.templateCatalog = templateCatalog;
        this.customTemplates = customTemplates;
        this.methodologies = methodologies;
        this.versions = versions;
        this.factors = factors;
        this.levels = levels;
        this.abacGate = abacGate;
        this.audit = audit;
        this.snapshot = snapshot;
    }

    /**
     * Methodology-scoped variant (Epic E endpoint
     * {@code POST /methodologies/{id}/save-as-template}): snapshots the
     * methodology's LATEST version. Resolves the version tenant-scoped, then
     * delegates to {@link #saveAsTemplate(SaveAsTemplateCommand)} so the snapshot
     * + dup-code + audit logic is not duplicated. Cross-tenant methodology or a
     * methodology with no versions → 404.
     */
    @Transactional
    public UUID saveLatestVersionAsTemplate(UUID methodologyId, String code,
                                            java.util.Map<String, String> nameI18n,
                                            java.util.Map<String, String> descriptionI18n) {
        TenantContext ctx = TenantContextHolder.requireActive();
        // findByIdAndTenantId enforces the tenant boundary (cross-tenant → 404).
        methodologies.findByIdAndTenantId(methodologyId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        MethodologyVersionJpaEntity latest = versions
                .findFirstByTenantIdAndMethodologyIdOrderByVersionNumberDesc(
                        ctx.tenantId(), methodologyId)
                .orElseThrow(TenantAccessDeniedException::new);
        return saveAsTemplate(new SaveAsTemplateCommand(
                latest.getId(), code, nameI18n, descriptionI18n));
    }

    @Transactional
    public UUID saveAsTemplate(SaveAsTemplateCommand cmd) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.METHODOLOGY_CREATE);

        // Reserved-vs-builtin namespace collision → 409 (a custom code may not
        // shadow a built-in registry code).
        if (templateCatalog.isBuiltinCode(cmd.code())) {
            throw new ConflictException("METHODOLOGY_TEMPLATE_CODE_EXISTS",
                    "Template code is reserved by a built-in template");
        }
        // Duplicate per-tenant code → 409 (UNIQUE (tenant_id, code)).
        if (customTemplates.existsByTenantIdAndCode(ctx.tenantId(), cmd.code())) {
            throw new ConflictException("METHODOLOGY_TEMPLATE_CODE_EXISTS",
                    "Template code already exists in this tenant");
        }

        // Load the source version (tenant-scoped, must exist) + its methodology
        // (for type + project ABAC). Cross-tenant → 404.
        MethodologyVersionJpaEntity version = versions
                .findByIdAndTenantId(cmd.sourceVersionId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        MethodologyJpaEntity methodology = methodologies
                .findByIdAndTenantId(version.getMethodologyId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (methodology.getProjectId() != null) {
            // Saving a template reads the source — list-scope ABAC is sufficient.
            abacGate.enforceCanListInProject(ctx, methodology.getProjectId());
        }

        MethodologyTemplateSnapshot factorsSnapshot = buildSnapshot(ctx.tenantId(), version.getId());

        UUID templateId = UUID.randomUUID();
        MethodologyTemplateJpaEntity entity = new MethodologyTemplateJpaEntity(
                templateId, ctx.tenantId(), cmd.code(),
                methodology.getMethodologyType(), version.getScoringMode(),
                version.getTargetTotalPoints(), factorsSnapshot,
                MethodologyTemplateStatus.ACTIVE);
        entity.setNameI18n(cmd.nameI18n());
        entity.setDescriptionI18n(cmd.descriptionI18n());
        customTemplates.save(entity);

        audit.record(AuditEvent.builder(ctx)
                .projectId(methodology.getProjectId())
                .action(AuditAction.METHODOLOGY_TEMPLATE_CREATED)
                .entityType("MethodologyTemplate")
                .entityId(templateId)
                .reason("save-as-template from version " + version.getVersionNumber())
                .afterJson(snapshot.of(entity))
                .build());

        return templateId;
    }

    /**
     * Reads the version's factors + levels (tenant-scoped, ordered) and freezes
     * them into the jsonb snapshot shape. No ids/tenant/audit columns captured.
     */
    private MethodologyTemplateSnapshot buildSnapshot(UUID tenantId, UUID versionId) {
        List<FactorJpaEntity> srcFactors = factors
                .findAllByTenantIdAndMethodologyVersionIdOrderBySortOrderAsc(tenantId, versionId);
        // Batch every factor's levels in ONE tenant-scoped query (was one
        // findAllByTenantIdAndFactorId per factor → N+1). Global level_order ASC +
        // group-by-factorId preserves each factor's per-level order byte-for-byte
        // (BE-26 pattern, as in DefaultReportDataPort).
        Set<UUID> factorIds = new LinkedHashSet<>();
        for (FactorJpaEntity f : srcFactors) {
            factorIds.add(f.getId());
        }
        Map<UUID, List<FactorLevelJpaEntity>> levelsByFactor = new HashMap<>();
        if (!factorIds.isEmpty()) {
            for (FactorLevelJpaEntity l : levels
                    .findAllByTenantIdAndFactorIdInOrderByLevelOrderAsc(tenantId, factorIds)) {
                levelsByFactor.computeIfAbsent(l.getFactorId(), k -> new ArrayList<>()).add(l);
            }
        }
        List<MethodologyTemplateSnapshot.FactorSnapshot> factorSnaps = new ArrayList<>();
        for (FactorJpaEntity f : srcFactors) {
            List<MethodologyTemplateSnapshot.LevelSnapshot> levelSnaps = new ArrayList<>();
            for (FactorLevelJpaEntity l : levelsByFactor.getOrDefault(f.getId(), List.of())) {
                levelSnaps.add(new MethodologyTemplateSnapshot.LevelSnapshot(
                        l.getCode(), l.getLevelOrder(), l.getPoints(), l.getScaleValue(),
                        copy(l.getLabelI18n()), copy(l.getDescriptionI18n())));
            }
            factorSnaps.add(new MethodologyTemplateSnapshot.FactorSnapshot(
                    f.getCode(), f.getSortOrder(), f.getWeight(), f.getMaxPoints(),
                    f.isRequired(), copy(f.getNameI18n()), copy(f.getDescriptionI18n()),
                    levelSnaps));
        }
        return new MethodologyTemplateSnapshot(factorSnaps);
    }

    private static java.util.Map<String, String> copy(java.util.Map<String, String> in) {
        return in == null || in.isEmpty() ? null : new java.util.LinkedHashMap<>(in);
    }
}

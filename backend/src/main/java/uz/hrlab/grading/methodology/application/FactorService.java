package uz.hrlab.grading.methodology.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.methodology.domain.Factor;
import uz.hrlab.grading.methodology.domain.MethodologyVersionImmutabilityPolicy;
import uz.hrlab.grading.methodology.infrastructure.FactorJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Factor write operations (add / update / remove / reorder). All mutate
 * methodology_versions in DRAFT state only — enforced by
 * {@link MethodologyVersionImmutabilityPolicy} and the DB trigger
 * {@code trg_factor_immutability_on_locked_version}.
 */
@Service
public class FactorService {

    private final MethodologyRepository methodologies;
    private final MethodologyVersionRepository versions;
    private final FactorRepository factors;
    private final AbacGate abacGate;
    private final MethodologyVersionImmutabilityPolicy immutabilityPolicy;
    private final AuditService audit;
    private final MethodologyAuditSnapshot snapshot;

    public FactorService(MethodologyRepository methodologies,
                         MethodologyVersionRepository versions,
                         FactorRepository factors,
                         AbacGate abacGate,
                         MethodologyVersionImmutabilityPolicy immutabilityPolicy,
                         AuditService audit,
                         MethodologyAuditSnapshot snapshot) {
        this.methodologies = methodologies;
        this.versions = versions;
        this.factors = factors;
        this.abacGate = abacGate;
        this.immutabilityPolicy = immutabilityPolicy;
        this.audit = audit;
        this.snapshot = snapshot;
    }

    @Transactional
    public Factor add(UUID methodologyVersionId, FactorCommand cmd) {
        TenantContext ctx = requireEditPerm();
        VersionContext vctx = loadAndGate(methodologyVersionId, ctx);
        immutabilityPolicy.ensureMutable(vctx.version.getStatus());

        if (factors.existsByTenantIdAndMethodologyVersionIdAndCode(
                ctx.tenantId(), methodologyVersionId, cmd.code())) {
            throw new ValidationException("FACTOR_CODE_DUPLICATE",
                    "Factor code already exists for this methodology version");
        }
        int sortOrder = cmd.sortOrder() != null ? cmd.sortOrder()
                : nextSortOrder(ctx.tenantId(), methodologyVersionId);
        UUID id = UUID.randomUUID();
        FactorJpaEntity f = new FactorJpaEntity(
                id, ctx.tenantId(), methodologyVersionId, cmd.code(),
                cmd.weight(), cmd.maxPoints(), sortOrder,
                cmd.required() == null ? true : cmd.required());
        f.setNameI18n(cmd.nameI18n());
        f.setDescriptionI18n(cmd.descriptionI18n());
        factors.save(f);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(vctx.methodology.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.FACTOR_CREATED)
                .entityType("Factor")
                .entityId(id)
                .afterJson(snapshot.of(f))
                .build());
        return f.toDomain();
    }

    @Transactional
    public Factor update(UUID factorId, FactorCommand cmd) {
        TenantContext ctx = requireEditPerm();
        FactorJpaEntity f = factors.findByIdAndTenantId(factorId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        VersionContext vctx = loadAndGate(f.getMethodologyVersionId(), ctx);
        immutabilityPolicy.ensureMutable(vctx.version.getStatus());

        var beforeJson = snapshot.of(f);
        if (cmd.code() != null && !cmd.code().equals(f.getCode())) {
            if (factors.existsByTenantIdAndMethodologyVersionIdAndCode(
                    ctx.tenantId(), f.getMethodologyVersionId(), cmd.code())) {
                throw new ValidationException("FACTOR_CODE_DUPLICATE",
                        "Factor code already exists for this methodology version");
            }
            f.setCode(cmd.code());
        }
        if (cmd.nameI18n() != null) f.setNameI18n(cmd.nameI18n());
        if (cmd.descriptionI18n() != null) f.setDescriptionI18n(cmd.descriptionI18n());
        if (cmd.weight() != null) f.setWeight(cmd.weight());
        if (cmd.maxPoints() != null) f.setMaxPoints(cmd.maxPoints());
        if (cmd.required() != null) f.setRequired(cmd.required());
        if (cmd.sortOrder() != null) f.setSortOrder(cmd.sortOrder());
        factors.save(f);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(vctx.methodology.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.FACTOR_UPDATED)
                .entityType("Factor")
                .entityId(factorId)
                .beforeJson(beforeJson)
                .afterJson(snapshot.of(f))
                .build());
        return f.toDomain();
    }

    @Transactional
    public void remove(UUID factorId) {
        TenantContext ctx = requireEditPerm();
        FactorJpaEntity f = factors.findByIdAndTenantId(factorId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        VersionContext vctx = loadAndGate(f.getMethodologyVersionId(), ctx);
        immutabilityPolicy.ensureMutable(vctx.version.getStatus());

        var beforeJson = snapshot.of(f);
        factors.delete(f);
        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(vctx.methodology.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.FACTOR_REMOVED)
                .entityType("Factor")
                .entityId(factorId)
                .beforeJson(beforeJson)
                .build());
    }

    /**
     * Reorder factors atomically. Input is the complete ordered list of factor
     * ids for the version. Rejects partial lists or unknown ids. Avoids unique
     * index collisions by writing through a staging offset.
     */
    @Transactional
    public void reorder(UUID methodologyVersionId, List<UUID> orderedIds) {
        TenantContext ctx = requireEditPerm();
        VersionContext vctx = loadAndGate(methodologyVersionId, ctx);
        immutabilityPolicy.ensureMutable(vctx.version.getStatus());

        List<FactorJpaEntity> existing = factors
                .findAllByTenantIdAndMethodologyVersionIdOrderBySortOrderAsc(
                        ctx.tenantId(), methodologyVersionId);
        if (orderedIds == null || orderedIds.size() != existing.size()) {
            throw new ValidationException("FACTOR_REORDER_MISMATCH",
                    "Reorder payload must include every factor exactly once");
        }
        Set<UUID> existingIds = new HashSet<>();
        existing.forEach(e -> existingIds.add(e.getId()));
        if (!existingIds.equals(new HashSet<>(orderedIds))) {
            throw new ValidationException("FACTOR_REORDER_MISMATCH",
                    "Reorder payload does not match existing factor ids");
        }
        Map<UUID, FactorJpaEntity> byId = new HashMap<>();
        existing.forEach(e -> byId.put(e.getId(), e));

        // Two-phase rewrite to dodge unique(sort_order) collisions.
        int offset = 10_000;
        for (int i = 0; i < orderedIds.size(); i++) {
            FactorJpaEntity f = byId.get(orderedIds.get(i));
            f.setSortOrder(offset + i);
            factors.save(f);
        }
        for (int i = 0; i < orderedIds.size(); i++) {
            FactorJpaEntity f = byId.get(orderedIds.get(i));
            f.setSortOrder(i + 1);
            factors.save(f);
        }

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(vctx.methodology.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.FACTOR_REORDERED)
                .entityType("MethodologyVersion")
                .entityId(methodologyVersionId)
                .reason("count=" + orderedIds.size())
                .build());
    }

    private int nextSortOrder(UUID tenantId, UUID methodologyVersionId) {
        return factors
                .findAllByTenantIdAndMethodologyVersionIdOrderBySortOrderAsc(
                        tenantId, methodologyVersionId)
                .size() + 1;
    }

    private TenantContext requireEditPerm() {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.METHODOLOGY_EDIT)) {
            throw new PermissionDeniedException();
        }
        return ctx;
    }

    private VersionContext loadAndGate(UUID versionId, TenantContext ctx) {
        MethodologyVersionJpaEntity v = versions.findByIdAndTenantId(versionId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        MethodologyJpaEntity m = methodologies.findByIdAndTenantId(v.getMethodologyId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (m.getProjectId() != null) {
            abacGate.enforceCanWriteInProject(ctx, m.getProjectId());
        }
        return new VersionContext(m, v);
    }

    private record VersionContext(MethodologyJpaEntity methodology,
                                  MethodologyVersionJpaEntity version) { }
}

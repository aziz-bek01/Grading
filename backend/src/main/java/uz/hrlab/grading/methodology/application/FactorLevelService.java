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
import uz.hrlab.grading.methodology.domain.FactorLevel;
import uz.hrlab.grading.methodology.domain.MethodologyVersionImmutabilityPolicy;
import uz.hrlab.grading.methodology.infrastructure.FactorJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * FactorLevel write operations (add / update / remove / reorder). Same
 * DRAFT-free / APPROVED-carve-out / LOCKED-immutable gating as
 * {@link FactorService} (BE-2); the APPROVED branch sets the
 * {@code app.methodology_approved_edit} GUC and emits the BE-5 umbrella audit.
 */
@Service
public class FactorLevelService {

    private final MethodologyRepository methodologies;
    private final MethodologyVersionRepository versions;
    private final FactorRepository factors;
    private final FactorLevelRepository levels;
    private final AbacGate abacGate;
    private final MethodologyVersionImmutabilityPolicy immutabilityPolicy;
    private final AuditService audit;
    private final MethodologyAuditSnapshot snapshot;
    private final ApprovedEditGuc approvedEditGuc;
    private final ApprovedEditAudit approvedEditAudit;
    private final MethodologyReferencePort referencePort;

    public FactorLevelService(MethodologyRepository methodologies,
                              MethodologyVersionRepository versions,
                              FactorRepository factors,
                              FactorLevelRepository levels,
                              AbacGate abacGate,
                              MethodologyVersionImmutabilityPolicy immutabilityPolicy,
                              AuditService audit,
                              MethodologyAuditSnapshot snapshot,
                              ApprovedEditGuc approvedEditGuc,
                              ApprovedEditAudit approvedEditAudit,
                              MethodologyReferencePort referencePort) {
        this.methodologies = methodologies;
        this.versions = versions;
        this.factors = factors;
        this.levels = levels;
        this.abacGate = abacGate;
        this.immutabilityPolicy = immutabilityPolicy;
        this.audit = audit;
        this.snapshot = snapshot;
        this.approvedEditGuc = approvedEditGuc;
        this.approvedEditAudit = approvedEditAudit;
        this.referencePort = referencePort;
    }

    @Transactional
    public FactorLevel add(UUID factorId, FactorLevelCommand cmd) {
        TenantContext ctx = requireEditPerm();
        Ctx vctx = loadAndGate(factorId, ctx);

        if (levels.existsByTenantIdAndFactorIdAndCode(ctx.tenantId(), factorId, cmd.code())) {
            throw new ValidationException("LEVEL_CODE_DUPLICATE",
                    "Level code already exists for this factor");
        }
        // Server-authoritative ordering. The client-supplied cmd.levelOrder() is
        // intentionally IGNORED on ADD (advisory only): the frontend sent a
        // 0-based count of loaded levels, which collided with the 1-indexed
        // orders of template-created levels and tripped
        // uq_factor_levels_factor_level_order (23505). Always append last as
        // max(existing)+1 (starting at 1 for an empty factor) so the new level
        // is unique against the constraint regardless of any existing
        // distribution or client value.
        int order = nextLevelOrder(ctx.tenantId(), factorId);
        UUID id = UUID.randomUUID();
        FactorLevelJpaEntity l = new FactorLevelJpaEntity(
                id, ctx.tenantId(), factorId, cmd.code(),
                order, cmd.points(), cmd.scaleValue());
        l.setLabelI18n(cmd.labelI18n());
        l.setDescriptionI18n(cmd.descriptionI18n());
        levels.save(l);

        var afterJson = snapshot.of(l);
        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(vctx.methodology.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.FACTOR_LEVEL_CREATED)
                .entityType("FactorLevel")
                .entityId(id)
                .afterJson(afterJson)
                .build());
        if (vctx.approvedEdit) {
            approvedEditAudit.emit(ctx.tenantId(), vctx.methodology.getProjectId(),
                    ctx.userId(), vctx.version.getId(), afterJson, "factor level added");
        }
        return l.toDomain();
    }

    @Transactional
    public FactorLevel update(UUID levelId, FactorLevelCommand cmd) {
        TenantContext ctx = requireEditPerm();
        FactorLevelJpaEntity l = levels.findByIdAndTenantId(levelId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        Ctx vctx = loadAndGate(l.getFactorId(), ctx);

        var beforeJson = snapshot.of(l);
        if (cmd.code() != null && !cmd.code().equals(l.getCode())) {
            if (levels.existsByTenantIdAndFactorIdAndCode(ctx.tenantId(), l.getFactorId(), cmd.code())) {
                throw new ValidationException("LEVEL_CODE_DUPLICATE",
                        "Level code already exists for this factor");
            }
            l.setCode(cmd.code());
        }
        if (cmd.levelOrder() != null) l.setLevelOrder(cmd.levelOrder());
        if (cmd.points() != null) l.setPoints(cmd.points());
        if (cmd.scaleValue() != null) l.setScaleValue(cmd.scaleValue());
        if (cmd.labelI18n() != null) l.setLabelI18n(cmd.labelI18n());
        if (cmd.descriptionI18n() != null) l.setDescriptionI18n(cmd.descriptionI18n());
        levels.save(l);

        var afterJson = snapshot.of(l);
        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(vctx.methodology.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.FACTOR_LEVEL_UPDATED)
                .entityType("FactorLevel")
                .entityId(levelId)
                .beforeJson(beforeJson)
                .afterJson(afterJson)
                .build());
        if (vctx.approvedEdit) {
            approvedEditAudit.emit(ctx.tenantId(), vctx.methodology.getProjectId(),
                    ctx.userId(), vctx.version.getId(), afterJson, "factor level scoring fields edited");
        }
        return l.toDomain();
    }

    /**
     * Remove a factor level. BE-4: a level referenced by any
     * {@code evaluation_scores} that is being edited under the APPROVED carve-out
     * is SOFT-deprecated (preserved for historical evaluations) rather than
     * hard-deleted, emitting {@code FACTOR_LEVEL_DEPRECATED}. Unreferenced levels
     * are hard-deleted; a raw FK violation (23503) maps to
     * {@code LEVEL_REFERENCED_BY_EVALUATIONS}.
     */
    @Transactional
    public void remove(UUID levelId) {
        TenantContext ctx = requireEditPerm();
        FactorLevelJpaEntity l = levels.findByIdAndTenantId(levelId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        Ctx vctx = loadAndGate(l.getFactorId(), ctx);

        var beforeJson = snapshot.of(l);
        boolean referenced = referencePort.isFactorLevelReferenced(ctx.tenantId(), levelId);

        if (referenced && vctx.approvedEdit) {
            l.setDeprecatedAt(OffsetDateTime.now());
            l.setDeprecatedBy(ctx.userId());
            levels.save(l);
            var afterJson = snapshot.of(l);
            audit.record(AuditEvent.builder()
                    .tenantId(ctx.tenantId())
                    .projectId(vctx.methodology.getProjectId())
                    .actorUserId(ctx.userId())
                    .action(AuditAction.FACTOR_LEVEL_DEPRECATED)
                    .entityType("FactorLevel")
                    .entityId(levelId)
                    .beforeJson(beforeJson)
                    .afterJson(afterJson)
                    .build());
            approvedEditAudit.emit(ctx.tenantId(), vctx.methodology.getProjectId(),
                    ctx.userId(), vctx.version.getId(), afterJson, "factor level deprecated");
            return;
        }

        try {
            levels.delete(l);
            levels.flush();
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new ValidationException("LEVEL_REFERENCED_BY_EVALUATIONS",
                    "Factor level is referenced by existing evaluations and cannot be deleted");
        }
        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(vctx.methodology.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.FACTOR_LEVEL_REMOVED)
                .entityType("FactorLevel")
                .entityId(levelId)
                .beforeJson(beforeJson)
                .build());
        if (vctx.approvedEdit) {
            approvedEditAudit.emit(ctx.tenantId(), vctx.methodology.getProjectId(),
                    ctx.userId(), vctx.version.getId(), beforeJson, "factor level removed");
        }
    }

    @Transactional
    public void reorder(UUID factorId, List<UUID> orderedIds) {
        TenantContext ctx = requireEditPerm();
        Ctx vctx = loadAndGate(factorId, ctx);

        List<FactorLevelJpaEntity> existing = levels
                .findAllByTenantIdAndFactorIdOrderByLevelOrderAsc(ctx.tenantId(), factorId);
        if (orderedIds == null || orderedIds.size() != existing.size()) {
            throw new ValidationException("LEVEL_REORDER_MISMATCH",
                    "Reorder payload must include every level exactly once");
        }
        Set<UUID> existingIds = new HashSet<>();
        existing.forEach(e -> existingIds.add(e.getId()));
        if (!existingIds.equals(new HashSet<>(orderedIds))) {
            throw new ValidationException("LEVEL_REORDER_MISMATCH",
                    "Reorder payload does not match existing level ids");
        }
        Map<UUID, FactorLevelJpaEntity> byId = new HashMap<>();
        existing.forEach(e -> byId.put(e.getId(), e));

        int offset = 10_000;
        for (int i = 0; i < orderedIds.size(); i++) {
            FactorLevelJpaEntity l = byId.get(orderedIds.get(i));
            l.setLevelOrder(offset + i);
            levels.save(l);
        }
        for (int i = 0; i < orderedIds.size(); i++) {
            FactorLevelJpaEntity l = byId.get(orderedIds.get(i));
            l.setLevelOrder(i + 1);
            levels.save(l);
        }
        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(vctx.methodology.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.FACTOR_LEVEL_REORDERED)
                .entityType("Factor")
                .entityId(factorId)
                .reason("count=" + orderedIds.size())
                .build());
        if (vctx.approvedEdit) {
            approvedEditAudit.emit(ctx.tenantId(), vctx.methodology.getProjectId(),
                    ctx.userId(), vctx.version.getId(), null, "factor levels reordered");
        }
    }

    /**
     * Next collision-free {@code level_order} for a factor: {@code max+1},
     * starting at 1 for an empty factor (matching the existing 1-indexed
     * convention used by template-created levels). Using {@code max} rather than
     * {@code count} guarantees uniqueness even when existing orders have gaps.
     */
    private int nextLevelOrder(UUID tenantId, UUID factorId) {
        Integer max = levels.findMaxLevelOrderByFactorId(tenantId, factorId);
        return max == null ? 1 : max + 1;
    }

    /** Coarse gate (BE-2): METHODOLOGY_EDIT OR METHODOLOGY_EDIT_APPROVED. */
    private TenantContext requireEditPerm() {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.METHODOLOGY_EDIT)
                && !ctx.hasPermission(PermissionCodes.METHODOLOGY_EDIT_APPROVED)) {
            throw new PermissionDeniedException();
        }
        return ctx;
    }

    private Ctx loadAndGate(UUID factorId, TenantContext ctx) {
        FactorJpaEntity f = factors.findByIdAndTenantId(factorId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        MethodologyVersionJpaEntity v = versions
                .findByIdAndTenantId(f.getMethodologyVersionId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        MethodologyJpaEntity m = methodologies
                .findByIdAndTenantId(v.getMethodologyId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (m.getProjectId() != null) {
            abacGate.enforceCanWriteInProject(ctx, m.getProjectId());
        }
        boolean approvedEdit = immutabilityPolicy.ensureMutableOrApprovedEdit(
                v.getStatus(), ctx.hasPermission(PermissionCodes.METHODOLOGY_EDIT_APPROVED));
        if (approvedEdit) {
            approvedEditGuc.enableForCurrentTransaction();
        }
        return new Ctx(m, v, f, approvedEdit);
    }

    private record Ctx(MethodologyJpaEntity methodology,
                       MethodologyVersionJpaEntity version,
                       FactorJpaEntity factor,
                       boolean approvedEdit) { }
}

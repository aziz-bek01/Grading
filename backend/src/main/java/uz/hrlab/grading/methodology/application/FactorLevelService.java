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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** FactorLevel write operations (add / update / remove / reorder). */
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

    public FactorLevelService(MethodologyRepository methodologies,
                              MethodologyVersionRepository versions,
                              FactorRepository factors,
                              FactorLevelRepository levels,
                              AbacGate abacGate,
                              MethodologyVersionImmutabilityPolicy immutabilityPolicy,
                              AuditService audit,
                              MethodologyAuditSnapshot snapshot) {
        this.methodologies = methodologies;
        this.versions = versions;
        this.factors = factors;
        this.levels = levels;
        this.abacGate = abacGate;
        this.immutabilityPolicy = immutabilityPolicy;
        this.audit = audit;
        this.snapshot = snapshot;
    }

    @Transactional
    public FactorLevel add(UUID factorId, FactorLevelCommand cmd) {
        TenantContext ctx = requireEditPerm();
        Ctx vctx = loadAndGate(factorId, ctx);
        immutabilityPolicy.ensureMutable(vctx.version.getStatus());

        if (levels.existsByTenantIdAndFactorIdAndCode(ctx.tenantId(), factorId, cmd.code())) {
            throw new ValidationException("LEVEL_CODE_DUPLICATE",
                    "Level code already exists for this factor");
        }
        int order = cmd.levelOrder() != null ? cmd.levelOrder()
                : nextLevelOrder(ctx.tenantId(), factorId);
        UUID id = UUID.randomUUID();
        FactorLevelJpaEntity l = new FactorLevelJpaEntity(
                id, ctx.tenantId(), factorId, cmd.code(),
                order, cmd.points(), cmd.scaleValue());
        l.setLabelI18n(cmd.labelI18n());
        l.setDescriptionI18n(cmd.descriptionI18n());
        levels.save(l);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(vctx.methodology.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.FACTOR_LEVEL_CREATED)
                .entityType("FactorLevel")
                .entityId(id)
                .afterJson(snapshot.of(l))
                .build());
        return l.toDomain();
    }

    @Transactional
    public FactorLevel update(UUID levelId, FactorLevelCommand cmd) {
        TenantContext ctx = requireEditPerm();
        FactorLevelJpaEntity l = levels.findByIdAndTenantId(levelId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        Ctx vctx = loadAndGate(l.getFactorId(), ctx);
        immutabilityPolicy.ensureMutable(vctx.version.getStatus());

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

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(vctx.methodology.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.FACTOR_LEVEL_UPDATED)
                .entityType("FactorLevel")
                .entityId(levelId)
                .beforeJson(beforeJson)
                .afterJson(snapshot.of(l))
                .build());
        return l.toDomain();
    }

    @Transactional
    public void remove(UUID levelId) {
        TenantContext ctx = requireEditPerm();
        FactorLevelJpaEntity l = levels.findByIdAndTenantId(levelId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        Ctx vctx = loadAndGate(l.getFactorId(), ctx);
        immutabilityPolicy.ensureMutable(vctx.version.getStatus());

        var beforeJson = snapshot.of(l);
        levels.delete(l);
        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(vctx.methodology.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.FACTOR_LEVEL_REMOVED)
                .entityType("FactorLevel")
                .entityId(levelId)
                .beforeJson(beforeJson)
                .build());
    }

    @Transactional
    public void reorder(UUID factorId, List<UUID> orderedIds) {
        TenantContext ctx = requireEditPerm();
        Ctx vctx = loadAndGate(factorId, ctx);
        immutabilityPolicy.ensureMutable(vctx.version.getStatus());

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
    }

    private int nextLevelOrder(UUID tenantId, UUID factorId) {
        return levels.findAllByTenantIdAndFactorIdOrderByLevelOrderAsc(tenantId, factorId)
                .size() + 1;
    }

    private TenantContext requireEditPerm() {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.METHODOLOGY_EDIT)) {
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
        return new Ctx(m, v, f);
    }

    private record Ctx(MethodologyJpaEntity methodology,
                       MethodologyVersionJpaEntity version,
                       FactorJpaEntity factor) { }
}

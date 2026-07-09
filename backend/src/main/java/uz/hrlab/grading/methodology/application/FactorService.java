package uz.hrlab.grading.methodology.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
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

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Factor write operations (add / update / remove / reorder). DRAFT versions are
 * freely mutable ({@code METHODOLOGY_EDIT}); APPROVED versions are mutable ONLY
 * for {@code METHODOLOGY_EDIT_APPROVED} holders (the super-admin approved-edit
 * carve-out, BE-2) — in which case the transaction GUC
 * {@code app.methodology_approved_edit} is set so the DB triggers
 * {@code prevent_locked_factor_changes} permit the write. LOCKED / ARCHIVED stay
 * immutable. The single status × permission decision lives in
 * {@link MethodologyVersionImmutabilityPolicy#ensureMutableOrApprovedEdit}.
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
    private final ApprovedEditGuc approvedEditGuc;
    private final ApprovedEditAudit approvedEditAudit;
    private final MethodologyReferencePort referencePort;

    public FactorService(MethodologyRepository methodologies,
                         MethodologyVersionRepository versions,
                         FactorRepository factors,
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
        this.abacGate = abacGate;
        this.immutabilityPolicy = immutabilityPolicy;
        this.audit = audit;
        this.snapshot = snapshot;
        this.approvedEditGuc = approvedEditGuc;
        this.approvedEditAudit = approvedEditAudit;
        this.referencePort = referencePort;
    }

    @Transactional
    public Factor add(UUID methodologyVersionId, FactorCommand cmd) {
        TenantContext ctx = requireEditPerm();
        VersionContext vctx = loadAndGate(methodologyVersionId, ctx);

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

        var afterJson = snapshot.of(f);
        audit.record(AuditEvent.builder(ctx)
                .projectId(vctx.methodology.getProjectId())
                .action(AuditAction.FACTOR_CREATED)
                .entityType("Factor")
                .entityId(id)
                .afterJson(afterJson)
                .build());
        if (vctx.approvedEdit) {
            approvedEditAudit.emit(ctx.tenantId(), vctx.methodology.getProjectId(),
                    ctx.userId(), methodologyVersionId, afterJson, "factor added");
        }
        return f.toDomain();
    }

    @Transactional
    public Factor update(UUID factorId, FactorCommand cmd) {
        TenantContext ctx = requireEditPerm();
        FactorJpaEntity f = factors.findByIdAndTenantId(factorId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        VersionContext vctx = loadAndGate(f.getMethodologyVersionId(), ctx);

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

        var afterJson = snapshot.of(f);
        audit.record(AuditEvent.builder(ctx)
                .projectId(vctx.methodology.getProjectId())
                .action(AuditAction.FACTOR_UPDATED)
                .entityType("Factor")
                .entityId(factorId)
                .beforeJson(beforeJson)
                .afterJson(afterJson)
                .build());
        if (vctx.approvedEdit) {
            approvedEditAudit.emit(ctx.tenantId(), vctx.methodology.getProjectId(),
                    ctx.userId(), f.getMethodologyVersionId(), afterJson, "factor scoring fields edited");
        }
        return f.toDomain();
    }

    /**
     * Remove a factor. BE-4: if the factor is referenced by any
     * {@code evaluation_scores} / {@code evaluation_calibration_events} AND the
     * version is being edited under the APPROVED carve-out, it is SOFT-deprecated
     * (preserved for historical evaluations + reporting) rather than hard-deleted;
     * a {@code FACTOR_DEPRECATED} audit is emitted. An unreferenced factor (on
     * DRAFT or APPROVED) is hard-deleted as before. A raw FK violation (23503) is
     * surfaced as a clean {@code FACTOR_REFERENCED_BY_EVALUATIONS} domain error.
     */
    @Transactional
    public void remove(UUID factorId) {
        TenantContext ctx = requireEditPerm();
        FactorJpaEntity f = factors.findByIdAndTenantId(factorId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        VersionContext vctx = loadAndGate(f.getMethodologyVersionId(), ctx);

        var beforeJson = snapshot.of(f);
        boolean referenced = referencePort.isFactorReferenced(ctx.tenantId(), factorId);

        if (referenced && vctx.approvedEdit) {
            // Soft-deprecate: keep the row for historical evaluations + reporting.
            f.setDeprecatedAt(OffsetDateTime.now());
            f.setDeprecatedBy(ctx.userId());
            factors.save(f);
            var afterJson = snapshot.of(f);
            audit.record(AuditEvent.builder(ctx)
                    .projectId(vctx.methodology.getProjectId())
                    .action(AuditAction.FACTOR_DEPRECATED)
                    .entityType("Factor")
                    .entityId(factorId)
                    .beforeJson(beforeJson)
                    .afterJson(afterJson)
                    .build());
            approvedEditAudit.emit(ctx.tenantId(), vctx.methodology.getProjectId(),
                    ctx.userId(), f.getMethodologyVersionId(), afterJson, "factor deprecated");
            return;
        }

        try {
            factors.delete(f);
            factors.flush();
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new ValidationException("FACTOR_REFERENCED_BY_EVALUATIONS",
                    "Factor is referenced by existing evaluations and cannot be deleted");
        }
        audit.record(AuditEvent.builder(ctx)
                .projectId(vctx.methodology.getProjectId())
                .action(AuditAction.FACTOR_REMOVED)
                .entityType("Factor")
                .entityId(factorId)
                .beforeJson(beforeJson)
                .build());
        if (vctx.approvedEdit) {
            approvedEditAudit.emit(ctx.tenantId(), vctx.methodology.getProjectId(),
                    ctx.userId(), f.getMethodologyVersionId(), beforeJson, "factor removed");
        }
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

        audit.record(AuditEvent.builder(ctx)
                .projectId(vctx.methodology.getProjectId())
                .action(AuditAction.FACTOR_REORDERED)
                .entityType("MethodologyVersion")
                .entityId(methodologyVersionId)
                .reason("count=" + orderedIds.size())
                .build());
        if (vctx.approvedEdit) {
            approvedEditAudit.emit(ctx.tenantId(), vctx.methodology.getProjectId(),
                    ctx.userId(), methodologyVersionId, null, "factors reordered");
        }
    }

    private int nextSortOrder(UUID tenantId, UUID methodologyVersionId) {
        return factors
                .findAllByTenantIdAndMethodologyVersionIdOrderBySortOrderAsc(
                        tenantId, methodologyVersionId)
                .size() + 1;
    }

    /**
     * Coarse gate (BE-2): accept {@code METHODOLOGY_EDIT} OR
     * {@code METHODOLOGY_EDIT_APPROVED}. The fine status × permission decision is
     * made centrally in
     * {@link MethodologyVersionImmutabilityPolicy#ensureMutableOrApprovedEdit}.
     */
    private TenantContext requireEditPerm() {
        return TenantContextHolder.requireActive().requireAny(
                PermissionCodes.METHODOLOGY_EDIT, PermissionCodes.METHODOLOGY_EDIT_APPROVED);
    }

    /**
     * Tenant-scoped load + ABAC + immutability gate. Applies the approved-edit
     * carve-out: on an APPROVED version the caller MUST hold
     * {@code METHODOLOGY_EDIT_APPROVED}, and the transaction GUC is set so the DB
     * trigger permits the write. The returned {@code approvedEdit} flag drives
     * the BE-5 umbrella audit.
     */
    private VersionContext loadAndGate(UUID versionId, TenantContext ctx) {
        MethodologyVersionJpaEntity v = versions.findByIdAndTenantId(versionId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        MethodologyJpaEntity m = methodologies.findByIdAndTenantId(v.getMethodologyId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (m.getProjectId() != null) {
            abacGate.enforceCanWriteInProject(ctx, m.getProjectId());
        }
        boolean approvedEdit = immutabilityPolicy.ensureMutableOrApprovedEdit(
                v.getStatus(), ctx.hasPermission(PermissionCodes.METHODOLOGY_EDIT_APPROVED));
        if (approvedEdit) {
            // Transaction-local GUC ONLY on the APPROVED branch; never on DRAFT.
            approvedEditGuc.enableForCurrentTransaction();
        }
        return new VersionContext(m, v, approvedEdit);
    }

    private record VersionContext(MethodologyJpaEntity methodology,
                                  MethodologyVersionJpaEntity version,
                                  boolean approvedEdit) { }
}

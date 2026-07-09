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
import uz.hrlab.grading.methodology.domain.Methodology;
import uz.hrlab.grading.methodology.domain.MethodologyType;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.Map;
import java.util.UUID;

/**
 * Update methodology container metadata (name + description + methodology_type).
 * Name/description work regardless of version status — the Methodology row is
 * just the container. {@code methodology_type} is a labelling / template-origin
 * attribute with NO scoring side-effect, but is only editable while the
 * methodology has no APPROVED/LOCKED version (otherwise the declared type of an
 * already-approved structure would silently change). Factor/level/version edits
 * go through different use cases.
 */
@Service
public class UpdateMethodologyMetadataUseCase {

    private final MethodologyRepository methodologies;
    private final MethodologyVersionRepository versions;
    private final AbacGate abacGate;
    private final AuditService audit;
    private final MethodologyAuditSnapshot snapshot;

    public UpdateMethodologyMetadataUseCase(MethodologyRepository methodologies,
                                            MethodologyVersionRepository versions,
                                            AbacGate abacGate,
                                            AuditService audit,
                                            MethodologyAuditSnapshot snapshot) {
        this.methodologies = methodologies;
        this.versions = versions;
        this.abacGate = abacGate;
        this.audit = audit;
        this.snapshot = snapshot;
    }

    /** Back-compat overload — name + description only (no type change). */
    @Transactional
    public Methodology update(UUID id, Map<String, String> nameI18n,
                              Map<String, String> descriptionI18n) {
        return update(id, nameI18n, descriptionI18n, null);
    }

    /**
     * @param methodologyType optional new type; {@code null} leaves it unchanged.
     * @throws ValidationException {@code METHODOLOGY_TYPE_LOCKED} when a type
     *         change is requested but the methodology already has an
     *         APPROVED or LOCKED version.
     */
    @Transactional
    public Methodology update(UUID id, Map<String, String> nameI18n,
                              Map<String, String> descriptionI18n,
                              MethodologyType methodologyType) {
        TenantContext ctx = TenantContextHolder.requireActive();
        // F-402: defense-in-depth RBAC re-check (matches Approve/Lock/Create pattern).
        ctx.require(PermissionCodes.METHODOLOGY_EDIT);
        MethodologyJpaEntity m = methodologies.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (m.getProjectId() != null) {
            abacGate.enforceCanWriteInProject(ctx, m.getProjectId());
        }
        if (methodologyType != null && methodologyType != m.getMethodologyType()) {
            ensureNoApprovedOrLockedVersion(ctx.tenantId(), id);
        }
        var beforeJson = snapshot.of(m);
        if (nameI18n != null) m.setNameI18n(nameI18n);
        if (descriptionI18n != null) m.setDescriptionI18n(descriptionI18n);
        if (methodologyType != null) m.setMethodologyType(methodologyType);
        methodologies.save(m);

        audit.record(AuditEvent.builder(ctx)
                .projectId(m.getProjectId())
                .action(AuditAction.METHODOLOGY_UPDATED)
                .entityType("Methodology")
                .entityId(id)
                .beforeJson(beforeJson)
                .afterJson(snapshot.of(m))
                .build());
        return m.toDomain();
    }

    /**
     * methodology_type may only change while the structure is still being built
     * (all versions DRAFT or ARCHIVED). If ANY version reached APPROVED/LOCKED
     * the declared type is part of an approved structure — changing it would be
     * a silent retro-edit, so it is blocked; the user must create a new version.
     */
    private void ensureNoApprovedOrLockedVersion(UUID tenantId, UUID methodologyId) {
        boolean hasApprovedOrLocked = versions
                .findAllByTenantIdAndMethodologyIdOrderByVersionNumberDesc(tenantId, methodologyId)
                .stream()
                .map(MethodologyVersionJpaEntity::getStatus)
                .anyMatch(s -> s == MethodologyVersionStatus.APPROVED
                        || s == MethodologyVersionStatus.LOCKED);
        if (hasApprovedOrLocked) {
            throw new ValidationException("METHODOLOGY_TYPE_LOCKED",
                    "methodology_type cannot change once a version is approved or locked — "
                            + "create a new version instead");
        }
    }
}

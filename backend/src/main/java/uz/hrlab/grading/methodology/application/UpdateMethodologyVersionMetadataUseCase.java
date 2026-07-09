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
import uz.hrlab.grading.methodology.domain.MethodologyVersion;
import uz.hrlab.grading.methodology.domain.MethodologyVersionImmutabilityPolicy;
import uz.hrlab.grading.methodology.domain.ScoringMode;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Edit version-scoped methodology metadata ({@code scoring_mode} +
 * {@code target_total_points}) on a DRAFT version. Mirrors the
 * {@link FactorService} write pattern exactly: RBAC re-check → tenant load →
 * project ABAC → {@link MethodologyVersionImmutabilityPolicy#ensureMutable}
 * DRAFT-only guard → mutate → audit.
 *
 * <p>DRAFT versions are fully editable while the consultant is still building;
 * APPROVED/LOCKED/ARCHIVED stay immutable (the immutability invariant). Both
 * columns already exist on {@code methodology_versions} — no migration needed.
 * Switching scoring mode PRESERVES all factors/levels; only the scoring-time
 * interpretation of existing weights/points/scale_value changes.
 */
@Service
public class UpdateMethodologyVersionMetadataUseCase {

    private final MethodologyRepository methodologies;
    private final MethodologyVersionRepository versions;
    private final AbacGate abacGate;
    private final MethodologyVersionImmutabilityPolicy immutabilityPolicy;
    private final AuditService audit;
    private final MethodologyAuditSnapshot snapshot;

    public UpdateMethodologyVersionMetadataUseCase(MethodologyRepository methodologies,
                                                   MethodologyVersionRepository versions,
                                                   AbacGate abacGate,
                                                   MethodologyVersionImmutabilityPolicy immutabilityPolicy,
                                                   AuditService audit,
                                                   MethodologyAuditSnapshot snapshot) {
        this.methodologies = methodologies;
        this.versions = versions;
        this.abacGate = abacGate;
        this.immutabilityPolicy = immutabilityPolicy;
        this.audit = audit;
        this.snapshot = snapshot;
    }

    /**
     * Partial update — a {@code null} argument leaves that field unchanged.
     *
     * @throws ValidationException {@code SCORING_TARGET_REQUIRED} when the
     *         effective scoring mode after the edit is {@code WEIGHTED_SCALE}
     *         but the effective target is not strictly positive.
     */
    @Transactional
    public MethodologyVersion update(UUID versionId, ScoringMode scoringMode,
                                     BigDecimal targetTotalPoints) {
        TenantContext ctx = requireEditPerm();
        MethodologyVersionJpaEntity v = versions.findByIdAndTenantId(versionId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        MethodologyJpaEntity m = methodologies.findByIdAndTenantId(v.getMethodologyId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (m.getProjectId() != null) {
            abacGate.enforceCanWriteInProject(ctx, m.getProjectId());
        }
        immutabilityPolicy.ensureMutable(v.getStatus());

        ScoringMode effectiveMode = scoringMode != null ? scoringMode : v.getScoringMode();
        BigDecimal effectiveTarget = targetTotalPoints != null
                ? targetTotalPoints : v.getTargetTotalPoints();
        // WEIGHTED_SCALE is the only mode that requires a positive target at
        // scoring time; reject an invalid combination at edit time so a DRAFT
        // can never reach approval in a non-scorable state.
        if (effectiveMode == ScoringMode.WEIGHTED_SCALE
                && (effectiveTarget == null || effectiveTarget.signum() <= 0)) {
            throw new ValidationException("SCORING_TARGET_REQUIRED",
                    "target_total_points must be greater than 0 for WEIGHTED_SCALE");
        }

        var beforeJson = snapshot.of(v);
        if (scoringMode != null) v.setScoringMode(scoringMode);
        if (targetTotalPoints != null) v.setTargetTotalPoints(targetTotalPoints);
        versions.save(v);

        audit.record(AuditEvent.builder(ctx)
                .projectId(m.getProjectId())
                .action(AuditAction.METHODOLOGY_VERSION_UPDATED)
                .entityType("MethodologyVersion")
                .entityId(versionId)
                .beforeJson(beforeJson)
                .afterJson(snapshot.of(v))
                .build());
        return v.toDomain();
    }

    private TenantContext requireEditPerm() {
        return TenantContextHolder.requireActive().require(PermissionCodes.METHODOLOGY_EDIT);
    }
}

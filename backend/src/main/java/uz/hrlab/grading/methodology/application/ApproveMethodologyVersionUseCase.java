package uz.hrlab.grading.methodology.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.approval.application.CreateApprovalRequestCommand;
import uz.hrlab.grading.approval.application.CreateApprovalRequestUseCase;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.methodology.domain.MethodologyVersion;
import uz.hrlab.grading.methodology.domain.MethodologyVersionPrimaryLocaleValidator;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatusTransitionPolicy;
import uz.hrlab.grading.methodology.domain.MethodologyVersionTransition;
import uz.hrlab.grading.methodology.domain.MethodologyVersionTransitionRejectedException;
import uz.hrlab.grading.methodology.domain.MethodologyWeightValidationPolicy;
import uz.hrlab.grading.methodology.infrastructure.FactorJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DRAFT → APPROVED. Validates:
 * <ol>
 *   <li>state transition allowed</li>
 *   <li>at least one factor present</li>
 *   <li>each factor has the primary locale (ru-RU) name</li>
 *   <li>each factor has at least 2 levels</li>
 *   <li>weight sum matches the scoring mode's invariant</li>
 * </ol>
 * Re-checks {@code METHODOLOGY_APPROVE} server-side independent of the
 * controller {@code @PreAuthorize}.
 */
@Service
public class ApproveMethodologyVersionUseCase {

    private final MethodologyRepository methodologies;
    private final MethodologyVersionRepository versions;
    private final FactorRepository factors;
    private final FactorLevelRepository levels;
    private final AbacGate abacGate;
    private final MethodologyVersionStatusTransitionPolicy transitionPolicy;
    private final MethodologyWeightValidationPolicy weightPolicy;
    private final MethodologyVersionPrimaryLocaleValidator localeValidator;
    private final AuditService audit;
    private final MethodologyAuditSnapshot snapshot;
    private final CreateApprovalRequestUseCase createApprovalRequest;

    public ApproveMethodologyVersionUseCase(MethodologyRepository methodologies,
                                            MethodologyVersionRepository versions,
                                            FactorRepository factors,
                                            FactorLevelRepository levels,
                                            AbacGate abacGate,
                                            MethodologyVersionStatusTransitionPolicy transitionPolicy,
                                            MethodologyWeightValidationPolicy weightPolicy,
                                            MethodologyVersionPrimaryLocaleValidator localeValidator,
                                            AuditService audit,
                                            MethodologyAuditSnapshot snapshot,
                                            CreateApprovalRequestUseCase createApprovalRequest) {
        this.methodologies = methodologies;
        this.versions = versions;
        this.factors = factors;
        this.levels = levels;
        this.abacGate = abacGate;
        this.transitionPolicy = transitionPolicy;
        this.weightPolicy = weightPolicy;
        this.localeValidator = localeValidator;
        this.audit = audit;
        this.snapshot = snapshot;
        this.createApprovalRequest = createApprovalRequest;
    }

    @Transactional
    public MethodologyVersion approve(UUID versionId) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.METHODOLOGY_APPROVE);
        MethodologyVersionJpaEntity v = versions.findByIdAndTenantId(versionId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        MethodologyJpaEntity m = methodologies.findByIdAndTenantId(v.getMethodologyId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (m.getProjectId() != null) {
            abacGate.enforceCanWriteInProject(ctx, m.getProjectId());
        }
        transitionPolicy.check(v.getStatus(), MethodologyVersionTransition.APPROVE);

        List<FactorJpaEntity> factorRows = factors
                .findAllByTenantIdAndMethodologyVersionIdOrderBySortOrderAsc(
                        ctx.tenantId(), versionId);
        if (factorRows.isEmpty()) {
            throw new MethodologyVersionTransitionRejectedException(
                    "Methodology version must have at least one factor before approval");
        }
        // ≥ 2 levels per factor
        for (FactorJpaEntity f : factorRows) {
            int count = levels.findAllByTenantIdAndFactorIdOrderByLevelOrderAsc(
                    ctx.tenantId(), f.getId()).size();
            if (count < 2) {
                throw new MethodologyVersionTransitionRejectedException(
                        "Factor '" + f.getCode() + "' must have at least 2 levels");
            }
        }
        localeValidator.validate(factorRows);
        weightPolicy.validate(v.getScoringMode(), v.getTargetTotalPoints(), factorRows);

        var beforeJson = snapshot.of(v);
        OffsetDateTime now = OffsetDateTime.now();
        v.setStatus(MethodologyVersionStatus.APPROVED);
        v.setApprovedAt(now);
        v.setApprovedBy(ctx.userId());
        versions.save(v);

        audit.record(AuditEvent.builder(ctx)
                .projectId(m.getProjectId())
                .action(AuditAction.METHODOLOGY_VERSION_APPROVED)
                .entityType("MethodologyVersion")
                .entityId(versionId)
                .beforeJson(beforeJson)
                .afterJson(snapshot.of(v))
                .build());

        // PO-9: auto-record ApprovalRequest (single-step, already APPROVED) so
        // the §23 "all approvals logged" acceptance criterion is satisfied for
        // Methodology (which has a direct DRAFT→APPROVED state machine with no
        // intermediate UNDER_REVIEW). Skipped for tenant-level methodologies
        // (projectId == null).
        if (m.getProjectId() != null) {
            createApprovalRequest.createSystemAndAutoApproveFirstStep(
                    CreateApprovalRequestCommand.singleStep(
                            m.getProjectId(),
                            ApprovalEntityType.METHODOLOGY_VERSION,
                            versionId,
                            PermissionCodes.METHODOLOGY_APPROVE),
                    null);
        }
        return v.toDomain();
    }
}

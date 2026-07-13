package uz.hrlab.grading.methodology.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.approval.application.CreateApprovalRequestCommand;
import uz.hrlab.grading.approval.application.CreateApprovalRequestUseCase;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.application.StatusTransitionExecutor;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.methodology.domain.MethodologyVersion;
import uz.hrlab.grading.methodology.domain.MethodologyVersionPrimaryLocaleValidator;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatusTransitionPolicy;
import uz.hrlab.grading.methodology.domain.MethodologyVersionTransition;
import uz.hrlab.grading.methodology.domain.MethodologyVersionTransitionRejectedException;
import uz.hrlab.grading.methodology.domain.MethodologyWeightValidationPolicy;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final MethodologyVersionStatusTransitionPolicy transitionPolicy;
    private final MethodologyWeightValidationPolicy weightPolicy;
    private final MethodologyVersionPrimaryLocaleValidator localeValidator;
    private final MethodologyAuditSnapshot snapshot;
    private final CreateApprovalRequestUseCase createApprovalRequest;
    private final StatusTransitionExecutor transitions;

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
        this.transitionPolicy = transitionPolicy;
        this.weightPolicy = weightPolicy;
        this.localeValidator = localeValidator;
        this.snapshot = snapshot;
        this.createApprovalRequest = createApprovalRequest;
        this.transitions = new StatusTransitionExecutor(abacGate, audit);
    }

    @Transactional
    public MethodologyVersion approve(UUID versionId) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.METHODOLOGY_APPROVE);
        MethodologyVersionJpaEntity v = versions.findByIdAndTenantId(versionId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        MethodologyJpaEntity m = methodologies.findByIdAndTenantId(v.getMethodologyId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        OffsetDateTime now = OffsetDateTime.now();
        transitions.transition(ctx)
                .abacProjectWrite(m.getProjectId())
                .checkTransition(() -> transitionPolicy.check(v.getStatus(), MethodologyVersionTransition.APPROVE))
                .beforeMutate(() -> validateFactorsAndWeights(ctx, versionId, v))
                .snapshot(() -> snapshot.of(v))
                .mutate(() -> {
                    v.setStatus(MethodologyVersionStatus.APPROVED);
                    v.setApprovedAt(now);
                    v.setApprovedBy(ctx.userId());
                })
                .save(() -> versions.save(v))
                .audit(AuditAction.METHODOLOGY_VERSION_APPROVED, "MethodologyVersion",
                        versionId, m.getProjectId())
                // PO-9: auto-record ApprovalRequest (single-step, already APPROVED) so
                // the §23 "all approvals logged" acceptance criterion is satisfied for
                // Methodology (which has a direct DRAFT→APPROVED state machine with no
                // intermediate UNDER_REVIEW). Skipped for tenant-level methodologies
                // (projectId == null).
                .afterSave(() -> autoRecordApprovalRequest(versionId, m))
                .execute();
        return v.toDomain();
    }

    private void validateFactorsAndWeights(TenantContext ctx, UUID versionId,
                                           MethodologyVersionJpaEntity v) {
        List<FactorJpaEntity> factorRows = factors
                .findAllByTenantIdAndMethodologyVersionIdOrderBySortOrderAsc(
                        ctx.tenantId(), versionId);
        if (factorRows.isEmpty()) {
            throw new MethodologyVersionTransitionRejectedException(
                    "Methodology version must have at least one factor before approval");
        }
        // ≥ 2 levels per factor — batch every factor's levels in ONE tenant-scoped
        // query (was one findAllByTenantIdAndFactorId per factor → N+1 on the
        // approval hot path), then count per factor in memory. Iterating factorRows
        // for the check preserves the original first-failing-factor rejection order.
        List<UUID> factorIds = new ArrayList<>(factorRows.size());
        for (FactorJpaEntity f : factorRows) {
            factorIds.add(f.getId());
        }
        Map<UUID, Integer> levelCounts = new HashMap<>();
        for (FactorLevelJpaEntity lvl : levels
                .findAllByTenantIdAndFactorIdInOrderByLevelOrderAsc(ctx.tenantId(), factorIds)) {
            levelCounts.merge(lvl.getFactorId(), 1, Integer::sum);
        }
        for (FactorJpaEntity f : factorRows) {
            if (levelCounts.getOrDefault(f.getId(), 0) < 2) {
                throw new MethodologyVersionTransitionRejectedException(
                        "Factor '" + f.getCode() + "' must have at least 2 levels");
            }
        }
        localeValidator.validate(factorRows);
        weightPolicy.validate(v.getScoringMode(), v.getTargetTotalPoints(), factorRows);
    }

    private void autoRecordApprovalRequest(UUID versionId, MethodologyJpaEntity m) {
        if (m.getProjectId() != null) {
            createApprovalRequest.createSystemAndAutoApproveFirstStep(
                    CreateApprovalRequestCommand.singleStep(
                            m.getProjectId(),
                            ApprovalEntityType.METHODOLOGY_VERSION,
                            versionId,
                            PermissionCodes.METHODOLOGY_APPROVE),
                    null);
        }
    }
}

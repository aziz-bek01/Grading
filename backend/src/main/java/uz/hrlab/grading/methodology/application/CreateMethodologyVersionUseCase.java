package uz.hrlab.grading.methodology.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.methodology.domain.MethodologyVersion;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatusTransitionPolicy;
import uz.hrlab.grading.methodology.domain.MethodologyVersionTransition;
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

import java.util.List;
import java.util.UUID;

/**
 * APPROVED / LOCKED → new DRAFT (architecture ADR-002). The source row is
 * unchanged; a fresh {@link MethodologyVersionJpaEntity} is created with
 * {@code versionNumber = source.versionNumber + 1} and
 * {@code previousVersionId = source.id}. Factors + levels are deep-copied.
 */
@Service
public class CreateMethodologyVersionUseCase {

    private final MethodologyRepository methodologies;
    private final MethodologyVersionRepository versions;
    private final FactorRepository factors;
    private final FactorLevelRepository levels;
    private final AbacGate abacGate;
    private final MethodologyVersionStatusTransitionPolicy transitionPolicy;
    private final AuditService audit;
    private final MethodologyAuditSnapshot snapshot;

    public CreateMethodologyVersionUseCase(MethodologyRepository methodologies,
                                           MethodologyVersionRepository versions,
                                           FactorRepository factors,
                                           FactorLevelRepository levels,
                                           AbacGate abacGate,
                                           MethodologyVersionStatusTransitionPolicy transitionPolicy,
                                           AuditService audit,
                                           MethodologyAuditSnapshot snapshot) {
        this.methodologies = methodologies;
        this.versions = versions;
        this.factors = factors;
        this.levels = levels;
        this.abacGate = abacGate;
        this.transitionPolicy = transitionPolicy;
        this.audit = audit;
        this.snapshot = snapshot;
    }

    @Transactional
    public MethodologyVersion createNewVersion(UUID sourceVersionId) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.METHODOLOGY_EDIT);
        MethodologyVersionJpaEntity source = versions
                .findByIdAndTenantId(sourceVersionId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        MethodologyJpaEntity m = methodologies
                .findByIdAndTenantId(source.getMethodologyId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (m.getProjectId() != null) {
            abacGate.enforceCanWriteInProject(ctx, m.getProjectId());
        }
        transitionPolicy.check(source.getStatus(),
                MethodologyVersionTransition.CREATE_NEW_VERSION);

        UUID newVersionId = UUID.randomUUID();
        MethodologyVersionJpaEntity v = new MethodologyVersionJpaEntity(
                newVersionId, ctx.tenantId(), source.getMethodologyId(),
                source.getVersionNumber() + 1,
                MethodologyVersionStatus.DRAFT,
                source.getScoringMode(),
                source.getTargetTotalPoints(),
                source.getId());
        versions.save(v);

        // Deep-copy factors + levels
        List<FactorJpaEntity> srcFactors = factors
                .findAllByTenantIdAndMethodologyVersionIdOrderBySortOrderAsc(
                        ctx.tenantId(), sourceVersionId);
        for (FactorJpaEntity sf : srcFactors) {
            UUID newFactorId = UUID.randomUUID();
            FactorJpaEntity nf = new FactorJpaEntity(
                    newFactorId, ctx.tenantId(), newVersionId,
                    sf.getCode(), sf.getWeight(), sf.getMaxPoints(),
                    sf.getSortOrder(), sf.isRequired());
            nf.setNameI18n(sf.getNameI18n());
            nf.setDescriptionI18n(sf.getDescriptionI18n());
            factors.save(nf);

            List<FactorLevelJpaEntity> srcLevels = levels
                    .findAllByTenantIdAndFactorIdOrderByLevelOrderAsc(
                            ctx.tenantId(), sf.getId());
            for (FactorLevelJpaEntity sl : srcLevels) {
                FactorLevelJpaEntity nl = new FactorLevelJpaEntity(
                        UUID.randomUUID(), ctx.tenantId(), newFactorId,
                        sl.getCode(), sl.getLevelOrder(), sl.getPoints(),
                        sl.getScaleValue());
                nl.setLabelI18n(sl.getLabelI18n());
                nl.setDescriptionI18n(sl.getDescriptionI18n());
                levels.save(nl);
            }
        }

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(m.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.METHODOLOGY_VERSION_REVISION_CREATED)
                .entityType("MethodologyVersion")
                .entityId(newVersionId)
                .reason("Cloned from version " + source.getVersionNumber())
                .beforeJson(snapshot.of(source))
                .afterJson(snapshot.of(v))
                .build());
        return v.toDomain();
    }
}

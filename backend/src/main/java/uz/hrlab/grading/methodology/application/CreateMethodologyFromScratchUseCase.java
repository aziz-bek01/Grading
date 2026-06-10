package uz.hrlab.grading.methodology.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.ConflictException;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.methodology.domain.MethodologyStatus;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

/**
 * Creates a CUSTOM methodology + DRAFT v1 with NO factors. User fills in
 * factors afterwards via AddFactorUseCase.
 */
@Service
public class CreateMethodologyFromScratchUseCase {

    private final MethodologyRepository methodologies;
    private final MethodologyVersionRepository versions;
    private final ProjectRepository projects;
    private final AbacGate abacGate;
    private final AuditService audit;
    private final MethodologyAuditSnapshot snapshot;

    public CreateMethodologyFromScratchUseCase(MethodologyRepository methodologies,
                                               MethodologyVersionRepository versions,
                                               ProjectRepository projects,
                                               AbacGate abacGate,
                                               AuditService audit,
                                               MethodologyAuditSnapshot snapshot) {
        this.methodologies = methodologies;
        this.versions = versions;
        this.projects = projects;
        this.abacGate = abacGate;
        this.audit = audit;
        this.snapshot = snapshot;
    }

    @Transactional
    public MethodologyAggregate create(CreateMethodologyCommand cmd) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.METHODOLOGY_CREATE)) {
            throw new PermissionDeniedException();
        }
        if (cmd.projectId() != null) {
            projects.findByIdAndTenantId(cmd.projectId(), ctx.tenantId())
                    .orElseThrow(TenantAccessDeniedException::new);
            abacGate.enforceCanWriteInProject(ctx, cmd.projectId());
        }

        if (methodologies.existsByTenantIdAndProjectIdAndCode(
                ctx.tenantId(), cmd.projectId(), cmd.code())) {
            // B2: a duplicate code is a state CONFLICT, not a malformed request —
            // map to 409 (was 400) with the stable METHODOLOGY_CODE_DUPLICATE code
            // so the FE can switch on it and show an inline "code already used"
            // message under the field.
            throw new ConflictException("METHODOLOGY_CODE_DUPLICATE",
                    "Methodology code already exists in this scope");
        }

        UUID methodologyId = UUID.randomUUID();
        MethodologyJpaEntity m = new MethodologyJpaEntity(
                methodologyId, ctx.tenantId(), cmd.projectId(), cmd.code(),
                cmd.methodologyType(), MethodologyStatus.ACTIVE);
        m.setNameI18n(cmd.nameI18n());
        m.setDescriptionI18n(cmd.descriptionI18n());
        methodologies.save(m);

        UUID versionId = UUID.randomUUID();
        MethodologyVersionJpaEntity v = new MethodologyVersionJpaEntity(
                versionId, ctx.tenantId(), methodologyId, 1,
                MethodologyVersionStatus.DRAFT,
                cmd.scoringMode(), cmd.targetTotalPoints(), null);
        versions.save(v);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(cmd.projectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.METHODOLOGY_CREATED)
                .entityType("Methodology")
                .entityId(methodologyId)
                .afterJson(snapshot.of(m))
                .build());
        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(cmd.projectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.METHODOLOGY_VERSION_CREATED)
                .entityType("MethodologyVersion")
                .entityId(versionId)
                .afterJson(snapshot.of(v))
                .build());

        return new MethodologyAggregate(m.toDomain(), v.toDomain());
    }
}

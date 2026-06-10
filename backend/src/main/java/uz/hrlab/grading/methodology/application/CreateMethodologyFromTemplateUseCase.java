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
import uz.hrlab.grading.methodology.infrastructure.FactorJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

/**
 * Instantiates a {@link MethodologyJpaEntity} + DRAFT
 * {@link MethodologyVersionJpaEntity} + factor/level rows from EITHER a built-in
 * template (CLASSIC_8_FACTOR / EXTENDED_11_CRITERIA / CUSTOM) OR a tenant
 * DB-backed CUSTOM template — resolved uniformly through the single
 * {@link TemplateCatalog} port (Epic E). The deep-copy / instantiation logic is
 * source-agnostic: both resolve to one {@link MethodologyTemplate} shape.
 */
@Service
public class CreateMethodologyFromTemplateUseCase {

    private final TemplateCatalog templateCatalog;
    private final MethodologyRepository methodologies;
    private final MethodologyVersionRepository versions;
    private final FactorRepository factors;
    private final FactorLevelRepository levels;
    private final ProjectRepository projects;
    private final AbacGate abacGate;
    private final AuditService audit;
    private final MethodologyAuditSnapshot snapshot;

    public CreateMethodologyFromTemplateUseCase(TemplateCatalog templateCatalog,
                                                MethodologyRepository methodologies,
                                                MethodologyVersionRepository versions,
                                                FactorRepository factors,
                                                FactorLevelRepository levels,
                                                ProjectRepository projects,
                                                AbacGate abacGate,
                                                AuditService audit,
                                                MethodologyAuditSnapshot snapshot) {
        this.templateCatalog = templateCatalog;
        this.methodologies = methodologies;
        this.versions = versions;
        this.factors = factors;
        this.levels = levels;
        this.projects = projects;
        this.abacGate = abacGate;
        this.audit = audit;
        this.snapshot = snapshot;
    }

    @Transactional
    public MethodologyAggregate create(CreateMethodologyFromTemplateCommand cmd) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.METHODOLOGY_CREATE)) {
            throw new PermissionDeniedException();
        }

        // Resolve from built-in OR tenant custom via the single catalog port.
        // A cross-tenant/archived custom code throws ResourceNotFoundException (404).
        MethodologyTemplate template = templateCatalog.findInstantiable(
                ctx.tenantId(), cmd.templateCode());

        if (cmd.projectId() != null) {
            projects.findByIdAndTenantId(cmd.projectId(), ctx.tenantId())
                    .orElseThrow(TenantAccessDeniedException::new);
            abacGate.enforceCanWriteInProject(ctx, cmd.projectId());
        }

        if (methodologies.existsByTenantIdAndProjectIdAndCode(
                ctx.tenantId(), cmd.projectId(), cmd.code())) {
            // B2: duplicate code is a 409 CONFLICT (was 400), stable code kept so
            // the FE renders an inline "code already used" message. Mirrors the
            // from-scratch path for a consistent contract across both create flows.
            throw new ConflictException("METHODOLOGY_CODE_DUPLICATE",
                    "Methodology code already exists in this scope");
        }

        UUID methodologyId = UUID.randomUUID();
        MethodologyJpaEntity m = new MethodologyJpaEntity(
                methodologyId, ctx.tenantId(), cmd.projectId(), cmd.code(),
                template.methodologyType(), MethodologyStatus.ACTIVE);
        m.setNameI18n(cmd.nameI18n());
        m.setDescriptionI18n(cmd.descriptionI18n());
        methodologies.save(m);

        UUID versionId = UUID.randomUUID();
        MethodologyVersionJpaEntity v = new MethodologyVersionJpaEntity(
                versionId, ctx.tenantId(), methodologyId, 1,
                MethodologyVersionStatus.DRAFT,
                template.scoringMode(), template.targetTotalPoints(), null);
        versions.save(v);

        // Seed factors + levels from template
        for (MethodologyTemplate.FactorTemplate ft : template.factors()) {
            UUID factorId = UUID.randomUUID();
            FactorJpaEntity f = new FactorJpaEntity(
                    factorId, ctx.tenantId(), versionId, ft.code(),
                    ft.weight(), ft.maxPoints(), ft.sortOrder(), ft.required());
            f.setNameI18n(ft.nameI18n());
            f.setDescriptionI18n(ft.descriptionI18n());
            factors.save(f);

            for (MethodologyTemplate.LevelTemplate lt : ft.levels()) {
                FactorLevelJpaEntity l = new FactorLevelJpaEntity(
                        UUID.randomUUID(), ctx.tenantId(), factorId, lt.code(),
                        lt.levelOrder(), lt.points(), lt.scaleValue());
                l.setLabelI18n(lt.labelI18n());
                l.setDescriptionI18n(lt.descriptionI18n());
                levels.save(l);
            }
        }

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(cmd.projectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.METHODOLOGY_CREATED)
                .entityType("Methodology")
                .entityId(methodologyId)
                .reason("from-template:" + cmd.templateCode())
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

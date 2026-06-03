package uz.hrlab.grading.gradestructure.application;

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
import uz.hrlab.grading.gradestructure.domain.GradeBandGapPolicy;
import uz.hrlab.grading.gradestructure.domain.GradeStructureStatus;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureRepository;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

/**
 * Instantiate a {@link GradeStructureJpaEntity} + N {@link GradeJpaEntity}s +
 * N {@link GradeBandJpaEntity}s from a built-in template (GRADE_14 / GRADE_16 /
 * CUSTOM). All rows are created in DRAFT.
 */
@Service
public class CreateGradeStructureFromTemplateUseCase {

    private final GradeStructureTemplateRegistry templates;
    private final GradeStructureRepository structures;
    private final GradeRepository grades;
    private final GradeBandRepository bands;
    private final ProjectRepository projects;
    private final AbacGate abacGate;
    private final AuditService audit;
    private final GradeStructureAuditSnapshot snapshot;

    public CreateGradeStructureFromTemplateUseCase(GradeStructureTemplateRegistry templates,
                                                   GradeStructureRepository structures,
                                                   GradeRepository grades,
                                                   GradeBandRepository bands,
                                                   ProjectRepository projects,
                                                   AbacGate abacGate,
                                                   AuditService audit,
                                                   GradeStructureAuditSnapshot snapshot) {
        this.templates = templates;
        this.structures = structures;
        this.grades = grades;
        this.bands = bands;
        this.projects = projects;
        this.abacGate = abacGate;
        this.audit = audit;
        this.snapshot = snapshot;
    }

    @Transactional
    public GradeStructureAggregate create(CreateGradeStructureFromTemplateCommand cmd) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.GRADE_EDIT)) {
            throw new PermissionDeniedException();
        }
        GradeStructureTemplate template = templates.require(cmd.templateCode());

        if (cmd.projectId() != null) {
            projects.findByIdAndTenantId(cmd.projectId(), ctx.tenantId())
                    .orElseThrow(TenantAccessDeniedException::new);
            abacGate.enforceCanWriteInProject(ctx, cmd.projectId());
        }
        if (structures.existsByTenantIdAndProjectIdAndCode(
                ctx.tenantId(), cmd.projectId(), cmd.code())) {
            throw new ValidationException("GRADE_STRUCTURE_CODE_DUPLICATE",
                    "Grade structure code already exists in this scope");
        }

        UUID structureId = UUID.randomUUID();
        GradeStructureJpaEntity s = new GradeStructureJpaEntity(
                structureId, ctx.tenantId(), cmd.projectId(), cmd.code(),
                template.structureType(), GradeStructureStatus.DRAFT,
                1, null,
                cmd.gapPolicy() == null ? GradeBandGapPolicy.STRICT_NO_GAPS : cmd.gapPolicy());
        s.setNameI18n(cmd.nameI18n() != null && !cmd.nameI18n().isEmpty()
                ? cmd.nameI18n() : template.nameI18n());
        s.setDescriptionI18n(cmd.descriptionI18n() != null && !cmd.descriptionI18n().isEmpty()
                ? cmd.descriptionI18n() : template.descriptionI18n());
        structures.save(s);

        var gradeList = new java.util.ArrayList<uz.hrlab.grading.gradestructure.domain.Grade>();
        var bandList  = new java.util.ArrayList<uz.hrlab.grading.gradestructure.domain.GradeBand>();
        for (GradeStructureTemplate.GradeRowTemplate gt : template.grades()) {
            UUID gradeId = UUID.randomUUID();
            GradeJpaEntity g = new GradeJpaEntity(
                    gradeId, ctx.tenantId(), structureId, gt.gradeNumber(), gt.sortOrder());
            g.setNameI18n(gt.nameI18n());
            grades.save(g);
            gradeList.add(g.toDomain());

            GradeBandJpaEntity b = new GradeBandJpaEntity(
                    UUID.randomUUID(), ctx.tenantId(), gradeId, structureId,
                    gt.minScore(), gt.maxScore());
            bands.save(b);
            bandList.add(b.toDomain());
        }

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(cmd.projectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.GRADE_STRUCTURE_CREATED)
                .entityType("GradeStructure")
                .entityId(structureId)
                .reason("from-template:" + cmd.templateCode())
                .afterJson(snapshot.of(s))
                .build());

        return new GradeStructureAggregate(s.toDomain(), gradeList, bandList);
    }
}

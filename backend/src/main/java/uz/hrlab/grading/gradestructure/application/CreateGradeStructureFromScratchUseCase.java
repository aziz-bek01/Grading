package uz.hrlab.grading.gradestructure.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.gradestructure.domain.GradeBandGapPolicy;
import uz.hrlab.grading.gradestructure.domain.GradeStructureStatus;
import uz.hrlab.grading.gradestructure.domain.GradeStructureType;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureRepository;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.UUID;

/** Create a CUSTOM (empty) grade structure. User adds grades + bands later. */
@Service
public class CreateGradeStructureFromScratchUseCase {

    private final GradeStructureRepository structures;
    private final ProjectRepository projects;
    private final AbacGate abacGate;
    private final AuditService audit;
    private final GradeStructureAuditSnapshot snapshot;

    public CreateGradeStructureFromScratchUseCase(GradeStructureRepository structures,
                                                  ProjectRepository projects,
                                                  AbacGate abacGate,
                                                  AuditService audit,
                                                  GradeStructureAuditSnapshot snapshot) {
        this.structures = structures;
        this.projects = projects;
        this.abacGate = abacGate;
        this.audit = audit;
        this.snapshot = snapshot;
    }

    @Transactional
    public GradeStructureAggregate create(CreateGradeStructureCommand cmd) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.GRADE_EDIT);
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
                cmd.structureType() == null ? GradeStructureType.CUSTOM : cmd.structureType(),
                GradeStructureStatus.DRAFT, 1, null,
                cmd.gapPolicy() == null ? GradeBandGapPolicy.STRICT_NO_GAPS : cmd.gapPolicy());
        s.setNameI18n(cmd.nameI18n());
        s.setDescriptionI18n(cmd.descriptionI18n());
        structures.save(s);

        audit.record(AuditEvent.builder(ctx)
                .projectId(cmd.projectId())
                .action(AuditAction.GRADE_STRUCTURE_CREATED)
                .entityType("GradeStructure")
                .entityId(structureId)
                .reason("from-scratch")
                .afterJson(snapshot.of(s))
                .build());

        return new GradeStructureAggregate(s.toDomain(), List.of(), List.of());
    }
}

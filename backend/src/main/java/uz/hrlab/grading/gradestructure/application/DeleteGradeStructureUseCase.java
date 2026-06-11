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
import uz.hrlab.grading.gradestructure.domain.GradeStructureImmutabilityPolicy;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.UUID;

/**
 * Hard delete of a DRAFT-only grade structure (BE-4) — mirrors the
 * EVALUATION_DELETED pattern. Non-DRAFT structures keep the ARCHIVE path
 * (the immutability policy rejects APPROVED/LOCKED/ARCHIVED with the existing
 * transition-rejected error). Cascade-deletes the structure's grades + bands.
 *
 * <p>Gated by {@code GRADE_EDIT}. Tenant comes from {@code TenantContext};
 * cross-tenant id → 404 via {@code findByIdAndTenantId}.
 */
@Service
public class DeleteGradeStructureUseCase {

    private final GradeStructureRepository structures;
    private final GradeRepository grades;
    private final GradeBandRepository bands;
    private final GradeStructureImmutabilityPolicy immutability;
    private final AbacGate abacGate;
    private final AuditService audit;
    private final GradeStructureAuditSnapshot snapshot;

    public DeleteGradeStructureUseCase(GradeStructureRepository structures,
                                       GradeRepository grades,
                                       GradeBandRepository bands,
                                       GradeStructureImmutabilityPolicy immutability,
                                       AbacGate abacGate,
                                       AuditService audit,
                                       GradeStructureAuditSnapshot snapshot) {
        this.structures = structures;
        this.grades = grades;
        this.bands = bands;
        this.immutability = immutability;
        this.abacGate = abacGate;
        this.audit = audit;
        this.snapshot = snapshot;
    }

    @Transactional
    public void delete(UUID structureId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.GRADE_EDIT)) {
            throw new PermissionDeniedException();
        }
        GradeStructureJpaEntity s = structures.findByIdAndTenantId(structureId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (s.getProjectId() != null) {
            abacGate.enforceCanWriteInProject(ctx, s.getProjectId());
        }
        // DRAFT-only — APPROVED/LOCKED/ARCHIVED rejected with the existing
        // transition-rejected error (use create-new-version / archive instead).
        immutability.ensureMutable(s.getStatus());

        var before = snapshot.of(s);

        // Cascade-delete grades + their bands (FK order: bands first).
        List<GradeJpaEntity> gs = grades
                .findAllByTenantIdAndGradeStructureIdOrderBySortOrderAsc(ctx.tenantId(), structureId);
        for (GradeJpaEntity g : gs) {
            bands.findByTenantIdAndGradeId(ctx.tenantId(), g.getId()).ifPresent(bands::delete);
        }
        grades.deleteAll(gs);
        structures.delete(s);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(s.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.GRADE_STRUCTURE_DELETED)
                .entityType("GradeStructure")
                .entityId(structureId)
                .reason("hard delete (DRAFT)")
                .beforeJson(before)
                .build());
    }
}

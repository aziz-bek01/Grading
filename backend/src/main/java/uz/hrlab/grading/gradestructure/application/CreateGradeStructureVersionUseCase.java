package uz.hrlab.grading.gradestructure.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.gradestructure.domain.GradeStructure;
import uz.hrlab.grading.gradestructure.domain.GradeStructureStatus;
import uz.hrlab.grading.gradestructure.domain.GradeStructureStatusTransitionPolicy;
import uz.hrlab.grading.gradestructure.domain.GradeStructureTransition;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * APPROVED / LOCKED → new DRAFT row. Source unchanged. Grades + bands are
 * deep-copied. The new row's {@code versionNumber} = source + 1,
 * {@code previousVersionId} = source.id, {@code code} is auto-suffixed with
 * "-v{n}" so the unique constraint (tenant, project, code) is preserved.
 */
@Service
public class CreateGradeStructureVersionUseCase {

    private final GradeStructureRepository structures;
    private final GradeRepository grades;
    private final GradeBandRepository bands;
    private final GradeStructureStatusTransitionPolicy transitionPolicy;
    private final AbacGate abacGate;
    private final AuditService audit;
    private final GradeStructureAuditSnapshot snapshot;

    public CreateGradeStructureVersionUseCase(GradeStructureRepository structures,
                                              GradeRepository grades,
                                              GradeBandRepository bands,
                                              GradeStructureStatusTransitionPolicy transitionPolicy,
                                              AbacGate abacGate,
                                              AuditService audit,
                                              GradeStructureAuditSnapshot snapshot) {
        this.structures = structures;
        this.grades = grades;
        this.bands = bands;
        this.transitionPolicy = transitionPolicy;
        this.abacGate = abacGate;
        this.audit = audit;
        this.snapshot = snapshot;
    }

    @Transactional
    public GradeStructure createNewVersion(UUID sourceId) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.GRADE_EDIT);
        GradeStructureJpaEntity source = structures.findByIdAndTenantId(sourceId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (source.getProjectId() != null) {
            abacGate.enforceCanWriteInProject(ctx, source.getProjectId());
        }
        transitionPolicy.check(source.getStatus(), GradeStructureTransition.CREATE_NEW_VERSION);

        int nextVersion = source.getVersionNumber() + 1;
        String newCode = source.getCode() + "-v" + nextVersion;

        UUID newId = UUID.randomUUID();
        GradeStructureJpaEntity v = new GradeStructureJpaEntity(
                newId, ctx.tenantId(), source.getProjectId(), newCode,
                source.getStructureType(), GradeStructureStatus.DRAFT,
                nextVersion, source.getId(), source.getGapPolicy());
        v.setNameI18n(source.getNameI18n());
        v.setDescriptionI18n(source.getDescriptionI18n());
        structures.save(v);

        // Deep-copy grades + bands.
        List<GradeJpaEntity> srcGrades = grades
                .findAllByTenantIdAndGradeStructureIdOrderBySortOrderAsc(
                        ctx.tenantId(), sourceId);
        // Batch every source grade's band in ONE structure-scoped query (was one
        // findByTenantIdAndGradeId per grade → N+1). One band per grade (uq on
        // grade_id), keyed by gradeId; the per-grade clone/insert order below is
        // unchanged. Mirrors SaveAsGradeTemplateUseCase#buildSnapshot.
        Map<UUID, GradeBandJpaEntity> bandByGrade = new HashMap<>();
        for (GradeBandJpaEntity sb : bands
                .findAllByTenantIdAndGradeStructureIdOrderByMinScoreAsc(ctx.tenantId(), sourceId)) {
            bandByGrade.put(sb.getGradeId(), sb);
        }
        for (GradeJpaEntity sg : srcGrades) {
            UUID newGradeId = UUID.randomUUID();
            GradeJpaEntity ng = new GradeJpaEntity(newGradeId, ctx.tenantId(), newId,
                    sg.getGradeNumber(), sg.getSortOrder());
            ng.setNameI18n(sg.getNameI18n());
            ng.setDescriptionI18n(sg.getDescriptionI18n());
            grades.save(ng);

            GradeBandJpaEntity sb = bandByGrade.get(sg.getId());
            if (sb != null) {
                GradeBandJpaEntity nb = new GradeBandJpaEntity(
                        UUID.randomUUID(), ctx.tenantId(), newGradeId, newId,
                        sb.getMinScore(), sb.getMaxScore());
                bands.save(nb);
            }
        }

        audit.record(AuditEvent.builder(ctx)
                .projectId(source.getProjectId())
                .action(AuditAction.GRADE_STRUCTURE_REVISION_CREATED)
                .entityType("GradeStructure")
                .entityId(newId)
                .reason("Cloned from version " + source.getVersionNumber())
                .beforeJson(snapshot.of(source))
                .afterJson(snapshot.of(v))
                .build());
        return v.toDomain();
    }
}

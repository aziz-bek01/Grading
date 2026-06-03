package uz.hrlab.grading.gradestructure.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.gradestructure.domain.Grade;
import uz.hrlab.grading.gradestructure.domain.GradeBand;
import uz.hrlab.grading.gradestructure.domain.GradeStructureStatus;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-side queries for grade structures. */
@Service
public class GradeStructureQueries {

    private final GradeStructureRepository structures;
    private final GradeRepository grades;
    private final GradeBandRepository bands;

    public GradeStructureQueries(GradeStructureRepository structures,
                                 GradeRepository grades,
                                 GradeBandRepository bands) {
        this.structures = structures;
        this.grades = grades;
        this.bands = bands;
    }

    @Transactional(readOnly = true)
    public Page<GradeStructureJpaEntity> findByProject(UUID projectId,
                                                       GradeStructureStatus status,
                                                       Pageable pageable) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.GRADE_READ)) {
            throw new PermissionDeniedException();
        }
        if (status != null && projectId != null) {
            return structures.findAllByTenantIdAndProjectIdAndStatus(
                    ctx.tenantId(), projectId, status, pageable);
        }
        if (projectId != null) {
            return structures.findAllByTenantIdAndProjectId(
                    ctx.tenantId(), projectId, pageable);
        }
        return structures.findAllByTenantId(ctx.tenantId(), pageable);
    }

    @Transactional(readOnly = true)
    public GradeStructureAggregate findDetail(UUID id) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.GRADE_READ)) {
            throw new PermissionDeniedException();
        }
        GradeStructureJpaEntity s = structures.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        List<GradeJpaEntity> gs = grades
                .findAllByTenantIdAndGradeStructureIdOrderBySortOrderAsc(ctx.tenantId(), id);
        List<Grade> gradeList = new ArrayList<>(gs.size());
        List<GradeBand> bandList = new ArrayList<>();
        for (GradeJpaEntity g : gs) {
            gradeList.add(g.toDomain());
            bands.findByTenantIdAndGradeId(ctx.tenantId(), g.getId())
                    .ifPresent(b -> bandList.add(b.toDomain()));
        }
        return new GradeStructureAggregate(s.toDomain(), gradeList, bandList);
    }

    @Transactional(readOnly = true)
    public List<GradeBandJpaEntity> bandsOf(UUID structureId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.GRADE_READ)) {
            throw new PermissionDeniedException();
        }
        return bands.findAllByTenantIdAndGradeStructureIdOrderByMinScoreAsc(
                ctx.tenantId(), structureId);
    }

    /**
     * Returns the active grade structure for a project — used by evaluation
     * approval to look up the grade. Preference order: LOCKED > APPROVED > none.
     * Caller passes tenantId because this is also called internally from other
     * use cases that already have a TenantContext.
     */
    @Transactional(readOnly = true)
    public Optional<GradeStructureJpaEntity> findActiveForProject(UUID tenantId, UUID projectId) {
        if (tenantId == null || projectId == null) return Optional.empty();
        var locked = structures.findAllByTenantIdAndProjectIdAndStatusOrderByVersionNumberDesc(
                tenantId, projectId, GradeStructureStatus.LOCKED);
        if (!locked.isEmpty()) return Optional.of(locked.get(0));
        var approved = structures.findAllByTenantIdAndProjectIdAndStatusOrderByVersionNumberDesc(
                tenantId, projectId, GradeStructureStatus.APPROVED);
        if (!approved.isEmpty()) return Optional.of(approved.get(0));
        return Optional.empty();
    }

    /** Wrap an empty page for "no project filter, no results". */
    public static Page<GradeStructureJpaEntity> empty(Pageable p) {
        return new PageImpl<>(List.of(), p, 0);
    }
}

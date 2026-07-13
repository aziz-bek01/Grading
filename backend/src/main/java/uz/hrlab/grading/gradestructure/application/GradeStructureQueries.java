package uz.hrlab.grading.gradestructure.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.gradestructure.api.GradeStructureResponse;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    // BE-035 — returns the enriched wire DTO (mapped in-tx). The list-view
    // grade_count/createdAt/updatedAt fields live on the JpaEntity (not the
    // domain), so the batched-count enrichment + GradeStructureResponse.fromList
    // mapping is done here, keeping the persistence type out of the controller.
    @Transactional(readOnly = true)
    public Page<GradeStructureResponse> findByProject(UUID projectId,
                                                      GradeStructureStatus status,
                                                      Pageable pageable) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.GRADE_READ);
        Page<GradeStructureJpaEntity> page;
        if (status != null && projectId != null) {
            page = structures.findAllByTenantIdAndProjectIdAndStatus(
                    ctx.tenantId(), projectId, status, pageable);
        } else if (projectId != null) {
            page = structures.findAllByTenantIdAndProjectId(
                    ctx.tenantId(), projectId, pageable);
        } else {
            page = structures.findAllByTenantId(ctx.tenantId(), pageable);
        }
        List<UUID> ids = page.getContent().stream()
                .map(GradeStructureJpaEntity::getId).toList();
        Map<UUID, Integer> counts = gradeCountsByStructureIds(ids);
        return page.map(e -> GradeStructureResponse.fromList(e, counts.getOrDefault(e.getId(), 0)));
    }

    /**
     * Batched grade-count per structure id (BE-1) so the list endpoint enriches
     * each row with {@code grade_count} in ONE query (no N+1). Tenant-scoped.
     * Structure ids absent from the result have zero grades → the caller
     * defaults to 0.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Integer> gradeCountsByStructureIds(Collection<UUID> structureIds) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.GRADE_READ);
        Map<UUID, Integer> out = new HashMap<>();
        if (structureIds == null || structureIds.isEmpty()) {
            return out;
        }
        for (Object[] row : grades.countByStructureIds(ctx.tenantId(), structureIds)) {
            out.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return out;
    }

    @Transactional(readOnly = true)
    public GradeStructureAggregate findDetail(UUID id) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.GRADE_READ);
        GradeStructureJpaEntity s = structures.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        List<GradeJpaEntity> gs = grades
                .findAllByTenantIdAndGradeStructureIdOrderBySortOrderAsc(ctx.tenantId(), id);
        // BE — batch every grade's band in ONE structure-scoped query (was one
        // findByTenantIdAndGradeId per grade → N+1). One band per grade (MVP 1);
        // iterate grades in sort_order so bandList keeps its original ordering.
        Map<UUID, GradeBandJpaEntity> bandByGrade = new HashMap<>();
        for (GradeBandJpaEntity b : bands
                .findAllByTenantIdAndGradeStructureIdOrderByMinScoreAsc(ctx.tenantId(), id)) {
            bandByGrade.put(b.getGradeId(), b);
        }
        List<Grade> gradeList = new ArrayList<>(gs.size());
        List<GradeBand> bandList = new ArrayList<>();
        for (GradeJpaEntity g : gs) {
            gradeList.add(g.toDomain());
            GradeBandJpaEntity b = bandByGrade.get(g.getId());
            if (b != null) {
                bandList.add(b.toDomain());
            }
        }
        return new GradeStructureAggregate(s.toDomain(), gradeList, bandList);
    }

    @Transactional(readOnly = true)
    public List<GradeBandJpaEntity> bandsOf(UUID structureId) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.GRADE_READ);
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

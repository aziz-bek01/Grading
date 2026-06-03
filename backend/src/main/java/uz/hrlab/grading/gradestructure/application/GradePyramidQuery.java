package uz.hrlab.grading.gradestructure.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.gradestructure.infrastructure.GradeJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Aggregates assignment counts per grade for a given grade structure — the
 * input for the Grade Pyramid dashboard (architecture §17).
 *
 * <p>Counts come from {@code evaluations.assigned_grade_number} grouped by
 * grade. Salary fields are NOT touched here (Phase 6 has no salary access).
 */
@Service
public class GradePyramidQuery {

    private final GradeStructureRepository structures;
    private final GradeRepository grades;
    private final JdbcTemplate jdbc;

    public GradePyramidQuery(GradeStructureRepository structures,
                             GradeRepository grades,
                             JdbcTemplate jdbc) {
        this.structures = structures;
        this.grades = grades;
        this.jdbc = jdbc;
    }

    public record GradePyramidRow(int gradeNumber, UUID gradeId, long evaluationCount) { }

    @Transactional(readOnly = true)
    public List<GradePyramidRow> pyramid(UUID structureId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.GRADE_READ)) {
            throw new PermissionDeniedException();
        }
        GradeStructureJpaEntity s = structures.findByIdAndTenantId(structureId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        List<GradeJpaEntity> gradeList = grades
                .findAllByTenantIdAndGradeStructureIdOrderBySortOrderAsc(
                        ctx.tenantId(), structureId);
        List<GradePyramidRow> out = new ArrayList<>(gradeList.size());
        for (GradeJpaEntity g : gradeList) {
            // Count evaluations whose grade_band_id points to this grade's band
            // (one band per grade in MVP 1) within the same tenant + project.
            Long count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM evaluations e " +
                            "JOIN grade_bands b ON b.id = e.grade_band_id " +
                            "WHERE e.tenant_id = ? AND b.grade_id = ?",
                    Long.class, ctx.tenantId(), g.getId());
            out.add(new GradePyramidRow(g.getGradeNumber(), g.getId(),
                    count == null ? 0L : count));
        }
        return out;
    }
}

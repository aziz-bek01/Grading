package uz.hrlab.grading.gradestructure.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregates assignment counts per grade for a given grade structure — the
 * input for the Grade Pyramid dashboard (architecture §17).
 *
 * <p>Counts come from {@code evaluations.grade_band_id} grouped by band. Each
 * row is enriched (BE-2) with the grade's {@code name_i18n}, the band's
 * {@code min_score} / {@code max_score}, and a {@code position_count} (distinct
 * positions assigned to the band) alongside {@code evaluation_count}. Salary
 * fields are NOT touched here (Phase 6 has no salary access).
 */
@Service
public class GradePyramidQuery {

    private final GradeStructureRepository structures;
    private final GradeRepository grades;
    private final GradeBandRepository bands;
    private final JdbcTemplate jdbc;

    public GradePyramidQuery(GradeStructureRepository structures,
                             GradeRepository grades,
                             GradeBandRepository bands,
                             JdbcTemplate jdbc) {
        this.structures = structures;
        this.grades = grades;
        this.bands = bands;
        this.jdbc = jdbc;
    }

    /**
     * Enriched pyramid row. {@code nameI18n} + {@code minScore}/{@code maxScore}
     * may be null/empty for a grade with no band yet (DRAFT structures).
     */
    public record GradePyramidRow(int gradeNumber, UUID gradeId,
                                  Map<String, String> nameI18n,
                                  BigDecimal minScore, BigDecimal maxScore,
                                  long positionCount, long evaluationCount) { }

    @Transactional(readOnly = true)
    public List<GradePyramidRow> pyramid(UUID structureId) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.GRADE_READ);
        GradeStructureJpaEntity s = structures.findByIdAndTenantId(structureId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        List<GradeJpaEntity> gradeList = grades
                .findAllByTenantIdAndGradeStructureIdOrderBySortOrderAsc(
                        ctx.tenantId(), structureId);
        List<GradePyramidRow> out = new ArrayList<>(gradeList.size());
        for (GradeJpaEntity g : gradeList) {
            GradeBandJpaEntity band = bands
                    .findByTenantIdAndGradeId(ctx.tenantId(), g.getId()).orElse(null);
            // Count evaluations / distinct positions whose grade_band_id points
            // to this grade's band (one band per grade in MVP 1) within the same
            // tenant. A grade with no band yet contributes zero counts.
            long evaluationCount = 0L;
            long positionCount = 0L;
            if (band != null) {
                Long ev = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM evaluations e " +
                                "WHERE e.tenant_id = ? AND e.grade_band_id = ?",
                        Long.class, ctx.tenantId(), band.getId());
                evaluationCount = ev == null ? 0L : ev;
                Long pos = jdbc.queryForObject(
                        "SELECT COUNT(DISTINCT e.position_id) FROM evaluations e " +
                                "WHERE e.tenant_id = ? AND e.grade_band_id = ?",
                        Long.class, ctx.tenantId(), band.getId());
                positionCount = pos == null ? 0L : pos;
            }
            out.add(new GradePyramidRow(g.getGradeNumber(), g.getId(),
                    g.getNameI18n(),
                    band == null ? null : band.getMinScore(),
                    band == null ? null : band.getMaxScore(),
                    positionCount, evaluationCount));
        }
        return out;
    }
}

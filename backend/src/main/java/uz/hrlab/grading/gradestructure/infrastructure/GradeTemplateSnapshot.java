package uz.hrlab.grading.gradestructure.infrastructure;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Serialization shape of the {@code grade_templates.grades_snapshot} jsonb
 * column (BE-7). A FROZEN deep copy of a grade structure's grades + bands —
 * NOT FKs — so a template round-trips loss-free and reproducibly regardless of
 * later edits to the source structure.
 *
 * <p>Field names mirror the {@link GradeJpaEntity} / {@link GradeBandJpaEntity}
 * properties. The shape agreed in 033-create-grade-templates.yaml header:
 * <pre>
 * { "grades": [ { "gradeNumber", "sortOrder", "nameI18n":{},
 *                 "descriptionI18n":{}|null, "minScore":num|null,
 *                 "maxScore":num|null } ] }
 * </pre>
 *
 * <p>NUMERIC values are stored as {@link BigDecimal} so the NUMERIC(12,4)
 * precision of the source band columns is preserved. The snapshot captures NO
 * ids, tenant_id, or audit columns — those are re-minted at instantiation time.
 * A grade with no band yet serializes {@code minScore}/{@code maxScore} as null.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record GradeTemplateSnapshot(
        List<GradeSnapshot> grades
) {

    public record GradeSnapshot(
            int gradeNumber,
            int sortOrder,
            Map<String, String> nameI18n,
            Map<String, String> descriptionI18n,
            BigDecimal minScore,
            BigDecimal maxScore
    ) { }
}

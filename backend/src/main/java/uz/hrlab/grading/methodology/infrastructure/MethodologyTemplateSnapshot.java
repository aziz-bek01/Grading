package uz.hrlab.grading.methodology.infrastructure;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Serialization shape of the {@code methodology_templates.factors_snapshot}
 * jsonb column (Epic E). A FROZEN deep copy of a methodology version's factors
 * + levels — NOT FKs — so a template round-trips loss-free and reproducibly
 * regardless of later edits to the source version.
 *
 * <p>Field names mirror the {@link FactorJpaEntity} / {@link FactorLevelJpaEntity}
 * properties and the columns in 011-create-factors.yaml. The shape is the
 * contract agreed in 032-create-methodology-templates.yaml header:
 * <pre>
 * { "factors": [ { "code","sortOrder","weight","maxPoints","required",
 *                  "nameI18n":{}, "descriptionI18n":{}|null,
 *                  "levels":[ {"code","levelOrder","points","scaleValue":{}|null,
 *                              "labelI18n":{}, "descriptionI18n":{}|null} ] } ] }
 * </pre>
 *
 * <p>NUMERIC values are stored as {@link BigDecimal} so the NUMERIC(12,4)
 * precision of the source columns is preserved. The snapshot captures NO ids,
 * tenant_id, or audit columns — those are re-minted at instantiation time.
 *
 * <p>This record lives in {@code infrastructure} because it is the persistence
 * shape of a jsonb column; the {@code application.TemplateCatalog} port maps it
 * to/from the common {@code MethodologyTemplate} instantiation shape so the
 * application layer never depends on the jsonb wire format.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record MethodologyTemplateSnapshot(
        List<FactorSnapshot> factors
) {

    public record FactorSnapshot(
            String code,
            int sortOrder,
            BigDecimal weight,
            BigDecimal maxPoints,
            boolean required,
            Map<String, String> nameI18n,
            Map<String, String> descriptionI18n,
            List<LevelSnapshot> levels
    ) { }

    public record LevelSnapshot(
            String code,
            int levelOrder,
            BigDecimal points,
            BigDecimal scaleValue,
            Map<String, String> labelI18n,
            Map<String, String> descriptionI18n
    ) { }
}

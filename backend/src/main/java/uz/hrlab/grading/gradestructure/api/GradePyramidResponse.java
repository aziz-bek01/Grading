package uz.hrlab.grading.gradestructure.api;

import uz.hrlab.grading.gradestructure.application.GradePyramidQuery;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Grade pyramid dashboard payload. Each {@link Row} is the FE contract the page
 * consumes (BE-2): {@code grade_id, grade_number, grade_name (from name_i18n),
 * position_count, evaluation_count, min_score, max_score} — snake_case via the
 * global Jackson strategy.
 */
public record GradePyramidResponse(List<Row> rows) {

    public record Row(int gradeNumber, UUID gradeId, Map<String, String> gradeName,
                      long positionCount, long evaluationCount,
                      BigDecimal minScore, BigDecimal maxScore) { }

    public static GradePyramidResponse from(List<GradePyramidQuery.GradePyramidRow> rows) {
        return new GradePyramidResponse(rows.stream()
                .map(r -> new Row(r.gradeNumber(), r.gradeId(), r.nameI18n(),
                        r.positionCount(), r.evaluationCount(),
                        r.minScore(), r.maxScore()))
                .toList());
    }
}

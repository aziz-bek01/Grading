package uz.hrlab.grading.gradestructure.api;

import uz.hrlab.grading.gradestructure.application.GradePyramidQuery;

import java.util.List;
import java.util.UUID;

public record GradePyramidResponse(List<Row> rows) {

    public record Row(int gradeNumber, UUID gradeId, long evaluationCount) { }

    public static GradePyramidResponse from(List<GradePyramidQuery.GradePyramidRow> rows) {
        return new GradePyramidResponse(rows.stream()
                .map(r -> new Row(r.gradeNumber(), r.gradeId(), r.evaluationCount()))
                .toList());
    }
}

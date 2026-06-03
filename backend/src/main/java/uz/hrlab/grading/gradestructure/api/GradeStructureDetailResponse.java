package uz.hrlab.grading.gradestructure.api;

import uz.hrlab.grading.gradestructure.application.GradeStructureAggregate;

import java.util.List;

public record GradeStructureDetailResponse(
        GradeStructureResponse structure,
        List<GradeResponse> grades,
        List<GradeBandResponse> bands
) {
    public static GradeStructureDetailResponse from(GradeStructureAggregate a) {
        return new GradeStructureDetailResponse(
                GradeStructureResponse.from(a.structure()),
                a.grades().stream().map(GradeResponse::from).toList(),
                a.bands().stream().map(GradeBandResponse::from).toList());
    }
}

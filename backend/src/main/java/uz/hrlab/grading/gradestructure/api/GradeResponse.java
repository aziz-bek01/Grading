package uz.hrlab.grading.gradestructure.api;

import uz.hrlab.grading.gradestructure.domain.Grade;

import java.util.Map;
import java.util.UUID;

public record GradeResponse(
        UUID id,
        UUID gradeStructureId,
        int gradeNumber,
        int sortOrder,
        Map<String, String> nameI18n,
        Map<String, String> descriptionI18n
) {
    public static GradeResponse from(Grade g) {
        return new GradeResponse(g.id(), g.gradeStructureId(),
                g.gradeNumber(), g.sortOrder(), g.nameI18n(), g.descriptionI18n());
    }
}

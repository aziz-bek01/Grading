package uz.hrlab.grading.gradestructure.api;

import jakarta.validation.constraints.Min;

import java.util.Map;

public record GradeRequest(
        @Min(1) int gradeNumber,
        int sortOrder,
        Map<String, String> nameI18n,
        Map<String, String> descriptionI18n
) { }

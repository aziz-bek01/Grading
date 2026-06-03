package uz.hrlab.grading.gradestructure.api;

import java.util.Map;

public record UpdateGradeStructureMetadataRequest(
        Map<String, String> nameI18n,
        Map<String, String> descriptionI18n
) { }

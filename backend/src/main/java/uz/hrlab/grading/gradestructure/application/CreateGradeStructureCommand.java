package uz.hrlab.grading.gradestructure.application;

import uz.hrlab.grading.gradestructure.domain.GradeBandGapPolicy;
import uz.hrlab.grading.gradestructure.domain.GradeStructureType;

import java.util.Map;
import java.util.UUID;

public record CreateGradeStructureCommand(
        UUID projectId,
        String code,
        Map<String, String> nameI18n,
        Map<String, String> descriptionI18n,
        GradeStructureType structureType,
        GradeBandGapPolicy gapPolicy
) { }

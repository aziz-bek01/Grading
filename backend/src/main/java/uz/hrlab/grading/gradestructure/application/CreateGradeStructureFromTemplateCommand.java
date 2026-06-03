package uz.hrlab.grading.gradestructure.application;

import uz.hrlab.grading.gradestructure.domain.GradeBandGapPolicy;

import java.util.Map;
import java.util.UUID;

public record CreateGradeStructureFromTemplateCommand(
        String templateCode,
        UUID projectId,
        String code,
        Map<String, String> nameI18n,
        Map<String, String> descriptionI18n,
        GradeBandGapPolicy gapPolicy
) { }

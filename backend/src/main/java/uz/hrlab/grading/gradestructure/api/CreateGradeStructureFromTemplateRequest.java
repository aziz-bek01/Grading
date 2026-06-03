package uz.hrlab.grading.gradestructure.api;

import jakarta.validation.constraints.NotBlank;
import uz.hrlab.grading.gradestructure.domain.GradeBandGapPolicy;

import java.util.Map;
import java.util.UUID;

public record CreateGradeStructureFromTemplateRequest(
        @NotBlank String templateCode,
        UUID projectId,
        @NotBlank String code,
        Map<String, String> nameI18n,
        Map<String, String> descriptionI18n,
        GradeBandGapPolicy gapPolicy
) { }

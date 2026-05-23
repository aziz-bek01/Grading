package uz.hrlab.grading.position.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public record UpdatePositionCommand(
        UUID departmentId,
        Map<@NotBlank String, @NotBlank @Size(max = 500) String> titleI18n,
        @Size(max = 200) String function,
        @Size(max = 100) String category,
        @Size(max = 100) String jobFamily,
        @Size(max = 100) String jobLevel
) { }

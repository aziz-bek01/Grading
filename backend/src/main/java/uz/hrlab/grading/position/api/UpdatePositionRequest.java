package uz.hrlab.grading.position.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public record UpdatePositionRequest(
        UUID departmentId,
        Map<@NotBlank String, @NotBlank @Size(max = 500) String> titleI18n,
        @Size(max = 200) String function,
        @Size(max = 100) String category,
        @Size(max = 100) String jobFamily,
        @Size(max = 100) String jobLevel
) { }

package uz.hrlab.grading.position.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public record CreatePositionRequest(
        @NotNull UUID projectId,
        @NotNull UUID departmentId,
        @NotBlank @Pattern(regexp = "^[A-Z0-9][A-Z0-9_-]{0,63}$") String code,
        @NotNull @NotEmpty Map<@NotBlank String, @NotBlank @Size(max = 500) String> titleI18n,
        @Size(max = 200) String function,
        @Size(max = 100) String category,
        @Size(max = 100) String jobFamily,
        @Size(max = 100) String jobLevel
) { }

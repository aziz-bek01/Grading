package uz.hrlab.grading.project.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Map;

public record CreateProjectRequest(
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_-]{1,63}$") String code,
        @NotNull @NotEmpty Map<@NotBlank String, @NotBlank @Size(max = 500) String> nameI18n,
        @Size(max = 2000) String description,
        LocalDate startDate,
        LocalDate endDate
) { }

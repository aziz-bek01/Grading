package uz.hrlab.grading.project.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Map;

/**
 * Partial update — only fields explicitly set are mutated (no mass-assignment).
 * {@code null} field => leave existing value untouched.
 */
public record UpdateProjectCommand(
        Map<@NotBlank String, @NotBlank @Size(max = 500) String> nameI18n,
        @Size(max = 2000) String description,
        LocalDate startDate,
        LocalDate endDate
) { }

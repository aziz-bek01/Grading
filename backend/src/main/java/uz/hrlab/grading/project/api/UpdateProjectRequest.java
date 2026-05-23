package uz.hrlab.grading.project.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Map;

public record UpdateProjectRequest(
        Map<@NotBlank String, @NotBlank @Size(max = 500) String> nameI18n,
        @Size(max = 2000) String description,
        LocalDate startDate,
        LocalDate endDate
) { }

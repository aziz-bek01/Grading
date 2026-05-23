package uz.hrlab.grading.jobanalysis.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ArchiveQuestionnaireRequest(
        @NotBlank @Size(min = 10, max = 1000) String reason
) { }

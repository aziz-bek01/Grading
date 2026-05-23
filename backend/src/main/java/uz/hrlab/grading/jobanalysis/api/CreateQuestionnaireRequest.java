package uz.hrlab.grading.jobanalysis.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateQuestionnaireRequest(
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$") String templateCode
) { }

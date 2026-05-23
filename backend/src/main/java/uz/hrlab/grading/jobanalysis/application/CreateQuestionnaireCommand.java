package uz.hrlab.grading.jobanalysis.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record CreateQuestionnaireCommand(
        @NotNull UUID positionId,
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$") String templateCode
) { }

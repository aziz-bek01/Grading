package uz.hrlab.grading.evaluation.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpsertScoreRequest(
        @NotNull UUID factorId,
        @NotNull UUID factorLevelId,
        @Size(max = 4000) String comment
) {
}

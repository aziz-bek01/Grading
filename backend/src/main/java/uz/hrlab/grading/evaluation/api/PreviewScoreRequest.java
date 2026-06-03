package uz.hrlab.grading.evaluation.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record PreviewScoreRequest(
        @NotNull UUID methodologyVersionId,
        @Valid List<Selection> selections
) {
    public record Selection(
            @NotNull UUID factorId,
            @NotNull UUID factorLevelId
    ) { }
}

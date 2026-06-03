package uz.hrlab.grading.evaluation.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CalibrateScoreRequest(
        @NotNull UUID factorId,
        @NotNull
        @DecimalMin(value = "0", inclusive = true, message = "newRawFactorScore must be non-negative")
        BigDecimal newRawFactorScore,
        @NotNull @Size(min = 20, max = 4000,
                message = "reason must be at least 20 characters")
        String reason
) {
}

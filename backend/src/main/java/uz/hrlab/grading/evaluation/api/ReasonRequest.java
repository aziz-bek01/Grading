package uz.hrlab.grading.evaluation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReasonRequest(
        @NotBlank @Size(min = 5, max = 4000) String reason
) {
}

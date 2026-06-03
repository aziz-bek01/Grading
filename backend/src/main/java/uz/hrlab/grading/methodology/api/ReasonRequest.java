package uz.hrlab.grading.methodology.api;

import jakarta.validation.constraints.NotBlank;

/** Common body for archive endpoints. */
public record ReasonRequest(@NotBlank String reason) { }

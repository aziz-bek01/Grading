package uz.hrlab.grading.jobprofile.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body envelope for actions that require an explicit, audit-logged reason. */
public record ReasonRequest(
        @NotBlank @Size(min = 10, max = 1000) String reason
) { }

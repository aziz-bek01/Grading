package uz.hrlab.grading.gradestructure.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReasonRequest(@NotBlank @Size(min = 20, max = 2000) String reason) { }

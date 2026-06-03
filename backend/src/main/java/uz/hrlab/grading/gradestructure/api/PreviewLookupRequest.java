package uz.hrlab.grading.gradestructure.api;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PreviewLookupRequest(@NotNull BigDecimal score) { }

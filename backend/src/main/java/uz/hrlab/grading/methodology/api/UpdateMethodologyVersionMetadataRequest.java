package uz.hrlab.grading.methodology.api;

import uz.hrlab.grading.methodology.domain.ScoringMode;

import java.math.BigDecimal;

/**
 * Partial-update body for {@code PATCH /api/v1/methodology-versions/{id}}.
 * Both fields are nullable — a {@code null} leaves the corresponding column
 * unchanged. Serialized as {@code scoring_mode} / {@code target_total_points}
 * (global SNAKE_CASE strategy). Edits apply only to DRAFT versions.
 */
public record UpdateMethodologyVersionMetadataRequest(
        ScoringMode scoringMode,
        BigDecimal targetTotalPoints
) { }

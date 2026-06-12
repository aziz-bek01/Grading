package uz.hrlab.grading.evaluation.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Panel detail = the panel summary + its roster + progress (N/M). Blind-safe:
 * the roster carries STATUS only, never scores (REQ-ISO-5). Averaged totals +
 * per-factor breakdown live in {@link PanelResultResponse}, gated separately.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PanelDetailResponse(
        PanelResponse panel,
        List<PanelAssignmentResponse> roster,
        int completedCount,
        int totalCount
) { }

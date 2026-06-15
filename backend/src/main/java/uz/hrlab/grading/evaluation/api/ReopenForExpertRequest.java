package uz.hrlab.grading.evaluation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Feature 2 — body for {@code POST /api/v1/panels/{panelId}/reopen-for-expert}.
 * Reopen an APPROVED panel to add an additional expert. snake_case on the wire
 * via the global naming strategy ({@code additional_evaluator_user_id},
 * {@code reason}). The panel id comes from the path; tenant/actor are derived
 * server-side from the security context (never a client param).
 */
public record ReopenForExpertRequest(
        @NotNull UUID additionalEvaluatorUserId,
        @NotBlank @Size(min = 5) String reason
) { }

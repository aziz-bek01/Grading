package uz.hrlab.grading.evaluation.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.hrlab.grading.evaluation.domain.EvaluatorRole;

import java.util.List;
import java.util.UUID;

/**
 * Bulk-create-panels body (BE-1/BE-3) — set up a whole department commission in
 * ONE call: one shared {@code roster} applied to every position in
 * {@code positionIds}, all scored against the same {@code methodologyVersionId}.
 *
 * <p>Each {@code positionId} row is routed server-side through the EXISTING
 * single-panel {@code CreatePanelUseCase} + {@code AssignEvaluatorUseCase} so
 * every per-row security check (permission, tenant scoping, version-status
 * guard, ABAC department write-gate, one-active-panel uniqueness) and the
 * per-panel / per-seat audit fire UNCHANGED. NO create/assign logic is
 * duplicated here.
 *
 * <p>The min-3 mandatory-trio rule is NOT enforced at create — bulk create only
 * assigns the seats the admin provided and leaves panels in COLLECTING; the
 * roster lock (LockRosterUseCase) remains the single enforcement point. So a
 * partial / not-yet-complete roster is allowed at this stage.
 *
 * <p>{@code positionIds} is {@code @NotEmpty} and capped at
 * {@link PanelController#MAX_PAGE_SIZE} (200) per call (same cap as bulk-create
 * evaluations). Snake_case on the wire via global Jackson SNAKE_CASE:
 * {@code methodology_version_id}, {@code position_ids}, {@code roster},
 * {@code evaluator_user_id}, {@code evaluator_role}.
 */
public record BulkCreatePanelsRequest(
        @NotNull UUID methodologyVersionId,

        @NotEmpty(message = "position_ids must contain at least one position")
        @Size(max = PanelController.MAX_PAGE_SIZE,
                message = "position_ids capped at " + PanelController.MAX_PAGE_SIZE + " per call")
        List<UUID> positionIds,

        @Valid
        List<RosterSeat> roster
) {
    /** One shared roster seat — the same user+role applied to every panel. */
    public record RosterSeat(
            @NotNull UUID evaluatorUserId,
            @NotNull EvaluatorRole evaluatorRole
    ) {
    }
}

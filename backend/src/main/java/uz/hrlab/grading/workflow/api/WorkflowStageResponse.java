package uz.hrlab.grading.workflow.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import uz.hrlab.grading.workflow.domain.ProjectWorkflowStage;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Function;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowStageResponse(
        UUID id,
        String stage,
        String status,
        BigDecimal completionPercent,
        UUID responsibleUserId,
        // BE-10 (SWEEP) — server-resolved display name for responsibleUserId.
        // Wire key: responsible_user_name. UUID kept for traceability; name is
        // NON_NULL (omitted when unknown / non-member). Mirrors the
        // MethodologyVersionResponse approvedByName / lockedByName pattern.
        String responsibleUserName,
        OffsetDateTime lastUpdatedAt,
        UUID lastUpdatedBy,
        // BE-10 (SWEEP) — server-resolved display name for lastUpdatedBy.
        // Wire key: last_updated_by_name. NON_NULL.
        String lastUpdatedByName,
        int sortOrder
) {
    /** Legacy / no-name overload (callers that do not resolve actor names). */
    public static WorkflowStageResponse from(ProjectWorkflowStage s) {
        return from(s, id -> null);
    }

    /**
     * BE-10 — resolved-name overload. {@code nameFn} maps a tenant-derived user
     * id to a display name (batch-resolved by the caller via the shared
     * {@code ActorNameResolver}); null → field omitted on the wire.
     */
    public static WorkflowStageResponse from(ProjectWorkflowStage s, Function<UUID, String> nameFn) {
        return new WorkflowStageResponse(
                s.id(), s.stage().name(), s.status().name(),
                s.completionPercent(), s.responsibleUserId(),
                nameFn == null ? null : nameFn.apply(s.responsibleUserId()),
                s.lastUpdatedAt(), s.lastUpdatedBy(),
                nameFn == null ? null : nameFn.apply(s.lastUpdatedBy()),
                s.sortOrder());
    }
}

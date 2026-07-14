package uz.hrlab.grading.approval.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import uz.hrlab.grading.approval.domain.ApprovalRequest;
import uz.hrlab.grading.approval.domain.ApprovalStep;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApprovalRequestResponse(
        UUID id,
        UUID projectId,
        String entityType,
        UUID entityId,
        // BE-3 — i18n human label for the wrapped entity (4 locales). Wire key:
        // entity_label_i18n. NON_NULL: omitted when the entity is missing /
        // cross-tenant / unlabellable (FE falls back to a short id).
        Map<String, String> entityLabelI18n,
        // Bug 2 — i18n human label for the approval's PROJECT (project name_i18n,
        // 4 locales). Wire key: project_label_i18n (same _i18n suffix shape as
        // entity_label_i18n). Lets the GLOBAL cross-project inbox show WHICH
        // project a card belongs to instead of a raw project_id UUID. NON_NULL:
        // omitted when the project is missing / cross-tenant (FE falls back).
        Map<String, String> projectLabelI18n,
        UUID requestedBy,
        // BE-3 — server-resolved display name for requestedBy. Wire key:
        // initiated_by_name. NON_NULL: omitted when unknown / non-member.
        String initiatedByName,
        OffsetDateTime requestedAt,
        String currentStatus,
        Map<String, String> notesI18n,
        List<ApprovalStepResponse> steps
) {
    /**
     * Write-path / legacy overload — no enrichment (create, approve, reject,
     * request-changes, cancel). Enrichment fields are omitted (NON_NULL) so the
     * write responses stay lean; the FE re-fetches the detail for the enriched
     * view.
     */
    public static ApprovalRequestResponse from(ApprovalRequest r) {
        return new ApprovalRequestResponse(
                r.id(), r.projectId(), r.entityType().name(), r.entityId(),
                null, null, r.requestedBy(), null, r.requestedAt(), r.currentStatus().name(),
                r.notesI18n(),
                r.steps().stream().map(ApprovalStepResponse::from).toList());
    }

    /**
     * Enriched READ overload (BE-3 / BE-5). {@code entityLabelI18n} +
     * {@code initiatedByName} populate the request-level fields;
     * {@code stepNameFn} maps each step approver/decider id to a name (already
     * batch-resolved by the caller — no per-row queries here).
     *
     * @param entityLabelI18n  i18n label map (may be {@code null} → omitted)
     * @param projectLabelI18n i18n project-name map (may be {@code null} → omitted)
     * @param initiatedByName  initiator display name (may be {@code null} → omitted)
     * @param stepNameFn       id → name function for approver / decider resolution
     */
    public static ApprovalRequestResponse from(ApprovalRequest r,
                                               Map<String, String> entityLabelI18n,
                                               Map<String, String> projectLabelI18n,
                                               String initiatedByName,
                                               Function<UUID, String> stepNameFn) {
        List<ApprovalStepResponse> steps = r.steps().stream()
                .map(s -> toStep(s, stepNameFn))
                .toList();
        return new ApprovalRequestResponse(
                r.id(), r.projectId(), r.entityType().name(), r.entityId(),
                entityLabelI18n, projectLabelI18n, r.requestedBy(), initiatedByName,
                r.requestedAt(), r.currentStatus().name(), r.notesI18n(), steps);
    }

    private static ApprovalStepResponse toStep(ApprovalStep s, Function<UUID, String> stepNameFn) {
        String approverName = stepNameFn == null ? null : stepNameFn.apply(s.approverUserId());
        String decidedByName = stepNameFn == null ? null : stepNameFn.apply(s.decidedBy());
        return ApprovalStepResponse.from(s, approverName, decidedByName);
    }
}

package uz.hrlab.grading.approval.domain;

/** Canonical entity types that may be wrapped in an approval request (MVP 2). */
public enum ApprovalEntityType {
    METHODOLOGY_VERSION,
    EVALUATION,
    JOB_PROFILE,
    GRADE_STRUCTURE,
    /**
     * MVP2 multi-evaluator — a panel's averaged result submitted for the CEO
     * sign-off step. The approval target is the panel (entity_id = panel.id),
     * gated by {@code EVALUATION_PANEL_APPROVE} (REQ-CEO-1/-2/-3).
     */
    EVALUATION_PANEL
}

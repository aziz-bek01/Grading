package uz.hrlab.grading.position.domain;

/**
 * Position lifecycle (Phase 2 — limited subset).
 * Future phases extend to EVALUATED, APPROVED via the workflow module.
 */
public enum PositionStatus {
    DRAFT,
    ACTIVE,
    ARCHIVED
}

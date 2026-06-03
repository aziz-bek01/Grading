package uz.hrlab.grading.reporting.domain;

/**
 * Lifecycle of a {@code Report} aggregate (mirrors ExportJob 8-status FSM —
 * integration-blueprint §9.1, architecture §17 / ADR-009).
 */
public enum ReportStatus {
    REQUESTED,
    QUEUED,
    GENERATING,
    GENERATED,
    FAILED,
    DOWNLOADED,
    EXPIRED,
    CANCELLED
}

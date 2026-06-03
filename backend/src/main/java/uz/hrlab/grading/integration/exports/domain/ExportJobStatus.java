package uz.hrlab.grading.integration.exports.domain;

/** Lifecycle of an export job (integration-blueprint §9.1, 8 statuses). */
public enum ExportJobStatus {
    REQUESTED,
    QUEUED,
    GENERATING,
    GENERATED,
    FAILED,
    DOWNLOADED,
    EXPIRED,
    CANCELLED
}

package uz.hrlab.grading.jobanalysis.domain;

/**
 * Symbolic transition actions for {@link QuestionnaireStatus}
 * (D-308 + F-301 — symmetry with JobProfileTransition).
 */
public enum QuestionnaireTransition {
    /** First answer saved — DRAFT → IN_PROGRESS. */
    START_ANSWERING,
    /** Required answers met — DRAFT|IN_PROGRESS → COMPLETED. */
    SUBMIT,
    /** Any non-archived state → ARCHIVED (reason required). */
    ARCHIVE
}

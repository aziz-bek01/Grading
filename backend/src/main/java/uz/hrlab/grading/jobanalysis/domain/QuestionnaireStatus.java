package uz.hrlab.grading.jobanalysis.domain;

/**
 * Job analysis questionnaire lifecycle (PRD §E7 + D-308 alignment).
 *
 * <pre>
 *   DRAFT        → IN_PROGRESS (first answer saved)
 *   DRAFT        → ARCHIVED    (reason)
 *   IN_PROGRESS  → COMPLETED   (submit; required answered)
 *   IN_PROGRESS  → ARCHIVED    (reason)
 *   COMPLETED    → ARCHIVED    (reason)
 *   ARCHIVED     → ∅           (terminal)
 * </pre>
 *
 * <p>Frontend and backend keep this set strictly in sync — D-308 was the drift
 * remediation that introduced {@code IN_PROGRESS} server-side.
 */
public enum QuestionnaireStatus {
    DRAFT,
    IN_PROGRESS,
    COMPLETED,
    ARCHIVED
}

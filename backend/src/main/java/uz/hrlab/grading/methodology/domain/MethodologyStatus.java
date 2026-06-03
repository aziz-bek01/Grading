package uz.hrlab.grading.methodology.domain;

/**
 * Container-level status of a {@link Methodology}. The version is where the
 * DRAFT/APPROVED/LOCKED state machine lives; the container itself is just
 * {@code ACTIVE} or {@code ARCHIVED}.
 */
public enum MethodologyStatus {
    ACTIVE,
    ARCHIVED
}

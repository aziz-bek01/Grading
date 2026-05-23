package uz.hrlab.grading.jobprofile.domain;

/**
 * Job profile lifecycle (Phase 3 — PRD §E6, architecture §14 row "Job analysis").
 *
 * <ul>
 *   <li>{@link #DRAFT}        — authorable; editable</li>
 *   <li>{@link #UNDER_REVIEW} — submitted for approval; no edits</li>
 *   <li>{@link #APPROVED}     — immutable historical record; edit ⇒ new revision</li>
 *   <li>{@link #ARCHIVED}     — terminal; immutable</li>
 * </ul>
 */
public enum JobProfileStatus {
    DRAFT,
    UNDER_REVIEW,
    APPROVED,
    ARCHIVED
}

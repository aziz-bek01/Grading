package uz.hrlab.grading.access.application;

/**
 * Tri-state ABAC decision returned by every {@link AbacPolicy} /
 * scope policy. {@code NOT_APPLICABLE} means "this policy has nothing
 * to say"; the caller composes policies and DENIES if any returns
 * {@code DENY}, otherwise permits when at least one returns
 * {@code PERMIT} (security-blueprint §7).
 */
public enum PolicyDecision {
    PERMIT,
    DENY,
    NOT_APPLICABLE;

    public boolean isDeny() { return this == DENY; }
    public boolean isPermit() { return this == PERMIT; }
}

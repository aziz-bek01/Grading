package uz.hrlab.grading.methodology.domain;

/**
 * Methodology-version state machine
 * (architecture §9 + §14 + ADR-002: approved methodology is immutable).
 *
 * <pre>
 *  DRAFT     → APPROVED   (validation: weights + locales + ≥1 factor + ≥2 levels)
 *  DRAFT     → ARCHIVED
 *  APPROVED  → LOCKED     (production lock; permission METHODOLOGY_LOCK)
 *  APPROVED  → ARCHIVED
 *  APPROVED  → (new DRAFT) — CREATE_NEW_VERSION (separate row; source unchanged)
 *  LOCKED    → ARCHIVED
 *  ARCHIVED  → ∅
 * </pre>
 *
 * <p>Edit on APPROVED / LOCKED / ARCHIVED is FORBIDDEN — must call
 * {@code CreateMethodologyVersionUseCase}.
 */
public enum MethodologyVersionStatus {
    DRAFT,
    APPROVED,
    LOCKED,
    ARCHIVED
}

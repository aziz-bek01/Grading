package uz.hrlab.grading.reporting.domain;

/**
 * Canonical report types (architecture §17 — 6 dashboards / report types).
 *
 * <p>MVP 2 Phase 3 ships three fully implemented templates ({@link #GRADE_DISTRIBUTION},
 * {@link #POSITION_CATALOG}, {@link #METHODOLOGY_SPEC}); the remaining three
 * return a "Coming in MVP 3" placeholder so the UI surface is complete.
 */
public enum ReportType {
    GRADE_DISTRIBUTION,
    POSITION_CATALOG,
    EVALUATION_SUMMARY,
    METHODOLOGY_SPEC,
    AUDIT_SUMMARY,
    EXECUTIVE_SUMMARY
}

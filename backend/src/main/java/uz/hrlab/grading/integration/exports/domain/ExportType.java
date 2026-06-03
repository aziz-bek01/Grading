package uz.hrlab.grading.integration.exports.domain;

/**
 * Canonical export types (integration-blueprint §6, 10 use cases).
 *
 * <p>Salary-bearing exports ({@code SALARY_RANGES}, {@code RED_GREEN_CIRCLE},
 * {@code COMPENSATION_SCENARIOS}) require {@code SALARY_EXPORT} permission and
 * set {@code contains_salary_data=true} on the job row.
 */
public enum ExportType {
    POSITION_CATALOG,
    JOB_PROFILES,
    METHODOLOGY,
    EVALUATION_MATRIX,
    GRADE_STRUCTURE,
    GRADE_PYRAMID,
    SALARY_RANGES,
    RED_GREEN_CIRCLE,
    COMPENSATION_SCENARIOS,
    REPORT_EXECUTIVE
}

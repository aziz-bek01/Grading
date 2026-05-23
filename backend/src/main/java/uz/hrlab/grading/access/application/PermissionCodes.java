package uz.hrlab.grading.access.application;

/**
 * Canonical permission codes (security-blueprint §7.3 + PRD permission matrix).
 *
 * <p>These are the only codes accepted by {@link PermissionService}. Keep this
 * file in sync with seed {@code 001-default-permissions.yaml} and the
 * frontend permission constants.
 */
public final class PermissionCodes {

    private PermissionCodes() { }

    // Tenant
    public static final String TENANT_READ   = "TENANT_READ";
    public static final String TENANT_CREATE = "TENANT_CREATE";
    public static final String TENANT_EDIT   = "TENANT_EDIT";

    // Project
    public static final String PROJECT_READ   = "PROJECT_READ";
    public static final String PROJECT_CREATE = "PROJECT_CREATE";
    public static final String PROJECT_EDIT   = "PROJECT_EDIT";

    // Organization
    public static final String ORG_READ = "ORG_READ";
    public static final String ORG_EDIT = "ORG_EDIT";

    // Position
    public static final String POSITION_READ   = "POSITION_READ";
    public static final String POSITION_CREATE = "POSITION_CREATE";
    public static final String POSITION_EDIT   = "POSITION_EDIT";

    // Job profile
    public static final String JOB_PROFILE_READ    = "JOB_PROFILE_READ";
    public static final String JOB_PROFILE_EDIT    = "JOB_PROFILE_EDIT";
    public static final String JOB_PROFILE_APPROVE = "JOB_PROFILE_APPROVE";

    // Job analysis (Phase 3)
    public static final String JOB_ANALYSIS_READ = "JOB_ANALYSIS_READ";
    public static final String JOB_ANALYSIS_EDIT = "JOB_ANALYSIS_EDIT";

    // Methodology
    public static final String METHODOLOGY_READ    = "METHODOLOGY_READ";
    public static final String METHODOLOGY_EDIT    = "METHODOLOGY_EDIT";
    public static final String METHODOLOGY_APPROVE = "METHODOLOGY_APPROVE";
    public static final String METHODOLOGY_LOCK    = "METHODOLOGY_LOCK";

    // Evaluation
    public static final String EVALUATION_READ    = "EVALUATION_READ";
    public static final String EVALUATION_EDIT    = "EVALUATION_EDIT";
    public static final String EVALUATION_APPROVE = "EVALUATION_APPROVE";

    // Grade
    public static final String GRADE_READ = "GRADE_READ";
    public static final String GRADE_EDIT = "GRADE_EDIT";

    // Salary — defined now, granted to no role in MVP 1.
    public static final String SALARY_VIEW         = "SALARY_VIEW";
    public static final String SALARY_EDIT         = "SALARY_EDIT";
    public static final String SALARY_EXPORT       = "SALARY_EXPORT";
    public static final String SALARY_SCENARIO_RUN = "SALARY_SCENARIO_RUN";

    // Reporting
    public static final String REPORT_READ   = "REPORT_READ";
    public static final String REPORT_CREATE = "REPORT_CREATE";
    public static final String REPORT_EXPORT = "REPORT_EXPORT";

    // Audit / access management
    public static final String AUDIT_READ          = "AUDIT_READ";
    public static final String USER_ACCESS_MANAGE  = "USER_ACCESS_MANAGE";

    // Files (foundation only — MVP 2 ships)
    public static final String FILE_UPLOAD   = "FILE_UPLOAD";
    public static final String FILE_DOWNLOAD = "FILE_DOWNLOAD";

    // AI assist (foundation — MVP 4 ships)
    public static final String AI_ASSIST_USE = "AI_ASSIST_USE";
}

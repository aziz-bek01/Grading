package uz.hrlab.grading.audit.application;

/**
 * Canonical audit action codes (architecture §8.5 + PRD audit matrix).
 *
 * <p>Use these constants as the {@code action} column value of an audit
 * record. Keep the catalog flat and stable — downstream forensics search by
 * literal string.
 */
public final class AuditAction {

    private AuditAction() { }

    // Authentication
    public static final String LOGIN_SUCCESS         = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED          = "LOGIN_FAILED";
    public static final String MFA_CHALLENGE         = "MFA_CHALLENGE";
    public static final String MFA_FAILED            = "MFA_FAILED";
    public static final String LOGOUT                = "LOGOUT";
    public static final String TENANT_CONTEXT_SWITCH = "TENANT_CONTEXT_SWITCH";

    // Tenant
    public static final String TENANT_CREATED   = "TENANT_CREATED";
    public static final String TENANT_SUSPENDED = "TENANT_SUSPENDED";
    public static final String TENANT_ARCHIVED  = "TENANT_ARCHIVED";

    // Access
    public static final String USER_INVITED       = "USER_INVITED";
    public static final String USER_ACCESS_CHANGED = "USER_ACCESS_CHANGED";
    public static final String ROLE_ASSIGNED      = "ROLE_ASSIGNED";

    // Project / org
    public static final String PROJECT_CREATED   = "PROJECT_CREATED";
    public static final String PROJECT_UPDATED   = "PROJECT_UPDATED";
    public static final String PROJECT_LOCKED    = "PROJECT_LOCKED";
    public static final String PROJECT_ARCHIVED  = "PROJECT_ARCHIVED";
    public static final String DEPARTMENT_CREATED = "DEPARTMENT_CREATED";
    public static final String DEPARTMENT_UPDATED = "DEPARTMENT_UPDATED";
    public static final String DEPARTMENT_ARCHIVED = "DEPARTMENT_ARCHIVED";

    // Position / profile
    public static final String POSITION_CREATED       = "POSITION_CREATED";
    public static final String POSITION_UPDATED       = "POSITION_UPDATED";
    public static final String POSITION_ARCHIVED      = "POSITION_ARCHIVED";

    // Job profile (Phase 3)
    public static final String JOB_PROFILE_CREATED            = "JOB_PROFILE_CREATED";
    public static final String JOB_PROFILE_UPDATED            = "JOB_PROFILE_UPDATED";
    public static final String JOB_PROFILE_SUBMITTED          = "JOB_PROFILE_SUBMITTED";
    public static final String JOB_PROFILE_CHANGES_REQUESTED  = "JOB_PROFILE_CHANGES_REQUESTED";
    public static final String JOB_PROFILE_APPROVED           = "JOB_PROFILE_APPROVED";
    public static final String JOB_PROFILE_ARCHIVED           = "JOB_PROFILE_ARCHIVED";
    public static final String JOB_PROFILE_REVISION_CREATED   = "JOB_PROFILE_REVISION_CREATED";

    // Job analysis (Phase 3)
    public static final String JOB_ANALYSIS_QUESTIONNAIRE_CREATED = "JOB_ANALYSIS_QUESTIONNAIRE_CREATED";
    public static final String JOB_ANALYSIS_ANSWER_UPDATED        = "JOB_ANALYSIS_ANSWER_UPDATED";
    public static final String JOB_ANALYSIS_SUBMITTED             = "JOB_ANALYSIS_SUBMITTED";
    public static final String JOB_ANALYSIS_ARCHIVED              = "JOB_ANALYSIS_ARCHIVED";

    // Methodology
    public static final String METHODOLOGY_CREATED  = "METHODOLOGY_CREATED";
    public static final String METHODOLOGY_UPDATED  = "METHODOLOGY_UPDATED";
    public static final String METHODOLOGY_APPROVED = "METHODOLOGY_APPROVED";
    public static final String METHODOLOGY_LOCKED   = "METHODOLOGY_LOCKED";

    // Scoring
    public static final String EVALUATION_CREATED       = "EVALUATION_CREATED";
    public static final String EVALUATION_SCORE_CHANGED = "EVALUATION_SCORE_CHANGED";
    public static final String EVALUATION_APPROVED      = "EVALUATION_APPROVED";

    // Grade
    public static final String GRADE_STRUCTURE_UPDATED = "GRADE_STRUCTURE_UPDATED";
    public static final String GRADE_ASSIGNED          = "GRADE_ASSIGNED";

    // Salary / sensitive
    public static final String SALARY_VIEWED   = "SALARY_VIEWED";
    public static final String SALARY_EXPORTED = "SALARY_EXPORTED";

    // Reports / files
    public static final String REPORT_EXPORTED       = "REPORT_EXPORTED";
    public static final String ATTACHMENT_DOWNLOADED = "ATTACHMENT_DOWNLOADED";

    // Security probing
    public static final String CROSS_TENANT_OR_PROJECT_ACCESS_ATTEMPT
            = "CROSS_TENANT_OR_PROJECT_ACCESS_ATTEMPT";
}

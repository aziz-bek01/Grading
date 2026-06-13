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

    // Client company (Sprint F, E9 — F-1 BE).
    //
    // Companion permissions to the tenant control-plane endpoints. A tenant
    // owns exactly one {@code client_companies} row (UNIQUE constraint), but
    // the access surface is separate because Client Company Admins MUST be
    // able to read/edit their own client profile (legalName, brandName,
    // industry, countryCode, taxId) without holding the heavier
    // {@code TENANT_EDIT} platform permission.
    public static final String CLIENT_LIST   = "CLIENT_LIST";
    public static final String CLIENT_VIEW   = "CLIENT_VIEW";
    public static final String CLIENT_UPDATE = "CLIENT_UPDATE";

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
    public static final String METHODOLOGY_CREATE  = "METHODOLOGY_CREATE";
    public static final String METHODOLOGY_EDIT    = "METHODOLOGY_EDIT";
    public static final String METHODOLOGY_APPROVE = "METHODOLOGY_APPROVE";
    public static final String METHODOLOGY_LOCK    = "METHODOLOGY_LOCK";
    /**
     * Super-admin-only authority to edit scoring fields (factor / level weight,
     * points, scale, etc.) on an APPROVED methodology version, in-place, without
     * cutting a new version. Already-evaluated positions are preserved
     * byte-for-byte via the per-evaluation {@code methodology_basis_snapshot}
     * (DA-1) and soft-deprecation of referenced rows (DA-2). DRAFT edits remain
     * the realm of {@link #METHODOLOGY_EDIT}; LOCKED / ARCHIVED stay immutable
     * regardless. Granted to HRLAB_SUPER_ADMIN ONLY (seed 004 invariant guard).
     */
    public static final String METHODOLOGY_EDIT_APPROVED = "METHODOLOGY_EDIT_APPROVED";

    // Evaluation
    public static final String EVALUATION_READ    = "EVALUATION_READ";
    public static final String EVALUATION_EDIT    = "EVALUATION_EDIT";
    public static final String EVALUATION_APPROVE = "EVALUATION_APPROVE";
    public static final String EVALUATION_LOCK    = "EVALUATION_LOCK";
    public static final String CALIBRATION_EDIT   = "CALIBRATION_EDIT";

    // Evaluation panel — multi-evaluator (MVP2). Kept in sync with seed 038 and
    // the frontend permission constants.
    //   EVALUATION_PANEL_MANAGE  — build roster / assign-withdraw / lock-roster /
    //                              submit-to-CEO / reopen (HR Director / PM).
    //   EVALUATION_PANEL_APPROVE — the dedicated CEO sign-off step authority,
    //                              DISTINCT from per-sheet EVALUATION_APPROVE
    //                              (REQ-CEO-2, deny-by-default).
    //   CAMPAIGN_RESULTS_VIEW    — lifts the blind to see averaged totals +
    //                              per-evaluator breakdown after AVERAGED;
    //                              EVALUATION_READ alone NEVER lifts it (REQ-ISO-3).
    public static final String EVALUATION_PANEL_MANAGE  = "EVALUATION_PANEL_MANAGE";
    public static final String EVALUATION_PANEL_APPROVE = "EVALUATION_PANEL_APPROVE";
    public static final String CAMPAIGN_RESULTS_VIEW    = "CAMPAIGN_RESULTS_VIEW";

    // Grade
    public static final String GRADE_READ = "GRADE_READ";
    public static final String GRADE_EDIT = "GRADE_EDIT";
    /** Grade structure approval — DRAFT → APPROVED transition (Phase 6). */
    public static final String GRADE_STRUCTURE_APPROVE = "GRADE_STRUCTURE_APPROVE";
    /** Grade structure lock — APPROVED → LOCKED transition (Phase 6). */
    public static final String GRADE_STRUCTURE_LOCK    = "GRADE_STRUCTURE_LOCK";

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
    public static final String AUDIT_VIEW          = "AUDIT_VIEW";
    public static final String USER_ACCESS_MANAGE  = "USER_ACCESS_MANAGE";

    // User management (MVP 1 Phase 1.5 — access admin module).
    //
    // These are fine-grained companions to the umbrella
    // {@link #USER_ACCESS_MANAGE} permission. {@code USER_INVITE},
    // {@code USER_UPDATE}, {@code USER_MEMBERSHIP_MANAGE} and {@code USER_ROLE_ASSIGN}
    // are scoped to the caller's own tenant; {@code USER_ROLE_ASSIGN_HRLAB}
    // additionally allows attaching HRLab platform roles (granted only to
    // HRLAB_SUPER_ADMIN). {@code USER_SALARY_PERMISSION_TOGGLE} is the gate
    // for flipping {@code user_tenant_memberships.salary_data_permission}
    // — granted ONLY to CLIENT_COMPANY_ADMIN; HRLab roles must NEVER hold it
    // (security-blueprint §8: HRLab consultants do not view client salary).
    public static final String USER_LIST                      = "USER_LIST";
    public static final String USER_VIEW                      = "USER_VIEW";
    public static final String USER_INVITE                    = "USER_INVITE";
    public static final String USER_UPDATE                    = "USER_UPDATE";
    public static final String USER_MEMBERSHIP_MANAGE         = "USER_MEMBERSHIP_MANAGE";
    public static final String USER_ROLE_ASSIGN               = "USER_ROLE_ASSIGN";
    public static final String USER_ROLE_ASSIGN_HRLAB         = "USER_ROLE_ASSIGN_HRLAB";
    public static final String USER_SALARY_PERMISSION_TOGGLE  = "USER_SALARY_PERMISSION_TOGGLE";

    // Files (foundation only — MVP 2 ships)
    public static final String FILE_UPLOAD   = "FILE_UPLOAD";
    public static final String FILE_DOWNLOAD = "FILE_DOWNLOAD";

    // AI assist (foundation — MVP 4 ships)
    public static final String AI_ASSIST_USE = "AI_ASSIST_USE";

    // Workflow (MVP 2 Phase 1)
    public static final String WORKFLOW_READ = "WORKFLOW_READ";
    public static final String WORKFLOW_EDIT = "WORKFLOW_EDIT";

    // Approval requests (MVP 2 Phase 1)
    public static final String APPROVAL_REQUEST_CREATE = "APPROVAL_REQUEST_CREATE";
    public static final String APPROVAL_REQUEST_DECIDE = "APPROVAL_REQUEST_DECIDE";
    public static final String APPROVAL_REQUEST_CANCEL = "APPROVAL_REQUEST_CANCEL";

    // Comments (MVP 2 Phase 1)
    public static final String COMMENT_READ   = "COMMENT_READ";
    public static final String COMMENT_CREATE = "COMMENT_CREATE";
    public static final String COMMENT_EDIT   = "COMMENT_EDIT";
    public static final String COMMENT_DELETE = "COMMENT_DELETE";

    // Integration — imports (MVP 2 Phase 2)
    public static final String ORG_IMPORT         = "ORG_IMPORT";
    public static final String POSITION_IMPORT    = "POSITION_IMPORT";
    public static final String METHODOLOGY_IMPORT = "METHODOLOGY_IMPORT";
    public static final String GRADE_IMPORT       = "GRADE_IMPORT";
    public static final String PAYROLL_IMPORT     = "PAYROLL_IMPORT";

    // Integration — generic file lifecycle (MVP 2 Phase 2)
    public static final String IMPORT_READ   = "IMPORT_READ";
    public static final String IMPORT_CANCEL = "IMPORT_CANCEL";
    public static final String EXPORT_READ   = "EXPORT_READ";
    public static final String EXPORT_REQUEST = "EXPORT_REQUEST";
}

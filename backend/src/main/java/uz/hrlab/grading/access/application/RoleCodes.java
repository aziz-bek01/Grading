package uz.hrlab.grading.access.application;

/**
 * Canonical role codes (matches seed {@code 002-default-roles.yaml}
 * and {@code role-permissions-matrix.md} §3).
 */
public final class RoleCodes {

    private RoleCodes() { }

    public static final String HRLAB_SUPER_ADMIN          = "HRLAB_SUPER_ADMIN";
    public static final String HRLAB_PROJECT_MANAGER      = "HRLAB_PROJECT_MANAGER";
    public static final String HRLAB_CONSULTANT           = "HRLAB_CONSULTANT";
    public static final String HRLAB_ANALYST              = "HRLAB_ANALYST";
    public static final String CLIENT_COMPANY_ADMIN       = "CLIENT_COMPANY_ADMIN";
    public static final String CLIENT_HR_DIRECTOR         = "CLIENT_HR_DIRECTOR";
    public static final String CLIENT_HR_SPECIALIST       = "CLIENT_HR_SPECIALIST";
    public static final String EVALUATION_COMMITTEE_MEMBER = "EVALUATION_COMMITTEE_MEMBER";
    public static final String DEPARTMENT_MANAGER         = "DEPARTMENT_MANAGER";
    public static final String VIEWER                     = "VIEWER";
    public static final String EXTERNAL_AUDITOR           = "EXTERNAL_AUDITOR";
}

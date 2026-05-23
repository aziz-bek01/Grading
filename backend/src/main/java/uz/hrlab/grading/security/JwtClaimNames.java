package uz.hrlab.grading.security;

/**
 * Custom JWT claim names emitted by the IdP for grading.hrlab.uz
 * (architecture §8.1, security-blueprint §5.1).
 */
public final class JwtClaimNames {

    private JwtClaimNames() { }

    public static final String ACTIVE_TENANT_ID    = "active_tenant_id";
    public static final String ACTIVE_PROJECT_IDS  = "active_project_ids";
    public static final String ROLES               = "roles";
    public static final String PERMISSIONS         = "permissions";
    public static final String SALARY_DATA_PERM    = "salary_data_permission";
    public static final String DEPARTMENT_SCOPE    = "department_scope";
    public static final String LOCALE              = "locale";
    public static final String EMAIL               = "email";
}

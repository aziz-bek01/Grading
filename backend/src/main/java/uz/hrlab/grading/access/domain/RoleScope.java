package uz.hrlab.grading.access.domain;

/**
 * Whether a role is defined at the platform (HRLab) or tenant (client) layer.
 * See database-blueprint §5.1 {@code roles.scope}.
 */
public enum RoleScope {
    PLATFORM,
    TENANT
}

package uz.hrlab.grading.access.application;

import uz.hrlab.grading.tenancy.application.TenantContext;

/**
 * Contract for module-specific ABAC policies (security-blueprint §7).
 *
 * <p>Implementations decide whether a specific user can act on a specific
 * object — combining RBAC permission codes with object-level attributes such
 * as {@code tenant_id}, {@code project_id}, {@code department_id},
 * methodology / evaluation status, salary-sensitivity, etc.
 *
 * @param <T> the entity (or projection) the policy reasons about
 */
public interface AbacPolicy<T> {

    boolean canRead(TenantContext ctx, T target);

    boolean canEdit(TenantContext ctx, T target);
}

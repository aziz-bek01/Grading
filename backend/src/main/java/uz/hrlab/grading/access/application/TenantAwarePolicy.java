package uz.hrlab.grading.access.application;

import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.tenancy.application.TenantContext;

import java.util.UUID;

/**
 * Convenience base for ABAC policies operating on tenant-scoped entities.
 * Subclasses still implement {@link AbacPolicy#canRead}/{@code canEdit}; this
 * class adds shared helpers (sanitized exceptions, project-scope checks).
 *
 * <p>{@link #requireSameTenant} is the canonical way to enforce
 * "object's tenant_id == ctx.tenant_id" with a 404-style exception.
 */
public abstract class TenantAwarePolicy<T> implements AbacPolicy<T> {

    /** Map of entity to its {@code tenant_id}. */
    protected abstract UUID tenantIdOf(T target);

    /** Map of entity to its {@code project_id}, or null if not project-scoped. */
    protected UUID projectIdOf(T target) {
        return null;
    }

    /** Throws if the entity is not in the active tenant. */
    protected void requireSameTenant(TenantContext ctx, T target) {
        UUID entityTenant = tenantIdOf(target);
        if (entityTenant == null || !entityTenant.equals(ctx.tenantId())) {
            throw new TenantAccessDeniedException();
        }
    }

    /**
     * Returns whether the active user is assigned to the entity's project.
     * HRLab Super Admins and Project Managers bypass project-scope (handled
     * in concrete policy).
     */
    protected boolean isInActiveProject(TenantContext ctx, T target) {
        UUID projectId = projectIdOf(target);
        if (projectId == null) return true;
        return ctx.projectIds() != null && ctx.projectIds().contains(projectId);
    }
}

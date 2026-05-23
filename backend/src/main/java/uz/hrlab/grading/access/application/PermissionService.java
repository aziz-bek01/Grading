package uz.hrlab.grading.access.application;

import org.springframework.stereotype.Service;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

/**
 * RBAC permission checks against the current {@link TenantContext}.
 *
 * <p>Use this from {@code @PreAuthorize("@permissions.has('POSITION_READ')")}
 * or call directly from policies/use-cases. ABAC checks (project/department
 * scope, methodology status, etc.) live in module-specific {@link AbacPolicy}
 * implementations.
 *
 * <p>Default-deny: if there is no active context the answer is always
 * {@code false} (never throws here — callers throw the appropriate sanitized
 * exception).
 */
@Service("permissions")
public class PermissionService {

    public boolean has(String permission) {
        TenantContext ctx = TenantContextHolder.get();
        return ctx != null && ctx.hasPermission(permission);
    }

    public boolean hasAny(String... permissions) {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx == null) return false;
        for (String p : permissions) {
            if (ctx.hasPermission(p)) return true;
        }
        return false;
    }

    public boolean hasAll(String... permissions) {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx == null) return false;
        for (String p : permissions) {
            if (!ctx.hasPermission(p)) return false;
        }
        return true;
    }

    public boolean hasRole(String role) {
        TenantContext ctx = TenantContextHolder.get();
        return ctx != null && ctx.hasRole(role);
    }

    /** Salary gate — see security-blueprint §8 and architecture §8.4. */
    public boolean canViewSalary() {
        TenantContext ctx = TenantContextHolder.get();
        return ctx != null && ctx.salaryPermission() && ctx.hasPermission(PermissionCodes.SALARY_VIEW);
    }
}

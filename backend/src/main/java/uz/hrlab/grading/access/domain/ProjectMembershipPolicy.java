package uz.hrlab.grading.access.domain;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.access.application.AbacRequest;
import uz.hrlab.grading.access.application.PolicyDecision;
import uz.hrlab.grading.access.application.RoleCodes;
import uz.hrlab.grading.access.application.ScopePolicy;
import uz.hrlab.grading.tenancy.application.TenantContext;

import java.util.UUID;

/**
 * F-06 ABAC backlog: ensures the requested {@code projectId} is in the
 * caller's {@code TenantContext.activeProjectIds} set
 * (security-blueprint §7, architecture §8.2 example).
 *
 * <p>Bypass set (must see EVERY project within their tenant scope without
 * explicit per-project assignment — per {@code role-permissions-matrix.md} §3):
 * <ul>
 *   <li>{@code HRLAB_SUPER_ADMIN} — platform-wide</li>
 *   <li>{@code HRLAB_PROJECT_MANAGER} — multi-tenant by design</li>
 *   <li>{@code CLIENT_COMPANY_ADMIN} — TENANT-scoped role (F-201)</li>
 *   <li>{@code CLIENT_HR_DIRECTOR} — TENANT-scoped role (F-201)</li>
 * </ul>
 * Tenant boundary is still enforced upstream by the tenant filter on
 * {@code findByIdAndTenantId}, so a cross-tenant project still resolves to
 * 404 — these bypass entries only short-circuit the per-project membership
 * check inside the active tenant.
 */
@Component
public class ProjectMembershipPolicy implements ScopePolicy {

    @Override
    public String name() { return "ProjectMembershipPolicy"; }

    @Override
    public PolicyDecision evaluate(AbacRequest req) {
        TenantContext ctx = req.context();
        UUID projectId = req.projectId();
        if (projectId == null) {
            return PolicyDecision.NOT_APPLICABLE;
        }
        if (isTenantWideBypass(ctx)) {
            return PolicyDecision.PERMIT;
        }
        if (ctx.projectIds() == null || ctx.projectIds().isEmpty()) {
            // No explicit assignment — fail closed for project-scoped roles.
            return PolicyDecision.DENY;
        }
        return ctx.projectIds().contains(projectId)
                ? PolicyDecision.PERMIT
                : PolicyDecision.DENY;
    }

    private static boolean isTenantWideBypass(TenantContext ctx) {
        return ctx.hasRole(RoleCodes.HRLAB_SUPER_ADMIN)
                || ctx.hasRole(RoleCodes.HRLAB_PROJECT_MANAGER)
                || ctx.hasRole(RoleCodes.CLIENT_COMPANY_ADMIN)
                || ctx.hasRole(RoleCodes.CLIENT_HR_DIRECTOR);
    }
}

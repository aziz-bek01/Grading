package uz.hrlab.grading.access.domain;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.access.application.AbacRequest;
import uz.hrlab.grading.access.application.PolicyDecision;
import uz.hrlab.grading.access.application.RoleCodes;
import uz.hrlab.grading.access.application.ScopePolicy;
import uz.hrlab.grading.tenancy.application.TenantContext;

import java.util.UUID;

/**
 * F-06 ABAC backlog: Department-scope policy for the
 * {@code DEPARTMENT_MANAGER} role.
 *
 * <p>Logic:
 * <ul>
 *   <li>HRLab roles (Super Admin / PM / Consultant / Analyst) and Client HR
 *       Director / Client Company Admin bypass — full visibility within their
 *       tenant.</li>
 *   <li>For Department Manager: target's {@code departmentId} must belong to
 *       the user's {@code TenantContext.departmentScope} set, else
 *       {@code DENY}.</li>
 *   <li>If the request has no {@code departmentId} (entity is not
 *       department-scoped), the policy is {@code NOT_APPLICABLE}.</li>
 * </ul>
 */
@Component
public class DepartmentScopePolicy implements ScopePolicy {

    @Override
    public String name() { return "DepartmentScopePolicy"; }

    @Override
    public PolicyDecision evaluate(AbacRequest req) {
        TenantContext ctx = req.context();
        if (req.departmentId() == null) {
            return PolicyDecision.NOT_APPLICABLE;
        }
        if (isBypass(ctx)) {
            return PolicyDecision.PERMIT;
        }
        if (!ctx.hasRole(RoleCodes.DEPARTMENT_MANAGER)) {
            return PolicyDecision.NOT_APPLICABLE;
        }
        UUID departmentId = req.departmentId();
        if (ctx.departmentScope() == null || !ctx.departmentScope().contains(departmentId)) {
            return PolicyDecision.DENY;
        }
        return PolicyDecision.PERMIT;
    }

    private boolean isBypass(TenantContext ctx) {
        return ctx.hasRole(RoleCodes.HRLAB_SUPER_ADMIN)
                || ctx.hasRole(RoleCodes.HRLAB_PROJECT_MANAGER)
                || ctx.hasRole(RoleCodes.HRLAB_CONSULTANT)
                || ctx.hasRole(RoleCodes.HRLAB_ANALYST)
                || ctx.hasRole(RoleCodes.CLIENT_COMPANY_ADMIN)
                || ctx.hasRole(RoleCodes.CLIENT_HR_DIRECTOR);
    }
}

package uz.hrlab.grading.access.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;

/**
 * F-06 ABAC backlog: ensures an {@code HRLAB_CONSULTANT} user is actually
 * assigned to the active tenant via {@code public.user_tenant_memberships}.
 *
 * <p>Lives in the application layer (not domain) because it consults the
 * infrastructure layer's repository — the ArchUnit
 * {@code domainMustNotDependOnInfrastructure} rule forbids domain↦infra.
 *
 * <p>Bypass: Super Admin + Project Manager (they can hop tenants by design).
 */
@Component
public class ConsultantTenantAssignmentPolicy implements ScopePolicy {

    private final UserTenantMembershipRepository memberships;

    public ConsultantTenantAssignmentPolicy(UserTenantMembershipRepository memberships) {
        this.memberships = memberships;
    }

    @Override
    public String name() { return "ConsultantTenantAssignmentPolicy"; }

    @Override
    public PolicyDecision evaluate(AbacRequest req) {
        TenantContext ctx = req.context();
        if (ctx == null || ctx.tenantId() == null || ctx.userId() == null) {
            return PolicyDecision.NOT_APPLICABLE;
        }
        if (ctx.hasRole(RoleCodes.HRLAB_SUPER_ADMIN)
                || ctx.hasRole(RoleCodes.HRLAB_PROJECT_MANAGER)) {
            return PolicyDecision.PERMIT;
        }
        if (!ctx.hasRole(RoleCodes.HRLAB_CONSULTANT)
                && !ctx.hasRole(RoleCodes.HRLAB_ANALYST)) {
            return PolicyDecision.NOT_APPLICABLE;
        }
        // F-205 — prefer the per-request membership cache populated by
        // TenantContextFilter; fall back to a single repo lookup if the
        // context was built without it (unit tests).
        boolean assigned = ctx.tenantMemberships() != null
                ? ctx.tenantMemberships().contains(ctx.tenantId())
                : memberships.existsByUserIdAndTenantId(ctx.userId(), ctx.tenantId());
        return assigned ? PolicyDecision.PERMIT : PolicyDecision.DENY;
    }
}

package uz.hrlab.grading.access.domain;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.access.application.AbacRequest;
import uz.hrlab.grading.access.application.PolicyDecision;
import uz.hrlab.grading.access.application.RoleCodes;
import uz.hrlab.grading.access.application.ScopePolicy;
import uz.hrlab.grading.tenancy.application.TenantContext;

import java.util.Set;

/**
 * F-06 ABAC backlog: Viewer / External Auditor roles see only "approved"
 * entities (no drafts).
 *
 * <p>The notion of "approved" varies per resource:
 * <ul>
 *   <li>Project: {@code ACTIVE} or {@code LOCKED}</li>
 *   <li>Department: {@code ACTIVE}</li>
 *   <li>Position: {@code ACTIVE}</li>
 * </ul>
 *
 * <p>Other roles: {@code NOT_APPLICABLE} — they may legitimately see drafts.
 */
@Component
public class ApprovedEntityFilterPolicy implements ScopePolicy {

    private static final Set<String> APPROVED_STATUSES =
            Set.of("ACTIVE", "LOCKED", "APPROVED");

    @Override
    public String name() { return "ApprovedEntityFilterPolicy"; }

    @Override
    public PolicyDecision evaluate(AbacRequest req) {
        TenantContext ctx = req.context();
        if (!ctx.hasRole(RoleCodes.VIEWER) && !ctx.hasRole(RoleCodes.EXTERNAL_AUDITOR)) {
            return PolicyDecision.NOT_APPLICABLE;
        }
        // Higher-privilege roles bundled in this user override the filter.
        if (ctx.hasRole(RoleCodes.HRLAB_SUPER_ADMIN)
                || ctx.hasRole(RoleCodes.HRLAB_PROJECT_MANAGER)) {
            return PolicyDecision.PERMIT;
        }
        if (req.status() == null) {
            return PolicyDecision.NOT_APPLICABLE;
        }
        return APPROVED_STATUSES.contains(req.status().toUpperCase())
                ? PolicyDecision.PERMIT
                : PolicyDecision.DENY;
    }
}

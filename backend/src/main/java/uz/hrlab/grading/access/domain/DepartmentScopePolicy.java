package uz.hrlab.grading.access.domain;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.access.application.AbacRequest;
import uz.hrlab.grading.access.application.PolicyDecision;
import uz.hrlab.grading.access.application.RoleCodes;
import uz.hrlab.grading.access.application.ScopePolicy;
import uz.hrlab.grading.tenancy.application.TenantContext;

import java.util.UUID;

/**
 * F-06 ABAC backlog (extended by E4-S2/S3): Department-scope policy for the
 * two department-scoped roles — {@code DEPARTMENT_MANAGER} (the "department
 * director") and {@code EVALUATION_COMMITTEE_MEMBER} (the "evaluation
 * expert").
 *
 * <p>Logic:
 * <ul>
 *   <li>HRLab roles (Super Admin / PM / Consultant / Analyst) and Client HR
 *       Director / Client Company Admin bypass — full visibility within their
 *       tenant ({@link #isTenantWideBypass(TenantContext)}).</li>
 *   <li>For a department-scoped role: target's {@code departmentId} must belong
 *       to the user's {@code TenantContext.departmentScope} (subtree-closed)
 *       set, else {@code DENY}. An EMPTY scope ⇒ DENY (fail-closed: a
 *       department-scoped user who has been assigned no department can act on
 *       nothing).</li>
 *   <li>If the request has no {@code departmentId} (entity is not
 *       department-scoped), the policy is {@code NOT_APPLICABLE}.</li>
 * </ul>
 *
 * <p>SECURITY (E4-S2/S3): the role classification below is the SINGLE SOURCE OF
 * TRUTH for "this caller's reads and writes are confined to their assigned
 * department subtree". The list-query filter ({@code FindPositionQuery} /
 * {@code EvaluationQueries}) and the evaluation write gate must consult
 * {@link #isDepartmentScoped(TenantContext)} / {@link #isTenantWideBypass(TenantContext)}
 * here rather than re-listing role codes — keeping the bypass and the scoped
 * set defined in exactly one place.
 */
@Component
public class DepartmentScopePolicy implements ScopePolicy {

    /**
     * Single source of truth for the role code of the "department director" —
     * the dept-scoped role whose users are confined to (and the natural
     * suggestion for) their department subtree. Exposed (BE-5) so the panel
     * roster-suggestion query references THIS constant instead of re-typing the
     * literal {@code "DEPARTMENT_MANAGER"} elsewhere, keeping the dept-scoped
     * director role defined in exactly one place (mirrors
     * {@link #isDepartmentScoped(TenantContext)}).
     */
    public static final String DEPARTMENT_DIRECTOR_ROLE_CODE = RoleCodes.DEPARTMENT_MANAGER;

    @Override
    public String name() { return "DepartmentScopePolicy"; }

    @Override
    public PolicyDecision evaluate(AbacRequest req) {
        TenantContext ctx = req.context();
        if (req.departmentId() == null) {
            return PolicyDecision.NOT_APPLICABLE;
        }
        if (isTenantWideBypass(ctx)) {
            return PolicyDecision.PERMIT;
        }
        if (!isDepartmentScoped(ctx)) {
            return PolicyDecision.NOT_APPLICABLE;
        }
        UUID departmentId = req.departmentId();
        // Fail-closed: a department-scoped caller with an empty (or null) scope
        // can act on nothing — every department is outside their assignment.
        if (ctx.departmentScope() == null || !ctx.departmentScope().contains(departmentId)) {
            return PolicyDecision.DENY;
        }
        return PolicyDecision.PERMIT;
    }

    /**
     * Single source of truth for "this caller is CONSTRAINED to their assigned
     * department subtree" — the two roles that E4 confines: the department
     * director and the evaluation expert. A {@code true} result means the
     * caller's list reads must be filtered to {@code ctx.departmentScope()} and
     * their writes gated by the department check.
     *
     * <p>A bypass caller is never department-scoped; this method returns
     * {@code false} for them, so call sites should test bypass first (or rely
     * on the gate, which permits bypass before reaching the scoped branch).
     */
    public static boolean isDepartmentScoped(TenantContext ctx) {
        return ctx != null
                && !isTenantWideBypass(ctx)
                && (ctx.hasRole(RoleCodes.DEPARTMENT_MANAGER)
                || ctx.hasRole(RoleCodes.EVALUATION_COMMITTEE_MEMBER));
    }

    /**
     * Single source of truth for "this caller sees EVERY department within their
     * tenant without an explicit manager-scope" — the bypass set above.
     *
     * <p>Exposed (P2-2) so {@code SetUserDepartmentScopesUseCase} can reuse the
     * exact same role set when deciding whether a department-scope GRANT must be
     * constrained to the caller's own subtree. Keeping it here avoids re-listing
     * role codes in the use case and keeps the bypass definition in one place.
     */
    public static boolean isTenantWideBypass(TenantContext ctx) {
        return ctx != null
                && (ctx.hasRole(RoleCodes.HRLAB_SUPER_ADMIN)
                || ctx.hasRole(RoleCodes.HRLAB_PROJECT_MANAGER)
                || ctx.hasRole(RoleCodes.HRLAB_CONSULTANT)
                || ctx.hasRole(RoleCodes.HRLAB_ANALYST)
                || ctx.hasRole(RoleCodes.CLIENT_COMPANY_ADMIN)
                || ctx.hasRole(RoleCodes.CLIENT_HR_DIRECTOR)
                // REQ-CEO — the CEO has org-wide oversight: read panels / positions
                // / results across EVERY department (same treatment as the HR
                // Director). They are NOT a panel evaluator, so this read bypass does
                // not affect the panel bias blind (which is the separate
                // RoleCodes.PANEL_OVERSIGHT_ROLES set).
                || ctx.hasRole(RoleCodes.CLIENT_CEO));
    }
}

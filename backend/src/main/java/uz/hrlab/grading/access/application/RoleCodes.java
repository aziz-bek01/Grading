package uz.hrlab.grading.access.application;

import java.util.Set;

/**
 * Canonical role codes (matches seed {@code 002-default-roles.yaml}
 * and {@code role-permissions-matrix.md} §3).
 */
public final class RoleCodes {

    private RoleCodes() { }

    public static final String HRLAB_SUPER_ADMIN          = "HRLAB_SUPER_ADMIN";
    public static final String HRLAB_PROJECT_MANAGER      = "HRLAB_PROJECT_MANAGER";
    public static final String HRLAB_CONSULTANT           = "HRLAB_CONSULTANT";
    public static final String HRLAB_ANALYST              = "HRLAB_ANALYST";
    public static final String CLIENT_COMPANY_ADMIN       = "CLIENT_COMPANY_ADMIN";
    public static final String CLIENT_HR_DIRECTOR         = "CLIENT_HR_DIRECTOR";
    public static final String CLIENT_HR_SPECIALIST       = "CLIENT_HR_SPECIALIST";
    public static final String EVALUATION_COMMITTEE_MEMBER = "EVALUATION_COMMITTEE_MEMBER";
    public static final String DEPARTMENT_MANAGER         = "DEPARTMENT_MANAGER";
    public static final String VIEWER                     = "VIEWER";
    public static final String EXTERNAL_AUDITOR           = "EXTERNAL_AUDITOR";

    /**
     * HRLab platform oversight / cross-evaluator aggregation roles.
     *
     * <p>These are the genuine calibration/oversight identities — by construction
     * they are NOT panel evaluators (peers), so letting them read every per-evaluator
     * panel sheet does NOT leak a peer's score to another committee member. They are
     * the ONLY roles allowed to bypass the panel bias-isolation blind
     * ({@code PanelBiasGuard}); see Defect-2.
     *
     * <p>Deliberately EXCLUDES {@code CLIENT_HR_DIRECTOR} and any tenant
     * department-director role: those are (or routinely double as) panel evaluators,
     * so granting them the bias bypass re-opens the peer-score leak. The legitimate
     * AGGREGATE result surface (averaged totals + breakdown after AVERAGED) stays
     * gated by the separate {@code CAMPAIGN_RESULTS_VIEW} permission in
     * {@code PanelQueries.getResult} — that surface is unchanged.
     */
    public static final Set<String> PANEL_OVERSIGHT_ROLES = Set.of(
            HRLAB_SUPER_ADMIN,
            HRLAB_CONSULTANT,
            HRLAB_PROJECT_MANAGER);
}

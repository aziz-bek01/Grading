package uz.hrlab.grading.access.application;

import java.util.Set;

/**
 * Runtime guard list for the role→permission admin API (slice E2).
 *
 * <p>These permission codes may NEVER be granted to ANY role through
 * {@code PUT /api/v1/roles/{roleId}/permissions}. They are the runtime
 * counterpart of the SEED-TIME invariants enforced in
 * {@code db/changelog/seeds/004-default-role-permissions.yaml} (the dollar-quoted
 * {@code DO $$ ... $$} blocks {@code seed-default-role-permissions-invariants} +
 * {@code seed-user-management-invariants}).
 *
 * <p>Before E2 the only way a role acquired a permission was the seed, so a SQL
 * invariant was sufficient. E2 makes runtime grants possible, which means the
 * SAME invariants MUST now also be enforced at grant time — otherwise an admin
 * could re-introduce, via the API, exactly what the seed forbade. A requested
 * code present in this set is rejected with {@code 422 PERMISSION_RESTRICTED}
 * (see {@link RolePermissionAdminUseCase}).
 *
 * <h3>Why each code is restricted (mapped to the seed invariant it lifts)</h3>
 * <ul>
 *   <li><b>SALARY_VIEW / SALARY_EDIT / SALARY_EXPORT / SALARY_SCENARIO_RUN</b> —
 *       seed rule 1 / {@code salary_grants} invariant: salary is the strictest
 *       data domain (architecture §8.4). It is NEVER a role-level grant; access
 *       is per-user via {@code user_tenant_memberships.salary_data_permission}
 *       plus the salary toggle. Granting it to a role would let everyone holding
 *       that role read client compensation.</li>
 *   <li><b>AUDIT_READ / AUDIT_VIEW</b> — seed rule 2 / {@code audit_grants}
 *       invariant: audit access is a separate, narrowly-held permission (only
 *       HRLAB_SUPER_ADMIN + EXTERNAL_AUDITOR by design). Audit must not become a
 *       freely grantable role permission or the tamper-evidence guarantee is
 *       diluted.</li>
 *   <li><b>TENANT_CREATE / TENANT_EDIT</b> — seed rule 3: control-plane tenant
 *       lifecycle belongs to HRLAB_SUPER_ADMIN only. A client-tenant role must
 *       never be able to create or mutate tenants.</li>
 *   <li><b>USER_ROLE_ASSIGN_HRLAB</b> — {@code seed-user-management-invariants}
 *       ({@code client_hrlab_role_grants}): the power to attach HRLab platform
 *       roles is reserved for HRLAB_SUPER_ADMIN. Granting it to any other role is
 *       a privilege-escalation vector (a client admin could mint HRLab access).</li>
 *   <li><b>USER_SALARY_PERMISSION_TOGGLE</b> — {@code seed-user-management-invariants}
 *       ({@code hrlab_salary_grants}): the salary toggle must never reach HRLab
 *       roles (separation of duties). Because the admin API cannot reason about
 *       which roles are HRLab at grant time per-target, the conservative,
 *       defense-in-depth choice is to forbid granting the toggle to ANY role —
 *       it stays seed-only on CLIENT_COMPANY_ADMIN.</li>
 *   <li><b>USER_ACCESS_MANAGE</b> — the umbrella access-management permission and
 *       the gate of THIS very API. Allowing it to be granted at runtime would let
 *       a role bootstrap its own access-administration powers (meta-escalation).
 *       It stays seed-only on HRLAB_SUPER_ADMIN + CLIENT_COMPANY_ADMIN.</li>
 *   <li><b>AI_ASSIST_USE</b> — seed rule 5 / {@code ai_grants} invariant: an
 *       MVP-4 module not yet shipped; granted to no role. Restricting it prevents
 *       pre-enabling an unfinished, security-sensitive capability.</li>
 * </ul>
 *
 * <p>The set is built from {@link PermissionCodes} constants (single source of
 * truth) so a rename is caught at compile time. It is immutable and may be
 * shared across threads.
 */
public final class RestrictedPermissions {

    private RestrictedPermissions() { }

    /**
     * Permission codes that the admin API must never grant to any role. Kept in
     * sync with the seed-time invariants in {@code 004-default-role-permissions.yaml}.
     */
    public static final Set<String> CODES = Set.of(
            // Salary domain — seed rule 1 / salary_grants invariant.
            PermissionCodes.SALARY_VIEW,
            PermissionCodes.SALARY_EDIT,
            PermissionCodes.SALARY_EXPORT,
            PermissionCodes.SALARY_SCENARIO_RUN,
            // Audit — seed rule 2 / audit_grants invariant.
            PermissionCodes.AUDIT_READ,
            PermissionCodes.AUDIT_VIEW,
            // Tenant control-plane lifecycle — seed rule 3.
            PermissionCodes.TENANT_CREATE,
            PermissionCodes.TENANT_EDIT,
            // User-management escalation vectors — seed-user-management-invariants.
            PermissionCodes.USER_ROLE_ASSIGN_HRLAB,
            PermissionCodes.USER_SALARY_PERMISSION_TOGGLE,
            PermissionCodes.USER_ACCESS_MANAGE,
            // AI assist — seed rule 5 / ai_grants invariant (MVP 4, unshipped).
            PermissionCodes.AI_ASSIST_USE
    );

    /** True when {@code code} may never be granted via the admin API. */
    public static boolean isRestricted(String code) {
        return code != null && CODES.contains(code);
    }
}

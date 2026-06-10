package uz.hrlab.grading.access.api;

/**
 * One row of the role→permission matrix served by
 * {@code GET /api/v1/roles/{roleId}/permissions} (slice E2).
 *
 * <p>The GET returns EVERY permission in the catalog (not only the granted ones)
 * so the frontend can render a complete checkbox matrix. Each item carries:
 * <ul>
 *   <li>{@code granted} — whether THIS role currently has the permission;</li>
 *   <li>{@code restricted} — whether the permission is in
 *       {@link uz.hrlab.grading.access.application.RestrictedPermissions} and may
 *       never be granted to any role (the FE renders it disabled + flagged).</li>
 * </ul>
 *
 * <p>Serializes (global SNAKE_CASE Jackson strategy) as:
 * <pre>
 * { "code": "PROJECT_READ", "resource": "PROJECT", "action": "READ",
 *   "granted": true, "restricted": false }
 * </pre>
 *
 * @param code       canonical permission code
 * @param resource   grouping resource (from {@code permissions.resource}; may be null)
 * @param action     grouping action (from {@code permissions.action}; may be null)
 * @param granted    {@code granted} — currently held by this role
 * @param restricted {@code restricted} — never grantable via the admin API
 */
public record RolePermissionItem(
        String code,
        String resource,
        String action,
        boolean granted,
        boolean restricted
) {
}

package uz.hrlab.grading.access.api;

import java.util.List;

/**
 * Response for {@code GET /api/v1/roles/{roleId}/permissions} and the body
 * returned by {@code PUT /api/v1/roles/{roleId}/permissions} (slice E2).
 *
 * <p>Serializes (global SNAKE_CASE Jackson strategy) as:
 * <pre>
 * {
 *   "role_code": "CLIENT_HR_DIRECTOR",
 *   "scope": "TENANT",
 *   "is_system": true,
 *   "editable_by_caller": true,
 *   "items": [ { "code": "...", "resource": "...", "action": "...",
 *                "granted": true, "restricted": false }, ... ]
 * }
 * </pre>
 *
 * <p>{@code items} is the FULL permission catalog (every {@code permissions} row),
 * each annotated with {@code granted}/{@code restricted}, so the frontend renders
 * a complete matrix. {@code editable_by_caller} tells the FE whether to render the
 * matrix read-only: it is {@code false} when the role is a system role the caller
 * cannot edit (only HRLAB_SUPER_ADMIN may edit system roles in this slice).
 *
 * @param roleCode         {@code role_code} — the role being inspected/edited
 * @param scope            {@code scope} — {@code PLATFORM} or {@code TENANT}
 * @param isSystem         {@code is_system} — true for all seeded roles today
 * @param editableByCaller {@code editable_by_caller} — caller-specific edit gate
 * @param items            full permission catalog with per-permission flags
 */
public record RolePermissionsResponse(
        String roleCode,
        String scope,
        boolean isSystem,
        boolean editableByCaller,
        List<RolePermissionItem> items
) {
}

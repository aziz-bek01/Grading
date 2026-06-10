package uz.hrlab.grading.access.api;

import java.util.Map;
import java.util.UUID;

/**
 * One entry in the assignable-role catalog served by
 * {@code GET /api/v1/roles} (slice E1).
 *
 * <p>Serializes (global SNAKE_CASE Jackson strategy) EXACTLY as:
 * <pre>
 * {
 *   "id": "8c2b...-uuid",
 *   "code": "CLIENT_HR_DIRECTOR",
 *   "name_i18n": { "ru-RU": "...", "uz-Cyrl-UZ": "...", "uz-Latn-UZ": "...", "en-US": "..." },
 *   "scope": "PLATFORM" | "TENANT",
 *   "is_system": true,
 *   "is_custom": false,
 *   "assignable_by_caller": true,
 *   "reason_if_not": null | "HRLAB_ONLY" | "PLATFORM_SCOPE"
 * }
 * </pre>
 *
 * <p>The data-driven contract lets the frontend stop hardcoding the grantable
 * role list: {@code assignable_by_caller} is computed per-caller from the same
 * policy that gates the actual assignment, and {@code reason_if_not} explains a
 * {@code false}. {@code is_system}/{@code is_custom} are forward-compatible
 * placeholders (all seeded roles are system roles until slice E3 introduces
 * tenant-scoped custom roles).
 *
 * <p>NOTE on {@code reason_if_not}: under the platform-wide Jackson policy
 * {@code default-property-inclusion: non_null} the key is OMITTED when the role
 * is assignable (reason is {@code null}) — identical to how {@code active_tenant_id}
 * behaves in {@code CurrentUserResponse}. The frontend reads an absent key as
 * {@code undefined}, semantically equal to {@code null}.
 *
 * @param id                 role primary key → {@code id}. Custom-role
 *                           mutations ({@code PUT}/{@code DELETE /roles/{roleId}},
 *                           slice E3) are addressed by this id, so the frontend
 *                           carries it from the catalog row. Safe to expose (it
 *                           is the PK, not tenant-bearing data); the E2 permission
 *                           matrix is keyed by {@code code} instead.
 * @param code               canonical role code (catalog identifier)
 * @param nameI18n           role display name per supported locale →
 *                           {@code name_i18n}
 * @param scope              {@code PLATFORM} or {@code TENANT}
 * @param isSystem           {@code is_system} — always {@code true} for now
 * @param isCustom           {@code is_custom} — always {@code false} for now
 * @param assignableByCaller {@code assignable_by_caller} — caller-specific
 * @param reasonIfNot        {@code reason_if_not} — {@code null} when assignable
 */
public record RoleResponse(
        UUID id,
        String code,
        Map<String, String> nameI18n,
        String scope,
        boolean isSystem,
        boolean isCustom,
        boolean assignableByCaller,
        String reasonIfNot
) {
}

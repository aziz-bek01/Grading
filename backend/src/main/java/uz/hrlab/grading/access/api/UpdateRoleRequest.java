package uz.hrlab.grading.access.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * Request body for {@code PUT /api/v1/roles/{roleId}} — edit a tenant CUSTOM role
 * (slice E3). Both fields are OPTIONAL (partial update):
 *
 * <pre>
 * {
 *   "name_i18n": { "ru-RU": "..." },                 // optional — rename
 *   "name": "Senior Reviewer",                         // optional fallback
 *   "permission_codes": ["PROJECT_READ"]               // optional — replace-set
 * }
 * </pre>
 *
 * <p>When {@code permission_codes} is {@code null} the permission set is left
 * untouched; when present it is the COMPLETE desired set (replace-set, same
 * semantics as E2) and goes through the shared {@code RolePermissionGuard}
 * (restricted + caller-held). When {@code name_i18n}/{@code name} are both
 * {@code null} the name is left untouched.
 *
 * <p><b>No {@code tenant_id} / {@code code} / {@code scope} fields.</b> The tenant
 * comes from {@link uz.hrlab.grading.tenancy.application.TenantContext}; a custom
 * role's code/scope/system-flag are immutable after create.
 *
 * @param nameI18n        {@code name_i18n} — optional per-locale display name
 * @param name            optional flat display-name fallback
 * @param permissionCodes {@code permission_codes} — optional complete desired set
 */
public record UpdateRoleRequest(
        @JsonProperty("name_i18n") Map<String, String> nameI18n,
        @Size(max = 128) String name,
        @JsonProperty("permission_codes") List<String> permissionCodes
) {
}

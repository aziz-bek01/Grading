package uz.hrlab.grading.access.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * Request body for {@code POST /api/v1/roles} — create a tenant CUSTOM role
 * (slice E3).
 *
 * <pre>
 * {
 *   "code": "REVIEWER",
 *   "name_i18n": { "ru-RU": "...", "en-US": "..." },   // optional
 *   "name": "Reviewer",                                  // fallback if no name_i18n
 *   "permission_codes": ["PROJECT_READ", "EVALUATION_READ"]
 * }
 * </pre>
 *
 * <p><b>No {@code tenant_id} field on purpose.</b> The owning tenant is derived
 * from {@link uz.hrlab.grading.tenancy.application.TenantContext} only
 * (security-blueprint §13; ArchitectureTest Rule 7 forbids a tenantId field on a
 * request DTO). {@code is_system=false}, {@code scope=TENANT} and {@code tenant_id}
 * are all set server-side by {@code CustomRoleUseCase}.
 *
 * @param code            canonical custom-role code (unique within the tenant;
 *                        must not collide with a system role code)
 * @param nameI18n        {@code name_i18n} — optional per-locale display name
 * @param name            optional flat display name (fallback when no
 *                        {@code name_i18n} entry is usable)
 * @param permissionCodes {@code permission_codes} — initial permission set
 */
public record CreateRoleRequest(
        @NotBlank @Size(max = 64) String code,
        @JsonProperty("name_i18n") Map<String, String> nameI18n,
        @Size(max = 128) String name,
        @JsonProperty("permission_codes") @NotNull List<String> permissionCodes
) {
}

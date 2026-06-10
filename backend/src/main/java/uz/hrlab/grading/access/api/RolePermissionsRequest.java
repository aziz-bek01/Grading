package uz.hrlab.grading.access.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request body for {@code PUT /api/v1/roles/{roleId}/permissions} (slice E2).
 *
 * <p>REPLACE-SET semantics: {@code permission_codes} is the COMPLETE desired set
 * of permissions for the role. Codes present here but not currently granted are
 * INSERTED; codes currently granted but absent here are DELETED. Sending the
 * current set unchanged is a no-op (idempotent).
 *
 * <p><b>No {@code tenant_id} field on purpose.</b> Roles and {@code role_permissions}
 * are global control-plane tables in this slice (not tenant-scoped), so there is
 * no tenant to carry — and ArchitectureTest Rule 7 forbids a tenant id on a
 * request DTO anyway. Authorization is by RBAC ({@code USER_ACCESS_MANAGE}) plus
 * the runtime guards in
 * {@link uz.hrlab.grading.access.application.RolePermissionAdminUseCase}.
 *
 * @param permissionCodes {@code permission_codes} — the complete desired permission set
 */
public record RolePermissionsRequest(
        @JsonProperty("permission_codes") @NotNull List<String> permissionCodes
) {
}

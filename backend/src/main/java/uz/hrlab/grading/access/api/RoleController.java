package uz.hrlab.grading.access.api;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.hrlab.grading.access.application.ListRolesQuery;
import uz.hrlab.grading.access.application.RolePermissionAdminUseCase;

import java.util.List;
import java.util.UUID;

/**
 * Role catalog endpoint (slice E1).
 *
 * <p>{@code GET /api/v1/roles[?assignableOnly=true]} returns the data-driven,
 * scope-aware list of roles with a per-caller {@code assignable_by_caller} flag,
 * so the frontend no longer hardcodes which roles are grantable and every
 * intended role (including future custom roles) becomes assignable to the right
 * caller.
 *
 * <p>Security:
 * <ul>
 *   <li>{@code @PreAuthorize} — a user who can manage or list users (or assign
 *       roles) may READ the catalog. This is a read; no write/assignment is
 *       performed here (the assign gate stays in {@link uz.hrlab.grading.access.application.AssignRoleUseCase}).</li>
 *   <li>{@code tenant_id} is NEVER taken from the request — the query reads it
 *       from {@code TenantContextHolder} (security-blueprint §20.1).</li>
 *   <li>No audit event — reads are not audited (per slice spec).</li>
 * </ul>
 *
 * <p>Slice E2 extends this controller with admin CRUD over a role's PERMISSION
 * set ({@code GET}/{@code PUT .../permissions}), gated by
 * {@code USER_ACCESS_MANAGE}. Business guards (restricted permissions, no
 * privilege escalation, system-role edit gate) live in
 * {@link RolePermissionAdminUseCase}; mutations emit per-change audit events.
 */
@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final ListRolesQuery listRolesQuery;
    private final RolePermissionAdminUseCase rolePermissionAdmin;

    public RoleController(ListRolesQuery listRolesQuery,
                          RolePermissionAdminUseCase rolePermissionAdmin) {
        this.listRolesQuery = listRolesQuery;
        this.rolePermissionAdmin = rolePermissionAdmin;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('USER_ROLE_ASSIGN','USER_ACCESS_MANAGE','USER_LIST')")
    public List<RoleResponse> list(
            @RequestParam(name = "assignableOnly", defaultValue = "false") boolean assignableOnly) {
        return listRolesQuery.list(assignableOnly);
    }

    /**
     * Read the full permission matrix for one role (slice E2). Returns every
     * catalog permission with per-permission {@code granted}/{@code restricted}
     * flags so the frontend renders a complete checkbox grid.
     */
    @GetMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('USER_ACCESS_MANAGE')")
    public RolePermissionsResponse getPermissions(@PathVariable UUID roleId) {
        return rolePermissionAdmin.getRolePermissions(roleId);
    }

    /**
     * Replace-set the role's permissions (slice E2). Body is the COMPLETE desired
     * set; the use case diffs against current grants, inserts/deletes the delta,
     * and audits each change. Guards: system-role edit (403), restricted code
     * (422 {@code PERMISSION_RESTRICTED}), caller-not-held code
     * (422 {@code PERMISSION_NOT_HELD_BY_CALLER}).
     */
    @PutMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('USER_ACCESS_MANAGE')")
    public RolePermissionsResponse replacePermissions(@PathVariable UUID roleId,
                                                      @Valid @RequestBody RolePermissionsRequest request) {
        return rolePermissionAdmin.replaceRolePermissions(roleId, request.permissionCodes());
    }
}

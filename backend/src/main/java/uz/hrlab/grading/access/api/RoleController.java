package uz.hrlab.grading.access.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.hrlab.grading.access.application.CustomRoleUseCase;
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
 * set ({@code GET}/{@code PUT /roles/{roleCode}/permissions}), addressed by role
 * CODE to match the frontend, gated by {@code USER_ACCESS_MANAGE}. Business guards
 * (restricted permissions, no privilege escalation, system-role edit gate,
 * by-code C-1 tenant isolation) live in {@link RolePermissionAdminUseCase};
 * mutations emit per-change audit events. Slice E3 custom-role edit/delete
 * ({@code PUT}/{@code DELETE /roles/{roleId}}) stay addressed by the role id.
 */
@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final ListRolesQuery listRolesQuery;
    private final RolePermissionAdminUseCase rolePermissionAdmin;
    private final CustomRoleUseCase customRoleUseCase;

    public RoleController(ListRolesQuery listRolesQuery,
                          RolePermissionAdminUseCase rolePermissionAdmin,
                          CustomRoleUseCase customRoleUseCase) {
        this.listRolesQuery = listRolesQuery;
        this.rolePermissionAdmin = rolePermissionAdmin;
        this.customRoleUseCase = customRoleUseCase;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('USER_ROLE_ASSIGN','USER_ACCESS_MANAGE','USER_LIST')")
    public List<RoleResponse> list(
            @RequestParam(name = "assignableOnly", defaultValue = "false") boolean assignableOnly) {
        return listRolesQuery.list(assignableOnly);
    }

    /**
     * Read the full permission matrix for one role (slice E2). Addressed by role
     * CODE (matches the frontend, which keys the matrix by code; see
     * {@code rolesApi.ts}). Returns every catalog permission with per-permission
     * {@code granted}/{@code restricted} flags so the frontend renders a complete
     * checkbox grid. The use case resolves the code to a SYSTEM role, else the
     * caller's-tenant CUSTOM role, else 404 (no cross-tenant reveal).
     */
    @GetMapping("/{roleCode}/permissions")
    @PreAuthorize("hasAuthority('USER_ACCESS_MANAGE')")
    public RolePermissionsResponse getPermissions(@PathVariable String roleCode) {
        return rolePermissionAdmin.getRolePermissions(roleCode);
    }

    /**
     * Replace-set the role's permissions (slice E2). Addressed by role CODE (see
     * the GET above). Body is the COMPLETE desired set; the use case diffs against
     * current grants, inserts/deletes the delta, and audits each change. Guards:
     * system-role edit (403), restricted code (422 {@code PERMISSION_RESTRICTED}),
     * caller-not-held code (422 {@code PERMISSION_NOT_HELD_BY_CALLER}); a
     * cross-tenant custom code resolves to 404.
     */
    @PutMapping("/{roleCode}/permissions")
    @PreAuthorize("hasAuthority('USER_ACCESS_MANAGE')")
    public RolePermissionsResponse replacePermissions(@PathVariable String roleCode,
                                                      @Valid @RequestBody RolePermissionsRequest request) {
        return rolePermissionAdmin.replaceRolePermissions(roleCode, request.permissionCodes());
    }

    // ----------------------------------------------------------- E3 custom roles

    /**
     * Create a tenant CUSTOM role (slice E3). The owning tenant comes from
     * {@code TenantContext} (never the body). Guards: reserved-system-code (409
     * {@code ROLE_CODE_RESERVED}), in-tenant duplicate (409 {@code ROLE_CODE_EXISTS}),
     * restricted/caller-held permission guard (422). Returns the created role in the
     * E1 {@link RoleResponse} shape.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USER_ACCESS_MANAGE')")
    public RoleResponse create(@Valid @RequestBody CreateRoleRequest request) {
        return customRoleUseCase.create(request.code(), request.nameI18n(),
                request.name(), request.permissionCodes());
    }

    /**
     * Edit a tenant CUSTOM role (slice E3): rename and/or replace-set its
     * permissions. System roles are not editable here (403); a cross-tenant role
     * is 404. Permission changes go through the shared restricted/caller-held guard.
     */
    @PutMapping("/{roleId}")
    @PreAuthorize("hasAuthority('USER_ACCESS_MANAGE')")
    public RoleResponse update(@PathVariable UUID roleId,
                               @Valid @RequestBody UpdateRoleRequest request) {
        return customRoleUseCase.edit(roleId, request.nameI18n(),
                request.name(), request.permissionCodes());
    }

    /**
     * Delete a tenant CUSTOM role (slice E3). System role → 403; cross-tenant →
     * 404; still-assigned role → 409 {@code ROLE_IN_USE}.
     */
    @DeleteMapping("/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('USER_ACCESS_MANAGE')")
    public void delete(@PathVariable UUID roleId) {
        customRoleUseCase.delete(roleId);
    }
}

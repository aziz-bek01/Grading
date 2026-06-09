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
import uz.hrlab.grading.access.application.GetUserScopesQuery;
import uz.hrlab.grading.access.application.SetUserDepartmentScopesUseCase;
import uz.hrlab.grading.access.application.SetUserProjectAssignmentsUseCase;

import java.util.UUID;

/**
 * ABAC scope admin API (E4-S1) — assigns a user's per-tenant DEPARTMENT scope
 * and PROJECT assignments, the rows the auth path expands into
 * {@code TenantContext.departmentScope} / {@code projectIds} (E4-S0).
 *
 * <p>Same RBAC gate as the membership endpoints on {@link UserController}
 * ({@code USER_MEMBERSHIP_MANAGE} or the umbrella {@code USER_ACCESS_MANAGE}).
 * ABAC tenant-scope (the action must target a tenant the caller manages, and a
 * cross-tenant target → 404) is enforced inside the use cases via
 * {@link uz.hrlab.grading.access.application.UserManagementPolicy#requireCanManageInTenant}.
 *
 * <p>{@code tenantId} appears in path/query/body because the action targets the
 * (user, tenant) tuple — but the policy verifies it against the caller's active
 * tenant; the body value is never trusted as the tenant identity on its own.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserScopeController {

    private final GetUserScopesQuery getUserScopes;
    private final SetUserDepartmentScopesUseCase setDepartmentScopes;
    private final SetUserProjectAssignmentsUseCase setProjectAssignments;

    public UserScopeController(GetUserScopesQuery getUserScopes,
                              SetUserDepartmentScopesUseCase setDepartmentScopes,
                              SetUserProjectAssignmentsUseCase setProjectAssignments) {
        this.getUserScopes = getUserScopes;
        this.setDepartmentScopes = setDepartmentScopes;
        this.setProjectAssignments = setProjectAssignments;
    }

    // GET /api/v1/users/{id}/scopes?tenantId=...
    @GetMapping("/{id}/scopes")
    @PreAuthorize("hasAnyAuthority('USER_MEMBERSHIP_MANAGE','USER_ACCESS_MANAGE')")
    public UserScopeResponse scopes(@PathVariable UUID id,
                                    @RequestParam UUID tenantId) {
        return getUserScopes.byUserAndTenant(id, tenantId);
    }

    // PUT /api/v1/users/{id}/department-scopes   (replace-set)
    @PutMapping("/{id}/department-scopes")
    @PreAuthorize("hasAnyAuthority('USER_MEMBERSHIP_MANAGE','USER_ACCESS_MANAGE')")
    public UserScopeResponse setDepartmentScopes(@PathVariable UUID id,
                                                 @Valid @RequestBody SetDepartmentScopesRequest req) {
        return setDepartmentScopes.replace(id, req.tenantId(), req.departmentIds());
    }

    // PUT /api/v1/users/{id}/project-assignments  (replace-set)
    @PutMapping("/{id}/project-assignments")
    @PreAuthorize("hasAnyAuthority('USER_MEMBERSHIP_MANAGE','USER_ACCESS_MANAGE')")
    public UserScopeResponse setProjectAssignments(@PathVariable UUID id,
                                                   @Valid @RequestBody SetProjectAssignmentsRequest req) {
        return setProjectAssignments.replace(id, req.tenantId(), req.projectIds());
    }
}

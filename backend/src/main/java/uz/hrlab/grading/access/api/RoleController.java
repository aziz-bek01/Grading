package uz.hrlab.grading.access.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.hrlab.grading.access.application.ListRolesQuery;

import java.util.List;

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
 */
@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final ListRolesQuery listRolesQuery;

    public RoleController(ListRolesQuery listRolesQuery) {
        this.listRolesQuery = listRolesQuery;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('USER_ROLE_ASSIGN','USER_ACCESS_MANAGE','USER_LIST')")
    public List<RoleResponse> list(
            @RequestParam(name = "assignableOnly", defaultValue = "false") boolean assignableOnly) {
        return listRolesQuery.list(assignableOnly);
    }
}

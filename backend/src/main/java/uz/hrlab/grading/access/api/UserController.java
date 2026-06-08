package uz.hrlab.grading.access.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.hrlab.grading.access.application.AddMembershipUseCase;
import uz.hrlab.grading.access.application.AssignRoleUseCase;
import uz.hrlab.grading.access.application.GetUserAuditQuery;
import uz.hrlab.grading.access.application.GetUserDetailsQuery;
import uz.hrlab.grading.access.application.InviteUserUseCase;
import uz.hrlab.grading.access.application.ListUsersQuery;
import uz.hrlab.grading.access.application.PatchUserUseCase;
import uz.hrlab.grading.access.application.RevokeMembershipUseCase;
import uz.hrlab.grading.access.application.ToggleSalaryPermissionUseCase;
import uz.hrlab.grading.common.api.PageResponse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * User Management API (MVP 1 Phase 1.5 — access admin module).
 *
 * <p>10 endpoints — list / view / invite / patch / membership add/revoke /
 * role assign/remove / salary toggle / audit timeline. Every mutation
 * records ONE {@link uz.hrlab.grading.audit.application.AuditAction USER_*}
 * row in {@code system_audit_log} via the use case layer.
 *
 * <p>{@code @PreAuthorize} pinned to RBAC permission codes from
 * {@link uz.hrlab.grading.access.application.PermissionCodes}; ABAC scoping
 * (tenant overlap, HRLab role gating, self-grant defense) is enforced inside
 * {@link uz.hrlab.grading.access.application.UserManagementPolicy}.
 *
 * <p>{@code tenantId} appears in path/query for THIS module because the action
 * targets the (user, tenant) tuple — but the policy still verifies the value
 * against the caller's membership set; non-super-admins cannot reach into a
 * tenant they don't belong to even if they spoof the URL.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final ListUsersQuery listUsers;
    private final GetUserDetailsQuery getUserDetails;
    private final InviteUserUseCase inviteUser;
    private final PatchUserUseCase patchUser;
    private final AddMembershipUseCase addMembership;
    private final RevokeMembershipUseCase revokeMembership;
    private final AssignRoleUseCase assignRole;
    private final ToggleSalaryPermissionUseCase toggleSalary;
    private final GetUserAuditQuery getUserAudit;

    public UserController(ListUsersQuery listUsers,
                          GetUserDetailsQuery getUserDetails,
                          InviteUserUseCase inviteUser,
                          PatchUserUseCase patchUser,
                          AddMembershipUseCase addMembership,
                          RevokeMembershipUseCase revokeMembership,
                          AssignRoleUseCase assignRole,
                          ToggleSalaryPermissionUseCase toggleSalary,
                          GetUserAuditQuery getUserAudit) {
        this.listUsers = listUsers;
        this.getUserDetails = getUserDetails;
        this.inviteUser = inviteUser;
        this.patchUser = patchUser;
        this.addMembership = addMembership;
        this.revokeMembership = revokeMembership;
        this.assignRole = assignRole;
        this.toggleSalary = toggleSalary;
        this.getUserAudit = getUserAudit;
    }

    // 1) GET /api/v1/users
    @GetMapping
    @PreAuthorize("hasAnyAuthority('USER_LIST','USER_ACCESS_MANAGE')")
    public PageResponse<UserListResponse> list(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<UserListResponse> result = listUsers.list(tenantId, status, role, search, page, size);
        return PageResponse.of(result, r -> r);
    }

    // 2) POST /api/v1/users
    @PostMapping
    @PreAuthorize("hasAnyAuthority('USER_INVITE','USER_ACCESS_MANAGE')")
    public ResponseEntity<UserDetailsResponse> invite(@Valid @RequestBody InviteUserRequest req) {
        UserDetailsResponse body = inviteUser.invite(
                req.email(), req.fullName(), req.locale(), req.tenantId(), req.roleCodes(),
                req.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    // 3) GET /api/v1/users/{id}
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('USER_VIEW','USER_LIST','USER_ACCESS_MANAGE')")
    public UserDetailsResponse details(@PathVariable UUID id) {
        return getUserDetails.byId(id);
    }

    // 4) PATCH /api/v1/users/{id}
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('USER_UPDATE','USER_ACCESS_MANAGE')")
    public UserDetailsResponse patch(@PathVariable UUID id,
                                     @Valid @RequestBody PatchUserRequest req) {
        return patchUser.patch(id, req.fullName(), req.locale(), req.status(),
                req.email(), req.password());
    }

    // 5) POST /api/v1/users/{id}/memberships
    @PostMapping("/{id}/memberships")
    @PreAuthorize("hasAnyAuthority('USER_MEMBERSHIP_MANAGE','USER_ACCESS_MANAGE')")
    public ResponseEntity<UserDetailsResponse> addMembership(
            @PathVariable UUID id,
            @Valid @RequestBody AddMembershipRequest req) {
        UserDetailsResponse body = addMembership.add(id, req.tenantId(), req.roleCodes());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    // 6) DELETE /api/v1/users/{id}/memberships/{tenantId}
    @DeleteMapping("/{id}/memberships/{tenantId}")
    @PreAuthorize("hasAnyAuthority('USER_MEMBERSHIP_MANAGE','USER_ACCESS_MANAGE')")
    public ResponseEntity<Void> revokeMembership(
            @PathVariable UUID id, @PathVariable UUID tenantId) {
        revokeMembership.revoke(id, tenantId);
        return ResponseEntity.noContent().build();
    }

    // 7) POST /api/v1/users/{id}/memberships/{tenantId}/roles
    @PostMapping("/{id}/memberships/{tenantId}/roles")
    @PreAuthorize("hasAnyAuthority('USER_ROLE_ASSIGN','USER_ACCESS_MANAGE')")
    public ResponseEntity<UserDetailsResponse> assignRole(
            @PathVariable UUID id, @PathVariable UUID tenantId,
            @Valid @RequestBody AssignRoleRequest req) {
        UserDetailsResponse body = assignRole.assign(id, tenantId, req.roleCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    // 8) DELETE /api/v1/users/{id}/memberships/{tenantId}/roles/{roleId}
    @DeleteMapping("/{id}/memberships/{tenantId}/roles/{userRoleId}")
    @PreAuthorize("hasAnyAuthority('USER_ROLE_ASSIGN','USER_ACCESS_MANAGE')")
    public UserDetailsResponse removeRole(
            @PathVariable UUID id, @PathVariable UUID tenantId,
            @PathVariable UUID userRoleId) {
        return assignRole.remove(id, tenantId, userRoleId);
    }

    // 9) PATCH /api/v1/users/{id}/memberships/{tenantId}/salary-permission
    @PatchMapping("/{id}/memberships/{tenantId}/salary-permission")
    @PreAuthorize("hasAuthority('USER_SALARY_PERMISSION_TOGGLE')")
    public UserDetailsResponse toggleSalary(
            @PathVariable UUID id, @PathVariable UUID tenantId,
            @Valid @RequestBody SalaryPermissionToggleRequest req) {
        return toggleSalary.toggle(id, tenantId, req.enabled(), req.reason());
    }

    // 10) GET /api/v1/users/{id}/audit
    @GetMapping("/{id}/audit")
    @PreAuthorize("hasAnyAuthority('AUDIT_VIEW','AUDIT_READ')")
    public List<UserAuditEntryResponse> audit(
            @PathVariable UUID id,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return getUserAudit.list(id, from, to);
    }
}

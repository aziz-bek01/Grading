package uz.hrlab.grading.access.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.api.RoleResponse;
import uz.hrlab.grading.access.infrastructure.PermissionJpaEntity;
import uz.hrlab.grading.access.infrastructure.PermissionRepository;
import uz.hrlab.grading.access.infrastructure.RoleJpaEntity;
import uz.hrlab.grading.access.infrastructure.RolePermissionId;
import uz.hrlab.grading.access.infrastructure.RolePermissionJpaEntity;
import uz.hrlab.grading.access.infrastructure.RolePermissionRepository;
import uz.hrlab.grading.access.infrastructure.RoleRepository;
import uz.hrlab.grading.access.infrastructure.UserRoleRepository;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.ConflictException;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;
import uz.hrlab.grading.tenancy.domain.Locale;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tenant CUSTOM-role CRUD (slice E3) — backs {@code POST /api/v1/roles},
 * {@code PUT /api/v1/roles/{roleId}} and {@code DELETE /api/v1/roles/{roleId}}.
 *
 * <p>A tenant admin (CLIENT_COMPANY_ADMIN) defines roles alongside the fixed
 * 11 system roles; HRLab Super Admin may act on any tenant. The active tenant is
 * ALWAYS sourced from {@link TenantContext} — never from the request — so a tenant
 * admin manages only its OWN custom roles (cross-tenant target → 404, no reveal).
 *
 * <h3>Guard reuse (no duplication)</h3>
 * Permission grants on create AND edit go through the shared
 * {@link RolePermissionGuard#validateAndResolve} — the SAME restricted +
 * caller-held no-escalation guard that {@link RolePermissionAdminUseCase} uses.
 * The guard lives in exactly one place; this use case owns only the role-lifecycle
 * concerns (code collision, in-use delete) and the {@code role_permissions} delta.
 *
 * <h3>Error contract</h3>
 * <ul>
 *   <li>create reserved-system-code → 409 {@code ROLE_CODE_RESERVED}</li>
 *   <li>create in-tenant duplicate code → 409 {@code ROLE_CODE_EXISTS}</li>
 *   <li>grant a restricted permission → 422 {@code PERMISSION_RESTRICTED}</li>
 *   <li>grant a permission the caller lacks → 422 {@code PERMISSION_NOT_HELD_BY_CALLER}</li>
 *   <li>edit/delete a system role → 403 {@code PERMISSION_DENIED}</li>
 *   <li>edit/delete a cross-tenant custom role → 404 (no reveal)</li>
 *   <li>delete a still-assigned role → 409 {@code ROLE_IN_USE}</li>
 * </ul>
 */
@Service
public class CustomRoleUseCase {

    private final RoleRepository roleRepo;
    private final PermissionRepository permissionRepo;
    private final RolePermissionRepository rolePermissionRepo;
    private final UserRoleRepository userRoleRepo;
    private final RolePermissionGuard guard;
    private final RoleOwnershipGuard ownershipGuard;
    private final UserManagementPolicy policy;
    private final AuditService audit;

    /** Case-insensitive set of canonical SYSTEM role codes — the reserved namespace. */
    private static final Set<String> SYSTEM_ROLE_CODES_UPPER = Set.of(
            RoleCodes.HRLAB_SUPER_ADMIN,
            RoleCodes.HRLAB_PROJECT_MANAGER,
            RoleCodes.HRLAB_CONSULTANT,
            RoleCodes.HRLAB_ANALYST,
            RoleCodes.CLIENT_COMPANY_ADMIN,
            RoleCodes.CLIENT_HR_DIRECTOR,
            RoleCodes.CLIENT_HR_SPECIALIST,
            RoleCodes.EVALUATION_COMMITTEE_MEMBER,
            RoleCodes.DEPARTMENT_MANAGER,
            RoleCodes.VIEWER,
            RoleCodes.EXTERNAL_AUDITOR
    ).stream().map(s -> s.toUpperCase()).collect(Collectors.toUnmodifiableSet());

    public CustomRoleUseCase(RoleRepository roleRepo,
                             PermissionRepository permissionRepo,
                             RolePermissionRepository rolePermissionRepo,
                             UserRoleRepository userRoleRepo,
                             RolePermissionGuard guard,
                             RoleOwnershipGuard ownershipGuard,
                             UserManagementPolicy policy,
                             AuditService audit) {
        this.roleRepo = roleRepo;
        this.permissionRepo = permissionRepo;
        this.rolePermissionRepo = rolePermissionRepo;
        this.userRoleRepo = userRoleRepo;
        this.guard = guard;
        this.ownershipGuard = ownershipGuard;
        this.policy = policy;
        this.audit = audit;
    }

    // --------------------------------------------------------------- CREATE

    /**
     * Create a tenant custom role with an initial permission set. Guard order
     * (fail-closed, first failure wins): reserved-system-code (409) →
     * in-tenant-duplicate (409) → restricted/caller-held permission guard (422) →
     * persist role + grants → audit.
     */
    @Transactional
    public RoleResponse create(String code, Map<String, String> nameI18n, String name,
                               List<String> permissionCodes) {
        TenantContext ctx = TenantContextHolder.requireActive();
        UUID tenantId = ctx.tenantId();
        if (tenantId == null) {
            throw new TenantAccessDeniedException();
        }
        String normalizedCode = code == null ? "" : code.trim();

        // (1) reserved system-code collision (case-insensitive) → 409. The two
        // partial unique indexes (E3) only enforce uniqueness WITHIN each
        // namespace, so the cross-namespace clash is rejected here in the backend.
        if (isReservedSystemCode(normalizedCode)) {
            throw new ConflictException("ROLE_CODE_RESERVED",
                    "Role code is reserved by a system role");
        }

        // (2) in-tenant duplicate custom code → 409.
        if (roleRepo.findByTenantIdAndCode(tenantId, normalizedCode).isPresent()) {
            throw new ConflictException("ROLE_CODE_EXISTS",
                    "A custom role with this code already exists in the tenant");
        }

        // (3) restricted + caller-held permission guard (shared with E2). 422 on
        // any escalation; resolves the codes we are about to grant.
        Set<PermissionJpaEntity> resolved = guard.validateAndResolve(ctx, permissionCodes);

        // (4) persist the role (is_system=false, scope=TENANT, tenant_id set).
        UUID roleId = UUID.randomUUID();
        RoleJpaEntity role = RoleJpaEntity.newCustom(roleId, tenantId, normalizedCode,
                resolveName(nameI18n, name, normalizedCode), null);
        roleRepo.save(role);

        audit.record(roleEvent(ctx, role, AuditAction.ROLE_CREATED,
                "code=" + role.getCode() + " scope=TENANT"));

        // (5) grant the resolved permissions, one audit row per grant.
        for (PermissionJpaEntity p : resolved) {
            rolePermissionRepo.save(new RolePermissionJpaEntity(new RolePermissionId(roleId, p.getId())));
            auditPermission(ctx, role, p.getCode(), AuditAction.ROLE_PERMISSION_GRANTED);
        }

        return toResponse(ctx, role);
    }

    // ----------------------------------------------------------------- EDIT

    /**
     * Edit a tenant custom role: optionally rename and/or replace-set its
     * permissions. System roles are NOT editable here (their permission edits live
     * in E2's {@code PUT .../permissions}, super-admin only) → 403; a cross-tenant
     * custom role → 404. Permission changes flow through the SAME shared guard.
     */
    @Transactional
    public RoleResponse edit(UUID roleId, Map<String, String> nameI18n, String name,
                             List<String> permissionCodes) {
        TenantContext ctx = TenantContextHolder.requireActive();
        RoleJpaEntity role = requireOwnCustomRole(ctx, roleId);

        boolean changed = false;

        // Rename (custom roles only; code/scope/system flag are immutable).
        if (nameI18n != null || name != null) {
            String newName = resolveName(nameI18n, name, role.getCode());
            if (newName != null && !newName.equals(role.getName())) {
                role.setName(newName);
                roleRepo.save(role);
                changed = true;
            }
        }

        // Replace-set permissions (only when supplied). null = leave untouched.
        if (permissionCodes != null) {
            changed |= applyPermissionDelta(ctx, role, permissionCodes);
        }

        if (changed) {
            audit.record(roleEvent(ctx, role, AuditAction.ROLE_UPDATED,
                    "code=" + role.getCode()));
        }
        return toResponse(ctx, role);
    }

    // --------------------------------------------------------------- DELETE

    /**
     * Delete a tenant custom role. System role → 403; cross-tenant custom role →
     * 404; a role still assigned to any user → 409 {@code ROLE_IN_USE} (the admin
     * must revoke it everywhere first so no user silently loses access). Otherwise
     * removes the {@code role_permissions} rows then the role itself; audits once.
     */
    @Transactional
    public void delete(UUID roleId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        RoleJpaEntity role = requireOwnCustomRole(ctx, roleId);

        // In-use guard — block before touching role_permissions (which the FK would
        // also block, but we want a clean 409 not a generic constraint violation).
        if (userRoleRepo.existsByRoleId(roleId)) {
            throw new ConflictException("ROLE_IN_USE",
                    "Role is still assigned to users; revoke the assignments first");
        }

        // Remove permission grants, then the role.
        List<RolePermissionJpaEntity> grants = rolePermissionRepo.findAllByIdRoleId(roleId);
        for (RolePermissionJpaEntity rp : grants) {
            rolePermissionRepo.delete(rp);
        }
        roleRepo.delete(role);

        audit.record(roleEvent(ctx, role, AuditAction.ROLE_DELETED,
                "code=" + role.getCode()));
    }

    // --------------------------------------------------------------- HELPERS

    /**
     * Resolve a CUSTOM role the caller is allowed to mutate. A system role is
     * rejected with 403 (well-known, no probing risk); a missing or cross-tenant
     * custom role yields 404 (no existence reveal).
     *
     * <p>Tenant-ownership is decided by the SHARED {@link RoleOwnershipGuard} —
     * the SAME check E2 ({@link RolePermissionAdminUseCase}) uses — so the
     * ownership rule is defined in exactly one place (no duplication, no drift).
     * The system-role 403 below is E3-specific (E2 instead lets super-admin edit
     * system roles) and stays here.
     */
    private RoleJpaEntity requireOwnCustomRole(TenantContext ctx, UUID roleId) {
        // Load tenant-agnostically: a missing id is an opaque 404; a system role
        // gets a precise 403; a custom role's ownership is decided by the guard.
        RoleJpaEntity role = roleRepo.findById(roleId)
                .orElseThrow(TenantAccessDeniedException::new); // → 404, no reveal
        if (role.isSystem()) {
            throw new PermissionDeniedException("System roles cannot be modified here");
        }
        ownershipGuard.requireCanManage(ctx, role); // cross-tenant custom → 404
        return role;
    }

    /**
     * Apply a replace-set permission delta on a custom role. Validation/resolution
     * go through the shared {@link RolePermissionGuard}; only the diff + per-change
     * audit live here.
     *
     * @return {@code true} when at least one grant/revoke happened
     */
    private boolean applyPermissionDelta(TenantContext ctx, RoleJpaEntity role,
                                         List<String> permissionCodes) {
        Set<PermissionJpaEntity> desired = guard.validateAndResolve(ctx, permissionCodes);
        Set<UUID> desiredIds = desired.stream()
                .map(PermissionJpaEntity::getId)
                .collect(Collectors.toUnmodifiableSet());

        List<RolePermissionJpaEntity> current = rolePermissionRepo.findAllByIdRoleId(role.getId());
        Set<UUID> currentIds = current.stream()
                .map(rp -> rp.getId().getPermissionId())
                .collect(Collectors.toUnmodifiableSet());

        boolean changed = false;

        // Inserts: desired - current.
        for (PermissionJpaEntity p : desired) {
            if (!currentIds.contains(p.getId())) {
                rolePermissionRepo.save(
                        new RolePermissionJpaEntity(new RolePermissionId(role.getId(), p.getId())));
                auditPermission(ctx, role, p.getCode(), AuditAction.ROLE_PERMISSION_GRANTED);
                changed = true;
            }
        }

        // Deletes: current - desired.
        for (RolePermissionJpaEntity rp : current) {
            UUID permissionId = rp.getId().getPermissionId();
            if (!desiredIds.contains(permissionId)) {
                rolePermissionRepo.delete(rp);
                auditPermission(ctx, role, codeOf(permissionId), AuditAction.ROLE_PERMISSION_REVOKED);
                changed = true;
            }
        }
        return changed;
    }

    private boolean isReservedSystemCode(String code) {
        return code != null && SYSTEM_ROLE_CODES_UPPER.contains(code.toUpperCase())
                // belt-and-braces: also reject ANY existing system row code that is
                // not in the canonical list (e.g. a future seed) via the DB.
                || (code != null && !code.isBlank() && isExistingSystemCode(code));
    }

    private boolean isExistingSystemCode(String code) {
        return roleRepo.findByCode(code)
                .map(RoleJpaEntity::isSystem)
                .orElse(false);
    }

    /**
     * Collapse {@code name_i18n} / flat {@code name} to the single {@code name}
     * column. Prefers RU, then EN, then any non-blank locale; falls back to the
     * flat {@code name}, then the {@code code}. (Per-locale translation rows are a
     * later slice; the column holds the canonical display name.)
     */
    private static String resolveName(Map<String, String> nameI18n, String name, String code) {
        if (nameI18n != null && !nameI18n.isEmpty()) {
            String ru = blankToNull(nameI18n.get(Locale.RU_RU));
            if (ru != null) return ru;
            String en = blankToNull(nameI18n.get(Locale.EN_US));
            if (en != null) return en;
            for (String v : nameI18n.values()) {
                String nv = blankToNull(v);
                if (nv != null) return nv;
            }
        }
        String flat = blankToNull(name);
        if (flat != null) return flat;
        return code;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private String codeOf(UUID permissionId) {
        return permissionRepo.findById(permissionId)
                .map(PermissionJpaEntity::getCode)
                .orElse(permissionId.toString());
    }

    private AuditEvent roleEvent(TenantContext ctx, RoleJpaEntity role, String action, String reason) {
        return AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .actorUserId(ctx.userId())
                .action(action)
                .entityType("Role")
                .entityId(role.getId())
                .reason(reason)
                .build();
    }

    private void auditPermission(TenantContext ctx, RoleJpaEntity role, String permissionCode, String action) {
        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .actorUserId(ctx.userId())
                .action(action)
                .entityType("RolePermission")
                .entityId(role.getId())
                .reason("roleCode=" + role.getCode() + " permissionCode=" + permissionCode)
                .build());
    }

    private RoleResponse toResponse(TenantContext ctx, RoleJpaEntity role) {
        UserManagementPolicy.RoleAssignDenialReason reason =
                policy.roleAssignDenialReason(ctx, role);
        return new RoleResponse(
                role.getCode(),
                nameI18n(role.getName()),
                role.getScope() == null ? null : role.getScope().name(),
                role.isSystem(),
                !role.isSystem(),
                reason == null,
                reason == null ? null : reason.name());
    }

    private static Map<String, String> nameI18n(String name) {
        String value = name == null ? "" : name;
        return Map.of(
                Locale.RU_RU, value,
                Locale.UZ_CYRL_UZ, value,
                Locale.UZ_LATN_UZ, value,
                Locale.EN_US, value);
    }
}

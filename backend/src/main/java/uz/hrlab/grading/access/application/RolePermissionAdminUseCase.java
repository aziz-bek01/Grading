package uz.hrlab.grading.access.application;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.api.RolePermissionItem;
import uz.hrlab.grading.access.api.RolePermissionsResponse;
import uz.hrlab.grading.access.infrastructure.PermissionJpaEntity;
import uz.hrlab.grading.access.infrastructure.PermissionRepository;
import uz.hrlab.grading.access.infrastructure.RoleJpaEntity;
import uz.hrlab.grading.access.infrastructure.RolePermissionId;
import uz.hrlab.grading.access.infrastructure.RolePermissionJpaEntity;
import uz.hrlab.grading.access.infrastructure.RolePermissionRepository;
import uz.hrlab.grading.access.infrastructure.RoleRepository;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.cache.CacheNames;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin CRUD over a single role's PERMISSION set (slice E2).
 *
 * <p>Backs {@code GET /api/v1/roles/{roleCode}/permissions} (read the matrix) and
 * {@code PUT /api/v1/roles/{roleCode}/permissions} (replace-set grant/revoke). The
 * RBAC gate ({@code USER_ACCESS_MANAGE}) is at the controller; this use case adds
 * the business guards and the audit trail.
 *
 * <h3>Resolution by CODE (matches the frontend contract)</h3>
 * The permission matrix is addressed by role CODE, not id (the FE keys it by code;
 * see {@code rolesApi.ts}). A code is resolved to exactly one role, fail-closed:
 * <ol>
 *   <li>try a SYSTEM role by code ({@code findByCodeAndIsSystemTrue} — system codes
 *       are globally unique and tenant-less);</li>
 *   <li>else try the CALLER'S-TENANT CUSTOM role by code
 *       ({@code findByTenantIdAndCode(ctx.tenantId(), code)});</li>
 *   <li>else 404 ({@link TenantAccessDeniedException}, no existence reveal).</li>
 * </ol>
 * A custom code can NEVER equal a system code (the create path rejects that), so
 * the order is unambiguous. <b>C-1 isolation:</b> resolving custom roles ONLY
 * within {@code ctx.tenantId()} means a foreign tenant's custom code never
 * resolves — it falls through to 404, so a caller can never read or edit another
 * tenant's custom role. The {@link RoleOwnershipGuard} is still applied after
 * resolution as defense-in-depth (it is a no-op for the system path and for an
 * own-tenant custom role, and remains the cross-tenant guard for the super-admin
 * path where a system code could front any tenant).
 *
 * <h3>Why this exists / E1 relationship</h3>
 * E1 made the role CATALOG data-driven and computed {@code assignable_by_caller}.
 * E2 manages the permissions ON a role. Before E2 the only writer of
 * {@code role_permissions} was the seed, whose dollar-quoted invariants blocked
 * dangerous grants at seed time. E2 makes runtime grants possible, so those
 * invariants are re-expressed at grant time via {@link RestrictedPermissions}
 * (the "restricted" guard).
 *
 * <h3>replaceRolePermissions guard order (fail-closed, first failure wins)</h3>
 * <ol>
 *   <li><b>(a) role exists</b> — unknown {@code roleCode} → 404 (no existence reveal).</li>
 *   <li><b>(b) system-role edit gate</b> — every seeded role is a system role
 *       today; only HRLAB_SUPER_ADMIN (a caller holding
 *       {@code USER_ROLE_ASSIGN_HRLAB}) may edit a system role, else 403.</li>
 *   <li><b>(c) restricted</b> — any requested code in {@link RestrictedPermissions}
 *       → 422 {@code PERMISSION_RESTRICTED}.</li>
 *   <li><b>(d) caller-not-held</b> — any requested code the CALLER does not hold
 *       → 422 {@code PERMISSION_NOT_HELD_BY_CALLER} (no privilege escalation:
 *       you cannot grant what you do not have).</li>
 *   <li><b>(e) apply delta</b> — insert added / delete removed rows, one audit
 *       event per change ({@code ROLE_PERMISSION_GRANTED}/{@code _REVOKED}).
 *       Idempotent: an unchanged set writes nothing and audits nothing.</li>
 * </ol>
 *
 * <p>Granted permissions take effect on the next token/context resolution: the
 * authority expansion path ({@code RolePermissionRepository.findPermissionCodesByRoleIds},
 * read by {@code DevUserAuthorityResolver} and {@code JwtTenantContextResolver})
 * is cached ({@code CacheNames.ROLE_PERMISSION_CODES}, 60s TTL). To make a
 * grant/revoke take effect immediately rather than after the TTL, the write path
 * ({@link #replaceRolePermissions(String, List)}) is annotated
 * {@code @CacheEvict(allEntries = true)} on that cache. {@code allEntries} (not a
 * keyed evict) is required because a single role can appear in MANY cached
 * role-id-set keys (every membership whose role set includes it); evicting the
 * whole — small, short-lived — cache is the correct, conservative invalidation.
 * The short TTL remains the backstop if eviction is ever missed.
 */
@Service
public class RolePermissionAdminUseCase {

    private final RoleRepository roleRepo;
    private final PermissionRepository permissionRepo;
    private final RolePermissionRepository rolePermissionRepo;
    private final RolePermissionGuard guard;
    private final RoleOwnershipGuard ownershipGuard;
    private final AuditService audit;

    public RolePermissionAdminUseCase(RoleRepository roleRepo,
                                      PermissionRepository permissionRepo,
                                      RolePermissionRepository rolePermissionRepo,
                                      RolePermissionGuard guard,
                                      RoleOwnershipGuard ownershipGuard,
                                      AuditService audit) {
        this.roleRepo = roleRepo;
        this.permissionRepo = permissionRepo;
        this.rolePermissionRepo = rolePermissionRepo;
        this.guard = guard;
        this.ownershipGuard = ownershipGuard;
        this.audit = audit;
    }

    // ------------------------------------------------------------------- READ

    /**
     * Read the full permission matrix for {@code roleCode}. Returns EVERY catalog
     * permission with {@code granted} (on this role) + {@code restricted} flags,
     * plus {@code editable_by_caller} so the FE can render the matrix read-only
     * for callers who may not edit this (system) role.
     */
    @Transactional(readOnly = true)
    public RolePermissionsResponse getRolePermissions(String roleCode) {
        TenantContext ctx = TenantContextHolder.requireActive();
        RoleJpaEntity role = resolveRoleByCode(ctx, roleCode);
        // C-1 — tenant-ownership guard (BOLA fix, defense-in-depth). For a CUSTOM
        // role the caller's active tenant must own it (super-admin may act
        // cross-tenant); a foreign custom role → 404 (no existence reveal). Note
        // resolveRoleByCode already restricts custom resolution to ctx.tenantId(),
        // so a foreign custom code never reaches here; the guard still backstops the
        // system path (where a system code could front any tenant).
        ownershipGuard.requireCanManage(ctx, role);

        UUID roleId = role.getId();
        Set<UUID> grantedPermissionIds = rolePermissionRepo.findAllByIdRoleId(roleId).stream()
                .map(rp -> rp.getId().getPermissionId())
                .collect(Collectors.toUnmodifiableSet());

        List<RolePermissionItem> items = permissionRepo.findAll().stream()
                .sorted(RolePermissionAdminUseCase::byResourceThenCode)
                .map(p -> new RolePermissionItem(
                        p.getCode(),
                        p.getResource(),
                        p.getAction(),
                        grantedPermissionIds.contains(p.getId()),
                        RestrictedPermissions.isRestricted(p.getCode())))
                .toList();

        return new RolePermissionsResponse(
                role.getCode(),
                role.getScope() == null ? null : role.getScope().name(),
                isSystemRole(role),
                canEdit(ctx, role),
                items);
    }

    // ----------------------------------------------------------------- WRITE

    /**
     * Replace-set the permissions on {@code roleCode} with {@code permissionCodes}.
     * Applies the guard order documented on the class. Returns the refreshed
     * matrix (same shape as {@link #getRolePermissions(String)}).
     */
    @Transactional
    @CacheEvict(cacheNames = CacheNames.ROLE_PERMISSION_CODES, allEntries = true)
    public RolePermissionsResponse replaceRolePermissions(String roleCode, List<String> permissionCodes) {
        TenantContext ctx = TenantContextHolder.requireActive();

        // (a) role must exist — resolved by CODE (system, else caller-tenant custom;
        // a foreign custom code never resolves → 404, preserving C-1 isolation).
        RoleJpaEntity role = resolveRoleByCode(ctx, roleCode);
        UUID roleId = role.getId();

        // (a2) C-1 — tenant-ownership guard (BOLA fix, defense-in-depth). A CUSTOM
        // role must belong to the caller's active tenant (super-admin may act
        // cross-tenant); a foreign custom role → 404 (no reveal), no save, no audit.
        // Custom resolution above is already tenant-scoped; the guard backstops the
        // system path. System roles pass through here and are gated by (b) below.
        ownershipGuard.requireCanManage(ctx, role);

        // (b) system-role edit gate.
        if (isSystemRole(role) && !canEdit(ctx, role)) {
            // 403, not 404: the role is a well-known system role, no probing risk.
            throw new PermissionDeniedException(
                    "Only HRLab Super Admin may edit a system role's permissions");
        }

        // (c)+(d) restricted + caller-not-held + resolve — delegated to the shared
        // RolePermissionGuard so the no-escalation rules are NOT duplicated between
        // this use case and CustomRoleUseCase (slice E3). Throws 422
        // PERMISSION_RESTRICTED / PERMISSION_NOT_HELD_BY_CALLER / PERMISSION_UNKNOWN.
        Set<PermissionJpaEntity> desiredPermissions = guard.validateAndResolve(ctx, permissionCodes);
        Set<UUID> desiredPermissionIds = desiredPermissions.stream()
                .map(PermissionJpaEntity::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // (e) compute + apply delta. Current grants first.
        List<RolePermissionJpaEntity> current = rolePermissionRepo.findAllByIdRoleId(roleId);
        Set<UUID> currentPermissionIds = current.stream()
                .map(rp -> rp.getId().getPermissionId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // Inserts: desired - current.
        for (PermissionJpaEntity p : desiredPermissions) {
            if (!currentPermissionIds.contains(p.getId())) {
                rolePermissionRepo.save(
                        new RolePermissionJpaEntity(new RolePermissionId(roleId, p.getId())));
                auditChange(ctx, role, p.getCode(), AuditAction.ROLE_PERMISSION_GRANTED);
            }
        }

        // Deletes: current - desired.
        for (RolePermissionJpaEntity rp : current) {
            UUID permissionId = rp.getId().getPermissionId();
            if (!desiredPermissionIds.contains(permissionId)) {
                rolePermissionRepo.delete(rp);
                auditChange(ctx, role, codeOf(permissionId), AuditAction.ROLE_PERMISSION_REVOKED);
            }
        }

        return getRolePermissions(role.getCode());
    }

    // --------------------------------------------------------------- HELPERS

    /**
     * Resolve a role by CODE for the permission-matrix endpoints (matches the FE,
     * which keys the matrix by code). Fail-closed order: SYSTEM role by code
     * first (globally unique, tenant-less), else the CALLER'S-TENANT CUSTOM role
     * by code, else 404 (no existence reveal).
     *
     * <p>C-1 isolation: custom resolution is restricted to {@code ctx.tenantId()}
     * via {@code findByTenantIdAndCode}, so another tenant's custom code never
     * resolves here — it falls through to the 404, and a caller can never read or
     * edit a foreign tenant's custom role through a code. A custom code can never
     * equal a system code (the create path rejects that), so the system-first
     * order is unambiguous.
     */
    private RoleJpaEntity resolveRoleByCode(TenantContext ctx, String roleCode) {
        String code = roleCode == null ? "" : roleCode.trim();
        if (code.isEmpty()) {
            throw new TenantAccessDeniedException(); // → 404, no reveal
        }
        return roleRepo.findByCodeAndIsSystemTrue(code)
                .or(() -> ctx.tenantId() == null
                        ? Optional.empty()
                        : roleRepo.findByTenantIdAndCode(ctx.tenantId(), code))
                // Unknown / cross-tenant code → 404 (NOT_FOUND), no reveal.
                .orElseThrow(TenantAccessDeniedException::new);
    }

    /**
     * Reads the real {@code is_system} column (slice E3 added it). The 11 seeded
     * roles are {@code is_system = true}; tenant-defined custom roles are
     * {@code is_system = false}. The single source of truth for the system/custom
     * distinction lives on the entity now.
     */
    private static boolean isSystemRole(RoleJpaEntity role) {
        return role.isSystem();
    }

    /**
     * A caller may edit a SYSTEM role's permissions only if they hold
     * {@code USER_ROLE_ASSIGN_HRLAB} — i.e. HRLAB_SUPER_ADMIN. A custom role is
     * editable here too (non-system → {@code true}); in practice tenant admins
     * edit custom-role permissions through {@code CustomRoleUseCase} (slice E3),
     * which reuses the same {@link RolePermissionGuard}. Reused for both the
     * {@code editable_by_caller} response flag and the write gate so the two never
     * diverge.
     */
    private static boolean canEdit(TenantContext ctx, RoleJpaEntity role) {
        if (isSystemRole(role)) {
            return ctx.hasPermission(PermissionCodes.USER_ROLE_ASSIGN_HRLAB);
        }
        return true;
    }

    private String codeOf(UUID permissionId) {
        return permissionRepo.findById(permissionId)
                .map(PermissionJpaEntity::getCode)
                .orElse(permissionId.toString());
    }

    private void auditChange(TenantContext ctx, RoleJpaEntity role, String permissionCode, String action) {
        audit.record(AuditEvent.builder(ctx)
                // Roles are global control-plane data — no tenant scope. The actor's
                // active tenant is still recorded for forensics on WHO acted.
                .action(action)
                .entityType("RolePermission")
                .entityId(role.getId())
                .reason("roleCode=" + role.getCode() + " permissionCode=" + permissionCode)
                .build());
    }

    private static int byResourceThenCode(PermissionJpaEntity a, PermissionJpaEntity b) {
        String ra = a.getResource() == null ? "" : a.getResource();
        String rb = b.getResource() == null ? "" : b.getResource();
        int byResource = ra.compareTo(rb);
        if (byResource != 0) return byResource;
        return a.getCode().compareTo(b.getCode());
    }
}

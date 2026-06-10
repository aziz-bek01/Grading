package uz.hrlab.grading.access.application;

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
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.UnprocessableEntityException;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin CRUD over a single role's PERMISSION set (slice E2).
 *
 * <p>Backs {@code GET /api/v1/roles/{roleId}/permissions} (read the matrix) and
 * {@code PUT /api/v1/roles/{roleId}/permissions} (replace-set grant/revoke). The
 * RBAC gate ({@code USER_ACCESS_MANAGE}) is at the controller; this use case adds
 * the business guards and the audit trail.
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
 *   <li><b>(a) role exists</b> — unknown {@code roleId} → 404 (no existence reveal).</li>
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
 * re-queries {@code role_permissions} per request — there is no cached snapshot
 * to go stale.
 */
@Service
public class RolePermissionAdminUseCase {

    private final RoleRepository roleRepo;
    private final PermissionRepository permissionRepo;
    private final RolePermissionRepository rolePermissionRepo;
    private final AuditService audit;

    public RolePermissionAdminUseCase(RoleRepository roleRepo,
                                      PermissionRepository permissionRepo,
                                      RolePermissionRepository rolePermissionRepo,
                                      AuditService audit) {
        this.roleRepo = roleRepo;
        this.permissionRepo = permissionRepo;
        this.rolePermissionRepo = rolePermissionRepo;
        this.audit = audit;
    }

    // ------------------------------------------------------------------- READ

    /**
     * Read the full permission matrix for {@code roleId}. Returns EVERY catalog
     * permission with {@code granted} (on this role) + {@code restricted} flags,
     * plus {@code editable_by_caller} so the FE can render the matrix read-only
     * for callers who may not edit this (system) role.
     */
    @Transactional(readOnly = true)
    public RolePermissionsResponse getRolePermissions(UUID roleId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        RoleJpaEntity role = requireRole(roleId);

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
     * Replace-set the permissions on {@code roleId} with {@code permissionCodes}.
     * Applies the guard order documented on the class. Returns the refreshed
     * matrix (same shape as {@link #getRolePermissions(UUID)}).
     */
    @Transactional
    public RolePermissionsResponse replaceRolePermissions(UUID roleId, List<String> permissionCodes) {
        TenantContext ctx = TenantContextHolder.requireActive();

        // (a) role must exist.
        RoleJpaEntity role = requireRole(roleId);

        // (b) system-role edit gate.
        if (isSystemRole(role) && !canEdit(ctx, role)) {
            // 403, not 404: the role is a well-known system role, no probing risk.
            throw new PermissionDeniedException(
                    "Only HRLab Super Admin may edit a system role's permissions");
        }

        // Normalize desired set (dedupe, reject nulls/blanks/unknowns up front).
        Set<String> desired = new LinkedHashSet<>();
        for (String code : permissionCodes) {
            if (code == null || code.isBlank()) {
                throw new UnprocessableEntityException("PERMISSION_UNKNOWN",
                        "permission_codes must not contain null/blank entries");
            }
            desired.add(code);
        }

        // (c) restricted — no requested code may be in the restricted set.
        for (String code : desired) {
            if (RestrictedPermissions.isRestricted(code)) {
                throw new UnprocessableEntityException("PERMISSION_RESTRICTED",
                        "Permission '" + code + "' may not be granted to any role");
            }
        }

        // (d) caller-not-held — no privilege escalation: you cannot grant a
        // permission you do not yourself hold.
        for (String code : desired) {
            if (!ctx.hasPermission(code)) {
                throw new UnprocessableEntityException("PERMISSION_NOT_HELD_BY_CALLER",
                        "You cannot grant a permission you do not hold");
            }
        }

        // Resolve every desired code to a catalog permission row (unknown → 422).
        // Done AFTER the held check so an unknown code surfaces as a clear error
        // only once it has otherwise passed; held check on an unknown code always
        // fails first anyway (ctx cannot hold a non-existent code), so in practice
        // this resolves the codes we are about to insert.
        Set<PermissionJpaEntity> desiredPermissions = new LinkedHashSet<>();
        for (String code : desired) {
            PermissionJpaEntity p = permissionRepo.findByCode(code)
                    .orElseThrow(() -> new UnprocessableEntityException("PERMISSION_UNKNOWN",
                            "Unknown permission code"));
            desiredPermissions.add(p);
        }
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

        return getRolePermissions(roleId);
    }

    // --------------------------------------------------------------- HELPERS

    private RoleJpaEntity requireRole(UUID roleId) {
        return roleRepo.findById(roleId)
                // Unknown role → 404 via TenantAccessDenied (NOT_FOUND), no reveal.
                .orElseThrow(TenantAccessDeniedException::new);
    }

    /**
     * Every seeded role is a system role today. The {@code roles} table has no
     * {@code is_system} column yet (arrives in slice E3 with tenant-scoped custom
     * roles); this method is the single placeholder so the rule lives in one
     * place and flips to a column read without touching callers.
     */
    private static boolean isSystemRole(RoleJpaEntity role) {
        return true;
    }

    /**
     * A caller may edit a (system) role only if they hold
     * {@code USER_ROLE_ASSIGN_HRLAB} — i.e. HRLAB_SUPER_ADMIN. Reused for both
     * the {@code editable_by_caller} response flag and the write gate so the two
     * never diverge.
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
        audit.record(AuditEvent.builder()
                // Roles are global control-plane data — no tenant scope. The actor's
                // active tenant is still recorded for forensics on WHO acted.
                .tenantId(ctx.tenantId())
                .actorUserId(ctx.userId())
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

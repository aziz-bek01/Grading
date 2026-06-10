package uz.hrlab.grading.access.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.access.infrastructure.PermissionJpaEntity;
import uz.hrlab.grading.access.infrastructure.PermissionRepository;
import uz.hrlab.grading.common.exception.UnprocessableEntityException;
import uz.hrlab.grading.tenancy.application.TenantContext;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared permission-grant guard reused by every writer of {@code role_permissions}
 * (slice E2 {@link RolePermissionAdminUseCase} and slice E3 {@link CustomRoleUseCase}).
 *
 * <p>Extracted so the no-escalation rules live in EXACTLY ONE place — duplicating
 * them across use cases would risk the two drifting apart and re-opening a
 * privilege-escalation hole. The guard enforces, in order (first failure wins):
 * <ol>
 *   <li><b>shape</b> — no null/blank entry → 422 {@code PERMISSION_UNKNOWN};</li>
 *   <li><b>restricted</b> — no code in {@link RestrictedPermissions} → 422
 *       {@code PERMISSION_RESTRICTED} (codes that may never be granted to ANY
 *       role: salary, audit, tenant lifecycle, escalation vectors);</li>
 *   <li><b>caller-held</b> — every code must be HELD BY THE CALLER → 422
 *       {@code PERMISSION_NOT_HELD_BY_CALLER} (you cannot grant what you do not
 *       have);</li>
 *   <li><b>resolve</b> — each code maps to a catalog {@code permissions} row →
 *       422 {@code PERMISSION_UNKNOWN} for an unknown code.</li>
 * </ol>
 *
 * <p>The guard is read-only and stateless; it performs NO writes and NO audit —
 * the caller owns the {@code role_permissions} delta + audit trail. It returns
 * the resolved {@link PermissionJpaEntity} set (insertion-ordered, deduped) so
 * the caller can diff/insert without re-querying the catalog.
 */
@Component
public class RolePermissionGuard {

    private final PermissionRepository permissionRepo;

    public RolePermissionGuard(PermissionRepository permissionRepo) {
        this.permissionRepo = permissionRepo;
    }

    /**
     * Validate {@code requestedCodes} against the restricted list and the caller's
     * own permissions, then resolve them to catalog rows.
     *
     * @param ctx            the authenticated caller (source of held permissions)
     * @param requestedCodes the desired permission codes (may contain duplicates)
     * @return the resolved, deduped, insertion-ordered permission rows
     * @throws UnprocessableEntityException on any guard failure (422)
     */
    public Set<PermissionJpaEntity> validateAndResolve(TenantContext ctx, List<String> requestedCodes) {
        // (1) shape — dedupe and reject null/blank up front.
        Set<String> desired = new LinkedHashSet<>();
        for (String code : requestedCodes) {
            if (code == null || code.isBlank()) {
                throw new UnprocessableEntityException("PERMISSION_UNKNOWN",
                        "permission_codes must not contain null/blank entries");
            }
            desired.add(code);
        }

        // (2) restricted — no requested code may be in the restricted set.
        for (String code : desired) {
            if (RestrictedPermissions.isRestricted(code)) {
                throw new UnprocessableEntityException("PERMISSION_RESTRICTED",
                        "Permission '" + code + "' may not be granted to any role");
            }
        }

        // (3) caller-held — no privilege escalation: you cannot grant a permission
        // you do not yourself hold.
        for (String code : desired) {
            if (ctx == null || !ctx.hasPermission(code)) {
                throw new UnprocessableEntityException("PERMISSION_NOT_HELD_BY_CALLER",
                        "You cannot grant a permission you do not hold");
            }
        }

        // (4) resolve — each desired code must map to a catalog permission row.
        Set<PermissionJpaEntity> resolved = new LinkedHashSet<>();
        for (String code : desired) {
            PermissionJpaEntity p = permissionRepo.findByCode(code)
                    .orElseThrow(() -> new UnprocessableEntityException("PERMISSION_UNKNOWN",
                            "Unknown permission code"));
            resolved.add(p);
        }
        return resolved;
    }
}

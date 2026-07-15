package uz.hrlab.grading.access.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.access.infrastructure.UserRoleRepository;

import java.util.UUID;

/**
 * The ONE platform-super-admin predicate (Fix A — platform super admins see and
 * switch into ALL ACTIVE tenants). This is the single linchpin reused by:
 * <ul>
 *   <li>{@code GetCurrentUserUseCase} — the read side ({@code /users/me}
 *       switcher lists all ACTIVE tenants for a super admin);</li>
 *   <li>{@code JwtTenantContextResolver} — the authz side (synthesizes a tenant
 *       context for an ACTIVE tenant the super admin has no membership in);</li>
 *   <li>{@code TenantContextFilter} — the F-205 fail-closed exception (the same
 *       cross-tenant activation must not be 403'd).</li>
 * </ul>
 *
 * <h3>Why it cannot be spoofed or leak to another role</h3>
 * The answer is derived STRICTLY from the user's REAL, persisted role grants on
 * REAL ACTIVE memberships ({@code user_roles → roles} joined to
 * {@code user_tenant_memberships}); it reads NOTHING from the JWT claims, the
 * {@code X-Active-Tenant-Id} header, or the requested/target tenant. It matches
 * the SINGLE {@code HRLAB_SUPER_ADMIN} system role and never any other role — so
 * a user who merely holds many tenant memberships, or holds any client-side
 * role, is {@code false}. Because the underlying query pins
 * {@code roles.is_system = true} and {@code user_tenant_memberships.status =
 * ACTIVE}, neither a look-alike tenant custom role nor a revoked super-admin
 * membership can turn it {@code true}.
 *
 * <p>Stateless and thread-safe.
 */
@Component
public class PlatformSuperAdminChecker {

    private final UserRoleRepository userRoles;

    public PlatformSuperAdminChecker(UserRoleRepository userRoles) {
        this.userRoles = userRoles;
    }

    /**
     * {@code true} iff {@code userId} is a platform Super Admin — i.e. holds the
     * {@code HRLAB_SUPER_ADMIN} system role on at least one ACTIVE membership.
     * Fail-closed: a {@code null} user id is never a super admin.
     */
    public boolean isPlatformSuperAdmin(UUID userId) {
        if (userId == null) {
            return false;
        }
        return userRoles.existsActiveSystemRoleHolder(userId, RoleCodes.HRLAB_SUPER_ADMIN);
    }
}

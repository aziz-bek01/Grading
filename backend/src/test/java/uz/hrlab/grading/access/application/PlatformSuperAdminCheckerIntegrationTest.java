package uz.hrlab.grading.access.application;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.hrlab.grading.AbstractIntegrationTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Truth table for the ONE platform-super-admin predicate
 * ({@link PlatformSuperAdminChecker}) — the linchpin of Fix A.
 *
 * <p>DB-backed (the predicate is a {@code user_roles → roles} join over ACTIVE
 * memberships against the seeded {@code HRLAB_SUPER_ADMIN} system role) so it
 * exercises the real query, not a mock. It proves the predicate is {@code true}
 * ONLY for a genuine {@code HRLAB_SUPER_ADMIN} holder on an ACTIVE membership and
 * {@code false} for every other shape — a client role, a user with many
 * memberships but no super-admin role, and a super-admin role on a REVOKED
 * membership. This is what makes the carve-out un-spoofable and un-widenable.
 */
@Tag("tenant-isolation")
@Tag("integration")
class PlatformSuperAdminCheckerIntegrationTest extends AbstractIntegrationTest {

    @Autowired PlatformSuperAdminChecker checker;

    @Test
    void superAdminHolderOnActiveMembershipIsTrue() {
        UUID tenant = seedTenant(UUID.randomUUID());
        UUID userId = seedUserRow();
        UUID membership = seedMembership(userId, tenant, "ACTIVE");
        attachRole(membership, RoleCodes.HRLAB_SUPER_ADMIN);

        assertThat(checker.isPlatformSuperAdmin(userId)).isTrue();
    }

    @Test
    void clientRoleHolderIsFalse() {
        UUID tenant = seedTenant(UUID.randomUUID());
        UUID userId = seedUserRow();
        UUID membership = seedMembership(userId, tenant, "ACTIVE");
        attachRole(membership, RoleCodes.CLIENT_HR_DIRECTOR);

        assertThat(checker.isPlatformSuperAdmin(userId))
                .as("a client role is never a platform super admin")
                .isFalse();
    }

    @Test
    void userWithManyMembershipsButNoSuperAdminRoleIsFalse() {
        UUID userId = seedUserRow();
        for (int i = 0; i < 3; i++) {
            UUID tenant = seedTenant(UUID.randomUUID());
            UUID membership = seedMembership(userId, tenant, "ACTIVE");
            attachRole(membership, RoleCodes.CLIENT_COMPANY_ADMIN);
        }

        assertThat(checker.isPlatformSuperAdmin(userId))
                .as("merely belonging to many tenants does not confer platform super admin")
                .isFalse();
    }

    @Test
    void superAdminRoleOnRevokedMembershipIsFalse() {
        UUID tenant = seedTenant(UUID.randomUUID());
        UUID userId = seedUserRow();
        // Holds the super-admin role, but the membership carrying it is REVOKED.
        UUID membership = seedMembership(userId, tenant, "REVOKED");
        attachRole(membership, RoleCodes.HRLAB_SUPER_ADMIN);

        assertThat(checker.isPlatformSuperAdmin(userId))
                .as("a revoked membership must not keep the super-admin predicate true")
                .isFalse();
    }

    @Test
    void nullUserIsFalse() {
        assertThat(checker.isPlatformSuperAdmin(null)).isFalse();
    }

    // ---------------------------------------------------------------- helpers

    private UUID seedUserRow() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO public.users (id, email, full_name, status, default_locale, version) "
                        + "VALUES (?, ?, 'Predicate User', 'ACTIVE', 'ru-RU', 0)",
                userId, "pred-" + userId + "@hrlab.uz");
        return userId;
    }

    private UUID seedMembership(UUID userId, UUID tenantId, String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO public.user_tenant_memberships (id, user_id, tenant_id, status, "
                        + "salary_data_permission, version) VALUES (?, ?, ?, ?, false, 0)",
                id, userId, tenantId, status);
        return id;
    }

    private void attachRole(UUID membershipId, String roleCode) {
        jdbcTemplate.update(
                "INSERT INTO public.user_roles (id, user_tenant_membership_id, role_id) "
                        + "SELECT ?, ?, r.id FROM public.roles r WHERE r.code = ?",
                UUID.randomUUID(), membershipId, roleCode);
    }
}

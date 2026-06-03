package uz.hrlab.grading.tenancy.application;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.access.application.RoleCodes;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link TenantManagementPolicy} — the ABAC gate of the
 * Tenant + Client Company control-plane module (Sprint F, E9 — F-1 BE).
 *
 * <p>Covers the four invariants the policy must uphold:
 * <ol>
 *   <li>HRLAB_SUPER_ADMIN bypasses every scope check;</li>
 *   <li>non-super-admin without an active tenant membership cannot view
 *       a tenant they don't belong to (404 / TenantAccessDenied);</li>
 *   <li>cross-tenant listing is denied for non-super-admin (returns
 *       scoped tenant ids = own memberships);</li>
 *   <li>mutation requires the caller's ACTIVE tenant to match the URL
 *       target tenant — being a passive member is not enough.</li>
 * </ol>
 */
@Tag("security")
class TenantManagementPolicyTest {

    private static final UUID CALLER_ID    = UUID.randomUUID();
    private static final UUID OWN_TENANT   = UUID.randomUUID();
    private static final UUID OTHER_TENANT = UUID.randomUUID();

    private final TenantManagementPolicy policy = new TenantManagementPolicy();

    // ------------------------------------------------------------------------
    //  1. Super Admin bypass
    // ------------------------------------------------------------------------

    @Test
    void superAdminCanListAcrossTenants() {
        TenantContext ctx = ctx(Set.of(RoleCodes.HRLAB_SUPER_ADMIN), Set.of());

        assertThat(policy.canListAcrossTenants(ctx)).isTrue();
    }

    @Test
    void superAdminCanViewAnyTenant() {
        TenantContext ctx = ctx(Set.of(RoleCodes.HRLAB_SUPER_ADMIN), Set.of());

        assertThatCode(() -> policy.requireCanViewTenant(ctx, OTHER_TENANT))
                .doesNotThrowAnyException();
    }

    @Test
    void superAdminCanManageAnyTenant() {
        TenantContext ctx = ctx(Set.of(RoleCodes.HRLAB_SUPER_ADMIN), Set.of());

        assertThatCode(() -> policy.requireCanManageTenant(ctx, OTHER_TENANT))
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------------
    //  2. Non-super-admin scope is membership-bound
    // ------------------------------------------------------------------------

    @Test
    void clientAdminCannotListAcrossTenants() {
        TenantContext ctx = ctx(Set.of(RoleCodes.CLIENT_COMPANY_ADMIN), Set.of(OWN_TENANT));

        assertThat(policy.canListAcrossTenants(ctx)).isFalse();
    }

    @Test
    void clientAdminScopedToOwnMemberships() {
        TenantContext ctx = ctx(Set.of(RoleCodes.CLIENT_COMPANY_ADMIN),
                Set.of(OWN_TENANT, OTHER_TENANT));

        Set<UUID> scoped = policy.scopedTenantIds(ctx);

        assertThat(scoped).containsExactlyInAnyOrder(OWN_TENANT, OTHER_TENANT);
    }

    @Test
    void scopedTenantIdsIsEmptyWhenNoMemberships() {
        TenantContext ctx = ctx(Set.of(RoleCodes.CLIENT_COMPANY_ADMIN), Set.of());

        Set<UUID> scoped = policy.scopedTenantIds(ctx);

        assertThat(scoped).isEmpty();
    }

    // ------------------------------------------------------------------------
    //  3. View denial across tenants
    // ------------------------------------------------------------------------

    @Test
    void viewDeniedWhenNotMember() {
        TenantContext ctx = ctx(Set.of(RoleCodes.CLIENT_COMPANY_ADMIN), Set.of(OWN_TENANT));

        assertThatThrownBy(() -> policy.requireCanViewTenant(ctx, OTHER_TENANT))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void viewPermittedForMember() {
        TenantContext ctx = ctx(Set.of(RoleCodes.CLIENT_COMPANY_ADMIN), Set.of(OWN_TENANT));

        assertThatCode(() -> policy.requireCanViewTenant(ctx, OWN_TENANT))
                .doesNotThrowAnyException();
    }

    @Test
    void viewDeniedWhenTargetTenantIdIsNull() {
        TenantContext ctx = ctx(Set.of(RoleCodes.CLIENT_COMPANY_ADMIN), Set.of(OWN_TENANT));

        assertThatThrownBy(() -> policy.requireCanViewTenant(ctx, null))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    // ------------------------------------------------------------------------
    //  4. Mutation requires ACTIVE tenant match (defense in depth)
    // ------------------------------------------------------------------------

    @Test
    void mutationDeniedForPassiveMembership() {
        // Caller is a member of OTHER_TENANT but their ACTIVE tenant is
        // OWN_TENANT — they cannot patch OTHER_TENANT through a multi-tenant
        // membership trick.
        TenantContext ctx = ctxActiveAndMemberships(OWN_TENANT,
                Set.of(RoleCodes.CLIENT_COMPANY_ADMIN),
                Set.of(OWN_TENANT, OTHER_TENANT));

        assertThatThrownBy(() -> policy.requireCanManageTenant(ctx, OTHER_TENANT))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void mutationPermittedForOwnActiveTenant() {
        TenantContext ctx = ctxActiveAndMemberships(OWN_TENANT,
                Set.of(RoleCodes.CLIENT_COMPANY_ADMIN),
                Set.of(OWN_TENANT));

        assertThatCode(() -> policy.requireCanManageTenant(ctx, OWN_TENANT))
                .doesNotThrowAnyException();
    }

    @Test
    void mutationDeniedForNonMember() {
        TenantContext ctx = ctxActiveAndMemberships(OWN_TENANT,
                Set.of(RoleCodes.CLIENT_COMPANY_ADMIN),
                Set.of(OWN_TENANT));

        assertThatThrownBy(() -> policy.requireCanManageTenant(ctx, OTHER_TENANT))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void clientCompanyMutationFollowsSameTenantGate() {
        TenantContext ctx = ctxActiveAndMemberships(OWN_TENANT,
                Set.of(RoleCodes.CLIENT_COMPANY_ADMIN),
                Set.of(OWN_TENANT));

        assertThatCode(() -> policy.requireCanManageClientCompany(ctx, OWN_TENANT))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> policy.requireCanManageClientCompany(ctx, OTHER_TENANT))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    // ------------------------------------------------------------------------
    //  helpers
    // ------------------------------------------------------------------------

    private static TenantContext ctx(Set<String> roles, Set<UUID> tenantMemberships) {
        return new TenantContext(CALLER_ID, OWN_TENANT, Set.of(), roles, Set.of(),
                Set.of(), false, "ru-RU", tenantMemberships);
    }

    private static TenantContext ctxActiveAndMemberships(UUID activeTenant, Set<String> roles,
                                                         Set<UUID> tenantMemberships) {
        return new TenantContext(CALLER_ID, activeTenant, Set.of(), roles, Set.of(),
                Set.of(), false, "ru-RU", tenantMemberships);
    }
}

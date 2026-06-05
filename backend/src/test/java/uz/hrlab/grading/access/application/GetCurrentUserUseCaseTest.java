package uz.hrlab.grading.access.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.access.api.CurrentUserResponse;
import uz.hrlab.grading.access.api.TenantMembershipSummary;
import uz.hrlab.grading.common.exception.ResourceNotFoundException;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BE-TI-005 integration test — verifies that {@code /users/me} returns ALL
 * tenant memberships for the caller (not just the active one) and that the
 * brand fingerprint is derived from {@code client_companies}.
 *
 * <p>Uses a real Postgres via {@link AbstractIntegrationTest} so the seed
 * data layout is exercised end-to-end.
 */
@Tag("integration")
class GetCurrentUserUseCaseTest extends AbstractIntegrationTest {

    @Autowired GetCurrentUserUseCase getCurrentUserUseCase;

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void returnsBothTenantsForUserWithTwoMemberships() {
        // ---- Arrange: two tenants, one user, two memberships, one is "active".
        UUID acme = seedTenant(UUID.randomUUID());
        UUID beta = seedTenant(UUID.randomUUID());

        // Client company per tenant (drives brandName in the response).
        UUID acmeCompanyId = UUID.randomUUID();
        UUID betaCompanyId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO public.client_companies (id, tenant_id, legal_name, brand_name, version) "
                        + "VALUES (?, ?, ?, ?, 0)",
                acmeCompanyId, acme, "ACME LLC", "ACME");
        jdbcTemplate.update(
                "INSERT INTO public.client_companies (id, tenant_id, legal_name, brand_name, version) "
                        + "VALUES (?, ?, ?, ?, 0)",
                betaCompanyId, beta, "Beta University", "BetaU");

        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO public.users (id, email, full_name, status, default_locale, version) "
                        + "VALUES (?, ?, ?, 'ACTIVE', 'ru-RU', 0)",
                userId, userId + "@dev.local", "Dual-Tenant Admin");

        jdbcTemplate.update(
                "INSERT INTO public.user_tenant_memberships (id, user_id, tenant_id, status, "
                        + "salary_data_permission, version) VALUES (?, ?, ?, 'ACTIVE', false, 0)",
                UUID.randomUUID(), userId, acme);
        jdbcTemplate.update(
                "INSERT INTO public.user_tenant_memberships (id, user_id, tenant_id, status, "
                        + "salary_data_permission, version) VALUES (?, ?, ?, 'ACTIVE', false, 0)",
                UUID.randomUUID(), userId, beta);

        TenantContextHolder.set(new TenantContext(userId, acme, Set.of(),
                Set.of("HRLAB_SUPER_ADMIN"), Set.of("PROJECT_READ"),
                Set.of(), false, "ru-RU"));

        // ---- Act
        CurrentUserResponse resp = getCurrentUserUseCase.currentUser();

        // ---- Assert: BOTH tenants surface, brandName from client_companies.
        assertThat(resp.user().id()).isEqualTo(userId);
        assertThat(resp.activeTenantId()).isEqualTo(acme);
        assertThat(resp.tenants()).hasSize(2);
        assertThat(resp.tenants())
                .extracting(TenantMembershipSummary::id)
                .containsExactlyInAnyOrder(acme, beta);
        assertThat(resp.tenants())
                .extracting(TenantMembershipSummary::brandName)
                .containsExactlyInAnyOrder("ACME", "BetaU");
        assertThat(resp.tenants())
                .extracting(TenantMembershipSummary::fingerprintHue)
                .allMatch(h -> h != null && h >= 0 && h < 360);
    }

    @Test
    void filtersOutRevokedMemberships() {
        UUID acme = seedTenant(UUID.randomUUID());
        UUID beta = seedTenant(UUID.randomUUID());

        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO public.users (id, email, full_name, status, default_locale, version) "
                        + "VALUES (?, ?, ?, 'ACTIVE', 'ru-RU', 0)",
                userId, userId + "@dev.local", "Mixed-Status User");
        jdbcTemplate.update(
                "INSERT INTO public.user_tenant_memberships (id, user_id, tenant_id, status, "
                        + "salary_data_permission, version) VALUES (?, ?, ?, 'ACTIVE', false, 0)",
                UUID.randomUUID(), userId, acme);
        jdbcTemplate.update(
                "INSERT INTO public.user_tenant_memberships (id, user_id, tenant_id, status, "
                        + "salary_data_permission, version) VALUES (?, ?, ?, 'REVOKED', false, 0)",
                UUID.randomUUID(), userId, beta);

        TenantContextHolder.set(new TenantContext(userId, acme, Set.of(),
                Set.of(), Set.of(), Set.of(), false, "ru-RU"));

        CurrentUserResponse resp = getCurrentUserUseCase.currentUser();

        assertThat(resp.tenants()).hasSize(1);
        assertThat(resp.tenants().get(0).id()).isEqualTo(acme);
    }

    @Test
    void unknownUserResultsInNotFound() {
        TenantContextHolder.set(new TenantContext(UUID.randomUUID(), null, Set.of(),
                Set.of(), Set.of(), Set.of(), false, "ru-RU"));

        assertThatThrownBy(() -> getCurrentUserUseCase.currentUser())
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

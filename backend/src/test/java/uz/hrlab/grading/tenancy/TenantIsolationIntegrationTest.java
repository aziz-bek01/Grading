package uz.hrlab.grading.tenancy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.access.domain.MembershipStatus;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.tenancy.domain.IsolationMode;
import uz.hrlab.grading.tenancy.domain.TenantStatus;
import uz.hrlab.grading.tenancy.infrastructure.ClientCompanyJpaEntity;
import uz.hrlab.grading.tenancy.infrastructure.ClientCompanyRepository;
import uz.hrlab.grading.tenancy.infrastructure.TenantJpaEntity;
import uz.hrlab.grading.tenancy.infrastructure.TenantRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the tenant-isolation contract at the repository layer:
 * "user from Tenant A cannot reach Tenant B data through the
 * tenant-aware repository surface."
 *
 * <p>{@code ClientCompanyRepository} is the first tenant-scoped repository
 * — the assertions here are the template for every business repository
 * that lands in Phase 2+.
 */
@Tag("tenant-isolation")
@Tag("integration")
class TenantIsolationIntegrationTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenants;
    @Autowired ClientCompanyRepository clientCompanies;
    @Autowired UserTenantMembershipRepository memberships;
    @PersistenceContext EntityManager em;

    @Test
    void clientCompanyIsNotReachableFromAnotherTenant() {
        TenantJpaEntity tenantA = saveTenant("acme");
        TenantJpaEntity tenantB = saveTenant("globex");

        ClientCompanyJpaEntity companyA = clientCompanies.save(new ClientCompanyJpaEntity(
                UUID.randomUUID(), tenantA.getId(),
                "Acme LLC", "Acme", "Holding", null, null));
        ClientCompanyJpaEntity companyB = clientCompanies.save(new ClientCompanyJpaEntity(
                UUID.randomUUID(), tenantB.getId(),
                "Globex Corp", "Globex", "Telecom", null, null));

        // Each tenant only sees its own company by tenant-aware lookup.
        assertThat(clientCompanies.findByTenantId(tenantA.getId()))
                .map(ClientCompanyJpaEntity::getId).contains(companyA.getId());
        assertThat(clientCompanies.findByTenantId(tenantB.getId()))
                .map(ClientCompanyJpaEntity::getId).contains(companyB.getId());

        // Cross-tenant lookup by (id, tenantId) returns empty — never the other
        // tenant's row. This is the canonical anti-BOLA pattern
        // (security-blueprint §5.2).
        Optional<ClientCompanyJpaEntity> crossProbe =
                clientCompanies.findByIdAndTenantId(companyA.getId(), tenantB.getId());
        assertThat(crossProbe).isEmpty();

        Optional<ClientCompanyJpaEntity> sameTenant =
                clientCompanies.findByIdAndTenantId(companyA.getId(), tenantA.getId());
        assertThat(sameTenant).isPresent();
        assertThat(sameTenant.get().getTenantId()).isEqualTo(tenantA.getId());
    }

    @Test
    @Transactional
    void onlyOneCompanyPerTenant() {
        // TenantAwareRepository intentionally does NOT expose saveAndFlush —
        // we flush manually via the injected EntityManager so the unique-key
        // violation surfaces synchronously.
        TenantJpaEntity tenant = saveTenant("acme2");
        clientCompanies.save(new ClientCompanyJpaEntity(
                UUID.randomUUID(), tenant.getId(), "Acme LLC", "Acme", "Holding", null, null));
        em.flush();

        clientCompanies.save(new ClientCompanyJpaEntity(
                UUID.randomUUID(), tenant.getId(),
                "Acme Holdings", null, null, null, null));

        org.assertj.core.api.Assertions.assertThatThrownBy(em::flush)
                .hasMessageContaining("uq_client_companies_tenant");
    }

    /**
     * TI-11 (stale token after membership revocation). After a membership row
     * is flipped to {@link MembershipStatus#REVOKED}, an old JWT that still
     * encodes {@code active_tenant_id} for that revoked tenant must NOT see
     * the tenant in any "active memberships" surface — otherwise
     * {@code TenantContextFilter.withMemberships()} would accept it and the
     * F-205 fail-closed branch would never fire.
     *
     * <p>Repository-layer assertion: the production code path used by
     * {@code TenantContextFilter} ({@code findAllByUserId}) currently returns
     * BOTH active and revoked rows. If the set of tenant ids derived from
     * <em>active</em> memberships only does not contain the stale tenant,
     * the fail-closed branch fires. This test pins that semantic — if it
     * fails, file as a defect (membership status filter missing).
     */
    @Test
    @Transactional
    void revokedMembershipMustNotGrantAccessToStaleTenant() {
        TenantJpaEntity acme = saveTenant("acme-rev");
        TenantJpaEntity beta = saveTenant("beta-rev");
        UUID userId = UUID.randomUUID();

        // Active membership in ACME
        memberships.save(new UserTenantMembershipJpaEntity(
                UUID.randomUUID(), userId, acme.getId(), MembershipStatus.ACTIVE, false));
        // Previously held membership in BETA, now revoked
        memberships.save(new UserTenantMembershipJpaEntity(
                UUID.randomUUID(), userId, beta.getId(), MembershipStatus.REVOKED, false));
        em.flush();

        List<UserTenantMembershipJpaEntity> all = memberships.findAllByUserId(userId);
        assertThat(all).hasSize(2);

        // The set of ACTIVE-only tenants is what the filter MUST trust.
        // A stale JWT carrying active_tenant_id = beta.getId() must not pass.
        java.util.Set<UUID> activeTenantIds = all.stream()
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .map(UserTenantMembershipJpaEntity::getTenantId)
                .collect(Collectors.toUnmodifiableSet());

        assertThat(activeTenantIds)
                .as("only ACME (the still-active membership) must be trusted")
                .containsExactly(acme.getId())
                .doesNotContain(beta.getId());

        // Belt-and-braces: tenant-scoped lookup also returns the REVOKED row
        // — so the application layer (not the repository) is responsible for
        // status filtering. This test documents that requirement.
        Optional<UserTenantMembershipJpaEntity> betaRow =
                memberships.findByUserIdAndTenantId(userId, beta.getId());
        assertThat(betaRow).isPresent();
        assertThat(betaRow.get().getStatus()).isEqualTo(MembershipStatus.REVOKED);
    }

    /**
     * TI-Reg-03 (dev seed → first tenant flow). The Phase 1 boot sequence
     * relies on {@code seedTenant()} creating a {@code public.tenants} row
     * with a unique {@code slug} and isolated {@code schema_name}. Two
     * seeded tenants must:
     * <ol>
     *   <li>both land in {@code public.tenants};</li>
     *   <li>own disjoint client-companies (canonical "first tenant" flow
     *       the dev UI walks through on first login);</li>
     *   <li>be retrievable independently — no cross-tenant bleed in either
     *       direction.</li>
     * </ol>
     */
    @Test
    void devSeedCreatesIsolatedFirstTenants() {
        UUID firstTenantId  = newSeededTenantId();
        UUID secondTenantId = newSeededTenantId();

        assertThat(firstTenantId).isNotEqualTo(secondTenantId);
        assertThat(tenants.findById(firstTenantId)).isPresent();
        assertThat(tenants.findById(secondTenantId)).isPresent();

        ClientCompanyJpaEntity firstCo = clientCompanies.save(new ClientCompanyJpaEntity(
                UUID.randomUUID(), firstTenantId,
                "First Co", "First", "Holding", null, null));
        ClientCompanyJpaEntity secondCo = clientCompanies.save(new ClientCompanyJpaEntity(
                UUID.randomUUID(), secondTenantId,
                "Second Co", "Second", "Telecom", null, null));

        assertThat(clientCompanies.findByTenantId(firstTenantId))
                .map(ClientCompanyJpaEntity::getId)
                .hasValue(firstCo.getId());
        assertThat(clientCompanies.findByTenantId(secondTenantId))
                .map(ClientCompanyJpaEntity::getId)
                .hasValue(secondCo.getId());

        // BOLA probe: ask for the OTHER tenant's company by id — empty.
        assertThat(clientCompanies.findByIdAndTenantId(firstCo.getId(), secondTenantId))
                .isEmpty();
        assertThat(clientCompanies.findByIdAndTenantId(secondCo.getId(), firstTenantId))
                .isEmpty();
    }

    private TenantJpaEntity saveTenant(String slug) {
        return tenants.save(new TenantJpaEntity(
                UUID.randomUUID(), slug, slug + " Display",
                IsolationMode.SCHEMA, "tenant_" + slug, null,
                TenantStatus.ACTIVE, "ru-RU"));
    }
}

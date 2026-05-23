package uz.hrlab.grading.tenancy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.tenancy.domain.IsolationMode;
import uz.hrlab.grading.tenancy.domain.TenantStatus;
import uz.hrlab.grading.tenancy.infrastructure.ClientCompanyJpaEntity;
import uz.hrlab.grading.tenancy.infrastructure.ClientCompanyRepository;
import uz.hrlab.grading.tenancy.infrastructure.TenantJpaEntity;
import uz.hrlab.grading.tenancy.infrastructure.TenantRepository;

import java.util.Optional;
import java.util.UUID;

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

    private TenantJpaEntity saveTenant(String slug) {
        return tenants.save(new TenantJpaEntity(
                UUID.randomUUID(), slug, slug + " Display",
                IsolationMode.SCHEMA, "tenant_" + slug, null,
                TenantStatus.ACTIVE, "ru-RU"));
    }
}

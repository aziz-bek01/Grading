package uz.hrlab.grading.tenancy.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.tenancy.api.ClientCompanyDetailResponse;
import uz.hrlab.grading.tenancy.infrastructure.ClientCompanyJpaEntity;
import uz.hrlab.grading.tenancy.infrastructure.ClientCompanyRepository;
import uz.hrlab.grading.tenancy.infrastructure.TenantJpaEntity;
import uz.hrlab.grading.tenancy.infrastructure.TenantRepository;

import java.util.Set;
import java.util.UUID;

/**
 * Query handler for {@code GET /api/v1/admin/clients/{clientId}}.
 *
 * <p>Two-step lookup:
 * <ol>
 *   <li>Resolve the {@code client_companies} row — Super Admin via
 *       {@link ClientCompanyRepository#findByIdForSuperAdmin}, all other
 *       roles via {@link ClientCompanyRepository#findByIdAndTenantIdIn}
 *       scoped to their membership set.</li>
 *   <li>Re-check {@link TenantManagementPolicy#requireCanViewTenant} on the
 *       resolved {@code tenantId} as defense in depth.</li>
 * </ol>
 *
 * <p>Cross-tenant id probes are answered with 404 (TenantAccessDenied),
 * not 403 — the URL never reveals tenant ownership.
 */
@Service
public class GetClientCompanyDetailsQuery {

    private final TenantManagementPolicy policy;
    private final ClientCompanyRepository clientCompanyRepository;
    private final TenantRepository tenantRepository;

    public GetClientCompanyDetailsQuery(TenantManagementPolicy policy,
                                        ClientCompanyRepository clientCompanyRepository,
                                        TenantRepository tenantRepository) {
        this.policy = policy;
        this.clientCompanyRepository = clientCompanyRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public ClientCompanyDetailResponse byId(UUID clientId) {
        TenantContext ctx = TenantContextHolder.requireActive();

        ClientCompanyJpaEntity company = resolve(ctx, clientId)
                .orElseThrow(TenantAccessDeniedException::new);

        // Defense in depth — the use case re-verifies the resolved tenant.
        policy.requireCanViewTenant(ctx, company.getTenantId());

        TenantJpaEntity tenant = tenantRepository.findById(company.getTenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        return new ClientCompanyDetailResponse(
                company.getId(),
                company.getTenantId(),
                tenant.getSlug(),
                tenant.getDisplayName(),
                company.getLegalName(),
                company.getBrandName(),
                company.getIndustry(),
                company.getCountryCode(),
                company.getTaxId());
    }

    private java.util.Optional<ClientCompanyJpaEntity> resolve(TenantContext ctx, UUID clientId) {
        if (policy.canListAcrossTenants(ctx)) {
            return clientCompanyRepository.findByIdForSuperAdmin(clientId);
        }
        Set<UUID> scoped = policy.scopedTenantIds(ctx);
        if (scoped.isEmpty()) {
            return java.util.Optional.empty();
        }
        return clientCompanyRepository.findByIdAndTenantIdIn(clientId, scoped);
    }
}

package uz.hrlab.grading.tenancy.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.tenancy.api.TenantDetailResponse;
import uz.hrlab.grading.tenancy.infrastructure.ClientCompanyJpaEntity;
import uz.hrlab.grading.tenancy.infrastructure.ClientCompanyRepository;
import uz.hrlab.grading.tenancy.infrastructure.TenantJpaEntity;
import uz.hrlab.grading.tenancy.infrastructure.TenantRepository;

import java.util.UUID;

/**
 * Query handler for {@code GET /api/v1/admin/tenants/{tenantId}}.
 *
 * <p>Returns the tenant + embedded 1:1 client_company row. Authorization is
 * via {@link TenantManagementPolicy#requireCanViewTenant(TenantContext, UUID)}
 * — non-super-admins must be members of the target tenant.
 */
@Service
public class GetTenantDetailsQuery {

    private final TenantManagementPolicy policy;
    private final TenantRepository tenantRepository;
    private final ClientCompanyRepository clientCompanyRepository;

    public GetTenantDetailsQuery(TenantManagementPolicy policy,
                                 TenantRepository tenantRepository,
                                 ClientCompanyRepository clientCompanyRepository) {
        this.policy = policy;
        this.tenantRepository = tenantRepository;
        this.clientCompanyRepository = clientCompanyRepository;
    }

    @Transactional(readOnly = true)
    public TenantDetailResponse byId(UUID tenantId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        policy.requireCanViewTenant(ctx, tenantId);

        TenantJpaEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(TenantAccessDeniedException::new);

        ClientCompanyJpaEntity company = clientCompanyRepository
                .findByTenantId(tenantId).orElse(null);

        TenantDetailResponse.ClientCompanySummary companyDto = company == null ? null :
                new TenantDetailResponse.ClientCompanySummary(
                        company.getId(),
                        company.getLegalName(),
                        company.getBrandName(),
                        company.getIndustry(),
                        company.getCountryCode(),
                        company.getTaxId());

        return new TenantDetailResponse(
                tenant.getId(),
                tenant.getSlug(),
                tenant.getDisplayName(),
                tenant.getStatus() == null ? null : tenant.getStatus().name(),
                tenant.getIsolationMode() == null ? null : tenant.getIsolationMode().name(),
                tenant.getSchemaName(),
                tenant.getDefaultLocale(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt(),
                companyDto);
    }
}

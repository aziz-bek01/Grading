package uz.hrlab.grading.tenancy.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.tenancy.api.ClientCompanyDetailResponse;
import uz.hrlab.grading.tenancy.infrastructure.ClientCompanyJpaEntity;
import uz.hrlab.grading.tenancy.infrastructure.ClientCompanyRepository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Use case for {@code PATCH /api/v1/admin/clients/{clientId}}.
 *
 * <p>Allowed mutations: legalName, brandName, industry, countryCode, taxId
 * — all optional, null = no change. Records ONE
 * {@link AuditAction#CLIENT_COMPANY_UPDATED} row with the changed keys
 * (no value dumps — taxId is treated as semi-sensitive).
 */
@Service
public class UpdateClientCompanyUseCase {

    private final TenantManagementPolicy policy;
    private final ClientCompanyRepository clientCompanyRepository;
    private final GetClientCompanyDetailsQuery detailsQuery;
    private final AuditService audit;

    public UpdateClientCompanyUseCase(TenantManagementPolicy policy,
                                      ClientCompanyRepository clientCompanyRepository,
                                      GetClientCompanyDetailsQuery detailsQuery,
                                      AuditService audit) {
        this.policy = policy;
        this.clientCompanyRepository = clientCompanyRepository;
        this.detailsQuery = detailsQuery;
        this.audit = audit;
    }

    @Transactional
    public ClientCompanyDetailResponse patch(UUID clientId, String legalName, String brandName,
                                             String industry, String countryCode, String taxId) {
        TenantContext ctx = TenantContextHolder.requireActive();

        // Resolve scoped to the caller's membership set (Super Admin gets
        // unscoped lookup). Refuses 404 for cross-tenant probes.
        ClientCompanyJpaEntity company = resolve(ctx, clientId)
                .orElseThrow(TenantAccessDeniedException::new);

        // Mutation gate — same door as tenant PATCH (active tenant must
        // equal the resolved company's tenant for non-super-admin).
        policy.requireCanManageClientCompany(ctx, company.getTenantId());

        boolean changed = false;
        StringBuilder reason = new StringBuilder();

        if (legalName != null) {
            String trimmed = legalName.trim();
            if (trimmed.isEmpty()) {
                throw new ValidationException("CLIENT_PATCH_BLANK_LEGAL_NAME",
                        "legalName cannot be blank when provided");
            }
            if (!trimmed.equals(company.getLegalName())) {
                company.setLegalName(trimmed);
                reason.append("legalName ");
                changed = true;
            }
        }
        if (brandName != null && !brandName.equals(company.getBrandName())) {
            company.setBrandName(brandName.isBlank() ? null : brandName.trim());
            reason.append("brandName ");
            changed = true;
        }
        if (industry != null && !industry.equals(company.getIndustry())) {
            company.setIndustry(industry.isBlank() ? null : industry.trim());
            reason.append("industry ");
            changed = true;
        }
        if (countryCode != null && !countryCode.equals(company.getCountryCode())) {
            // Re-check pattern defensively (DTO @Pattern is the primary gate).
            String normalized = countryCode.trim().toUpperCase();
            if (!normalized.matches("[A-Z]{2}")) {
                throw new ValidationException("CLIENT_PATCH_BAD_COUNTRY",
                        "countryCode must be a 2-letter ISO 3166-1 alpha-2");
            }
            company.setCountryCode(normalized);
            reason.append("countryCode ");
            changed = true;
        }
        if (taxId != null && !taxId.equals(company.getTaxId())) {
            company.setTaxId(taxId.isBlank() ? null : taxId.trim());
            // NB: deliberately not dumping the value — taxId is semi-sensitive.
            reason.append("taxId ");
            changed = true;
        }

        if (changed) {
            clientCompanyRepository.save(company);
            audit.record(AuditEvent.builder()
                    .tenantId(company.getTenantId())
                    .actorUserId(ctx.userId())
                    .action(AuditAction.CLIENT_COMPANY_UPDATED)
                    .entityType("ClientCompany")
                    .entityId(company.getId())
                    .reason(reason.toString().trim())
                    .build());
        }
        return detailsQuery.byId(clientId);
    }

    private Optional<ClientCompanyJpaEntity> resolve(TenantContext ctx, UUID clientId) {
        if (policy.canListAcrossTenants(ctx)) {
            return clientCompanyRepository.findByIdForSuperAdmin(clientId);
        }
        Set<UUID> scoped = policy.scopedTenantIds(ctx);
        if (scoped.isEmpty()) {
            return Optional.empty();
        }
        return clientCompanyRepository.findByIdAndTenantIdIn(clientId, scoped);
    }
}

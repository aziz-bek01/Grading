package uz.hrlab.grading.tenancy.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import uz.hrlab.grading.common.api.Pagination;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.tenancy.api.ClientCompanyListResponse;
import uz.hrlab.grading.tenancy.infrastructure.ClientCompanyJpaEntity;
import uz.hrlab.grading.tenancy.infrastructure.ClientCompanyRepository;
import uz.hrlab.grading.tenancy.infrastructure.TenantJpaEntity;
import uz.hrlab.grading.tenancy.infrastructure.TenantRepository;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Query handler for {@code GET /api/v1/admin/clients}.
 *
 * <p>Mirrors {@link ListTenantsQuery} — super-admin gets the unscoped
 * catalog, all other roles see only client companies belonging to tenants
 * they're a member of. Tenants are joined in-memory for the slug + display
 * name + status fields.
 */
@Service
public class ListClientCompaniesQuery {

    private final TenantManagementPolicy policy;
    private final ClientCompanyRepository clientCompanyRepository;
    private final TenantRepository tenantRepository;

    public ListClientCompaniesQuery(TenantManagementPolicy policy,
                                    ClientCompanyRepository clientCompanyRepository,
                                    TenantRepository tenantRepository) {
        this.policy = policy;
        this.clientCompanyRepository = clientCompanyRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public Page<ClientCompanyListResponse> list(String search, int page, int size) {
        TenantContext ctx = TenantContextHolder.requireActive();

        String safeSearch = emptyToNull(search);
        Pageable pageable = pageable(page, size);

        Page<ClientCompanyJpaEntity> companies;
        if (policy.canListAcrossTenants(ctx)) {
            companies = clientCompanyRepository.searchAll(safeSearch, pageable);
        } else {
            Set<UUID> scoped = policy.scopedTenantIds(ctx);
            if (scoped.isEmpty()) {
                return Page.empty(pageable);
            }
            companies = clientCompanyRepository.searchInTenantIds(scoped, safeSearch, pageable);
        }

        if (companies.isEmpty()) {
            return Page.empty(pageable);
        }

        Set<UUID> tenantIds = companies.getContent().stream()
                .map(ClientCompanyJpaEntity::getTenantId)
                .collect(Collectors.toSet());
        Map<UUID, TenantJpaEntity> tenantsById =
                tenantRepository.findAllByIdIn(tenantIds).stream()
                        .collect(Collectors.toMap(TenantJpaEntity::getId, t -> t));

        return companies.map(c -> toRow(c, tenantsById.get(c.getTenantId())));
    }

    private static ClientCompanyListResponse toRow(ClientCompanyJpaEntity c, TenantJpaEntity t) {
        return new ClientCompanyListResponse(
                c.getId(),
                c.getTenantId(),
                t == null ? null : t.getSlug(),
                t == null ? null : t.getDisplayName(),
                t == null || t.getStatus() == null ? null : t.getStatus().name(),
                c.getLegalName(),
                c.getBrandName(),
                c.getIndustry(),
                c.getCountryCode());
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static Pageable pageable(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Pagination.clampSize(size);
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "legalName"));
    }
}

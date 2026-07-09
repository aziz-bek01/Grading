package uz.hrlab.grading.tenancy.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import uz.hrlab.grading.common.api.Pagination;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.api.TenantListResponse;
import uz.hrlab.grading.tenancy.domain.TenantStatus;
import uz.hrlab.grading.tenancy.infrastructure.ClientCompanyJpaEntity;
import uz.hrlab.grading.tenancy.infrastructure.ClientCompanyRepository;
import uz.hrlab.grading.tenancy.infrastructure.TenantJpaEntity;
import uz.hrlab.grading.tenancy.infrastructure.TenantRepository;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Query handler for {@code GET /api/v1/admin/tenants}.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>HRLAB_SUPER_ADMIN sees the unscoped catalog
 *       ({@link TenantRepository#search}).</li>
 *   <li>All other roles see only tenants in their membership set
 *       ({@link TenantRepository#searchInIds}). Empty membership set returns
 *       an empty page.</li>
 *   <li>{@code search} matches case-insensitively against slug + display
 *       name. {@code status} is validated against {@link TenantStatus}; an
 *       invalid value is treated as null filter (consistent with
 *       {@code ListUsersQuery}).</li>
 *   <li>Sorted by {@code createdAt DESC} so the newest tenants surface first.</li>
 * </ul>
 *
 * <p>The 1:1 {@code client_companies} row is joined in-memory (one extra
 * {@code findAllByTenantIdIn} query) to keep the JPQL simple and to keep
 * each row stable when the company row is missing during provisioning.
 */
@Service
public class ListTenantsQuery {

    private final TenantManagementPolicy policy;
    private final TenantRepository tenantRepository;
    private final ClientCompanyRepository clientCompanyRepository;
    private final ProjectRepository projectRepository;
    private final UserTenantMembershipRepository membershipRepository;

    public ListTenantsQuery(TenantManagementPolicy policy,
                            TenantRepository tenantRepository,
                            ClientCompanyRepository clientCompanyRepository,
                            ProjectRepository projectRepository,
                            UserTenantMembershipRepository membershipRepository) {
        this.policy = policy;
        this.tenantRepository = tenantRepository;
        this.clientCompanyRepository = clientCompanyRepository;
        this.projectRepository = projectRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional(readOnly = true)
    public Page<TenantListResponse> list(String search, String status, int page, int size) {
        TenantContext ctx = TenantContextHolder.requireActive();

        TenantStatus statusEnum = parseStatus(status);
        String safeSearch = emptyToNull(search);
        Pageable pageable = pageable(page, size);

        Page<TenantJpaEntity> tenants;
        if (policy.canListAcrossTenants(ctx)) {
            tenants = tenantRepository.search(safeSearch, statusEnum, pageable);
        } else {
            Set<UUID> scoped = policy.scopedTenantIds(ctx);
            if (scoped.isEmpty()) {
                return Page.empty(pageable);
            }
            tenants = tenantRepository.searchInIds(scoped, safeSearch, statusEnum, pageable);
        }

        if (tenants.isEmpty()) {
            return Page.empty(pageable);
        }

        Set<UUID> tenantIds = tenants.getContent().stream()
                .map(TenantJpaEntity::getId)
                .collect(Collectors.toSet());
        Map<UUID, ClientCompanyJpaEntity> companiesByTenant =
                clientCompanyRepository.findAllByTenantIdIn(tenantIds).stream()
                        .collect(Collectors.toMap(ClientCompanyJpaEntity::getTenantId, c -> c));

        // Portfolio KPIs for the list table. Reuses the same O(1) indexed
        // counts as GetTenantStatsQuery; looped over the (bounded) page of
        // tenant ids so a single page render needs no extra /stats round-trip.
        Map<UUID, Long> projectCounts = tenantIds.stream()
                .collect(Collectors.toMap(id -> id, projectRepository::countByTenantId));
        Map<UUID, Long> userCounts = tenantIds.stream()
                .collect(Collectors.toMap(id -> id, membershipRepository::countByTenantId));

        return tenants.map(t -> toRow(t, companiesByTenant.get(t.getId()),
                projectCounts.getOrDefault(t.getId(), 0L),
                userCounts.getOrDefault(t.getId(), 0L)));
    }

    private static TenantListResponse toRow(TenantJpaEntity t, ClientCompanyJpaEntity c,
                                            long projectCount, long userCount) {
        return new TenantListResponse(
                t.getId(),
                t.getSlug(),
                t.getDisplayName(),
                t.getStatus() == null ? null : t.getStatus().name(),
                t.getIsolationMode() == null ? null : t.getIsolationMode().name(),
                t.getDefaultLocale(),
                c == null ? null : c.getLegalName(),
                c == null ? null : c.getBrandName(),
                c == null ? null : c.getIndustry(),
                t.getCreatedAt(),
                projectCount,
                userCount);
    }

    private static TenantStatus parseStatus(String s) {
        if (s == null || s.isBlank()) return null;
        try { return TenantStatus.valueOf(s.trim().toUpperCase()); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static Pageable pageable(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Pagination.clampSize(size);
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}

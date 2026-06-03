package uz.hrlab.grading.access.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.api.CurrentUserResponse;
import uz.hrlab.grading.access.api.TenantMembershipSummary;
import uz.hrlab.grading.access.domain.MembershipStatus;
import uz.hrlab.grading.access.infrastructure.UserJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserRepository;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.common.exception.ResourceNotFoundException;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;
import uz.hrlab.grading.tenancy.infrastructure.ClientCompanyJpaEntity;
import uz.hrlab.grading.tenancy.infrastructure.ClientCompanyRepository;
import uz.hrlab.grading.tenancy.infrastructure.TenantJpaEntity;
import uz.hrlab.grading.tenancy.infrastructure.TenantRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * BE-TI-005 — assembles {@code GET /api/v1/users/me} payload.
 *
 * <p>Membership list is sourced directly from {@code user_tenant_memberships}
 * (NOT from the JWT) so the response is the authoritative truth of which
 * tenants the caller can switch into. Revoked memberships are filtered out;
 * suspended/invited rows are kept so the UI can render the appropriate badge.
 *
 * <p>Roles + permissions remain JWT-derived because they are already pinned
 * to the active tenant by the IdP token contract (security-blueprint §5.1) —
 * recomputing them here from {@code user_roles} would risk surfacing
 * permissions from an OTHER tenant for the active context.
 */
@Service
public class GetCurrentUserUseCase {

    private final UserRepository userRepository;
    private final UserTenantMembershipRepository membershipRepository;
    private final TenantRepository tenantRepository;
    private final ClientCompanyRepository clientCompanyRepository;

    public GetCurrentUserUseCase(UserRepository userRepository,
                                 UserTenantMembershipRepository membershipRepository,
                                 TenantRepository tenantRepository,
                                 ClientCompanyRepository clientCompanyRepository) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.tenantRepository = tenantRepository;
        this.clientCompanyRepository = clientCompanyRepository;
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser() {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx == null || ctx.userId() == null) {
            // Reaching this means the security filter chain let an unauthenticated
            // request through — sanitized error, never expose internals.
            throw new ResourceNotFoundException("User not found");
        }

        UUID userId = ctx.userId();
        UserJpaEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<UserTenantMembershipJpaEntity> memberships = membershipRepository.findAllByUserId(userId)
                .stream()
                .filter(m -> m.getStatus() != MembershipStatus.REVOKED)
                .toList();

        List<TenantMembershipSummary> tenantCards = buildTenantCards(memberships);

        return new CurrentUserResponse(
                new CurrentUserResponse.UserIdentity(
                        user.getId(),
                        user.getEmail(),
                        user.getFullName(),
                        Optional.ofNullable(ctx.locale()).orElse(user.getDefaultLocale()),
                        user.getStatus() != null ? user.getStatus().name() : null
                ),
                tenantCards,
                ctx.tenantId(),
                Optional.ofNullable(ctx.roles()).orElse(Set.of()),
                Optional.ofNullable(ctx.permissions()).orElse(Set.of()),
                ctx.salaryPermission()
        );
    }

    private List<TenantMembershipSummary> buildTenantCards(List<UserTenantMembershipJpaEntity> memberships) {
        if (memberships.isEmpty()) {
            return List.of();
        }
        Set<UUID> tenantIds = memberships.stream()
                .map(UserTenantMembershipJpaEntity::getTenantId)
                .collect(Collectors.toSet());

        // Single round-trip per table — no N+1 (security-blueprint §5.2 caching pattern).
        Map<UUID, TenantJpaEntity> tenantsById = tenantRepository.findAllById(tenantIds).stream()
                .collect(Collectors.toMap(TenantJpaEntity::getId, t -> t));

        // ClientCompanyRepository is tenant-aware (no findAllById that ignores
        // tenant scope by design). We resolve client_company per tenant — the
        // table carries UNIQUE(tenant_id) so this is O(N) without leakage risk.
        Map<UUID, ClientCompanyJpaEntity> companiesByTenant = tenantIds.stream()
                .map(tid -> clientCompanyRepository.findByTenantId(tid).orElse(null))
                .filter(c -> c != null)
                .collect(Collectors.toMap(ClientCompanyJpaEntity::getTenantId, c -> c));

        List<TenantMembershipSummary> out = new ArrayList<>(memberships.size());
        for (UserTenantMembershipJpaEntity m : memberships) {
            TenantJpaEntity tenant = tenantsById.get(m.getTenantId());
            if (tenant == null) {
                // Membership references a missing tenant — skip rather than 500.
                continue;
            }
            ClientCompanyJpaEntity company = companiesByTenant.get(m.getTenantId());
            String brand = company != null && company.getBrandName() != null
                    ? company.getBrandName()
                    : tenant.getDisplayName();
            out.add(new TenantMembershipSummary(
                    tenant.getId(),
                    tenant.getSlug(),
                    brand,
                    computeFingerprintHue(tenant.getSlug()),
                    m.getStatus() != null ? m.getStatus().name() : null,
                    m.isSalaryDataPermission()
            ));
        }
        return out;
    }

    /**
     * Deterministic hue (0–359) derived from the tenant slug — used by the
     * frontend tenant fingerprint badge so each tenant has a stable visual
     * color WITHOUT requiring a per-tenant CMS write.
     */
    private static int computeFingerprintHue(String slug) {
        if (slug == null || slug.isBlank()) return 0;
        int hash = slug.hashCode();
        return Math.floorMod(hash, 360);
    }
}

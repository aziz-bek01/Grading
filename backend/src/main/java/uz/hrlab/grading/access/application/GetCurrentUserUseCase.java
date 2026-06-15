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
import java.util.Comparator;
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
 * <p>Roles + permissions are normally JWT/context-derived because the resolver
 * already pins them to the active tenant (security-blueprint §5.1) — recomputing
 * them here from {@code user_roles} would risk surfacing permissions from an
 * OTHER tenant for the active context.
 *
 * <h3>Shell-render fix for ambiguous multi-membership (BE-3-FIX, 2026-06-12)</h3>
 * {@code /users/me} is the IDENTITY endpoint, not tenant-scoped business data.
 * When a user belongs to MORE THAN ONE active tenant and the request carries no
 * {@code X-Active-Tenant-Id} header / {@code active_tenant_id} claim, the
 * resolver fails closed to {@code tenantId == null} (correct for data reads — no
 * cross-tenant leak, BE-3). But that would also leave {@code /me} permission-
 * empty, locking the SPA shell entirely for the top-privilege super admin (the
 * production regression). So, ONLY for this identity payload, we deterministically
 * pick a SINGLE default tenant and expand permissions for THAT one tenant via
 * {@link MembershipAuthorityResolver} so the shell can render. The SPA adopts the
 * returned {@code activeTenantId} and sends it as {@code X-Active-Tenant-Id} on
 * every subsequent request — so tenant-scoped DATA reads remain strictly scoped
 * and the fail-closed guarantee on those reads is untouched.
 *
 * <p>SECURITY: the expansion is for EXACTLY ONE membership/tenant — it never
 * unions permissions across tenants. The reported {@code activeTenantId} and the
 * reported {@code permissions} are always for the SAME single tenant, so the
 * shell never shows capabilities the user lacks in the tenant it will operate in.
 */
@Service
public class GetCurrentUserUseCase {

    private final UserRepository userRepository;
    private final UserTenantMembershipRepository membershipRepository;
    private final TenantRepository tenantRepository;
    private final ClientCompanyRepository clientCompanyRepository;
    private final MembershipAuthorityResolver authorityResolver;

    public GetCurrentUserUseCase(UserRepository userRepository,
                                 UserTenantMembershipRepository membershipRepository,
                                 TenantRepository tenantRepository,
                                 ClientCompanyRepository clientCompanyRepository,
                                 MembershipAuthorityResolver authorityResolver) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.tenantRepository = tenantRepository;
        this.clientCompanyRepository = clientCompanyRepository;
        this.authorityResolver = authorityResolver;
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

        // Default: the context already pinned an active tenant; its roles/
        // permissions are scoped to that tenant by the resolver. Use them verbatim.
        UUID activeTenantId = ctx.tenantId();
        Set<String> roles = Optional.ofNullable(ctx.roles()).orElse(Set.of());
        Set<String> permissions = Optional.ofNullable(ctx.permissions()).orElse(Set.of());
        boolean salaryPermission = ctx.salaryPermission();

        // Shell-render fix (BE-3-FIX): the resolver failed closed to NO active
        // tenant (ambiguous multi-membership, header-less first /me). For the
        // IDENTITY payload only, pick ONE default tenant deterministically and
        // expand permissions for THAT single tenant so the SPA shell renders.
        // Data reads remain fail-closed (they need the X-Active-Tenant-Id header,
        // which the SPA will now send because it adopts this activeTenantId).
        if (activeTenantId == null) {
            UserTenantMembershipJpaEntity defaultMembership = pickDefaultActiveMembership(memberships);
            if (defaultMembership != null) {
                MembershipAuthorityResolver.Authority authority =
                        authorityResolver.expand(defaultMembership);
                activeTenantId = defaultMembership.getTenantId();
                roles = authority.roleCodes();
                permissions = authority.permissionCodes();
                salaryPermission = authority.salaryPermission();
            }
        }

        return new CurrentUserResponse(
                new CurrentUserResponse.UserIdentity(
                        user.getId(),
                        user.getEmail(),
                        user.getFullName(),
                        Optional.ofNullable(ctx.locale()).orElse(user.getDefaultLocale()),
                        user.getStatus() != null ? user.getStatus().name() : null
                ),
                tenantCards,
                activeTenantId,
                roles,
                permissions,
                salaryPermission
        );
    }

    /**
     * Deterministically choose ONE default tenant for the {@code /users/me}
     * identity payload when the request resolved to no active tenant. Order:
     * earliest-created ACTIVE membership, tie-broken by tenant id for a stable
     * choice across requests. Returns {@code null} when the user has no ACTIVE
     * membership (e.g. only INVITED/SUSPENDED), in which case the shell stays
     * gated and the SPA prompts to pick a company-client.
     *
     * <p>SECURITY: returns a SINGLE membership — the caller expands permissions
     * for exactly this one tenant. Never unions across tenants.
     */
    private static UserTenantMembershipJpaEntity pickDefaultActiveMembership(
            List<UserTenantMembershipJpaEntity> memberships) {
        return memberships.stream()
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .min(Comparator
                        .comparing(UserTenantMembershipJpaEntity::getCreatedAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(UserTenantMembershipJpaEntity::getTenantId))
                .orElse(null);
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

        // PERF (P1) — resolve client companies for ALL membership tenants in ONE
        // tenant-scoped batch query (was one findByTenantId per membership →
        // N+1), mirroring the already-batched tenantRepository.findAllById above.
        // findAllByTenantIdIn keeps tenant_id pinned and the table carries
        // UNIQUE(tenant_id), so the map collects one row per tenant without leakage.
        Map<UUID, ClientCompanyJpaEntity> companiesByTenant =
                clientCompanyRepository.findAllByTenantIdIn(tenantIds).stream()
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

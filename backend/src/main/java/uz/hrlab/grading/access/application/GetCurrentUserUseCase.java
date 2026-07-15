package uz.hrlab.grading.access.application;

import org.springframework.data.domain.Pageable;
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
import uz.hrlab.grading.tenancy.domain.TenantStatus;
import uz.hrlab.grading.tenancy.infrastructure.ClientCompanyJpaEntity;
import uz.hrlab.grading.tenancy.infrastructure.ClientCompanyRepository;
import uz.hrlab.grading.tenancy.infrastructure.TenantJpaEntity;
import uz.hrlab.grading.tenancy.infrastructure.TenantRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
    private final PlatformSuperAdminChecker superAdminChecker;

    public GetCurrentUserUseCase(UserRepository userRepository,
                                 UserTenantMembershipRepository membershipRepository,
                                 TenantRepository tenantRepository,
                                 ClientCompanyRepository clientCompanyRepository,
                                 MembershipAuthorityResolver authorityResolver,
                                 PlatformSuperAdminChecker superAdminChecker) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.tenantRepository = tenantRepository;
        this.clientCompanyRepository = clientCompanyRepository;
        this.authorityResolver = authorityResolver;
        this.superAdminChecker = superAdminChecker;
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

        List<TenantMembershipSummary> tenantCards = buildTenantCards(userId, memberships);

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

    /**
     * Build the tenant switcher cards.
     *
     * <p>Fix A read side: a platform Super Admin's switcher lists ALL {@code ACTIVE}
     * tenants (mirrors {@code ListTenantsQuery}'s {@code canListAcrossTenants}
     * branch — the SAME {@code tenantRepository.search} query), UNIONED with the
     * caller's own memberships so nothing is lost and there are no duplicates. The
     * super-admin signal is the DB-derived {@link PlatformSuperAdminChecker}
     * predicate (never {@code ctx.roles()}, which is empty when the active tenant
     * is ambiguous on the first header-less {@code /me}). For EVERY other user the
     * output is byte-for-byte unchanged — their own memberships only, in the same
     * order, with the same status + salary fields.
     */
    private List<TenantMembershipSummary> buildTenantCards(
            UUID userId, List<UserTenantMembershipJpaEntity> memberships) {

        boolean superAdmin = superAdminChecker.isPlatformSuperAdmin(userId);
        if (memberships.isEmpty() && !superAdmin) {
            return List.of();
        }

        // Index the caller's REAL memberships by tenant id — these carry the
        // membership status badge + the per-tenant salary gate on the card.
        Map<UUID, UserTenantMembershipJpaEntity> membershipByTenant = new HashMap<>();
        for (UserTenantMembershipJpaEntity m : memberships) {
            membershipByTenant.putIfAbsent(m.getTenantId(), m);
        }
        Set<UUID> membershipTenantIds = membershipByTenant.keySet();

        // For a platform super admin, additionally source ALL ACTIVE tenants
        // (mirror ListTenantsQuery.canListAcrossTenants — reuse the same query).
        List<TenantJpaEntity> allActiveTenants = superAdmin
                ? tenantRepository.search(null, TenantStatus.ACTIVE, Pageable.unpaged()).getContent()
                : List.of();

        // One round-trip for the membership tenants (unchanged path). Union with
        // the all-active set (super admin only) — dedupe by tenant id.
        Map<UUID, TenantJpaEntity> tenantsById = new HashMap<>();
        tenantRepository.findAllById(membershipTenantIds)
                .forEach(t -> tenantsById.put(t.getId(), t));
        allActiveTenants.forEach(t -> tenantsById.putIfAbsent(t.getId(), t));

        if (tenantsById.isEmpty()) {
            return List.of();
        }

        // PERF (P1) — client companies for every tenant we will render in ONE
        // tenant-scoped batch query. findAllByTenantIdIn keeps tenant_id pinned and
        // the table carries UNIQUE(tenant_id), so the map collects one row per
        // tenant without leakage.
        Map<UUID, ClientCompanyJpaEntity> companiesByTenant =
                clientCompanyRepository.findAllByTenantIdIn(tenantsById.keySet()).stream()
                        .collect(Collectors.toMap(ClientCompanyJpaEntity::getTenantId, c -> c));

        List<TenantMembershipSummary> out = new ArrayList<>(tenantsById.size());
        Set<UUID> emitted = new HashSet<>();

        // 1) Membership cards FIRST, in the ORIGINAL membership iteration order —
        //    byte-for-byte unchanged for every user (status + salary flag intact).
        for (UserTenantMembershipJpaEntity m : memberships) {
            TenantJpaEntity tenant = tenantsById.get(m.getTenantId());
            if (tenant == null) {
                // Membership references a missing tenant — skip rather than 500.
                continue;
            }
            if (emitted.add(tenant.getId())) {
                out.add(toCard(tenant, companiesByTenant.get(tenant.getId()),
                        m.getStatus() != null ? m.getStatus().name() : null,
                        m.isSalaryDataPermission()));
            }
        }

        // 2) Super-admin ONLY: append the remaining ACTIVE tenants the caller is
        //    NOT a member of. These carry NO membership badge (null status → omitted
        //    by NON_NULL) and NO salary gate (no membership → no per-tenant salary
        //    permission). The frontend renders them as switchable cards.
        if (superAdmin) {
            for (TenantJpaEntity tenant : allActiveTenants) {
                if (!membershipTenantIds.contains(tenant.getId()) && emitted.add(tenant.getId())) {
                    out.add(toCard(tenant, companiesByTenant.get(tenant.getId()), null, false));
                }
            }
        }
        return out;
    }

    private TenantMembershipSummary toCard(TenantJpaEntity tenant, ClientCompanyJpaEntity company,
                                           String membershipStatus, boolean salaryDataPermission) {
        String brand = company != null && company.getBrandName() != null
                ? company.getBrandName()
                : tenant.getDisplayName();
        return new TenantMembershipSummary(
                tenant.getId(),
                tenant.getSlug(),
                brand,
                computeFingerprintHue(tenant.getSlug()),
                membershipStatus,
                salaryDataPermission);
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

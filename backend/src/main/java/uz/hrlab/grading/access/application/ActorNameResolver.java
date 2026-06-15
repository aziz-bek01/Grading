package uz.hrlab.grading.access.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.access.infrastructure.UserJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserRepository;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.access.domain.MembershipStatus;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Single source of truth for user-id → display-name resolution (Item 4 — NO
 * DUPLICATION). Every module that needs to turn a tenant-derived user UUID into
 * a human name (approval initiator / approver / decider, methodology
 * approvedBy / lockedBy, workflow responsible / lastUpdatedBy, future comments)
 * depends on THIS component. {@code MethodologyActorNameResolver} is now a thin
 * delegate over this class.
 *
 * <h2>Security (review R1–R4)</h2>
 * <ul>
 *   <li><b>R1/R2 — tenant-derived ids only.</b> Callers MUST only pass UUIDs
 *       harvested from rows already loaded via {@code findByIdAndTenantId} for
 *       the active tenant. This is NOT a directory-lookup service; never feed it
 *       a UUID taken from a request body / query string.</li>
 *   <li><b>R3/R4 — membership-aware, fail-soft.</b> {@code public.users} is the
 *       control-plane PII table and has NO {@code tenant_id}; a bare
 *       {@code users.findById} would resolve a name regardless of tenant. To
 *       prevent leaking the full name of a user who belongs to ANOTHER client
 *       company (e.g. a stale id, or a user who moved tenants), every resolve is
 *       gated on an ACTIVE {@code user_tenant_memberships} row for the active
 *       tenant. No active membership ⇒ {@code null} (the FE falls back to a
 *       localized "Unknown actor" / "Former user" label). A missing user ⇒
 *       {@code null}. Resolution never throws.</li>
 * </ul>
 */
@Component
public class ActorNameResolver {

    private final UserRepository users;
    private final UserTenantMembershipRepository memberships;

    public ActorNameResolver(UserRepository users,
                             UserTenantMembershipRepository memberships) {
        this.users = users;
        this.memberships = memberships;
    }

    /**
     * Resolve a single tenant-derived user id to a display name.
     *
     * @param tenantId active tenant (server-derived from TenantContext)
     * @param userId   a user id that came out of an already-tenant-loaded row
     * @return the user's full name, or {@code null} if {@code userId} is null,
     *         the user is unknown, or the user has no ACTIVE membership in the
     *         active tenant (cross-tenant safety — never leak a foreign name).
     */
    public String resolve(UUID tenantId, UUID userId) {
        if (tenantId == null || userId == null) {
            return null;
        }
        if (!hasActiveMembership(userId, tenantId)) {
            return null; // R4 — degrade rather than leak a cross-tenant name
        }
        return users.findById(userId)
                .map(UserJpaEntity::getFullName)
                .orElse(null);
    }

    /**
     * Batch variant for list reads (BE-5 — avoids N+1). Resolves every id in one
     * {@code users.findAllById} call after filtering to ids with an ACTIVE
     * membership in the active tenant. Unknown / non-member ids are simply absent
     * from the returned map (so callers get {@code null} on lookup, NON_NULL on
     * the wire). Never throws; an empty / null input yields an empty map.
     *
     * @param tenantId active tenant (server-derived)
     * @param userIds  user ids harvested from already-tenant-loaded rows
     */
    public Map<UUID, String> resolveAll(UUID tenantId, Collection<UUID> userIds) {
        if (tenantId == null || userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Set<UUID> distinct = userIds.stream()
                .filter(id -> id != null)
                .collect(Collectors.toCollection(HashSet::new));
        if (distinct.isEmpty()) {
            return Map.of();
        }
        // R3/R4 — keep only ids that hold an ACTIVE membership in this tenant, so
        // a stale / cross-tenant id can never resolve to a foreign name.
        // PERF (P1) — query ONLY the harvested ids (tenant-scoped) instead of
        // loading the whole tenant membership table and filtering in memory.
        Set<UUID> memberIds = memberships.findByTenantIdAndUserIdIn(tenantId, distinct).stream()
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .map(m -> m.getUserId())
                .filter(distinct::contains)
                .collect(Collectors.toCollection(HashSet::new));
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        List<UserJpaEntity> rows = users.findAllById(memberIds);
        Map<UUID, String> out = new HashMap<>(rows.size());
        for (UserJpaEntity u : rows) {
            out.put(u.getId(), u.getFullName());
        }
        return out;
    }

    private boolean hasActiveMembership(UUID userId, UUID tenantId) {
        return memberships.findByUserIdAndTenantId(userId, tenantId)
                .map(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .orElse(false);
    }
}

package uz.hrlab.grading.approval.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.access.application.ActorNameResolver;
import uz.hrlab.grading.approval.api.ApprovalRequestResponse;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.approval.domain.ApprovalRequest;
import uz.hrlab.grading.approval.domain.ApprovalStep;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Builds enriched {@link ApprovalRequestResponse}s for the READ paths (BE-5,
 * BE-6). Keeps the controller declarative: it owns the BATCH resolution of
 * user-display names ({@link ActorNameResolver}) and entity labels
 * ({@link ApprovalEntityLabelResolver}) so there is no per-row query and no
 * per-entity logic in the controller (Item 4 — no duplication, no N+1).
 *
 * <h2>Security</h2>
 * Every id passed to the resolvers is tenant-derived: it comes off
 * {@link ApprovalRequest} domain objects already loaded via
 * {@code findByIdAndTenantId} for the active tenant (see {@code ApprovalQueries}
 * / {@code ListMyPendingApprovalsQuery} / {@code FindApprovalRequestByEntityQuery}).
 * The {@code tenantId} is server-derived. The resolvers themselves are
 * membership-aware / tenant-scoped and fail-soft (null → field omitted on the
 * wire by {@code @JsonInclude(NON_NULL)}).
 */
@Component
public class ApprovalResponseAssembler {

    private final ActorNameResolver actorNames;
    private final ApprovalEntityLabelResolver entityLabels;

    public ApprovalResponseAssembler(ActorNameResolver actorNames,
                                     ApprovalEntityLabelResolver entityLabels) {
        this.actorNames = actorNames;
        this.entityLabels = entityLabels;
    }

    /** Enrich a single already-tenant-loaded request (getById detail path). */
    public ApprovalRequestResponse enrich(UUID tenantId, ApprovalRequest request) {
        return enrichAll(tenantId, List.of(request)).get(0);
    }

    /**
     * Enrich a page / list of already-tenant-loaded requests. Resolution is
     * batched: ONE {@code users.findAllById} for all requested-by + approver +
     * decided ids across the whole page (BE-5), and ONE repository call per
     * distinct entity-type's id set (BE-6, currently sequential per id within a
     * type — kept tenant-scoped and fail-soft; page sizes are small).
     */
    public List<ApprovalRequestResponse> enrichAll(UUID tenantId, List<ApprovalRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        // --- BE-5: harvest every tenant-derived user id, resolve in one batch ---
        Set<UUID> userIds = new HashSet<>();
        for (ApprovalRequest r : requests) {
            if (r.requestedBy() != null) {
                userIds.add(r.requestedBy());
            }
            for (ApprovalStep s : r.steps()) {
                if (s.approverUserId() != null) {
                    userIds.add(s.approverUserId());
                }
                if (s.decidedBy() != null) {
                    userIds.add(s.decidedBy());
                }
            }
        }
        Map<UUID, String> names = actorNames.resolveAll(tenantId, userIds);
        Function<UUID, String> nameFn = id -> id == null ? null : names.get(id);

        // --- BE-6: resolve entity labels per (type, id), tenant-scoped + cached ---
        Map<EntityKey, Map<String, String>> labelCache = new HashMap<>();

        List<ApprovalRequestResponse> out = new ArrayList<>(requests.size());
        for (ApprovalRequest r : requests) {
            Map<String, String> label = labelCache.computeIfAbsent(
                    new EntityKey(r.entityType(), r.entityId()),
                    k -> entityLabels.resolve(tenantId, k.type(), k.id()));
            String initiatedByName = nameFn.apply(r.requestedBy());
            out.add(ApprovalRequestResponse.from(r, label, initiatedByName, nameFn));
        }
        return out;
    }

    /** Page-local de-dup key so the same (type, id) is resolved at most once. */
    private record EntityKey(ApprovalEntityType type, UUID id) { }
}

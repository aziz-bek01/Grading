package uz.hrlab.grading.access.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.api.AuditEventResponse;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogJpaEntity;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogRepository;
import uz.hrlab.grading.common.api.Pagination;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

/**
 * D-1 — query handler for {@code GET /api/v1/audit}.
 *
 * <p>Permission + tenant-scope contract (security-blueprint §9.1 / §13):
 * <ul>
 *   <li>{@link PermissionCodes#AUDIT_READ} or {@link PermissionCodes#AUDIT_VIEW}
 *       is REQUIRED — the controller enforces it via {@code @PreAuthorize}.
 *       Defence in depth: we re-check here so a future re-routing of the
 *       endpoint can never silently drop the gate.</li>
 *   <li>Cross-tenant listing (i.e. {@code filter.tenantId() == null}) is
 *       allowed ONLY for HRLab Super Admin. Every other caller — including
 *       CLIENT_COMPANY_ADMIN — is auto-scoped to their own active tenant
 *       regardless of what they put in the query string.</li>
 *   <li>If a non-super-admin explicitly passes a different {@code tenantId},
 *       the filter is OVERWRITTEN to {@code ctx.tenantId()} — we never 403
 *       here because the URL is intentionally tenant-agnostic. The forensic
 *       trail is preserved through the standard
 *       {@code CROSS_TENANT_OR_PROJECT_ACCESS_ATTEMPT} pathway only when a
 *       resource lookup downstream throws — listing has nothing to leak.</li>
 * </ul>
 *
 * <p><b>No self-audit:</b> reading the audit log does NOT itself emit an
 * audit row — that would create an infinite loop. Only an EXPORT
 * (CSV download, Sprint E) is treated as a sensitive read worth logging.
 */
@Service
public class ListAuditEventsQuery {

    private static final int DEFAULT_PAGE_SIZE = 50;

    private final SystemAuditLogRepository auditRepo;

    public ListAuditEventsQuery(SystemAuditLogRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

    @Transactional(readOnly = true)
    public Page<AuditEventResponse> list(AuditQueryFilter filter) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.AUDIT_READ)
                && !ctx.hasPermission(PermissionCodes.AUDIT_VIEW)) {
            // Defence in depth — the controller @PreAuthorize should already
            // have rejected this caller.
            throw new PermissionDeniedException("Action not permitted");
        }

        boolean isSuper = ctx.hasRole(RoleCodes.HRLAB_SUPER_ADMIN);
        java.util.UUID effectiveTenantId;
        if (filter.tenantId() == null) {
            if (!isSuper) {
                // Auto-scope to caller's tenant (never leak null-tenant rows
                // to client admins; those rows are control-plane events).
                effectiveTenantId = ctx.tenantId();
            } else {
                effectiveTenantId = null; // explicit cross-tenant read
            }
        } else if (!isSuper && !filter.tenantId().equals(ctx.tenantId())) {
            // Non-super-admin asking for another tenant's log → silently
            // re-scope to their own active tenant. We do NOT raise
            // TenantAccessDenied here: the endpoint is tenant-agnostic by
            // design; raising would leak that "another tenant exists".
            effectiveTenantId = ctx.tenantId();
        } else {
            effectiveTenantId = filter.tenantId();
        }

        // Sanity: super-admin without an active tenant is fine (platform
        // scope); non-super-admin must have one.
        if (!isSuper && effectiveTenantId == null) {
            throw new TenantAccessDeniedException();
        }

        Pageable pageable = pageable(filter.page(), filter.size());
        Page<SystemAuditLogJpaEntity> page = auditRepo.search(
                effectiveTenantId,
                filter.actorUserId(),
                emptyToNull(filter.action()),
                emptyToNull(filter.entityType()),
                filter.entityId(),
                filter.from(),
                filter.to(),
                pageable);

        return page.map(ListAuditEventsQuery::toDto);
    }

    /**
     * Single audit-event lookup for {@code GET /api/v1/audit/{id}}.
     *
     * <p>Mirrors the tenant policy of {@link #list(AuditQueryFilter)}: a
     * non-super-admin may only read a row belonging to their own active tenant;
     * HRLab Super Admin may read any row (including null-tenant control-plane
     * events). When the row does not exist OR belongs to a different tenant for
     * a non-super-admin, we throw {@link TenantAccessDeniedException} so the
     * caller cannot distinguish "absent" from "another tenant's" — existence is
     * never leaked across tenants.
     */
    @Transactional(readOnly = true)
    public AuditEventResponse findById(java.util.UUID id) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.AUDIT_READ)
                && !ctx.hasPermission(PermissionCodes.AUDIT_VIEW)) {
            // Defence in depth — the controller @PreAuthorize should already
            // have rejected this caller.
            throw new PermissionDeniedException("Action not permitted");
        }

        SystemAuditLogJpaEntity row = auditRepo.findFirstByIdOrderByCreatedAtDesc(id)
                .orElseThrow(TenantAccessDeniedException::new);

        boolean isSuper = ctx.hasRole(RoleCodes.HRLAB_SUPER_ADMIN);
        if (!isSuper) {
            // Non-super-admins are confined to their own active tenant. A
            // null-tenant (control-plane) row or a row owned by another tenant
            // is reported as not-found — same status as a genuinely missing id.
            if (ctx.tenantId() == null || !ctx.tenantId().equals(row.getTenantId())) {
                throw new TenantAccessDeniedException();
            }
        }
        return toDto(row);
    }

    private static AuditEventResponse toDto(SystemAuditLogJpaEntity row) {
        return new AuditEventResponse(
                row.getId(),
                row.getAction(),
                row.getTenantId(),
                row.getProjectId(),
                row.getActorUserId(),
                row.getEntityType(),
                row.getEntityId(),
                row.getReason(),
                row.getIpAddress(),
                row.getUserAgent(),
                row.getCorrelationId(),
                row.getCreatedAt(),
                row.getHashCurrent());
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static Pageable pageable(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Pagination.clampSize(size <= 0 ? DEFAULT_PAGE_SIZE : size);
        // Sort is encoded in the JPQL "order by sa.createdAt desc"; we keep
        // an unsorted Pageable so the @Query is the single source of truth.
        return PageRequest.of(safePage, safeSize);
    }
}

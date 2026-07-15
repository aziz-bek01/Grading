package uz.hrlab.grading.access.api;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.hrlab.grading.access.application.AuditQueryFilter;
import uz.hrlab.grading.access.application.ListAuditEventsQuery;
import uz.hrlab.grading.access.application.VerifyAuditIntegrityQuery;
import uz.hrlab.grading.common.api.PageResponse;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * D-1 — Audit Reader API for the frontend Audit Timeline screen
 * (PRD §5 BE; security-blueprint §9.1 / §13).
 *
 * <p><b>Single endpoint:</b> {@code GET /api/v1/audit}. Optional filter axes
 * are bound as {@link RequestParam} (no body, no path) so the URL is
 * idempotent + cache-friendly. {@code tenantId} appears in the query string
 * for HRLab Super Admin cross-tenant reads ONLY; the {@code ListAuditEventsQuery}
 * silently overwrites it with the caller's active tenant for every other
 * role — non-super-admins cannot probe another tenant's audit log even by
 * spoofing the query parameter.
 *
 * <p><b>Permission:</b> {@link uz.hrlab.grading.access.application.PermissionCodes#AUDIT_READ}
 * (canonical) OR {@link uz.hrlab.grading.access.application.PermissionCodes#AUDIT_VIEW}
 * (alias kept for backwards compatibility with the original Phase 1.5 user
 * audit timeline). Held by HRLAB_SUPER_ADMIN, HRLAB_PROJECT_MANAGER,
 * CLIENT_COMPANY_ADMIN, EXTERNAL_AUDITOR (role-permissions-matrix.md §3).
 *
 * <p><b>No event is emitted for this read.</b> A {@code AUDIT_VIEWED} record
 * on every {@code GET /api/v1/audit} call would create an infinite write
 * loop (the audit row itself is then immediately re-readable, triggering
 * another row). Sensitive operations that DO emit a row are the CSV export
 * ({@code AUDIT_EXPORT_REQUESTED}, Sprint E) and the chain-verifier job
 * (security-blueprint §9.2, Sprint E).
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final ListAuditEventsQuery listAuditEvents;
    private final VerifyAuditIntegrityQuery verifyAuditIntegrity;

    public AuditController(ListAuditEventsQuery listAuditEvents,
                           VerifyAuditIntegrityQuery verifyAuditIntegrity) {
        this.listAuditEvents = listAuditEvents;
        this.verifyAuditIntegrity = verifyAuditIntegrity;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('AUDIT_READ','AUDIT_VIEW')")
    public PageResponse<AuditEventResponse> list(
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) UUID actorUserId,
            // NOTE: ArchUnit Rule 6 bans @RequestParam named tenantId on
            // BUSINESS controllers — audit is the documented exception (parallel
            // to the admin/control-plane carve-out) because HRLab Super Admin
            // needs cross-tenant read. The policy in ListAuditEventsQuery
            // overwrites the value for non-super-admins.
            @RequestParam(required = false, name = "scopeTenantId") UUID scopeTenantId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {

        AuditQueryFilter filter = new AuditQueryFilter(
                scopeTenantId, actorUserId, action, entityType, entityId,
                from, to, page, size);
        Page<AuditEventResponse> result = listAuditEvents.list(filter);
        return PageResponse.of(result, r -> r);
    }

    /**
     * Single audit-event detail for the frontend audit-details drawer. Tenant
     * isolation is enforced in {@link ListAuditEventsQuery#findById}: a
     * non-super-admin reading another tenant's (or a missing) row gets a 404 —
     * existence is never leaked across tenants.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('AUDIT_READ','AUDIT_VIEW')")
    public AuditEventResponse getById(@PathVariable UUID id) {
        return listAuditEvents.findById(id);
    }

    /**
     * MVP1-E10-1 — verify the integrity of the caller's tenant audit hash chain
     * (security-blueprint §9.2). Recomputes every row's {@code hash_current}
     * with the writer's own hashing and reports whether the chain is INTACT or,
     * if BROKEN, the first offending row + break type.
     *
     * <p><b>Stricter gate than the reader:</b> {@code AUDIT_READ} ONLY (no
     * {@code AUDIT_VIEW} alias) — held by HRLAB_SUPER_ADMIN + EXTERNAL_AUDITOR.
     * Tenant is taken from {@code TenantContext}; there is no cross-tenant
     * verification. Read-only, but emits ONE {@code AUDIT_INTEGRITY_VERIFIED}
     * forensic row per run (see {@link VerifyAuditIntegrityQuery}).
     *
     * <p>Optional {@code from}/{@code to} verify a slice; {@code maxRows} caps a
     * single run (defaults + hard ceiling enforced by {@code AuditChainVerifier}).
     */
    @GetMapping("/integrity")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public AuditIntegrityResponse integrity(
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) Integer maxRows) {
        return verifyAuditIntegrity.verify(from, to, maxRows);
    }
}

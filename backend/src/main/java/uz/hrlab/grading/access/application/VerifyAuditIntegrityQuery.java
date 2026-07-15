package uz.hrlab.grading.access.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.api.AuditIntegrityResponse;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditChainVerificationResult;
import uz.hrlab.grading.audit.application.AuditChainVerifier;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditJsonRedactor;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * MVP1-E10-1 — query handler for {@code GET /api/v1/audit/integrity}.
 *
 * <p>Permission + tenant-scope contract (security-blueprint §9.2 / §13):
 * <ul>
 *   <li>{@link PermissionCodes#AUDIT_READ} is REQUIRED — the controller enforces
 *       it via {@code @PreAuthorize} and this handler re-checks (defence in
 *       depth). Unlike the Audit Reader ({@code GET /api/v1/audit}) the integrity
 *       verifier does NOT accept the {@code AUDIT_VIEW} alias: chain verification
 *       is confined to the two roles that hold {@code AUDIT_READ} —
 *       HRLAB_SUPER_ADMIN and EXTERNAL_AUDITOR (DB invariant, seed 004).</li>
 *   <li>The verified tenant is ALWAYS {@code ctx.tenantId()} — never a client-
 *       supplied value. There is no cross-tenant verification path; a super
 *       admin verifies whichever tenant their context is pinned to.</li>
 * </ul>
 *
 * <p>The verification is read-only, but running it IS a sensitive forensic
 * action, so ONE append-only {@link AuditAction#AUDIT_INTEGRITY_VERIFIED} row is
 * emitted per run (who verified, when, outcome). Unlike listing the audit log
 * this cannot loop: it is an on-demand auditor action, not a per-read event.
 * The audit emit is best-effort — a failure to record the run must not fail the
 * (already computed) read.
 */
@Service
public class VerifyAuditIntegrityQuery {

    private static final Logger log = LoggerFactory.getLogger(VerifyAuditIntegrityQuery.class);

    private final AuditChainVerifier verifier;
    private final AuditService auditService;
    private final AuditJsonRedactor redactor;

    public VerifyAuditIntegrityQuery(AuditChainVerifier verifier,
                                     AuditService auditService,
                                     AuditJsonRedactor redactor) {
        this.verifier = verifier;
        this.auditService = auditService;
        this.redactor = redactor;
    }

    @Transactional(readOnly = true)
    public AuditIntegrityResponse verify(OffsetDateTime from, OffsetDateTime to, Integer maxRows) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.AUDIT_READ)) {
            // Defence in depth — the controller @PreAuthorize should already
            // have rejected this caller.
            throw new PermissionDeniedException("Action not permitted");
        }

        UUID tenantId = ctx.tenantId(); // from context, NEVER from request input
        AuditChainVerificationResult result = verifier.verify(tenantId, from, to, maxRows);

        recordVerificationRun(ctx, result);
        return toDto(result);
    }

    private void recordVerificationRun(TenantContext ctx, AuditChainVerificationResult result) {
        try {
            String reason = "status=" + result.status()
                    + " checked=" + result.checkedCount()
                    + " verified=" + result.independentlyVerifiedCount()
                    + " legacy=" + result.legacyUnverifiableCount()
                    + " chainLength=" + result.chainLength()
                    + (result.bounded() ? " bounded=true" : "");
            auditService.record(AuditEvent.builder(ctx)
                    .action(AuditAction.AUDIT_INTEGRITY_VERIFIED)
                    .entityType("AuditChain")
                    .entityId(ctx.tenantId())
                    .reason(redactor.redactReason(reason))
                    .build());
        } catch (RuntimeException ex) {
            // Read already succeeded — never fail the verification because the
            // forensic breadcrumb could not be written.
            log.warn("Failed to record AUDIT_INTEGRITY_VERIFIED: {}", ex.getMessage());
        }
    }

    private static AuditIntegrityResponse toDto(AuditChainVerificationResult r) {
        AuditIntegrityResponse.Break firstBreak = r.firstBreak() == null ? null
                : new AuditIntegrityResponse.Break(
                        r.firstBreak().rowId(),
                        r.firstBreak().createdAt(),
                        r.firstBreak().breakType().name(),
                        r.firstBreak().expectedHash(),
                        r.firstBreak().actualHash());
        return new AuditIntegrityResponse(
                r.tenantId(),
                r.status().name(),
                r.intact(),
                r.checkedCount(),
                r.chainLength(),
                r.independentlyVerifiedCount(),
                r.legacyUnverifiableCount(),
                r.verifiableFrom(),
                r.verifiedThrough(),
                r.from(),
                r.to(),
                r.bounded(),
                r.maxRows(),
                firstBreak,
                r.verifiedAt());
    }
}

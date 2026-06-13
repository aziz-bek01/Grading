package uz.hrlab.grading.methodology.application;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditJsonRedactor;
import uz.hrlab.grading.audit.application.AuditService;

import java.util.UUID;

/**
 * BE-5 — emits the single {@link AuditAction#METHODOLOGY_APPROVED_EDIT} umbrella
 * audit event per approved-version edit transaction. It does NOT replace the
 * per-field {@code FACTOR_UPDATED} / {@code FACTOR_LEVEL_UPDATED} /
 * {@code *_DEPRECATED} events — it ADDS the "approved edit" framing plus the
 * blast radius ({@code frozenEvaluationCount} = non-archived evaluations pinned
 * to the version at edit time, computed in-transaction).
 *
 * <p>Called ONLY on the APPROVED carve-out branch of the factor / level write
 * services, so exactly one umbrella row is written per approved-version edit.
 *
 * <h3>Umbrella contract (invariant)</h3>
 * <strong>Invariant:</strong> exactly ONE {@code METHODOLOGY_APPROVED_EDIT}
 * umbrella row is emitted per approved-edit <em>write-operation / transaction</em>
 * — and, given today's "one controller endpoint = one service write = one
 * transaction" topology, exactly one per approved-edit endpoint call.
 *
 * <p>Exactly ONE {@code METHODOLOGY_APPROVED_EDIT} umbrella row is emitted per
 * approved-edit <em>write-operation / transaction</em>. The cardinality is
 * therefore "one umbrella row per service write", NOT "one per field changed":
 * a single {@link FactorService#update}/{@link FactorLevelService#update}/etc.
 * call mutates one entity, records its per-field {@code FACTOR_UPDATED} /
 * {@code FACTOR_LEVEL_UPDATED} / {@code *_DEPRECATED} row, and then calls
 * {@link #emit} exactly once. Today every controller endpoint maps to exactly
 * one such service write inside one transaction, so endpoint ⇒ write ⇒ tx ⇒
 * one umbrella row hold as a chain of 1:1 mappings.
 *
 * <p><strong>Warning to future callers:</strong> if a batching caller is ever
 * introduced that performs several approved-version writes in a single
 * transaction (e.g. a bulk factor edit), this method will emit one umbrella row
 * <em>per write call</em>, i.e. multiple umbrella rows in that one transaction —
 * it does NOT coalesce to one umbrella row per transaction. A caller that needs
 * a single transaction-level umbrella row must aggregate the writes and invoke
 * {@link #emit} once itself; do not assume per-tx deduplication here.
 */
@Component
public class ApprovedEditAudit {

    private final AuditService audit;
    private final AuditJsonRedactor redactor;
    private final MethodologyReferencePort referencePort;

    public ApprovedEditAudit(AuditService audit,
                             AuditJsonRedactor redactor,
                             MethodologyReferencePort referencePort) {
        this.audit = audit;
        this.redactor = redactor;
        this.referencePort = referencePort;
    }

    /**
     * @param tenantId             active tenant
     * @param projectId            methodology's project (nullable for global)
     * @param actorUserId          the super admin performing the edit
     * @param methodologyVersionId the APPROVED version being edited (entity id)
     * @param fieldChange          before/after JSON of the changed entity (the
     *                             field delta), or {@code null}
     * @param reason               short human description (e.g. "factor weight edited")
     */
    public void emit(UUID tenantId, UUID projectId, UUID actorUserId,
                     UUID methodologyVersionId, JsonNode fieldChange, String reason) {
        long frozen = referencePort.countNonArchivedEvaluationsPinnedToVersion(
                tenantId, methodologyVersionId);
        JsonNode after = redactor.builder()
                .putRaw("frozenEvaluationCount", frozen)
                .putRaw("fieldChange", fieldChange)
                .build();
        audit.record(AuditEvent.builder()
                .tenantId(tenantId)
                .projectId(projectId)
                .actorUserId(actorUserId)
                .action(AuditAction.METHODOLOGY_APPROVED_EDIT)
                .entityType("MethodologyVersion")
                .entityId(methodologyVersionId)
                .reason(reason)
                .afterJson(after)
                .build());
    }
}

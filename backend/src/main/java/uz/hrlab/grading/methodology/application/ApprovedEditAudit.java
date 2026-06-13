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

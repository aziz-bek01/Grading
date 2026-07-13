package uz.hrlab.grading.common.application;

import com.fasterxml.jackson.databind.JsonNode;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.tenancy.application.TenantContext;

import java.util.Objects;

/**
 * Shared skeleton for the ~large family of "status-transition" use cases
 * (approve / lock / archive / submit / request-changes across evaluation,
 * grade-structure, methodology-version, job-profile and questionnaire).
 *
 * <p>Every such use case previously hand-repeated the SAME control-flow —
 * ABAC write-gate → transition-policy check → {@code beforeJson} snapshot →
 * status/timestamp/actor mutation → repo save → {@code afterJson} snapshot →
 * {@link AuditService#record} — which meant a future transition could silently
 * omit the ABAC gate or the audit row with no compiler help. This executor owns
 * that skeleton and its ORDERING, so:
 * <ul>
 *   <li>the ABAC gate is a mandatory step (the transition cannot be assembled
 *       without an {@code abac...} scope), and</li>
 *   <li>the audit row — with its {@code beforeJson}/{@code afterJson} pair taken
 *       from a single {@code snapshot} supplier — is written by the executor
 *       itself: a migrated use case cannot forget it because it never records
 *       the row directly.</li>
 * </ul>
 *
 * <p>The permission re-check ({@code requireActive().require(PERMISSION)}), the
 * tenant-scoped entity load, any reason validation, and any extra pre-load work
 * stay at the use-case head: their exact ordering relative to each other is
 * behaviour-observable and use-case specific. The executor picks up once the
 * entity (and any collaborators the ABAC scope / mutation need) are in hand.
 *
 * <p>Two optional hooks let richer use cases reuse the skeleton without losing
 * their specifics: {@link StatusTransition#beforeMutate(Runnable)} runs after
 * the transition check but before the {@code beforeJson} snapshot (e.g. the
 * methodology ≥2-levels / weight validation, the grade-band overlap/gap checks,
 * the evaluation completeness re-check), and
 * {@link StatusTransition#afterSave(Runnable)} runs after the audit row is
 * written (e.g. auto-opening the single-step {@code ApprovalRequest}). Pre-save
 * side effects that must be captured by {@code afterJson} (e.g. grade assignment)
 * belong in the {@code mutate} step.
 *
 * <p>Stateless helper — each use case builds one from the {@link AbacGate} and
 * {@link AuditService} it already receives, so no use-case constructor signature
 * changes (and no test constructing a use case directly needs touching).
 */
public class StatusTransitionExecutor {

    private final AbacGate abacGate;
    private final AuditService audit;

    public StatusTransitionExecutor(AbacGate abacGate, AuditService audit) {
        this.abacGate = abacGate;
        this.audit = audit;
    }

    /** Opens a fluent transition bound to the caller's already-resolved context. */
    public StatusTransition transition(TenantContext ctx) {
        return new StatusTransition(this, Objects.requireNonNull(ctx, "ctx"));
    }

    AbacGate abacGate() {
        return abacGate;
    }

    /**
     * Runs the assembled transition in the canonical order. All mandatory steps
     * are null-checked up front so a half-built transition fails fast rather than
     * silently skipping the ABAC gate or the audit snapshot.
     */
    void run(StatusTransition t) {
        Objects.requireNonNull(t.abac, "abac scope is required");
        Objects.requireNonNull(t.transitionCheck, "transition check is required");
        Objects.requireNonNull(t.snapshot, "audit snapshot supplier is required");
        Objects.requireNonNull(t.mutate, "mutate step is required");
        Objects.requireNonNull(t.save, "save step is required");
        Objects.requireNonNull(t.action, "audit action is required");
        Objects.requireNonNull(t.entityType, "audit entityType is required");
        Objects.requireNonNull(t.entityId, "audit entityId is required");

        t.abac.run();
        t.transitionCheck.run();
        if (t.beforeMutate != null) {
            t.beforeMutate.run();
        }

        JsonNode beforeJson = t.snapshot.get();
        t.mutate.run();
        t.save.run();
        JsonNode afterJson = t.snapshot.get();

        audit.record(AuditEvent.builder(t.ctx)
                .projectId(t.auditProjectId)
                .action(t.action)
                .entityType(t.entityType)
                .entityId(t.entityId)
                .reason(t.reason)
                .beforeJson(beforeJson)
                .afterJson(afterJson)
                .build());

        if (t.afterSave != null) {
            t.afterSave.run();
        }
    }
}

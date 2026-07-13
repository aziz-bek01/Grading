package uz.hrlab.grading.common.application;

import com.fasterxml.jackson.databind.JsonNode;
import uz.hrlab.grading.tenancy.application.TenantContext;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Fluent, single-use description of one status transition, executed by
 * {@link StatusTransitionExecutor}. See that class for the ordering contract.
 *
 * <p>Mandatory steps: an {@code abac...} scope, {@link #checkTransition},
 * {@link #snapshot}, {@link #mutate}, {@link #save} and {@link #audit}.
 * Optional: {@link #beforeMutate}, {@link #afterSave} and {@link #reason}.
 */
public final class StatusTransition {

    private final StatusTransitionExecutor executor;
    final TenantContext ctx;

    Runnable abac;
    Runnable transitionCheck;
    Runnable beforeMutate;
    Supplier<JsonNode> snapshot;
    Runnable mutate;
    Runnable save;
    Runnable afterSave;

    String action;
    String entityType;
    UUID entityId;
    UUID auditProjectId;
    String reason;

    StatusTransition(StatusTransitionExecutor executor, TenantContext ctx) {
        this.executor = executor;
        this.ctx = ctx;
    }

    // ---------------------------------------------------------------- ABAC scope

    /**
     * Project-scoped write gate — the common case. Null-guarded: a null
     * {@code projectId} (tenant-level template rows) skips the gate, matching the
     * {@code if (projectId != null) enforceCanWriteInProject(...)} idiom used by
     * grade-structure / methodology-version and equivalent to the unconditional
     * call used by evaluation (whose {@code projectId} is never null).
     */
    public StatusTransition abacProjectWrite(UUID projectId) {
        this.abac = () -> {
            if (projectId != null) {
                executor.abacGate().enforceCanWriteInProject(ctx, projectId);
            }
        };
        return this;
    }

    /**
     * Project + department-subtree write gate (job-profile / questionnaire): the
     * project membership check followed by the department-scope check, in that
     * order.
     */
    public StatusTransition abacProjectAndDepartmentWrite(UUID projectId, UUID departmentId) {
        this.abac = () -> {
            executor.abacGate().enforceCanWriteInProject(ctx, projectId);
            executor.abacGate().enforceCanWriteInDepartment(ctx, projectId, departmentId);
        };
        return this;
    }

    /**
     * Department-subtree write gate only (evaluation submit resolves the gate via
     * the evaluation's position without a separate project-membership call).
     */
    public StatusTransition abacDepartmentWrite(UUID projectId, UUID departmentId) {
        this.abac = () -> executor.abacGate().enforceCanWriteInDepartment(ctx, projectId, departmentId);
        return this;
    }

    /** Escape hatch for a scope none of the helpers express. */
    public StatusTransition abac(Runnable customGate) {
        this.abac = customGate;
        return this;
    }

    // ----------------------------------------------------------------- lifecycle

    public StatusTransition checkTransition(Runnable transitionCheck) {
        this.transitionCheck = transitionCheck;
        return this;
    }

    /** Optional extra validation; runs after the transition check, before snapshot. */
    public StatusTransition beforeMutate(Runnable beforeMutate) {
        this.beforeMutate = beforeMutate;
        return this;
    }

    /** Supplies the audit snapshot of the entity; invoked once before and once after mutation. */
    public StatusTransition snapshot(Supplier<JsonNode> snapshot) {
        this.snapshot = snapshot;
        return this;
    }

    /** Status flip + timestamp + actor, plus any side effect that {@code afterJson} must capture. */
    public StatusTransition mutate(Runnable mutate) {
        this.mutate = mutate;
        return this;
    }

    public StatusTransition save(Runnable save) {
        this.save = save;
        return this;
    }

    /** Optional post-audit continuation (e.g. cascade an approval-request). */
    public StatusTransition afterSave(Runnable afterSave) {
        this.afterSave = afterSave;
        return this;
    }

    // --------------------------------------------------------------------- audit

    public StatusTransition audit(String action, String entityType, UUID entityId, UUID projectId) {
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.auditProjectId = projectId;
        return this;
    }

    /** Optional audit reason (already redacted/trimmed by the caller as required). */
    public StatusTransition reason(String reason) {
        this.reason = reason;
        return this;
    }

    public void execute() {
        executor.run(this);
    }
}

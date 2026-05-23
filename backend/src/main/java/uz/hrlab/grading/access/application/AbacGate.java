package uz.hrlab.grading.access.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.tenancy.application.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Composite ABAC gate (security-blueprint §7, F-06 backlog).
 *
 * <p>Iterates every registered {@link ScopePolicy}. If any returns
 * {@link PolicyDecision#DENY}, the request is rejected with a
 * {@link TenantAccessDeniedException} (mapped to 404 by the global handler,
 * per cross-tenant-probing protection) and an
 * {@code ACCESS_DENIED_BY_ABAC} audit row is written including the offending
 * policy name.
 */
@Service
public class AbacGate {

    private static final String AUDIT_ACTION = "ACCESS_DENIED_BY_ABAC";
    private static final Logger log = LoggerFactory.getLogger(AbacGate.class);

    private final List<ScopePolicy> policies;
    private final AuditService audit;

    public AbacGate(List<ScopePolicy> policies, AuditService audit) {
        this.policies = policies;
        this.audit = audit;
    }

    /** Enforces every policy; throws {@link TenantAccessDeniedException} on deny. */
    public void enforce(AbacRequest request) {
        for (ScopePolicy p : policies) {
            PolicyDecision d = p.evaluate(request);
            if (d == PolicyDecision.DENY) {
                recordDenial(request, p.name());
                throw new TenantAccessDeniedException();
            }
        }
    }

    public void enforceCanReadProject(TenantContext ctx, UUID projectId, Object status) {
        enforce(new AbacRequest(
                ctx,
                "Project",
                projectId,
                projectId,
                null,
                status == null ? null : status.toString()));
    }

    public void enforceCanReadDepartment(TenantContext ctx, UUID departmentId, UUID projectId,
                                         Object status) {
        enforce(new AbacRequest(
                ctx,
                "Department",
                departmentId,
                projectId,
                departmentId,
                status == null ? null : status.toString()));
    }

    public void enforceCanReadPosition(TenantContext ctx, UUID positionId, UUID projectId,
                                       UUID departmentId, Object status) {
        enforce(new AbacRequest(
                ctx,
                "Position",
                positionId,
                projectId,
                departmentId,
                status == null ? null : status.toString()));
    }

    public void enforceCanListInProject(TenantContext ctx, UUID projectId) {
        enforce(new AbacRequest(
                ctx, "ProjectListing", null, projectId, null, null));
    }

    /**
     * F-202 — ABAC enforcement on the write path. Invoked by every Create /
     * Update / Archive use case after the entity has been loaded and the
     * tenant filter has matched. Re-uses {@link ProjectMembershipPolicy} and
     * {@link DepartmentScopePolicy}: project-scoped roles that lack membership
     * in the target project are denied with a 404 (no existence reveal) and
     * an {@code ACCESS_DENIED_BY_ABAC} audit row is written.
     */
    public void enforceCanWriteInProject(TenantContext ctx, UUID projectId) {
        enforce(new AbacRequest(
                ctx, "ProjectWrite", null, projectId, null, null));
    }

    /**
     * F-202 — Department-scoped write check (used by Department / Position
     * create + update + archive). Same semantics as
     * {@link #enforceCanWriteInProject(TenantContext, UUID)} but also exercises
     * {@code DepartmentScopePolicy} so Department Managers cannot mutate items
     * outside their department subtree.
     */
    public void enforceCanWriteInDepartment(TenantContext ctx, UUID projectId, UUID departmentId) {
        enforce(new AbacRequest(
                ctx, "DepartmentWrite", departmentId, projectId, departmentId, null));
    }

    private void recordDenial(AbacRequest request, String policyName) {
        try {
            TenantContext ctx = request.context();
            audit.record(AuditEvent.builder()
                    .tenantId(ctx == null ? null : ctx.tenantId())
                    .projectId(request.projectId())
                    .actorUserId(ctx == null ? null : ctx.userId())
                    .action(AUDIT_ACTION)
                    .entityType(request.entityType())
                    .entityId(request.entityId())
                    .reason("policy=" + policyName)
                    .build());
        } catch (Exception ex) {
            log.error("Failed to persist {} audit row policy={}", AUDIT_ACTION, policyName, ex);
        }
    }
}

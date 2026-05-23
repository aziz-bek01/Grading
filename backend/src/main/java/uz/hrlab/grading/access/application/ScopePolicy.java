package uz.hrlab.grading.access.application;

/**
 * Common contract for fine-grained ABAC scope policies (F-06 backlog).
 *
 * <p>Each implementation focuses on ONE attribute axis
 * (tenant assignment, department subtree, project membership, lifecycle
 * status). The {@link AbacGate} composes them.
 *
 * <p>Returning {@link PolicyDecision#DENY} causes the request to be rejected
 * with a 404 (cross-tenant probing protection — security-blueprint §11) and
 * an {@code ACCESS_DENIED_BY_ABAC} audit row.
 */
public interface ScopePolicy {

    String name();

    PolicyDecision evaluate(AbacRequest request);
}

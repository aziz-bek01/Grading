package uz.hrlab.grading.common.persistence;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

/**
 * Binds the PostgreSQL Row-Level-Security tenant GUC ({@code app.tenant_id})
 * to the connection of the <em>currently active</em> Spring transaction.
 *
 * <h2>Why an aspect, and why it runs inside the transaction</h2>
 * The RLS policies in changelog {@code 030-enable-rls.yaml} filter on
 * {@code current_setting('app.tenant_id', true)}. That GUC must be set with
 * {@code SET LOCAL} on the <strong>same connection that runs the JPA query</strong>
 * and <strong>inside the same transaction</strong> — {@code SET LOCAL} is scoped
 * to the current transaction and reverts at commit/rollback.
 *
 * <p>The previous implementation issued {@code SET LOCAL} from
 * {@code TenantContextFilter} (a servlet filter). At that point <em>no</em>
 * transaction is open, so {@link JdbcTemplate} borrowed a pooled connection,
 * ran {@code SET LOCAL} on it, and returned it immediately — the setting was
 * discarded. When the {@code @Transactional} service method later opened its
 * own transaction on a (possibly different) connection, the GUC was unset.
 * Under the non-superuser {@code grading_runtime} role with {@code FORCE ROW
 * LEVEL SECURITY}, {@code current_setting('app.tenant_id', true)} then resolved
 * to the empty string {@code ""}, and the policy's {@code ''::uuid} cast raised
 * PostgreSQL {@code 22P02} — surfacing as a 409 on every data page.
 *
 * <p>This aspect fixes the root cause: it advises every {@code @Transactional}
 * join point and runs <em>after</em> the transaction interceptor has opened the
 * transaction (the tx advisor is pinned to a higher precedence in
 * {@link TransactionManagementConfig}; this aspect is one step lower). Inside an
 * active Spring-managed transaction, {@code JdbcTemplate} (via
 * {@code DataSourceUtils}) reuses the transaction-bound connection — the very
 * same connection Hibernate executes the query on — so the {@code SET LOCAL}
 * applies to the subsequent JPA reads/writes.
 *
 * <h2>Fail-safe behaviour</h2>
 * <ul>
 *   <li>No active transaction → no-op (guarded by
 *       {@link TransactionSynchronizationManager#isActualTransactionActive()}),
 *       so we never leak a {@code SET} onto a borrowed-and-returned connection.</li>
 *   <li>No tenant context (control-plane / admin / unprovisioned request) →
 *       the GUC is intentionally left unset. Migration
 *       {@code 031-rls-nullif.yaml} makes an unset/empty GUC yield zero rows
 *       instead of {@code 22P02}, so this is fail-closed by construction.</li>
 * </ul>
 *
 * <p>Note: {@code SET LOCAL} cannot use a bind parameter. The tenant id is a
 * server-generated {@link UUID} whose {@code toString()} is the canonical
 * hyphenated form — no quoting/injection hazard.
 */
@Aspect
@Component
@Order(RlsTenantSessionAspect.ORDER)
public class RlsTenantSessionAspect {

    /**
     * One step lower in precedence than the transaction advisor
     * ({@link TransactionManagementConfig#TX_ADVISOR_ORDER}) so this advice
     * executes <em>inside</em> the transaction the interceptor just opened.
     */
    public static final int ORDER = TransactionManagementConfig.TX_ADVISOR_ORDER + 100;

    private static final Logger log = LoggerFactory.getLogger(RlsTenantSessionAspect.class);

    private final JdbcTemplate jdbcTemplate;

    public RlsTenantSessionAspect(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Matches any method (or method on a type) annotated {@code @Transactional}
     * — Spring's annotation and the JTA one — across the application layer.
     */
    @Pointcut("@annotation(org.springframework.transaction.annotation.Transactional)"
            + " || @within(org.springframework.transaction.annotation.Transactional)"
            + " || @annotation(jakarta.transaction.Transactional)"
            + " || @within(jakarta.transaction.Transactional)")
    void transactionalOperation() {
    }

    @Before("transactionalOperation()")
    public void bindTenantToTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            // Defensive: should not happen for @Transactional methods, but if
            // ordering ever regresses we must NOT set the GUC on a connection
            // that will be returned to the pool without a transaction.
            return;
        }
        TenantContext ctx = TenantContextHolder.get();
        if (ctx == null || ctx.tenantId() == null) {
            // Control-plane / admin / unprovisioned — leave the GUC unset.
            // 031-rls-nullif.yaml makes that return zero rows, not 22P02.
            return;
        }
        try {
            // Runs on the transaction-bound connection (same one Hibernate uses
            // for the query). SET LOCAL auto-resets at commit/rollback.
            jdbcTemplate.execute("SET LOCAL app.tenant_id = '" + ctx.tenantId() + "'");
        } catch (RuntimeException ex) {
            // Fail-closed: if we cannot bind the GUC the RLS policy returns zero
            // rows (never another tenant's data). Log and let the operation run.
            log.warn("Failed to SET LOCAL app.tenant_id={} on active transaction — "
                    + "RLS will return zero rows for this operation", ctx.tenantId(), ex);
        }
    }
}

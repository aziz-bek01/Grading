package uz.hrlab.grading.common.persistence;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Pins the {@code @Transactional} advisor to a deterministic precedence so the
 * {@link RlsTenantSessionAspect} can be ordered to run <em>inside</em> the
 * transaction the interceptor opens.
 *
 * <p>Spring Boot otherwise enables transaction management at
 * {@code Ordered.LOWEST_PRECEDENCE}; if our RLS aspect also sat at
 * {@code LOWEST_PRECEDENCE} their relative order would be undefined and the
 * {@code SET LOCAL} could fire before the transaction (and its connection)
 * exists. By giving the transaction advisor a fixed, higher precedence here and
 * placing the aspect one step lower ({@link RlsTenantSessionAspect#ORDER}), the
 * GUC is guaranteed to be bound to the already-open transaction's connection.
 *
 * <p>{@code TX_ADVISOR_ORDER} is well below {@code Ordered.LOWEST_PRECEDENCE},
 * leaving head-room for the aspect (+100) to remain lower-precedence without
 * overflowing.
 */
@Configuration
@EnableTransactionManagement(order = TransactionManagementConfig.TX_ADVISOR_ORDER)
public class TransactionManagementConfig {

    /** Fixed precedence for the {@code @Transactional} advisor. */
    public static final int TX_ADVISOR_ORDER = Integer.MAX_VALUE - 1000;
}

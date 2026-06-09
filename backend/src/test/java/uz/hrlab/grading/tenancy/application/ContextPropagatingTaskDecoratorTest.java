package uz.hrlab.grading.tenancy.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ContextPropagatingTaskDecorator}.
 *
 * <p>These run fully offline (no Spring context, no DB) and are the regression
 * guard for the forced-RLS async stall: the worker thread must see the
 * submitting thread's {@link TenantContext}, and the worker thread must be
 * cleaned up afterwards so pooled threads never leak one tenant's context into
 * the next task.
 */
@Tag("unit")
class ContextPropagatingTaskDecoratorTest {

    private final ContextPropagatingTaskDecorator decorator = new ContextPropagatingTaskDecorator();

    @AfterEach
    void tearDown() {
        // Keep the test thread (which is itself reused by JUnit) pristine.
        TenantContextHolder.clear();
        MDC.clear();
    }

    @Test
    void decoratedRunnableSeesCallerTenantContextOnWorkerThread() throws InterruptedException {
        TenantContext caller = context(UUID.randomUUID());
        TenantContextHolder.set(caller);

        AtomicReference<TenantContext> seenInsideRun = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() ->
                seenInsideRun.set(TenantContextHolder.get()));

        // Run on a DIFFERENT thread to simulate the pooled worker.
        runOnSeparateThread(decorated);

        assertThat(seenInsideRun.get())
                .as("worker thread must observe the submitting thread's tenant context")
                .isSameAs(caller);
    }

    @Test
    void decoratedRunnablePropagatesMdc() throws InterruptedException {
        TenantContextHolder.set(context(UUID.randomUUID()));
        MDC.put("correlationId", "corr-123");
        MDC.put("traceId", "trace-abc");

        AtomicReference<String> seenCorrelation = new AtomicReference<>();
        AtomicReference<String> seenTrace = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() -> {
            seenCorrelation.set(MDC.get("correlationId"));
            seenTrace.set(MDC.get("traceId"));
        });

        runOnSeparateThread(decorated);

        assertThat(seenCorrelation.get()).isEqualTo("corr-123");
        assertThat(seenTrace.get()).isEqualTo("trace-abc");
    }

    @Test
    void workerThreadIsCleanedUpAfterSuccessfulRun() throws InterruptedException {
        TenantContextHolder.set(context(UUID.randomUUID()));
        MDC.put("correlationId", "corr-success");
        Runnable decorated = decorator.decorate(() -> { /* no-op */ });

        AtomicReference<TenantContext> contextAfterRun = new AtomicReference<>();
        AtomicReference<String> mdcAfterRun = new AtomicReference<>();
        runOnSeparateThread(() -> {
            decorated.run();
            // Same thread, after the decorated task completed: must be clean.
            contextAfterRun.set(TenantContextHolder.get());
            mdcAfterRun.set(MDC.get("correlationId"));
        });

        assertThat(contextAfterRun.get())
                .as("pooled thread must not retain tenant context after the task")
                .isNull();
        assertThat(mdcAfterRun.get())
                .as("pooled thread must not retain MDC after the task")
                .isNull();
    }

    @Test
    void workerThreadIsCleanedUpEvenWhenTaskThrows() throws InterruptedException {
        TenantContextHolder.set(context(UUID.randomUUID()));
        MDC.put("correlationId", "corr-boom");
        Runnable decorated = decorator.decorate(() -> {
            throw new IllegalStateException("boom");
        });

        AtomicReference<TenantContext> contextAfterThrow = new AtomicReference<>();
        AtomicReference<String> mdcAfterThrow = new AtomicReference<>();
        runOnSeparateThread(() -> {
            assertThatThrownBy(decorated::run).isInstanceOf(IllegalStateException.class);
            contextAfterThrow.set(TenantContextHolder.get());
            mdcAfterThrow.set(MDC.get("correlationId"));
        });

        assertThat(contextAfterThrow.get())
                .as("tenant context must be cleared in finally, even on exception")
                .isNull();
        assertThat(mdcAfterThrow.get())
                .as("MDC must be cleared in finally, even on exception")
                .isNull();
    }

    @Test
    void nullCallerContextClearsWorkerThreadAndRestoresAfterwards() throws InterruptedException {
        // No caller context set on the submitting thread.
        TenantContextHolder.clear();
        Runnable decorated = decorator.decorate(() -> {
            // Inside run, the worker must NOT see any stale context.
            assertThat(TenantContextHolder.get()).isNull();
        });

        // Simulate a dirty pooled thread that already had context from a prior task.
        TenantContext stale = context(UUID.randomUUID());
        AtomicReference<TenantContext> contextAfterRun = new AtomicReference<>();
        runOnSeparateThread(() -> {
            TenantContextHolder.set(stale);
            decorated.run();
            contextAfterRun.set(TenantContextHolder.get());
        });

        // After run, the worker thread's PRIOR state is restored (snapshot/restore).
        assertThat(contextAfterRun.get())
                .as("decorator restores the worker thread's prior state, not a blind clear")
                .isSameAs(stale);
    }

    private static TenantContext context(UUID tenantId) {
        return new TenantContext(
                UUID.randomUUID(), tenantId, Set.of(), Set.of(), Set.of(),
                Set.of(), false, "en-US");
    }

    private static void runOnSeparateThread(Runnable r) throws InterruptedException {
        Thread t = new Thread(r, "test-worker");
        t.start();
        t.join(5_000);
        assertThat(t.isAlive()).as("worker thread completed").isFalse();
    }
}

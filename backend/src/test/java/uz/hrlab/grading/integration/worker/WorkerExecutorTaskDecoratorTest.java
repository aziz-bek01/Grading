package uz.hrlab.grading.integration.worker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import uz.hrlab.grading.reporting.infrastructure.ReportWorkerConfig;
import uz.hrlab.grading.tenancy.application.ContextPropagatingTaskDecorator;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards that every async worker executor carries a
 * {@link ContextPropagatingTaskDecorator}. Without it the worker thread loses
 * the tenant context and forced RLS hides all rows — the exact production bug
 * that stalled import batches at UPLOADED.
 *
 * <p>{@link ThreadPoolTaskExecutor} exposes no public getter for the decorator,
 * so we assert behaviour: a tenant context set on the submitting thread must be
 * visible inside a task executed on the pool, which holds only when the
 * decorator is wired. Runs offline (no Spring context, no DB).
 */
@Tag("unit")
class WorkerExecutorTaskDecoratorTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void importWorkerExecutorPropagatesTenantContext() throws Exception {
        assertPropagates(new IntegrationWorkerConfig().importWorkerExecutor());
    }

    @Test
    void exportWorkerExecutorPropagatesTenantContext() throws Exception {
        assertPropagates(new IntegrationWorkerConfig().exportWorkerExecutor());
    }

    @Test
    void reportWorkerExecutorPropagatesTenantContext() throws Exception {
        assertPropagates(new ReportWorkerConfig().reportWorkerExecutor());
    }

    private static void assertPropagates(Executor executor) throws Exception {
        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        ThreadPoolTaskExecutor tpe = (ThreadPoolTaskExecutor) executor;
        try {
            TenantContext caller = new TenantContext(
                    UUID.randomUUID(), UUID.randomUUID(), Set.of(), Set.of(),
                    Set.of(), Set.of(), false, "en-US");
            TenantContextHolder.set(caller);

            AtomicReference<TenantContext> seenOnWorker = new AtomicReference<>();
            // Submit on the executor — the wired TaskDecorator must capture the
            // caller context here and re-establish it on the pooled thread.
            Future<?> f = tpe.submit(() -> seenOnWorker.set(TenantContextHolder.get()));
            f.get();

            assertThat(seenOnWorker.get())
                    .as("executor must propagate the caller tenant context via TaskDecorator")
                    .isSameAs(caller);
        } finally {
            tpe.shutdown();
        }
    }
}

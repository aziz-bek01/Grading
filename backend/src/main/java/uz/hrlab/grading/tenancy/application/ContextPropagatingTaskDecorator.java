package uz.hrlab.grading.tenancy.application;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * Propagates the per-request {@link TenantContext} (and the SLF4J {@link MDC}
 * trace/correlation map) from the submitting thread onto the pooled worker
 * thread that executes an {@code @Async} task.
 *
 * <h2>Why this exists</h2>
 * {@link TenantContextHolder} is a {@link ThreadLocal}. When an
 * {@code @Async("...Executor")} method runs, Spring hands the task to a thread
 * in a {@link org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor}
 * pool that has <em>no</em> request context — {@code TenantContextHolder.get()}
 * returns {@code null}. With PostgreSQL <strong>forced</strong> RLS enabled,
 * {@code RlsTenantSessionAspect} then leaves {@code app.tenant_id} unset, so
 * every tenant-scoped query returns zero rows. That silently broke the import /
 * export / report workers (they reload by {@code findByIdAndTenantId(...)} and
 * got an empty result, leaving batches stuck at their initial status).
 *
 * <p>This decorator captures the context on the calling thread <em>at submit
 * time</em> and re-establishes it on the worker thread immediately before the
 * task body runs, so the RLS aspect can bind {@code app.tenant_id} normally.
 *
 * <h2>Cleanup is mandatory (security-critical)</h2>
 * Pool threads are reused. If we set a {@code TenantContext} on a pooled thread
 * and did not remove it, the next unrelated task scheduled onto that same thread
 * would inherit the previous tenant's context — a cross-tenant data-exposure
 * hazard. We therefore <strong>always</strong> restore the worker thread's
 * prior state in a {@code finally} block (snapshot/restore, not blind clear, so
 * nested submissions behave correctly), even when the task throws.
 *
 * <p>A {@code null} captured context (e.g. a system/scheduled submission with no
 * active request) is handled gracefully: the worker thread is cleared rather
 * than set, and restored afterwards.
 */
public final class ContextPropagatingTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // Captured ON THE SUBMITTING THREAD, at decorate() time.
        final TenantContext capturedContext = TenantContextHolder.get();
        final Map<String, String> capturedMdc = MDC.getCopyOfContextMap();

        return () -> {
            // Snapshot whatever this pooled thread currently holds so we can
            // restore it exactly — never leak across pooled tasks.
            final TenantContext previousContext = TenantContextHolder.get();
            final Map<String, String> previousMdc = MDC.getCopyOfContextMap();

            applyContext(capturedContext);
            applyMdc(capturedMdc);
            try {
                runnable.run();
            } finally {
                applyContext(previousContext);
                applyMdc(previousMdc);
            }
        };
    }

    private static void applyContext(TenantContext context) {
        if (context == null) {
            TenantContextHolder.clear();
        } else {
            TenantContextHolder.set(context);
        }
    }

    private static void applyMdc(Map<String, String> mdc) {
        if (mdc == null || mdc.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(mdc);
        }
    }
}

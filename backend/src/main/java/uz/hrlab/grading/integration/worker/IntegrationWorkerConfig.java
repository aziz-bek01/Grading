package uz.hrlab.grading.integration.worker;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import uz.hrlab.grading.tenancy.application.ContextPropagatingTaskDecorator;

import java.util.concurrent.Executor;

/**
 * Async worker executor configuration (integration-blueprint §4.1).
 *
 * <p>Two dedicated executors with 4 threads each — one for imports, one for
 * exports. Production targets a real queue (Redis Streams / RabbitMQ /
 * Kafka), but the in-process executor is sufficient to land the worker
 * contract for MVP 2 Phase 2 and the migration to a real broker becomes a
 * single bean swap.
 *
 * <p>Both executors carry a {@link ContextPropagatingTaskDecorator} so the
 * submitting request's {@link uz.hrlab.grading.tenancy.application.TenantContext}
 * (and MDC trace ids) reach the pooled worker thread. Without it,
 * {@code TenantContextHolder.get()} is null on the worker, the RLS aspect cannot
 * set {@code app.tenant_id}, and forced RLS hides every row — which silently
 * stalled the import/export workers.
 */
@Configuration
@EnableAsync
public class IntegrationWorkerConfig {

    @Bean(name = "importWorkerExecutor")
    public Executor importWorkerExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(64);
        ex.setThreadNamePrefix("import-worker-");
        ex.setTaskDecorator(new ContextPropagatingTaskDecorator());
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(30);
        ex.initialize();
        return ex;
    }

    @Bean(name = "exportWorkerExecutor")
    public Executor exportWorkerExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(64);
        ex.setThreadNamePrefix("export-worker-");
        ex.setTaskDecorator(new ContextPropagatingTaskDecorator());
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(30);
        ex.initialize();
        return ex;
    }
}

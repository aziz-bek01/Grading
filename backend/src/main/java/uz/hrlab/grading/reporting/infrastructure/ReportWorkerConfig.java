package uz.hrlab.grading.reporting.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import uz.hrlab.grading.tenancy.application.ContextPropagatingTaskDecorator;

import java.util.concurrent.Executor;

/**
 * Dedicated executor pool for the report-generation worker
 * (ADR-009 async report generation). 4 threads / 64-deep queue, mirroring the
 * {@code exportWorkerExecutor} from {@code IntegrationWorkerConfig}.
 *
 * <p>{@code @EnableAsync} is already activated by {@code IntegrationWorkerConfig}
 * — no need to repeat here, the executor bean alone is sufficient.
 *
 * <p>Carries the same {@link ContextPropagatingTaskDecorator} as the import /
 * export executors: {@code ReportGenerationJob.generate} is {@code @Async} +
 * {@code @Transactional} and reloads the row via {@code findByIdAndTenantId},
 * so without tenant-context propagation it hits the identical forced-RLS
 * zero-rows stall.
 */
@Configuration
public class ReportWorkerConfig {

    @Bean(name = "reportWorkerExecutor")
    public Executor reportWorkerExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(64);
        ex.setThreadNamePrefix("report-worker-");
        ex.setTaskDecorator(new ContextPropagatingTaskDecorator());
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(30);
        ex.initialize();
        return ex;
    }
}

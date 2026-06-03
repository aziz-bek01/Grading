package uz.hrlab.grading.reporting.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Dedicated executor pool for the report-generation worker
 * (ADR-009 async report generation). 4 threads / 64-deep queue, mirroring the
 * {@code exportWorkerExecutor} from {@code IntegrationWorkerConfig}.
 *
 * <p>{@code @EnableAsync} is already activated by {@code IntegrationWorkerConfig}
 * — no need to repeat here, the executor bean alone is sufficient.
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
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(30);
        ex.initialize();
        return ex;
    }
}

package uz.hrlab.grading.integration.worker;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async worker executor configuration (integration-blueprint §4.1).
 *
 * <p>Two dedicated executors with 4 threads each — one for imports, one for
 * exports. Production targets a real queue (Redis Streams / RabbitMQ /
 * Kafka), but the in-process executor is sufficient to land the worker
 * contract for MVP 2 Phase 2 and the migration to a real broker becomes a
 * single bean swap.
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
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(30);
        ex.initialize();
        return ex;
    }
}

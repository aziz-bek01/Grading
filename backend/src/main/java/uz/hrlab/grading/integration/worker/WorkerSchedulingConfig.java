package uz.hrlab.grading.integration.worker;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} so the {@link WorkerReQueuer} retry re-dispatcher
 * runs in the API runtime.
 *
 * <p>Deliberately excluded from the one-shot {@code migrate} profile: the
 * {@code grading-migrator} container runs the full Spring context only to apply
 * Liquibase and must then exit. {@code @EnableScheduling} with a {@code @Scheduled}
 * task starts a non-daemon scheduler thread that keeps the JVM alive forever,
 * which hung the deploy (the migrator never returned, timing out the rollout).
 * Scheduling is therefore activated here, not on {@link IntegrationWorkerConfig},
 * so it is absent under {@code SPRING_PROFILES_ACTIVE=migrate}.
 */
@Configuration
@EnableScheduling
@Profile("!migrate")
public class WorkerSchedulingConfig {
}

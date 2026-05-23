package uz.hrlab.grading;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * Skips a test class when no Docker daemon is reachable.
 *
 * <p>Hard-fails (returns enabled) when the env var
 * {@code GRADING_REQUIRE_DOCKER=true} — used by CI so a misconfigured runner
 * doesn't silently skip the entire integration suite.
 */
public class RequiresDocker implements ExecutionCondition {

    private static final String FORCE_ENV = "GRADING_REQUIRE_DOCKER";
    private static volatile Boolean cached;

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        boolean require = "true".equalsIgnoreCase(System.getenv(FORCE_ENV));
        if (require) {
            return ConditionEvaluationResult.enabled(
                    "GRADING_REQUIRE_DOCKER=true — Docker must be reachable");
        }
        if (isDockerAvailable()) {
            return ConditionEvaluationResult.enabled("Docker daemon reachable");
        }
        return ConditionEvaluationResult.disabled(
                "Skipped: Docker daemon not reachable for Testcontainers. "
                        + "Set GRADING_REQUIRE_DOCKER=true to fail instead of skipping.");
    }

    private boolean isDockerAvailable() {
        if (cached != null) return cached;
        synchronized (RequiresDocker.class) {
            if (cached != null) return cached;
            try {
                cached = DockerClientFactory.instance().isDockerAvailable();
            } catch (Throwable ex) {
                cached = false;
            }
            return cached;
        }
    }
}

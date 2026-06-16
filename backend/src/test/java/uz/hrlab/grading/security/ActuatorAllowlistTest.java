package uz.hrlab.grading.security;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline contract test for the actuator public allowlist (Batch 6
 * observability). Pure unit — no Spring context, no DB.
 *
 * <p>Pins the SECURITY-CRITICAL invariant: the Prometheus scrape endpoint
 * ({@code /actuator/prometheus}) and {@code /actuator/metrics} must NEVER be on
 * the anonymous {@link SecurityConfig#PUBLIC_PATHS} allowlist — only health
 * probes and info are public. Metrics can leak tenant counts / operational data,
 * so they fall through to {@code .anyRequest().authenticated()}.
 *
 * <p>This guards against a regression where a future developer broadens the
 * allowlist to {@code /actuator/**} (which would expose metrics anonymously).
 * The end-to-end 401/403-vs-200 behaviour is asserted by
 * {@code ActuatorEndpointSecurityIntegrationTest} (CI / Testcontainers).
 */
@Tag("security")
class ActuatorAllowlistTest {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private static final List<String> PUBLIC = Arrays.asList(SecurityConfig.PUBLIC_PATHS);

    private static boolean isPubliclyAllowed(String path) {
        return PUBLIC.stream().anyMatch(pattern -> MATCHER.match(pattern, path));
    }

    @Test
    void healthProbesArePublic() {
        assertThat(isPubliclyAllowed("/actuator/health")).isTrue();
        assertThat(isPubliclyAllowed("/actuator/health/liveness")).isTrue();
        assertThat(isPubliclyAllowed("/actuator/health/readiness")).isTrue();
    }

    @Test
    void infoIsPublic() {
        assertThat(isPubliclyAllowed("/actuator/info")).isTrue();
    }

    @Test
    void prometheusIsNotPublic() {
        // The scrape endpoint must require auth — it can leak tenant/operational data.
        assertThat(isPubliclyAllowed("/actuator/prometheus")).isFalse();
    }

    @Test
    void metricsAreNotPublic() {
        assertThat(isPubliclyAllowed("/actuator/metrics")).isFalse();
        assertThat(isPubliclyAllowed("/actuator/metrics/jvm.memory.used")).isFalse();
    }

    @Test
    void allowlistDoesNotContainAWildcardActuatorPattern() {
        // A bare /actuator/** would expose prometheus/metrics anonymously.
        assertThat(PUBLIC).noneMatch(p -> p.equals("/actuator/**") || p.equals("/actuator/*"));
    }
}

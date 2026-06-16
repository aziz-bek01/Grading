package uz.hrlab.grading.security;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import uz.hrlab.grading.AbstractIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end actuator security test (Batch 6 observability). Boots the FULL
 * application context (real {@link SecurityConfig}) against a Testcontainers
 * Postgres and asserts the security-critical contract over the wire:
 *
 * <ul>
 *   <li>{@code /actuator/health} and the liveness/readiness probes are PUBLIC
 *       (200, no auth) — needed by the Helm/k8s probes;</li>
 *   <li>{@code /actuator/prometheus} and {@code /actuator/metrics} are NOT
 *       public — an anonymous request is rejected (401), because metrics can
 *       leak tenant counts / operational data.</li>
 * </ul>
 *
 * <p>CI / Testcontainers only ({@code @Tag("integration")}); skipped without a
 * Docker daemon via {@code RequiresDocker}. The offline allowlist contract is
 * pinned by {@link ActuatorAllowlistTest}.
 */
@AutoConfigureMockMvc
@Tag("security")
@Tag("integration")
class ActuatorEndpointSecurityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void healthIsPubliclyReachable() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void livenessProbeIsPubliclyReachable() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());
    }

    @Test
    void readinessProbeIsPubliclyReachable() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk());
    }

    @Test
    void prometheusIsNotPubliclyReachableWithoutAuth() throws Exception {
        // SECURITY-CRITICAL: the scrape endpoint must require auth — it falls
        // through to .anyRequest().authenticated(), so anonymous gets 401.
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void metricsAreNotPubliclyReachableWithoutAuth() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }
}

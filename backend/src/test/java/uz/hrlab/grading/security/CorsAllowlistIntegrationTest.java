package uz.hrlab.grading.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import uz.hrlab.grading.AbstractIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the explicit CORS allowlist (security-blueprint §10
 * API-7, finding F-16).
 *
 * <p>Test profile config (application-test.yml) seeds the allowlist with
 * {@code http://localhost:5173,https://test.grading.hrlab.uz}. Origins
 * outside the allowlist must receive NO {@code Access-Control-Allow-Origin}
 * response header — Spring rejects the CORS preflight and the browser blocks
 * the request.
 *
 * <p>Extends {@link AbstractIntegrationTest} so the Spring context bootstraps
 * with a real Postgres (Testcontainers). The CORS rules themselves are pure
 * filter behaviour, but the surrounding security chain requires the full
 * application context. Without Docker the test is skipped by
 * {@code RequiresDocker} — set {@code GRADING_REQUIRE_DOCKER=true} in CI.
 */
@AutoConfigureMockMvc
class CorsAllowlistIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void preflightFromAllowedOriginSucceeds() throws Exception {
        mockMvc.perform(options("/api/v1/admin/tenants")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void preflightFromDisallowedOriginIsRejected() throws Exception {
        mockMvc.perform(options("/api/v1/admin/tenants")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void preflightFromSecondaryAllowedOriginSucceeds() throws Exception {
        mockMvc.perform(options("/api/v1/admin/tenants")
                        .header("Origin", "https://test.grading.hrlab.uz")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin",
                        "https://test.grading.hrlab.uz"));
    }
}

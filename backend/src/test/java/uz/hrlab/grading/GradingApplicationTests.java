package uz.hrlab.grading;

import org.junit.jupiter.api.Test;

/**
 * Smoke test — Spring context loads against a real Postgres + full Liquibase
 * migration set. Anything that fails to autowire surfaces here first.
 */
class GradingApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        // Empty body — the act of loading the context is the assertion.
    }
}

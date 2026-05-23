package uz.hrlab.grading;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

/**
 * Base class for tests that need a real Postgres via Testcontainers.
 *
 * <p>Tests are gated by {@link RequiresDocker}: if the local Docker daemon
 * isn't reachable, the test class is skipped — not failed. Set the
 * {@code GRADING_REQUIRE_DOCKER=true} env var (in CI) to make missing Docker
 * a hard failure instead of a skip.
 *
 * <p>All Liquibase changesets run on container startup so we assert against
 * the real production schema.
 *
 * <h3>Docker Desktop 4.55 workaround (Windows)</h3>
 * If Testcontainers reports {@code BadRequestException} from
 * {@code NpipeSocketClientProviderStrategy}, set
 * {@code DOCKER_HOST=tcp://localhost:2375} in Docker Desktop "Expose daemon
 * on tcp://localhost:2375 without TLS" or upgrade to a Docker Desktop release
 * that fixes the engine API regression.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@ExtendWith(RequiresDocker.class)
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("grading_test_db")
                    .withUsername("grading_test")
                    .withPassword("grading_test_pwd")
                    .withReuse(false);

    /**
     * Available for subclasses that need raw SQL access (control-plane row
     * seeding, FK precondition setup, native trigger probes).
     */
    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /**
     * Inserts a minimal {@code public.tenants} row so subsequent business-table
     * inserts under {@code tenant_id} satisfy {@code fk_*_tenant} (Phase 2
     * F-203 remediation). Uses {@code ON CONFLICT DO NOTHING} so the same id
     * can be requested multiple times. Returns the id for fluency.
     *
     * <p>Schema mode: in tests we run in {@code mode-shared}; the FK target is
     * {@code public.tenants} (control plane) regardless.
     */
    protected UUID seedTenant(UUID tenantId) {
        String slug = "test-" + tenantId.toString().substring(0, 8);
        jdbcTemplate.update(
                "INSERT INTO public.tenants (id, slug, display_name, isolation_mode, "
                        + "schema_name, database_name, status, default_locale, version) "
                        + "VALUES (?, ?, 'Test tenant', 'SCHEMA', ?, NULL, 'ACTIVE', 'ru-RU', 0) "
                        + "ON CONFLICT (id) DO NOTHING",
                tenantId, slug, "tenant_" + slug.replace('-', '_'));
        return tenantId;
    }

    /** Convenience: generate a fresh tenant id AND seed it. */
    protected UUID newSeededTenantId() {
        return seedTenant(UUID.randomUUID());
    }
}

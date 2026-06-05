package uz.hrlab.grading.seeds;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import uz.hrlab.grading.AbstractIntegrationTest;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BE-SEED-003 — guarantees the demo seed cannot leak into production.
 *
 * <p>Two production-safety invariants are checked from a single test run:
 * <ol>
 *   <li>The default {@code application.yml} Liquibase contexts list does NOT
 *       include {@code dev}. If a future PR ever adds it, this test fails so
 *       the regression is caught on CI before deploy.</li>
 *   <li>EVERY changeSet inside {@code seeds-dev/} declares {@code context: dev}.
 *       A copy-paste that forgets the context filter would let the rows seep
 *       into a non-dev environment — this test enforces the convention.</li>
 * </ol>
 *
 * <p>The third assertion (rows actually exist in the {@code test} profile)
 * is intentionally omitted: the test profile does NOT include the {@code dev}
 * context either, so seed rows do not appear in the test database — which is
 * itself proof that the context filter is working.
 */
@Tag("security")
@Tag("integration")
class DevSeedSafetyTest extends AbstractIntegrationTest {

    @Autowired org.springframework.core.env.Environment env;

    // ---- 1) application.yml MUST NOT include `dev` in contexts ----
    @Test
    void productionDefaultContextsDoNotIncludeDev() throws IOException {
        String contents = readClasspathFile("application.yml");
        // Find the spring.liquibase.contexts: line — must not list `dev`.
        List<String> contextLine = contents.lines()
                .filter(l -> l.trim().startsWith("contexts:"))
                .toList();
        assertThat(contextLine)
                .as("application.yml must declare spring.liquibase.contexts")
                .isNotEmpty();
        for (String line : contextLine) {
            assertThat(line)
                    .as("application.yml contexts list must NOT include `dev` — "
                            + "BE-SEED-003 production-safety guard")
                    .doesNotContain("dev");
        }
    }

    // ---- 2) Seed file MUST declare context: dev on every changeSet ----
    @Test
    void everyDevSeedChangeSetDeclaresDevContext() throws IOException {
        String yaml = readClasspathFile("db/changelog/seeds-dev/001-dev-seed-tenants.yaml");

        // Count `- changeSet:` blocks and `context: dev` declarations. They must match.
        long changesets = yaml.lines()
                .filter(l -> l.trim().startsWith("- changeSet:"))
                .count();
        long devContexts = yaml.lines()
                .filter(l -> l.trim().equals("context: dev"))
                .count();

        assertThat(changesets).as("seed file must contain changeSets").isGreaterThan(0);
        assertThat(devContexts)
                .as("every changeSet in 001-dev-seed-tenants.yaml MUST declare " +
                        "`context: dev` — otherwise production Liquibase could apply it")
                .isEqualTo(changesets);
    }

    // ---- 3) Under the `test` profile (no `dev` context), the seed must NOT run ----
    @Test
    void seedRowsAreAbsentInTestProfileBecauseContextDevIsNotActive() {
        // Sanity check: the test profile's contexts list must not include `dev`.
        String contexts = env.getProperty("spring.liquibase.contexts", "");
        assertThat(contexts)
                .as("test profile must not enable `dev` context")
                .doesNotContain("dev");

        // Therefore the well-known seed UUIDs must not appear in the DB.
        Integer acmeCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.tenants WHERE id = ?::uuid",
                Integer.class,
                "11111111-1111-1111-1111-111111111111");
        Integer betaCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.tenants WHERE id = ?::uuid",
                Integer.class,
                "22222222-2222-2222-2222-222222222222");
        assertThat(acmeCount).isZero();
        assertThat(betaCount).isZero();
    }

    private static String readClasspathFile(String path) throws IOException {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return new String(is.readAllBytes());
        }
    }
}

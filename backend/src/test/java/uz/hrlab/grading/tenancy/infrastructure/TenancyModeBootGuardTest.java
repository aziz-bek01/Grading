package uz.hrlab.grading.tenancy.infrastructure;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task #7 — proves the fail-fast boot guard for the DOCUMENTED-BUT-UNBUILT
 * {@code schema_per_tenant} tenancy mode. Uses {@link ApplicationContextRunner}
 * so we exercise the real property binding + {@code @PostConstruct} refresh path
 * without a database.
 */
@Tag("security")
class TenancyModeBootGuardTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of())
            .withUserConfiguration(GuardConfig.class);

    @Test
    void sharedModeBootsCleanly() {
        runner.withPropertyValues("grading.tenancy.mode=shared")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(TenancyModeBootGuard.class);
                    assertThat(ctx.getBean(TenancyProperties.class).getMode())
                            .isEqualTo(TenancyProperties.Mode.SHARED);
                });
    }

    @Test
    void schemaPerTenantModeFailsFastWithActionableMessage() {
        runner.withPropertyValues("grading.tenancy.mode=schema_per_tenant")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("SCHEMA_PER_TENANT")
                            .hasMessageContaining("GRADING_TENANCY_MODE")
                            .hasMessageContaining("not wired");
                });
    }

    @Configuration
    @EnableConfigurationProperties(TenancyProperties.class)
    static class GuardConfig {
        @Bean
        TenancyModeBootGuard tenancyModeBootGuard(TenancyProperties properties) {
            return new TenancyModeBootGuard(properties);
        }
    }
}

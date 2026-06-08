package uz.hrlab.grading.integration.idp;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import uz.hrlab.grading.integration.idp.application.IdentityProvisioningPort;
import uz.hrlab.grading.integration.idp.infrastructure.NoOpIdentityProvisioningClient;
import uz.hrlab.grading.integration.idp.infrastructure.ZitadelIdentityProvisioningClient;
import uz.hrlab.grading.integration.idp.infrastructure.ZitadelIdpProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring-wiring guard for the IdP provisioning beans.
 *
 * <p>The plain unit tests instantiate the clients directly via their
 * constructors, so they did NOT catch a constructor-autowiring defect that
 * crashed the application context ONLY when the real ZITADEL bean was created
 * by Spring (i.e. {@code grading.idp.zitadel.enabled=true}): with two
 * constructors and none annotated {@code @Autowired}, Spring fell back to
 * looking for a no-arg constructor and failed with
 * {@code NoSuchMethodException: <init>()}. This test loads the beans through a
 * real (mini) Spring context for BOTH flag states so the regression can never
 * reach production again — it runs as a fast offline unit test (no DB, no boot).
 */
@Tag("unit")
class ZitadelIdpWiringTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(IdpTestConfig.class);

    @Test
    void wiresZitadelClientWhenEnabled() {
        runner.withPropertyValues(
                        "grading.idp.zitadel.enabled=true",
                        "grading.idp.zitadel.api-base=https://auth.example",
                        "grading.idp.zitadel.org-id=org-123",
                        "grading.idp.zitadel.token=pat-123")
                .run(ctx -> assertThat(ctx)
                        .hasNotFailed()
                        .getBean(IdentityProvisioningPort.class)
                        .isInstanceOf(ZitadelIdentityProvisioningClient.class));
    }

    @Test
    void wiresNoOpWhenDisabled() {
        runner.withPropertyValues("grading.idp.zitadel.enabled=false")
                .run(ctx -> assertThat(ctx)
                        .hasNotFailed()
                        .getBean(IdentityProvisioningPort.class)
                        .isInstanceOf(NoOpIdentityProvisioningClient.class));
    }

    @EnableConfigurationProperties(ZitadelIdpProperties.class)
    @Import({ZitadelIdentityProvisioningClient.class, NoOpIdentityProvisioningClient.class})
    @Configuration
    static class IdpTestConfig {
    }
}

package uz.hrlab.grading.integration.idp;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.integration.idp.application.IdentityProvisioningPort;
import uz.hrlab.grading.integration.idp.infrastructure.NoOpIdentityProvisioningClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Proves the no-op port (active when {@code grading.idp.zitadel.enabled=false},
 * the local/test/CI default) returns a null external subject — the signal for
 * {@code InviteUserUseCase} to keep the invite DB-only and skip IdP audit events.
 */
@Tag("unit")
class NoOpIdentityProvisioningClientTest {

    private final NoOpIdentityProvisioningClient client = new NoOpIdentityProvisioningClient();

    @Test
    void provisionReturnsNullSubjectAndNotLinked() {
        IdentityProvisioningPort.ProvisionResult result =
                client.provisionUser("jane@client.uz", "Jane", "Doe", "irrelevant");

        assertThat(result.externalSubject()).isNull();
        assertThat(result.linkedExisting()).isFalse();
    }

    @Test
    void deactivateIsANoOp() {
        assertThatCode(() -> client.deactivateUser("any-subject"))
                .doesNotThrowAnyException();
    }
}

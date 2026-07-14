package uz.hrlab.grading.integration.idp.infrastructure;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import uz.hrlab.grading.access.application.IdentityProvisioningException;
import uz.hrlab.grading.access.application.IdentityProvisioningPort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

/**
 * Connector unit tests for {@link ZitadelIdentityProvisioningClient} against a
 * mocked HTTP server (no network). Covers the create / link / 409 / failure
 * paths and asserts the validated ZITADEL v2 contract (auth header, body shape,
 * pre-verified email, no-change-required password) WITHOUT ever asserting on the
 * password or the token leaking anywhere.
 */
@Tag("unit")
class ZitadelIdentityProvisioningClientTest {

    private static final String ORG = "376049121179533317";

    private ZitadelIdpProperties props() {
        ZitadelIdpProperties p = new ZitadelIdpProperties();
        p.setEnabled(true);
        p.setApiBase("https://auth.hrlab.uz");
        p.setOrgId(ORG);
        p.setToken("test-pat");
        return p;
    }

    @Test
    void createsHumanUserWithPreVerifiedEmailAndPreSetPassword() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://auth.hrlab.uz");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        // 1) up-front email search -> empty result (no existing user)
        server.expect(requestTo("https://auth.hrlab.uz/v2/users"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer test-pat"))
                .andExpect(jsonPath("$.queries[0].emailQuery.emailAddress").value("jane@client.uz"))
                .andRespond(withSuccess("{\"result\":[]}", MediaType.APPLICATION_JSON));

        // 2) create human user -> 201 with userId
        server.expect(requestTo("https://auth.hrlab.uz/v2/users/human"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer test-pat"))
                .andExpect(jsonPath("$.organization.orgId").value(ORG))
                .andExpect(jsonPath("$.username").value("jane@client.uz"))
                .andExpect(jsonPath("$.profile.givenName").value("Jane"))
                .andExpect(jsonPath("$.profile.familyName").value("Doe"))
                .andExpect(jsonPath("$.email.email").value("jane@client.uz"))
                .andExpect(jsonPath("$.email.isVerified").value(true))
                .andExpect(jsonPath("$.password.changeRequired").value(false))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"userId\":\"zid-123\"}"));

        ZitadelIdentityProvisioningClient client =
                new ZitadelIdentityProvisioningClient(props(), builder.build());

        IdentityProvisioningPort.ProvisionResult result =
                client.provisionUser("jane@client.uz", "Jane", "Doe", "S3cret!aA");

        assertThat(result.externalSubject()).isEqualTo("zid-123");
        assertThat(result.linkedExisting()).isFalse();
        server.verify();
    }

    @Test
    void linksExistingUserFoundUpFront() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://auth.hrlab.uz");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        // up-front search -> existing user, no create attempted (idempotent retry)
        server.expect(requestTo("https://auth.hrlab.uz/v2/users"))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"result\":[{\"userId\":\"zid-existing\"}]}",
                        MediaType.APPLICATION_JSON));

        ZitadelIdentityProvisioningClient client =
                new ZitadelIdentityProvisioningClient(props(), builder.build());

        IdentityProvisioningPort.ProvisionResult result =
                client.provisionUser("dup@client.uz", "Dup", "User", "S3cret!aA");

        assertThat(result.externalSubject()).isEqualTo("zid-existing");
        assertThat(result.linkedExisting()).isTrue();
        server.verify();
    }

    @Test
    void linksOn409AlreadyExists() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://auth.hrlab.uz");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        // 1) up-front search -> empty (race: not visible yet)
        server.expect(requestTo("https://auth.hrlab.uz/v2/users"))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"result\":[]}", MediaType.APPLICATION_JSON));
        // 2) create -> 409 ALREADY_EXISTS
        server.expect(requestTo("https://auth.hrlab.uz/v2/users/human"))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":6,\"message\":\"ALREADY_EXISTS\"}"));
        // 3) re-search -> now found -> link
        server.expect(requestTo("https://auth.hrlab.uz/v2/users"))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"result\":[{\"userId\":\"zid-after-409\"}]}",
                        MediaType.APPLICATION_JSON));

        ZitadelIdentityProvisioningClient client =
                new ZitadelIdentityProvisioningClient(props(), builder.build());

        IdentityProvisioningPort.ProvisionResult result =
                client.provisionUser("race@client.uz", "Race", "User", "S3cret!aA");

        assertThat(result.externalSubject()).isEqualTo("zid-after-409");
        assertThat(result.linkedExisting()).isTrue();
        server.verify();
    }

    @Test
    void throwsTypedExceptionOnServerError() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://auth.hrlab.uz");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://auth.hrlab.uz/v2/users"))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"result\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://auth.hrlab.uz/v2/users/human"))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":13,\"message\":\"internal\"}"));

        ZitadelIdentityProvisioningClient client =
                new ZitadelIdentityProvisioningClient(props(), builder.build());

        assertThatThrownBy(() ->
                client.provisionUser("err@client.uz", "Err", "User", "S3cret!aA"))
                .isInstanceOf(IdentityProvisioningException.class);
        server.verify();
    }

    @Test
    void constructorFailsFastWhenTokenMissing() {
        ZitadelIdpProperties bad = props();
        bad.setToken("");
        assertThatThrownBy(() ->
                new ZitadelIdentityProvisioningClient(bad, RestClient.builder().build()))
                .isInstanceOf(IllegalStateException.class);
    }

    // --- offboarding: deactivate / reactivate (validated v2 lifecycle calls) ---

    @Test
    void deactivatePostsToDeactivateEndpointWithBearerAndNoBody() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://auth.hrlab.uz");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://auth.hrlab.uz/v2/users/zid-123/deactivate"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer test-pat"))
                .andRespond(withSuccess()); // 200, no body

        ZitadelIdentityProvisioningClient client =
                new ZitadelIdentityProvisioningClient(props(), builder.build());

        client.deactivateUser("zid-123");
        server.verify();
    }

    @Test
    void reactivatePostsToReactivateEndpointWithBearerAndNoBody() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://auth.hrlab.uz");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://auth.hrlab.uz/v2/users/zid-123/reactivate"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer test-pat"))
                .andRespond(withSuccess()); // 200, no body

        ZitadelIdentityProvisioningClient client =
                new ZitadelIdentityProvisioningClient(props(), builder.build());

        client.reactivateUser("zid-123");
        server.verify();
    }

    @Test
    void deactivateSwallows404WhenUserAlreadyGone() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://auth.hrlab.uz");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://auth.hrlab.uz/v2/users/zid-gone/deactivate"))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":5,\"message\":\"NOT_FOUND\"}"));

        ZitadelIdentityProvisioningClient client =
                new ZitadelIdentityProvisioningClient(props(), builder.build());

        // idempotent-friendly: desired end-state (no active login) holds → swallow
        assertThatCode(() -> client.deactivateUser("zid-gone")).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void deactivateSwallowsPreconditionWhenAlreadyInactive() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://auth.hrlab.uz");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        // ZITADEL returns a 4xx precondition for "user is already inactive".
        server.expect(requestTo("https://auth.hrlab.uz/v2/users/zid-inactive/deactivate"))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":9,\"message\":\"FAILED_PRECONDITION\"}"));

        ZitadelIdentityProvisioningClient client =
                new ZitadelIdentityProvisioningClient(props(), builder.build());

        assertThatCode(() -> client.deactivateUser("zid-inactive")).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void deactivateThrowsOnServerError() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://auth.hrlab.uz");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://auth.hrlab.uz/v2/users/zid-123/deactivate"))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":13,\"message\":\"internal\"}"));

        ZitadelIdentityProvisioningClient client =
                new ZitadelIdentityProvisioningClient(props(), builder.build());

        // genuine failure (5xx) surfaces so the caller can flag a retry
        assertThatThrownBy(() -> client.deactivateUser("zid-123"))
                .isInstanceOf(IdentityProvisioningException.class);
        server.verify();
    }

    @Test
    void deactivateThrowsOnAuthFailure() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://auth.hrlab.uz");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://auth.hrlab.uz/v2/users/zid-123/deactivate"))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":16,\"message\":\"unauthenticated\"}"));

        ZitadelIdentityProvisioningClient client =
                new ZitadelIdentityProvisioningClient(props(), builder.build());

        // 401 is NOT an idempotent no-op — surface it
        assertThatThrownBy(() -> client.deactivateUser("zid-123"))
                .isInstanceOf(IdentityProvisioningException.class);
        server.verify();
    }

    @Test
    void deactivateSkipsCallForNullSubject() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://auth.hrlab.uz");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        // No expectations registered: any HTTP call would fail verification.

        ZitadelIdentityProvisioningClient client =
                new ZitadelIdentityProvisioningClient(props(), builder.build());

        assertThatCode(() -> client.deactivateUser(null)).doesNotThrowAnyException();
        assertThatCode(() -> client.deactivateUser("  ")).doesNotThrowAnyException();
        server.verify(); // proves no HTTP call was made
    }

    // --- credential edits: setPassword / changeEmail (validated v2 calls) ---

    @Test
    void setPasswordPostsValidatedBodyWithBearer() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://auth.hrlab.uz");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://auth.hrlab.uz/v2/users/zid-123/password"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer test-pat"))
                .andExpect(jsonPath("$.newPassword.password").value("N3wP@ssword"))
                .andExpect(jsonPath("$.newPassword.changeRequired").value(false))
                .andRespond(withSuccess()); // 200, no body

        ZitadelIdentityProvisioningClient client =
                new ZitadelIdentityProvisioningClient(props(), builder.build());

        client.setPassword("zid-123", "N3wP@ssword");
        server.verify();
    }

    @Test
    void setPasswordSkipsCallForNullOrBlankSubject() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://auth.hrlab.uz");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        // No expectations: any HTTP call would fail verification.

        ZitadelIdentityProvisioningClient client =
                new ZitadelIdentityProvisioningClient(props(), builder.build());

        assertThatCode(() -> client.setPassword(null, "N3wP@ssword")).doesNotThrowAnyException();
        assertThatCode(() -> client.setPassword("  ", "N3wP@ssword")).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void setPasswordThrowsOnServerError() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://auth.hrlab.uz");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://auth.hrlab.uz/v2/users/zid-123/password"))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":13,\"message\":\"internal\"}"));

        ZitadelIdentityProvisioningClient client =
                new ZitadelIdentityProvisioningClient(props(), builder.build());

        // 5xx is a genuine upstream failure → generic IDP_PROVISIONING_FAILED (→502)
        assertThatThrownBy(() -> client.setPassword("zid-123", "N3wP@ssword"))
                .isInstanceOf(IdentityProvisioningException.class)
                .extracting(ex -> ((IdentityProvisioningException) ex).getCode())
                .isEqualTo("IDP_PROVISIONING_FAILED");
        server.verify();
    }

    @Test
    void setPasswordMapsNotYetInitialized400ToSpecificReason() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://auth.hrlab.uz");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        // Real prod body for a USER_STATE_INITIAL account.
        server.expect(requestTo("https://auth.hrlab.uz/v2/users/zid-legacy/password"))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":9,\"message\":\"User is not yet initialized (COMMAND-M9dse)\"}"));

        ZitadelIdentityProvisioningClient client =
                new ZitadelIdentityProvisioningClient(props(), builder.build());

        assertThatThrownBy(() -> client.setPassword("zid-legacy", "N3wP@ssword"))
                .isInstanceOf(IdentityProvisioningException.class)
                .satisfies(ex -> {
                    IdentityProvisioningException ipe = (IdentityProvisioningException) ex;
                    assertThat(ipe.isActionable()).isTrue();
                    assertThat(ipe.getCode()).isEqualTo("USER_IDP_NOT_INITIALIZED");
                    // the password must never appear in the sanitised message
                    assertThat(ipe.getMessage()).doesNotContain("N3wP@ssword");
                });
        server.verify();
    }

    @Test
    void setPasswordMapsOther400ToPasswordRejected() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://auth.hrlab.uz");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        // A different 4xx policy rejection (not the not-initialized case).
        server.expect(requestTo("https://auth.hrlab.uz/v2/users/zid-123/password"))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":3,\"message\":\"Password does not match policy\"}"));

        ZitadelIdentityProvisioningClient client =
                new ZitadelIdentityProvisioningClient(props(), builder.build());

        assertThatThrownBy(() -> client.setPassword("zid-123", "N3wP@ssword"))
                .isInstanceOf(IdentityProvisioningException.class)
                .satisfies(ex -> {
                    IdentityProvisioningException ipe = (IdentityProvisioningException) ex;
                    assertThat(ipe.isActionable()).isTrue();
                    assertThat(ipe.getCode()).isEqualTo("USER_IDP_PASSWORD_REJECTED");
                });
        server.verify();
    }

    @Test
    void changeEmailPostsPreVerifiedEmailWithBearer() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://auth.hrlab.uz");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://auth.hrlab.uz/v2/users/zid-123/email"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer test-pat"))
                .andExpect(jsonPath("$.email").value("new@client.uz"))
                .andExpect(jsonPath("$.isVerified").value(true))
                .andRespond(withSuccess()); // 200, no body

        ZitadelIdentityProvisioningClient client =
                new ZitadelIdentityProvisioningClient(props(), builder.build());

        client.changeEmail("zid-123", "new@client.uz");
        server.verify();
    }

    @Test
    void changeEmailSkipsCallForNullSubject() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://auth.hrlab.uz");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        // No expectations: any HTTP call would fail verification.

        ZitadelIdentityProvisioningClient client =
                new ZitadelIdentityProvisioningClient(props(), builder.build());

        assertThatCode(() -> client.changeEmail(null, "new@client.uz")).doesNotThrowAnyException();
        assertThatCode(() -> client.changeEmail("  ", "new@client.uz")).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void changeEmailThrowsOnConflict() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://auth.hrlab.uz");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        // ZITADEL rejects the new email (e.g. already taken at the IdP) → surface
        // a SPECIFIC, actionable reason (→ 400) so the admin can pick another one,
        // while the caller's tx still rolls the grading email back (consistency).
        server.expect(requestTo("https://auth.hrlab.uz/v2/users/zid-123/email"))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":6,\"message\":\"ALREADY_EXISTS\"}"));

        ZitadelIdentityProvisioningClient client =
                new ZitadelIdentityProvisioningClient(props(), builder.build());

        assertThatThrownBy(() -> client.changeEmail("zid-123", "dup@client.uz"))
                .isInstanceOf(IdentityProvisioningException.class)
                .satisfies(ex -> {
                    IdentityProvisioningException ipe = (IdentityProvisioningException) ex;
                    assertThat(ipe.isActionable()).isTrue();
                    assertThat(ipe.getCode()).isEqualTo("USER_IDP_EMAIL_REJECTED");
                });
        server.verify();
    }

    @Test
    void changeEmailKeepsGenericFailureOnServerError() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://auth.hrlab.uz");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        // 5xx is a genuine upstream failure → generic IDP_PROVISIONING_FAILED (→502).
        server.expect(requestTo("https://auth.hrlab.uz/v2/users/zid-123/email"))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":13,\"message\":\"internal\"}"));

        ZitadelIdentityProvisioningClient client =
                new ZitadelIdentityProvisioningClient(props(), builder.build());

        assertThatThrownBy(() -> client.changeEmail("zid-123", "new@client.uz"))
                .isInstanceOf(IdentityProvisioningException.class)
                .extracting(ex -> ((IdentityProvisioningException) ex).getCode())
                .isEqualTo("IDP_PROVISIONING_FAILED");
        server.verify();
    }
}

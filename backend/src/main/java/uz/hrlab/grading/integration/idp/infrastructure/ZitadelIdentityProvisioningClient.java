package uz.hrlab.grading.integration.idp.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import uz.hrlab.grading.integration.idp.application.IdentityProvisioningException;
import uz.hrlab.grading.integration.idp.application.IdentityProvisioningPort;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * ZITADEL implementation of {@link IdentityProvisioningPort} (decision doc
 * Option A, admin-set-password variant — NO SMTP).
 *
 * <p>Active ONLY when {@code grading.idp.zitadel.enabled=true}. Uses the
 * validated v2 API against {@code https://auth.hrlab.uz}:
 * <ul>
 *   <li>{@code POST /v2/users/human} — create human user with a pre-verified
 *       email and a pre-set, no-change-required password ⇒ login-able
 *       immediately, no email step.</li>
 *   <li>409 ALREADY_EXISTS ⇒ {@code POST /v2/users} email search ⇒ reuse the
 *       existing user id (LINK, not fail). Also searched up-front so a retry of
 *       a partially-applied invite is idempotent.</li>
 * </ul>
 *
 * <h3>Secret hygiene</h3>
 * The PAT and the raw password are NEVER logged. Only safe metadata (HTTP
 * status, ZITADEL error id, the email) is logged at WARN on failure. The
 * Authorization header value is built per request and never echoed.
 */
@Component
@ConditionalOnProperty(prefix = "grading.idp.zitadel", name = "enabled", havingValue = "true")
public class ZitadelIdentityProvisioningClient implements IdentityProvisioningPort {

    private static final Logger log = LoggerFactory.getLogger(ZitadelIdentityProvisioningClient.class);

    private final ZitadelIdpProperties props;
    private final RestClient restClient;

    @Autowired
    public ZitadelIdentityProvisioningClient(ZitadelIdpProperties props) {
        this(props, defaultRestClient(props));
    }

    /** Test seam — inject a {@link RestClient} backed by a MockRestServiceServer. */
    ZitadelIdentityProvisioningClient(ZitadelIdpProperties props, RestClient restClient) {
        this.props = props;
        this.restClient = restClient;
        if (props.getApiBase() == null || props.getApiBase().isBlank()) {
            throw new IllegalStateException(
                    "grading.idp.zitadel.api-base must be set when grading.idp.zitadel.enabled=true");
        }
        if (props.getOrgId() == null || props.getOrgId().isBlank()) {
            throw new IllegalStateException(
                    "grading.idp.zitadel.org-id must be set when grading.idp.zitadel.enabled=true");
        }
        if (props.getToken() == null || props.getToken().isBlank()) {
            throw new IllegalStateException(
                    "grading.idp.zitadel.token must be set when grading.idp.zitadel.enabled=true");
        }
    }

    private static RestClient defaultRestClient(ZitadelIdpProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofMillis(props.getConnectTimeoutMs()).toMillis());
        factory.setReadTimeout((int) Duration.ofMillis(props.getReadTimeoutMs()).toMillis());
        return RestClient.builder()
                .baseUrl(props.getApiBase())
                .requestFactory(factory)
                .build();
    }

    @Override
    public ProvisionResult provisionUser(String email, String givenName, String familyName,
                                         String rawPassword) {
        String normalisedEmail = email == null ? null : email.trim();
        if (normalisedEmail == null || normalisedEmail.isBlank()) {
            throw new IdentityProvisioningException("Email is required to provision an IdP account");
        }

        // 1) Up-front search makes a retry of a partially-applied invite idempotent:
        //    if the user already exists, LINK to it without attempting a create.
        String existing = findUserIdByEmail(normalisedEmail);
        if (existing != null) {
            log.info("IdP account already present, linking. email={}", normalisedEmail);
            return new ProvisionResult(existing, true);
        }

        // 2) Create the human user — pre-verified email + pre-set password ⇒
        //    login-able immediately, no SMTP / verify step.
        Map<String, Object> body = Map.of(
                "organization", Map.of("orgId", props.getOrgId()),
                "username", normalisedEmail,
                "profile", Map.of(
                        "givenName", safe(givenName),
                        "familyName", safe(familyName)),
                "email", Map.of(
                        "email", normalisedEmail,
                        "isVerified", true),
                "password", Map.of(
                        "password", rawPassword,
                        "changeRequired", false));

        try {
            JsonNode resp = restClient.post()
                    .uri("/v2/users/human")
                    .headers(this::authHeaders)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            String userId = resp == null ? null : text(resp, "userId");
            if (userId == null || userId.isBlank()) {
                throw new IdentityProvisioningException(
                        "IdP create returned no user id for the invited account");
            }
            return new ProvisionResult(userId, false);
        } catch (RestClientResponseException ex) {
            HttpStatusCode status = ex.getStatusCode();
            if (status.value() == 409) {
                // ALREADY_EXISTS — link to the existing account instead of failing.
                String linked = findUserIdByEmail(normalisedEmail);
                if (linked != null) {
                    log.info("IdP create returned 409, linked existing account. email={}",
                            normalisedEmail);
                    return new ProvisionResult(linked, true);
                }
                throw new IdentityProvisioningException(
                        "IdP reported the account already exists but it could not be located to link");
            }
            // Any other 4xx/5xx — sanitised log (status + zitadel id only), typed throw.
            log.warn("IdP create failed. email={} status={} zitadelErr={}",
                    normalisedEmail, status.value(), zitadelErrorId(ex));
            throw new IdentityProvisioningException(
                    "Identity provider rejected the account creation (status " + status.value() + ")",
                    ex);
        } catch (IdentityProvisioningException ex) {
            throw ex;
        } catch (Exception ex) {
            // Network / timeout / unexpected — never leak internals to the client.
            log.warn("IdP create errored. email={} cause={}", normalisedEmail,
                    ex.getClass().getSimpleName());
            throw new IdentityProvisioningException(
                    "Identity provider is unreachable; the invite could not be completed", ex);
        }
    }

    @Override
    public void deactivateUser(String externalSubject) {
        // TODO (offboarding symmetry, US-3): call POST /v2/users/{id}/deactivate.
        // Wiring the revoke side into RevokeMembershipUseCase / PatchUserUseCase is
        // intentionally out of scope for this change (see decision doc §5). The port
        // method exists so that step is a pure additive follow-up.
        throw new UnsupportedOperationException(
                "ZITADEL deactivateUser is a documented TODO (offboarding symmetry, decision doc §5)");
    }

    /**
     * Search ZITADEL for a user by email. Returns the user id of the first match,
     * or null when none exists. Used both for up-front idempotency and for the
     * 409 link path.
     */
    private String findUserIdByEmail(String email) {
        Map<String, Object> body = Map.of(
                "queries", List.of(Map.of(
                        "emailQuery", Map.of("emailAddress", email))));
        try {
            JsonNode resp = restClient.post()
                    .uri("/v2/users")
                    .headers(this::authHeaders)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (resp == null) {
                return null;
            }
            JsonNode result = resp.get("result");
            if (result != null && result.isArray() && result.size() > 0) {
                JsonNode first = result.get(0);
                String id = text(first, "userId");
                return (id == null || id.isBlank()) ? null : id;
            }
            return null;
        } catch (RestClientResponseException ex) {
            log.warn("IdP user search failed. email={} status={} zitadelErr={}",
                    email, ex.getStatusCode().value(), zitadelErrorId(ex));
            throw new IdentityProvisioningException(
                    "Identity provider lookup failed (status " + ex.getStatusCode().value() + ")", ex);
        } catch (Exception ex) {
            log.warn("IdP user search errored. email={} cause={}", email,
                    ex.getClass().getSimpleName());
            throw new IdentityProvisioningException(
                    "Identity provider is unreachable; the invite could not be completed", ex);
        }
    }

    private void authHeaders(HttpHeaders headers) {
        // Built per request; the Bearer value (the PAT) is never logged or echoed.
        headers.setBearerAuth(props.getToken());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
    }

    /** Extract the ZITADEL structured error id from a response body, if present. Never the message body verbatim. */
    private static String zitadelErrorId(RestClientResponseException ex) {
        try {
            String raw = ex.getResponseBodyAsString();
            if (raw == null || raw.isBlank()) {
                return "n/a";
            }
            // ZITADEL bodies look like {"code":...,"message":...,"details":[...]}.
            // We surface only the numeric/string code, never the full body (avoids
            // accidentally logging echoed input).
            int idx = raw.indexOf("\"code\"");
            return idx >= 0 ? raw.substring(idx, Math.min(idx + 24, raw.length())) : "present";
        } catch (Exception ignore) {
            return "n/a";
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}

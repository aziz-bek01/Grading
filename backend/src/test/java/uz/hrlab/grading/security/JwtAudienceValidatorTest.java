package uz.hrlab.grading.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JwtAudienceValidator} — proves that JWTs with the
 * wrong {@code aud} claim are rejected (security-blueprint AUTH-04, F-02).
 */
class JwtAudienceValidatorTest {

    private static final String EXPECTED = "grading.hrlab.uz";

    @Test
    void tokenWithMatchingAudPasses() {
        JwtAudienceValidator validator = new JwtAudienceValidator(Set.of(EXPECTED));
        Jwt jwt = jwtWithAudience(List.of(EXPECTED));

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void tokenWithMatchingAudAmongMultipleAcceptedValuesPasses() {
        JwtAudienceValidator validator =
                new JwtAudienceValidator(Set.of(EXPECTED, "grading-internal"));
        Jwt jwt = jwtWithAudience(List.of("grading-internal"));

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void tokenWithWrongAudIsRejected() {
        JwtAudienceValidator validator = new JwtAudienceValidator(Set.of(EXPECTED));
        Jwt jwt = jwtWithAudience(List.of("hrlab-dashboard"));

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors())
                .anyMatch(e -> e.getErrorCode().equals(JwtAudienceValidator.ERROR_CODE));
    }

    @Test
    void tokenMissingAudIsRejected() {
        JwtAudienceValidator validator = new JwtAudienceValidator(Set.of(EXPECTED));
        Jwt jwt = jwtWithAudience(List.of());

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void emptyExpectedAudiencesIsAlwaysRejection() {
        JwtAudienceValidator validator = new JwtAudienceValidator(List.of());
        Jwt jwt = jwtWithAudience(List.of(EXPECTED));

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertThat(result.hasErrors())
                .as("empty expected-audience list must default-deny")
                .isTrue();
    }

    private static Jwt jwtWithAudience(List<String> audiences) {
        return new Jwt(
                "fake-token-value",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "RS256"),
                Map.of(
                        "sub", "user-1",
                        "aud", audiences,
                        "iss", "https://idp.example.com/realms/grading"
                ));
    }
}

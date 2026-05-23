package uz.hrlab.grading.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Explicit JWT {@code aud} (audience) claim validator
 * (security-blueprint §17.1 AUTH-04, finding F-02).
 *
 * <p>Spring Security's {@code JwtIssuerValidator} only validates {@code iss}.
 * Without an explicit {@code aud} check, a valid token issued by the same IdP
 * for a different client (e.g. {@code hrlab-dashboard}) is accepted by
 * {@code grading.hrlab.uz}. This validator closes that gap.
 *
 * <p>Fails closed: if {@link #expectedAudiences} is empty or the token has no
 * {@code aud} claim, validation fails. The token's {@code aud} claim must
 * intersect with the configured expected set (case-sensitive, exact match).
 */
public class JwtAudienceValidator implements OAuth2TokenValidator<Jwt> {

    static final String ERROR_CODE = "invalid_audience";

    private final Set<String> expectedAudiences;

    public JwtAudienceValidator(Collection<String> expectedAudiences) {
        Objects.requireNonNull(expectedAudiences, "expectedAudiences must not be null");
        this.expectedAudiences = Set.copyOf(expectedAudiences);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (expectedAudiences.isEmpty()) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    ERROR_CODE,
                    "No expected audience configured; rejecting token by default-deny policy",
                    null));
        }
        List<String> tokenAudiences = jwt.getAudience();
        if (tokenAudiences == null || tokenAudiences.isEmpty()) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    ERROR_CODE,
                    "JWT is missing the required aud claim",
                    null));
        }
        for (String aud : tokenAudiences) {
            if (aud != null && expectedAudiences.contains(aud)) {
                return OAuth2TokenValidatorResult.success();
            }
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                ERROR_CODE,
                "JWT aud claim does not match the expected audience for grading.hrlab.uz",
                null));
    }

    Set<String> expectedAudiences() {
        return expectedAudiences;
    }
}

package uz.hrlab.grading.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import uz.hrlab.grading.tenancy.application.TenantContext;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit test for {@link GradingJwtAuthenticationConverter} (BE-OIDC-002).
 *
 * <p>Verifies the authority mapping contract that makes
 * {@code @PreAuthorize("hasAuthority('…')")} pass under the OIDC path:
 * <ul>
 *   <li>permission codes are added VERBATIM (no prefix);</li>
 *   <li>role codes are added with a {@code ROLE_} prefix;</li>
 *   <li>an unprovisioned principal (empty context) → no authorities, but the
 *       token is still authenticated (fail-closed);</li>
 *   <li>a resolver exception fails closed rather than crashing the request.</li>
 * </ul>
 * The resolver is mocked — its DB resolution path is covered by
 * {@code JwtTenantContextResolverTest} (integration).
 */
class GradingJwtAuthenticationConverterTest {

    private final JwtTenantContextResolver resolver = mock(JwtTenantContextResolver.class);
    private final GradingJwtAuthenticationConverter converter =
            new GradingJwtAuthenticationConverter(resolver);

    private static Jwt sampleJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("376051031986929669")
                .claim("email", "tester@hrlab.uz")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Test
    void mapsPermissionsVerbatimAndRolesWithRolePrefix() {
        Jwt jwt = sampleJwt();
        TenantContext ctx = new TenantContext(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), Set.of(),
                Set.of("HRLAB_SUPER_ADMIN"),
                Set.of("CLIENT_READ", "PROJECT_READ"),
                Set.of(), false, "ru-RU");
        when(resolver.resolve(jwt)).thenReturn(ctx);

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token.isAuthenticated()).isTrue();
        assertThat(token.getAuthorities().stream().map(GrantedAuthority::getAuthority))
                .containsExactlyInAnyOrder(
                        "CLIENT_READ", "PROJECT_READ", "ROLE_HRLAB_SUPER_ADMIN");
    }

    @Test
    void unprovisionedPrincipalYieldsNoAuthoritiesButStaysAuthenticated() {
        Jwt jwt = sampleJwt();
        // Mirrors JwtTenantContextResolver's userless fail-closed context.
        TenantContext empty = new TenantContext(
                null, null, Set.of(), Set.of(), Set.of(), Set.of(), false, null);
        when(resolver.resolve(jwt)).thenReturn(empty);

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token.isAuthenticated()).isTrue();
        assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    void resolverFailureFailsClosedWithoutCrashing() {
        Jwt jwt = sampleJwt();
        when(resolver.resolve(jwt)).thenThrow(new RuntimeException("DB down"));

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token.isAuthenticated()).isTrue();
        assertThat(token.getAuthorities()).isEmpty();
    }
}

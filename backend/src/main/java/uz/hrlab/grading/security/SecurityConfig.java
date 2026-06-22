package uz.hrlab.grading.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Central Spring Security configuration.
 *
 * <p>Hard rules implemented here (security-blueprint §20.1 critical findings):
 * <ul>
 *   <li>Deny-by-default: every endpoint requires authentication after a small
 *       allowlist for actuator and OpenAPI.</li>
 *   <li>Stateless sessions — JWT only.</li>
 *   <li>CSRF disabled because all writes require a Bearer token.</li>
 *   <li>OAuth2 Resource Server with JWT validation (signature/iss/aud/exp).</li>
 *   <li>Explicit JWT {@code aud} validator (F-02).</li>
 *   <li>Explicit CORS allowlist (F-16) — no {@code *} wildcards.</li>
 *   <li>{@link DevAuthFilter} ONLY in dev profiles; constructor enforces.</li>
 *   <li>{@link TenantContextFilter} bound after authentication to populate
 *       the tenant context for downstream services.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
// Only load in a servlet web context. The Liquibase migrator runs the SAME jar
// with web-application-type=none (no web server), where no HttpSecurity bean
// exists — without this guard the migrator dies wiring securityFilterChain.
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

    /**
     * Public endpoints that never require auth.
     *
     * <p>SECURITY (Batch 6 observability): the actuator allowlist is INTENTIONALLY
     * narrow — only {@code /actuator/health/**} (liveness/readiness probes) and
     * {@code /actuator/info}. It is NOT {@code /actuator/**}. The Prometheus
     * scrape endpoint ({@code /actuator/prometheus}) and {@code /actuator/metrics}
     * are EXPOSED on the management surface (see {@code application.yml}) but
     * deliberately omitted here, so they fall through to
     * {@code .anyRequest().authenticated()}. Metrics can leak tenant counts and
     * operational data, so they must require authentication / internal-network
     * access — never anonymous. Do not widen this to {@code /actuator/**}.
     */
    static final String[] PUBLIC_PATHS = {
            "/actuator/health/**",
            "/actuator/info",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   TenantContextFilter tenantContextFilter,
                                                   CorsConfigurationSource corsConfigurationSource,
                                                   org.springframework.beans.factory.ObjectProvider<DevUserAuthorityResolver> devAuthorityResolver,
                                                   GradingJwtAuthenticationConverter jwtAuthenticationConverter,
                                                   Environment env) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // Deny by default — every endpoint MUST be explicitly allowlisted
                        // or guarded by @PreAuthorize.
                        .anyRequest().authenticated())
                // BE-OIDC-002: map the principal's effective permission/role codes
                // into Spring GrantedAuthorities so hasAuthority('…') checks pass
                // under the OIDC path (the default converter mints none for ZITADEL
                // access tokens, leaving every guarded endpoint 403).
                .oauth2ResourceServer(oauth ->
                        oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

        // Bind DevAuthFilter ONLY in dev profiles. Its constructor refuses to
        // start otherwise — belt-and-braces. The DB-backed authority resolver
        // is wired in when present (local/dev profiles); under the `test`
        // profile it is absent and the filter falls back to header-only mode.
        DevAuthFilter devAuthFilter = devAuthFilterIfActive(env, devAuthorityResolver.getIfAvailable());
        if (devAuthFilter != null) {
            http.addFilterBefore(devAuthFilter, UsernamePasswordAuthenticationFilter.class);
        }
        // TenantContextFilter must run AFTER authentication has been performed.
        http.addFilterAfter(tenantContextFilter, org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Register {@link ActiveTenantHeaderFilter} at {@link Ordered#HIGHEST_PRECEDENCE}
     * (BE-AUTH-MT-001).
     *
     * <p>The Spring Security {@code springSecurityFilterChain} {@code DelegatingFilterProxy}
     * is registered by Spring Boot at {@code SecurityProperties.DEFAULT_FILTER_ORDER}
     * ({@code -100}). Registering this filter at {@code Integer.MIN_VALUE} guarantees
     * it runs BEFORE the security chain — so by the time
     * {@code BearerTokenAuthenticationFilter} invokes
     * {@link GradingJwtAuthenticationConverter} (which calls
     * {@link JwtTenantContextResolver#resolve}), the {@code X-Active-Tenant-Id}
     * header is already bound on {@link ActiveTenantHeaderHolder}. We pin the order
     * explicitly instead of relying on {@code @Order}/component-scan so the
     * guarantee cannot silently regress.
     *
     * <p>Disabling the auto dispatcher-type registration is unnecessary; the
     * filter is a {@link org.springframework.web.filter.OncePerRequestFilter} so a
     * second pass (e.g. forwards) is a harmless no-op that re-binds the same value.
     */
    @Bean
    public FilterRegistrationBean<ActiveTenantHeaderFilter> activeTenantHeaderFilterRegistration() {
        FilterRegistrationBean<ActiveTenantHeaderFilter> registration =
                new FilterRegistrationBean<>(new ActiveTenantHeaderFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        registration.setName("activeTenantHeaderFilter");
        return registration;
    }

    private DevAuthFilter devAuthFilterIfActive(Environment env, DevUserAuthorityResolver authorityResolver) {
        for (String profile : env.getActiveProfiles()) {
            if (DevAuthFilter.ALLOWED_PROFILES.contains(profile)) {
                return new DevAuthFilter(env, authorityResolver);
            }
        }
        return null;
    }

    /**
     * Explicit CORS allowlist (security-blueprint §10 API-7, finding F-16).
     *
     * <p>Origins come from {@code grading.security.cors.allowed-origins} in
     * application config. Production profile defaults to an empty list — the
     * env var {@code GRADING_CORS_ALLOWED_ORIGINS} (comma-separated) MUST be
     * supplied at deploy time, otherwise no browser request is accepted.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${grading.security.cors.allowed-origins:}") String allowedOriginsCsv) {
        CorsConfiguration cfg = new CorsConfiguration();
        List<String> origins = parseCsv(allowedOriginsCsv);
        // Explicit list — Spring rejects "*" when combined with credentials, but
        // we also refuse to accept it as a configuration value for clarity.
        cfg.setAllowedOrigins(origins);
        cfg.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "Accept", "Accept-Language",
                "X-Correlation-Id"));
        cfg.setExposedHeaders(List.of("X-Correlation-Id"));
        cfg.setAllowCredentials(false);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    private static List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) return Collections.emptyList();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .filter(s -> !"*".equals(s))
                .toList();
    }

    /**
     * JWT decoder.
     *
     * <p>Wires three validators via {@link DelegatingOAuth2TokenValidator}:
     * <ol>
     *   <li>Default ({@code exp}, {@code nbf}, signature)</li>
     *   <li>{@code iss} via {@link JwtValidators#createDefaultWithIssuer(String)}</li>
     *   <li>{@code aud} via {@link JwtAudienceValidator} — finding F-02</li>
     * </ol>
     */
    @Bean
    @ConditionalOnProperty(prefix = "spring.security.oauth2.resourceserver.jwt",
            name = "issuer-uri")
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") String jwkSetUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.audience:grading.hrlab.uz}")
            String expectedAudiencesCsv) {
        // Build from the JWKS endpoint, NOT withIssuerLocation(): the latter does
        // OIDC discovery EAGERLY at bean creation, so the service cannot start
        // until the IdP/DNS is reachable. withJwkSetUri() fetches keys LAZILY on
        // the first token decode, so the API boots before the IdP exists. The
        // issuer ("iss") claim is still enforced below via createDefaultWithIssuer,
        // so no validation is lost. jwk-set-uri defaults to the Keycloak
        // convention derived from the issuer when not explicitly configured.
        String resolvedJwkSetUri = (jwkSetUri == null || jwkSetUri.isBlank())
                ? issuerUri.replaceAll("/$", "") + "/protocol/openid-connect/certs"
                : jwkSetUri;
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(resolvedJwkSetUri).build();
        List<String> expectedAudiences = parseCsv(expectedAudiencesCsv);
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withAudience = new JwtAudienceValidator(expectedAudiences);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
        return decoder;
    }

    /** Fallback no-op decoder for dev/test runs that never receive a real JWT. */
    @Bean
    @Profile({"local", "test", "dev"})
    @ConditionalOnProperty(prefix = "spring.security.oauth2.resourceserver.jwt",
            name = "issuer-uri", matchIfMissing = true)
    public JwtDecoder noopJwtDecoder() {
        return token -> {
            throw new org.springframework.security.oauth2.jwt.JwtException(
                    "Real JWT decoding is disabled in dev/test; use X-Dev-* headers");
        };
    }
}

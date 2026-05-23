package uz.hrlab.grading.common.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import static org.mockito.Mockito.mock;

/**
 * Minimal Spring Security configuration used by {@code @WebMvcTest} security
 * smoke tests (defect D-203). The real {@link uz.hrlab.grading.security.SecurityConfig}
 * pulls JPA / Liquibase / TenantContextFilter beans that would force a full
 * {@code @SpringBootTest}; this test slice provides only the rules that matter
 * to controller-edge security:
 *
 * <ul>
 *   <li>deny-by-default — every endpoint requires authentication;</li>
 *   <li>method-security enabled so {@code @PreAuthorize} fires;</li>
 *   <li>OAuth2 resource server with a mocked {@link JwtDecoder} so the
 *       MockMvc {@code with(jwt())} processor works.</li>
 * </ul>
 */
@TestConfiguration
@EnableMethodSecurity(prePostEnabled = true)
public class WebMvcSecurityTestConfig {

    @Bean
    public SecurityFilterChain webMvcTestSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /**
     * Mocked decoder — never invoked by tests using {@code with(jwt())} which
     * bypasses the decoder. Required only for the resource-server config to
     * boot without an issuer URI.
     */
    @Bean
    @Primary
    public JwtDecoder webMvcTestJwtDecoder() {
        return mock(JwtDecoder.class);
    }

    @Bean
    @Primary
    public AuthenticationManager webMvcTestAuthManager() {
        return mock(AuthenticationManager.class);
    }
}

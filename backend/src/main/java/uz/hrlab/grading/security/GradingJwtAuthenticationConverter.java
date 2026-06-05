package uz.hrlab.grading.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import uz.hrlab.grading.tenancy.application.TenantContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Maps a validated OIDC {@link Jwt} into a {@link JwtAuthenticationToken} that
 * carries the principal's effective {@link GrantedAuthority granted authorities}.
 *
 * <h3>Why this exists (BE-OIDC-002)</h3>
 * Spring's default {@code JwtAuthenticationConverter} only mints authorities from
 * a {@code scope}/{@code scp} claim. A real ZITADEL access token carries neither
 * grading's permission codes nor a usable scope, so under the OIDC path the
 * resulting token had {@code Granted Authorities=[]} and EVERY
 * {@code @PreAuthorize("hasAuthority('…')")} check denied → 403 on every
 * permission-guarded endpoint (only the unguarded {@code /users/me} worked).
 *
 * <p>This converter closes that gap by reusing the EXISTING
 * {@link JwtTenantContextResolver} — the single source of truth for the
 * {@code sub → email → external_idp_subject → user → memberships → roles →
 * permissions} resolution path. It does NOT duplicate any query logic: it asks
 * the resolver for the {@link TenantContext} and lifts the already-expanded
 * permission/role codes into Spring authorities.
 *
 * <h3>Authority format (must match {@code @PreAuthorize} expectations)</h3>
 * Controllers authorize exclusively via {@code hasAuthority('<PERMISSION_CODE>')}
 * / {@code hasAnyAuthority('A','B')} using PLAIN permission codes (no prefix) —
 * e.g. {@code CLIENT_READ}, {@code ORG_EDIT}, {@code EVALUATION_APPROVE}. There
 * is NO custom {@code PermissionEvaluator} and NO Spring-SpEL {@code hasRole(…)}
 * in any controller; the only {@code hasRole(…)} calls are
 * {@link TenantContext#hasRole(String)} evaluated server-side off the resolved
 * context. Therefore:
 * <ul>
 *   <li>each permission code is added VERBATIM (identical to
 *       {@link DevAuthentication#toAuthorities}, so dev and prod authorize the
 *       same way);</li>
 *   <li>each role code is additionally added with a {@code ROLE_} prefix — inert
 *       for today's checks but future-proofs any later {@code hasRole(…)} SpEL.</li>
 * </ul>
 *
 * <h3>Principal name</h3>
 * The token name is left as the default {@code jwt.getSubject()}. The
 * {@link TenantContext} (and hence {@code /users/me} via
 * {@code GetCurrentUserUseCase}) is still derived by
 * {@link TenantContextFilter} re-resolving the raw {@link Jwt} through the same
 * resolver, so nothing downstream depends on the name produced here.
 *
 * <h3>Fail-closed</h3>
 * An unknown / unprovisioned principal yields a context with no permissions →
 * empty authorities. The token stays {@code authenticated=true} (so the IdP
 * login is honoured and {@code /users/me} can report "not provisioned"), but
 * every guarded endpoint denies with 403. The converter never throws on a
 * resolution miss.
 */
@Component
public class GradingJwtAuthenticationConverter
        implements org.springframework.core.convert.converter.Converter<Jwt, AbstractAuthenticationToken> {

    private static final Logger log = LoggerFactory.getLogger(GradingJwtAuthenticationConverter.class);

    private final JwtTenantContextResolver resolver;

    public GradingJwtAuthenticationConverter(JwtTenantContextResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = resolveAuthorities(jwt);
        // Keep the default principal name (jwt subject); TenantContextFilter
        // re-resolves the TenantContext from the raw Jwt, so the name is not
        // load-bearing for /users/me or the tenant context.
        return new JwtAuthenticationToken(jwt, authorities);
    }

    private Collection<GrantedAuthority> resolveAuthorities(Jwt jwt) {
        TenantContext ctx;
        try {
            ctx = resolver.resolve(jwt);
        } catch (Exception ex) {
            // Never crash the request on a resolution failure — fail closed:
            // no authorities, authenticated token, 403 on guarded endpoints.
            log.warn("Failed to resolve authorities for JWT subject={} — failing closed (no authorities)",
                    jwt.getSubject(), ex);
            return List.of();
        }
        if (ctx == null) {
            return List.of();
        }

        List<GrantedAuthority> authorities = new ArrayList<>();
        Set<String> permissions = ctx.permissions();
        if (permissions != null) {
            // Permission codes VERBATIM — exactly what hasAuthority('X') expects
            // and identical to DevAuthentication's mapping.
            for (String perm : permissions) {
                if (perm != null && !perm.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority(perm));
                }
            }
        }
        Set<String> roles = ctx.roles();
        if (roles != null) {
            // ROLE_-prefixed roles: inert for current hasAuthority checks,
            // future-proofs any later hasRole(...) SpEL without re-touching this.
            for (String role : roles) {
                if (role != null && !role.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                }
            }
        }
        return authorities;
    }
}

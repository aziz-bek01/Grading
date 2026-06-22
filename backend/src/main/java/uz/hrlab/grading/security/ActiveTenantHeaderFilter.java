package uz.hrlab.grading.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Reads the {@code X-Active-Tenant-Id} request header as EARLY as possible and
 * stashes it in {@link ActiveTenantHeaderHolder} so that
 * {@link JwtTenantContextResolver} can read it deterministically during JWT
 * authentication — which runs inside the Spring Security filter chain, BEFORE
 * Spring's {@code DispatcherServlet} binds {@code RequestContextHolder}
 * (BE-AUTH-MT-001; see {@link ActiveTenantHeaderHolder} for the full mechanism).
 *
 * <p>This filter is registered at {@link org.springframework.core.Ordered#HIGHEST_PRECEDENCE}
 * via a {@code FilterRegistrationBean} (see {@code ActiveTenantHeaderFilterConfig})
 * so it runs BEFORE the {@code springSecurityFilterChain} {@code DelegatingFilterProxy}
 * — i.e. before {@code BearerTokenAuthenticationFilter} invokes the JWT converter.
 * Ordering is pinned explicitly rather than relying on component-scan order so the
 * guarantee cannot silently regress.
 *
 * <h3>No thread leakage</h3>
 * The ThreadLocal is ALWAYS cleared in a {@code finally} block, so a value set on
 * one request never bleeds into the next request served by the same pooled
 * thread. A request without the header clears any stale value (defense in depth).
 *
 * <h3>Security</h3>
 * The header is UNTRUSTED input. This filter only transports it; the resolver
 * cross-checks it against the user's ACTIVE memberships and still fails closed
 * when ambiguous. A header pointing at a non-member tenant is ignored, never
 * honoured — no cross-tenant escalation. The dev-auth path ({@code X-Dev-*}) does
 * not go through {@link JwtTenantContextResolver}, so it is unaffected by this
 * filter (it harmlessly sets/clears the ThreadLocal that the dev path ignores).
 */
public class ActiveTenantHeaderFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            ActiveTenantHeaderHolder.set(parseUuid(request.getHeader(JwtClaimNames.ACTIVE_TENANT_HEADER)));
            chain.doFilter(request, response);
        } finally {
            // CRITICAL: clear on every request, including the no-header path, so a
            // value never leaks across pooled request threads.
            ActiveTenantHeaderHolder.clear();
        }
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            // A malformed header is treated as absent — never throw, never break authn.
            return null;
        }
    }
}

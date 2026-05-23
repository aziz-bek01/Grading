package uz.hrlab.grading.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Resolves the {@link TenantContext} from the current {@link Authentication}
 * and binds it to the request thread + MDC.
 *
 * <p>Runs after Spring Security's authentication filters; if no authentication
 * is present (anonymous), no context is bound and the request continues.
 * Service code that requires a context calls
 * {@link TenantContextHolder#requireActive()}.
 *
 * <p>Always clears the context in a {@code finally} block — leakage between
 * threads is a Critical security issue (security-blueprint §5.1).
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);

    private final JwtTenantContextResolver jwtResolver;
    private final UserTenantMembershipRepository memberships;

    public TenantContextFilter(JwtTenantContextResolver jwtResolver,
                               UserTenantMembershipRepository memberships) {
        this.jwtResolver = jwtResolver;
        this.memberships = memberships;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String correlationId = request.getHeader("X-Correlation-Id");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put("correlationId", correlationId);
        response.setHeader("X-Correlation-Id", correlationId);

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            TenantContext context = extractContext(auth);
            if (context != null) {
                // F-205 — populate the per-request tenant-membership cache so
                // ConsultantTenantAssignmentPolicy does not issue N+1 EXISTS
                // queries on every ABAC evaluation. One SELECT per request.
                context = withMemberships(context);
                TenantContextHolder.set(context);
                if (context.tenantId() != null) {
                    MDC.put("tenantId", context.tenantId().toString());
                }
                if (context.userId() != null) {
                    MDC.put("userId", context.userId().toString());
                }
            }
            chain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
            MDC.remove("correlationId");
            MDC.remove("tenantId");
            MDC.remove("userId");
        }
    }

    /**
     * Eagerly load this user's tenant memberships ONCE per request and stash
     * the tenant-id set on the {@link TenantContext} (F-205).
     */
    private TenantContext withMemberships(TenantContext ctx) {
        if (ctx == null || ctx.userId() == null) return ctx;
        try {
            Set<UUID> tenantIds = memberships.findAllByUserId(ctx.userId()).stream()
                    .map(UserTenantMembershipJpaEntity::getTenantId)
                    .collect(Collectors.toUnmodifiableSet());
            return new TenantContext(
                    ctx.userId(), ctx.tenantId(), ctx.projectIds(),
                    ctx.roles(), ctx.permissions(), ctx.departmentScope(),
                    ctx.salaryPermission(), ctx.locale(), tenantIds);
        } catch (Exception ex) {
            // Don't fail the request if the memberships query errors —
            // ConsultantTenantAssignmentPolicy will fall back to a per-call
            // repo lookup (defense in depth).
            log.warn("Failed to preload tenant memberships for userId={}", ctx.userId(), ex);
            return ctx;
        }
    }

    private TenantContext extractContext(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return null;
        if (auth instanceof DevAuthentication dev) {
            return dev.getTenantContext();
        }
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            return jwtResolver.resolve(jwt);
        }
        log.debug("Authentication type not mapped to TenantContext: {}", auth.getClass().getName());
        return null;
    }
}

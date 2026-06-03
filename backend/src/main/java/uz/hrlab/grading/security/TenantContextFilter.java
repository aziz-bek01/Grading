package uz.hrlab.grading.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.api.ErrorResponse;
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
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public TenantContextFilter(JwtTenantContextResolver jwtResolver,
                               UserTenantMembershipRepository memberships,
                               AuditService auditService,
                               ObjectMapper objectMapper,
                               JdbcTemplate jdbcTemplate) {
        this.jwtResolver = jwtResolver;
        this.memberships = memberships;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
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
                // F-205 fail-closed: if the authenticated user carries an
                // active_tenant_id that is NOT in their user_tenant_memberships
                // set, the token is forged/stale — reject with 403 BEFORE any
                // controller runs. Membership cache may be null when the load
                // itself failed (defense in depth: in that case downstream
                // policies still enforce per-call).
                if (context.tenantId() != null
                        && context.tenantMemberships() != null
                        && !context.tenantMemberships().contains(context.tenantId())) {
                    rejectMembershipMismatch(request, response, context, correlationId);
                    return;
                }
                TenantContextHolder.set(context);
                if (context.tenantId() != null) {
                    MDC.put("tenantId", context.tenantId().toString());
                    // RLS defense-in-depth (changelog 030): set the PostgreSQL
                    // session GUC that tenant-isolation policies read. The
                    // value is bound for the duration of Spring's request TX;
                    // SET LOCAL auto-resets at commit/rollback so the GUC
                    // cannot leak across pooled connections.
                    setRlsTenantId(context.tenantId());
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

    /**
     * F-205 fail-closed handler. The token's {@code active_tenant_id} resolves
     * to a tenant the user does not belong to. We:
     * <ol>
     *   <li>emit a {@code TENANT_MEMBERSHIP_MISMATCH} audit row (tamper-evident,
     *       not just a SLF4J log line);</li>
     *   <li>return 403 with the canonical {@link ErrorResponse} envelope so the
     *       frontend renders a permission error, not a generic 500.</li>
     * </ol>
     * Audit-write failures must NEVER mask the 403 — they are caught and logged.
     */
    private void rejectMembershipMismatch(HttpServletRequest request,
                                          HttpServletResponse response,
                                          TenantContext ctx,
                                          String correlationId) throws IOException {
        log.warn("TENANT_MEMBERSHIP_MISMATCH userId={} attemptedTenantId={} memberships={} path={} cid={}",
                ctx.userId(), ctx.tenantId(), ctx.tenantMemberships(),
                request.getRequestURI(), correlationId);
        try {
            auditService.record(AuditEvent.builder()
                    .tenantId(ctx.tenantId())
                    .actorUserId(ctx.userId())
                    .action(AuditAction.TENANT_MEMBERSHIP_MISMATCH)
                    .entityType("HTTP_REQUEST")
                    .reason(request.getMethod() + " " + request.getRequestURI())
                    .ipAddress(clientIp(request))
                    .userAgent(request.getHeader("User-Agent"))
                    .correlationId(correlationId)
                    .traceId(MDC.get("traceId"))
                    .build());
        } catch (Exception persistFailure) {
            log.error("Failed to persist TENANT_MEMBERSHIP_MISMATCH audit row cid={}",
                    correlationId, persistFailure);
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ErrorResponse body = ErrorResponse.of(
                "PERMISSION_DENIED", "Action not permitted",
                correlationId, MDC.get("traceId"));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private static String clientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        return req.getRemoteAddr();
    }

    /**
     * Bind the active tenant id to the PostgreSQL session via {@code SET LOCAL
     * app.tenant_id = '<uuid>'}. Read by the RLS policies declared in
     * changelog {@code 030-enable-rls.yaml} as
     * {@code current_setting('app.tenant_id', true)::uuid}.
     *
     * <p>{@code SET LOCAL} is bounded to the current transaction — Spring
     * opens one for each {@code @Transactional} service call, and HikariCP
     * resets the connection on return. We tolerate failure here (warn-only)
     * because (a) the app-level {@code tenant_id} predicate is still in
     * force, and (b) without the GUC, RLS-protected queries return zero
     * rows rather than leaking — fail-closed by construction.
     */
    private void setRlsTenantId(UUID tenantId) {
        try {
            // UUID is generated server-side; toString() yields canonical
            // hyphenated form, no quoting hazard. Cannot use ? bind because
            // SET LOCAL does not accept parameters.
            jdbcTemplate.execute("SET LOCAL app.tenant_id = '" + tenantId + "'");
        } catch (Exception ex) {
            log.warn("Failed to SET LOCAL app.tenant_id={} — RLS will reject this request's tenant queries",
                    tenantId, ex);
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

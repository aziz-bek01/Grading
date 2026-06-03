package uz.hrlab.grading.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.env.Environment;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import uz.hrlab.grading.tenancy.application.TenantContext;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Test-only authentication filter that lets developers bootstrap a
 * {@link TenantContext} via headers when running under the {@code local}
 * profile.
 *
 * <p>HARD GUARD: the constructor refuses to start unless one of the allowed
 * dev profiles is active. If a deploy ever loads this filter outside dev,
 * Spring fails at boot time — there is no path where dev headers reach a
 * production runtime.
 *
 * <p>Headers (all optional except {@code X-Dev-User}):
 * <ul>
 *   <li>{@code X-Dev-User} — UUID of the user (required)</li>
 *   <li>{@code X-Dev-Tenant} — UUID of the active tenant</li>
 *   <li>{@code X-Dev-Projects} — comma-separated UUIDs</li>
 *   <li>{@code X-Dev-Roles} — comma-separated role codes</li>
 *   <li>{@code X-Dev-Permissions} — comma-separated permission codes</li>
 *   <li>{@code X-Dev-Salary} — {@code true}/{@code false}</li>
 *   <li>{@code X-Dev-Locale} — locale tag</li>
 * </ul>
 *
 * <p>This filter intentionally NEVER reads any cryptographic material — it is
 * pure header→context plumbing for integration tests.
 *
 * <p>BE-DEVAUTH-002: when {@code X-Dev-Roles}/{@code X-Dev-Permissions} are
 * absent (the normal frontend dev flow — it only sends user + tenant), the
 * filter delegates to {@link DevUserAuthorityResolver} to expand the user's
 * real roles + permissions from the database, so business endpoints stop
 * 403-ing. Explicit override headers still win for deterministic tests.
 */
public class DevAuthFilter extends OncePerRequestFilter {

    public static final String HEADER_USER        = "X-Dev-User";
    public static final String HEADER_TENANT      = "X-Dev-Tenant";
    public static final String HEADER_PROJECTS    = "X-Dev-Projects";
    public static final String HEADER_ROLES       = "X-Dev-Roles";
    public static final String HEADER_PERMISSIONS = "X-Dev-Permissions";
    public static final String HEADER_DEPTS       = "X-Dev-Departments";
    public static final String HEADER_SALARY      = "X-Dev-Salary";
    public static final String HEADER_LOCALE      = "X-Dev-Locale";

    /** Profiles where DevAuthFilter may run. Production profiles must NEVER appear here. */
    public static final Set<String> ALLOWED_PROFILES = Set.of("local", "test", "dev");

    /**
     * Optional DB-backed RBAC resolver. {@code null} in pure unit tests
     * (the {@link #DevAuthFilter(Environment)} constructor) where there is no
     * Spring context — in that case the filter falls back to header-only
     * behaviour exactly as before.
     */
    private final DevUserAuthorityResolver authorityResolver;

    /** Header-only constructor — used by unit tests with no Spring context. */
    public DevAuthFilter(Environment env) {
        this(env, null);
    }

    public DevAuthFilter(Environment env, DevUserAuthorityResolver authorityResolver) {
        if (env == null) {
            throw new IllegalStateException("DevAuthFilter: environment is null — refusing to start");
        }
        Set<String> active = Set.of(env.getActiveProfiles());
        boolean ok = active.stream().anyMatch(ALLOWED_PROFILES::contains);
        if (!ok) {
            throw new IllegalStateException(
                    "DevAuthFilter must only run under " + ALLOWED_PROFILES + " profiles; active=" + active);
        }
        this.authorityResolver = authorityResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String userHeader = request.getHeader(HEADER_USER);
        if (userHeader != null && !userHeader.isBlank()
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            UUID userId;
            try {
                userId = UUID.fromString(userHeader);
            } catch (IllegalArgumentException ex) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid X-Dev-User");
                return;
            }
            UUID tenantId = parseUuid(request.getHeader(HEADER_TENANT));

            // Header values first — explicit overrides keep integration tests
            // deterministic and let a developer simulate a narrower scope.
            Set<String> roles = parseStringSet(request.getHeader(HEADER_ROLES));
            Set<String> permissions = parseStringSet(request.getHeader(HEADER_PERMISSIONS));
            boolean salary = Boolean.parseBoolean(request.getHeader(HEADER_SALARY));

            // BE-DEVAUTH-002: if no roles/permissions were supplied via headers
            // (the normal frontend dev flow), expand the user's real RBAC from
            // the database so business endpoints stop returning PERMISSION_DENIED.
            if (authorityResolver != null && roles.isEmpty() && permissions.isEmpty() && tenantId != null) {
                DevUserAuthorityResolver.DevAuthority authority =
                        authorityResolver.resolve(userId, tenantId);
                roles = authority.roleCodes();
                permissions = authority.permissionCodes();
                // Salary header still takes precedence when explicitly set true;
                // otherwise fall back to the membership's salary_data_permission.
                if (request.getHeader(HEADER_SALARY) == null) {
                    salary = authority.salaryPermission();
                }
            }

            TenantContext ctx = new TenantContext(
                    userId,
                    tenantId,
                    parseUuidSet(request.getHeader(HEADER_PROJECTS)),
                    roles,
                    permissions,
                    parseUuidSet(request.getHeader(HEADER_DEPTS)),
                    salary,
                    request.getHeader(HEADER_LOCALE)
            );
            DevAuthentication auth = new DevAuthentication(userHeader, ctx);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(request, response);
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Set<UUID> parseUuidSet(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(DevAuthFilter::parseUuid)
                .filter(u -> u != null)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> parseStringSet(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
    }
}

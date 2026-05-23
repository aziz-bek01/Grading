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

    public DevAuthFilter(Environment env) {
        if (env == null) {
            throw new IllegalStateException("DevAuthFilter: environment is null — refusing to start");
        }
        Set<String> active = Set.of(env.getActiveProfiles());
        boolean ok = active.stream().anyMatch(ALLOWED_PROFILES::contains);
        if (!ok) {
            throw new IllegalStateException(
                    "DevAuthFilter must only run under " + ALLOWED_PROFILES + " profiles; active=" + active);
        }
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
            TenantContext ctx = new TenantContext(
                    userId,
                    parseUuid(request.getHeader(HEADER_TENANT)),
                    parseUuidSet(request.getHeader(HEADER_PROJECTS)),
                    parseStringSet(request.getHeader(HEADER_ROLES)),
                    parseStringSet(request.getHeader(HEADER_PERMISSIONS)),
                    parseUuidSet(request.getHeader(HEADER_DEPTS)),
                    Boolean.parseBoolean(request.getHeader(HEADER_SALARY)),
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

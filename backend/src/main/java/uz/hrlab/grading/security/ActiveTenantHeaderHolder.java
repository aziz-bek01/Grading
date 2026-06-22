package uz.hrlab.grading.security;

import java.util.UUID;

/**
 * Per-request carrier for the {@code X-Active-Tenant-Id} header value.
 *
 * <h3>Why this exists (BE-AUTH-MT-001)</h3>
 * {@link JwtTenantContextResolver} runs INSIDE the OAuth2 resource-server
 * authentication filter ({@code BearerTokenAuthenticationFilter} →
 * {@code GradingJwtAuthenticationConverter}). At that point in the Spring
 * Security filter chain, {@link org.springframework.web.context.request.RequestContextHolder}
 * is NOT reliably bound to the request thread:
 *
 * <ul>
 *   <li>{@code RequestContextHolder} is populated by Spring's
 *       {@code OrderedRequestContextFilter} (auto-registered by
 *       {@code WebMvcAutoConfiguration}) and, definitively, by the
 *       {@code DispatcherServlet} — the latter runs AFTER the whole security
 *       filter chain.</li>
 *   <li>The request-context binding is {@code threadContextInheritable=false}
 *       and is cleared the moment a filter that bound it returns, so any
 *       sub-path that re-reads it off a slightly different thread / after a
 *       wrapper unwinds sees {@code null}.</li>
 * </ul>
 *
 * When {@code RequestContextHolder} returns {@code null} at JWT-auth time, the
 * resolver could not read the header, a multi-membership Super Admin's selected
 * tenant was lost, and {@code pickActiveMembership} fell through to AMBIGUOUS →
 * {@code null} (fail closed). That produced the prod symptom: tenant-scoped
 * by-id reads ({@code findByIdAndTenantId}) ran with a {@code null} tenantId →
 * zero rows → {@code TenantAccessDeniedException} → 404 / "Рухсат йўқ", even for
 * entities in a tenant the user actively belongs to.
 *
 * <p>{@link ActiveTenantHeaderFilter} runs at {@link org.springframework.core.Ordered#HIGHEST_PRECEDENCE}
 * (before the Spring Security {@code DelegatingFilterProxy}) and stashes the
 * raw header value HERE, on a {@link ThreadLocal}, so the resolver can read it
 * deterministically during authentication regardless of {@code RequestContextHolder}
 * timing. The filter ALWAYS clears the ThreadLocal in a {@code finally} block —
 * no leakage across pooled request threads.
 *
 * <h3>Security note</h3>
 * This holder carries ONLY the raw, UNTRUSTED header value. It does not grant
 * any access. {@link JwtTenantContextResolver#pickActiveMembership} still honours
 * the value ONLY when the authenticated user has an ACTIVE membership in that
 * tenant, and STILL fails closed (no active tenant) when the header is absent,
 * there is more than one ACTIVE membership, and no claim resolves to a member
 * tenant. Making a PRESENT header reliably readable does not weaken any of those
 * checks.
 */
public final class ActiveTenantHeaderHolder {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private ActiveTenantHeaderHolder() { }

    /** Bind the parsed {@code X-Active-Tenant-Id} value for the current request thread. */
    static void set(UUID tenantId) {
        if (tenantId == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(tenantId);
        }
    }

    /** The {@code X-Active-Tenant-Id} bound for this thread, or {@code null} if none. */
    public static UUID get() {
        return CURRENT.get();
    }

    /** Remove the binding. MUST be called in a {@code finally} so pooled threads do not leak. */
    static void clear() {
        CURRENT.remove();
    }
}

package uz.hrlab.grading.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import uz.hrlab.grading.tenancy.application.TenantContext;

import java.util.List;

/**
 * Authentication token created by {@link DevAuthFilter} when running under
 * {@code local} profile. Carries a fully-built {@link TenantContext}.
 *
 * <p>Constructor refuses to build with a null context — silent fall-throughs
 * are a critical security issue.
 */
public class DevAuthentication extends AbstractAuthenticationToken {

    private final TenantContext tenantContext;
    private final String principalName;

    public DevAuthentication(String principalName, TenantContext tenantContext) {
        super(toAuthorities(tenantContext));
        if (tenantContext == null) {
            throw new IllegalArgumentException("tenantContext must not be null");
        }
        this.tenantContext = tenantContext;
        this.principalName = principalName;
        setAuthenticated(true);
    }

    private static List<GrantedAuthority> toAuthorities(TenantContext ctx) {
        if (ctx == null) return List.of();
        return ctx.permissions().stream()
                .map(p -> (GrantedAuthority) new SimpleGrantedAuthority(p))
                .toList();
    }

    @Override
    public Object getCredentials() { return ""; }

    @Override
    public Object getPrincipal() { return principalName; }

    public TenantContext getTenantContext() { return tenantContext; }
}

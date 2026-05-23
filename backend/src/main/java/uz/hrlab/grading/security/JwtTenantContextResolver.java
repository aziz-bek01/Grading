package uz.hrlab.grading.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import uz.hrlab.grading.tenancy.application.TenantContext;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Maps a validated {@link Jwt} into a {@link TenantContext}.
 *
 * <p>Authoritative source of {@code tenant_id} — never trust request input
 * (security-blueprint §20.1 finding 2). When the JWT is missing the
 * {@code active_tenant_id} claim for a business endpoint, the resulting
 * context has {@code tenantId=null} and downstream
 * {@code TenantContextHolder.requireActive()} call will fail securely.
 */
@Component
public class JwtTenantContextResolver {

    public TenantContext resolve(Jwt jwt) {
        UUID userId = parseUuid(jwt.getSubject());
        UUID tenantId = parseUuid(jwt.getClaimAsString(JwtClaimNames.ACTIVE_TENANT_ID));
        Set<UUID> projectIds = parseUuidSet(jwt.getClaim(JwtClaimNames.ACTIVE_PROJECT_IDS));
        Set<String> roles = parseStringSet(jwt.getClaim(JwtClaimNames.ROLES));
        Set<String> permissions = parseStringSet(jwt.getClaim(JwtClaimNames.PERMISSIONS));
        Set<UUID> departmentScope = parseUuidSet(jwt.getClaim(JwtClaimNames.DEPARTMENT_SCOPE));
        boolean salaryPerm = Boolean.TRUE.equals(jwt.getClaim(JwtClaimNames.SALARY_DATA_PERM));
        String locale = jwt.getClaimAsString(JwtClaimNames.LOCALE);
        return new TenantContext(userId, tenantId, projectIds, roles, permissions,
                departmentScope, salaryPerm, locale);
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Set<UUID> parseUuidSet(Object raw) {
        if (raw == null) return Set.of();
        if (raw instanceof Collection<?> col) {
            Set<UUID> out = new HashSet<>();
            for (Object o : col) {
                UUID u = parseUuid(Objects.toString(o, null));
                if (u != null) out.add(u);
            }
            return Set.copyOf(out);
        }
        return Set.of();
    }

    private static Set<String> parseStringSet(Object raw) {
        if (raw == null) return Set.of();
        if (raw instanceof Collection<?> col) {
            Set<String> out = new HashSet<>();
            for (Object o : col) {
                if (o != null) out.add(o.toString());
            }
            return Set.copyOf(out);
        }
        if (raw instanceof String s && !s.isBlank()) {
            return Set.of(List.of(s.split(",")).stream().map(String::trim).toArray(String[]::new));
        }
        return Set.of();
    }
}

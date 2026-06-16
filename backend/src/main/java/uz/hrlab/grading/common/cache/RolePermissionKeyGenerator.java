package uz.hrlab.grading.common.cache;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Deterministic cache key for the role→permission expansion
 * ({@code CacheNames.ROLE_PERMISSION_CODES}).
 *
 * <h3>Why a custom generator (correctness, not just hygiene)</h3>
 * The cached method takes a {@code List<UUID>} of role ids built by streaming an
 * unordered repository result. Spring's default {@code SimpleKeyGenerator} keys
 * on the argument value, and a {@code List} keys by <em>order</em> — so the same
 * logical role set could produce different keys on different requests (cache
 * miss / unbounded key churn) and, worse, two distinct sets could not be told
 * apart if a future caller passed duplicates. This generator canonicalises the
 * input into a {@link TreeSet} of UUIDs (deduplicated + sorted), so the key
 * depends ONLY on the membership in the set, never on iteration order.
 *
 * <h3>Why this is tenant-safe</h3>
 * Role ids are globally-unique UUIDs from the GLOBAL {@code public.roles} table
 * (roles/permissions are control-plane, not tenant-scoped). The key therefore
 * contains no tenant-scoped natural key that could collide across tenants: two
 * tenants share a key only when they reference the SAME global role, whose
 * permission-code set is identical by definition. No tenant id is needed in the
 * key for safety here (and adding one would be wrong — it would split a legit
 * shared global lookup into per-tenant copies for no benefit).
 */
@Component
public class RolePermissionKeyGenerator implements KeyGenerator {

    @Override
    public Object generate(Object target, Method method, Object... params) {
        if (params.length != 1 || !(params[0] instanceof Collection<?> roleIds)) {
            // Fail loud rather than silently mis-key — this generator is wired
            // ONLY to the single-arg findPermissionCodesByRoleIds(List<UUID>).
            throw new IllegalStateException(
                    "RolePermissionKeyGenerator expects exactly one Collection<UUID> arg, got "
                            + params.length + " on " + method.getName());
        }
        // TreeSet → deduplicated, sorted, order-independent canonical key.
        TreeSet<UUID> canonical = new TreeSet<>();
        for (Object o : roleIds) {
            if (o instanceof UUID id) {
                canonical.add(id);
            } else {
                throw new IllegalStateException(
                        "RolePermissionKeyGenerator expects UUID elements, got "
                                + (o == null ? "null" : o.getClass().getName()));
            }
        }
        return canonical;
    }
}

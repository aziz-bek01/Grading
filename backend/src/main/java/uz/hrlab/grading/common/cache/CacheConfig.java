package uz.hrlab.grading.common.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Application cache configuration (Caffeine, in-process).
 *
 * <h3>Scope: read-mostly GLOBAL dictionary data only</h3>
 * This cache exists for hot, read-mostly lookups whose key is provably safe
 * against cross-tenant collision. The first (and currently only) tenant member
 * is the role→permission-codes expansion
 * ({@code CacheNames.ROLE_PERMISSION_CODES}), which runs on every authenticated
 * request and is keyed by globally-unique role UUIDs from the GLOBAL
 * {@code public.roles}/{@code role_permissions} control-plane tables. See
 * {@link CacheNames} and {@link RolePermissionKeyGenerator} for the per-cache
 * tenant-safety argument.
 *
 * <p><b>Deliberately NOT cached:</b> salary, evaluation scores, or anything
 * keyed by a tenant-scoped natural key without the tenant id in the key. Adding
 * such an entry here would risk serving tenant A's value to tenant B.
 *
 * <h3>Defense-in-depth policy</h3>
 * <ul>
 *   <li><b>expireAfterWrite = 60s</b> — a short TTL is the backstop: even if a
 *       {@code @CacheEvict} were ever missed on a mutation, stale authority can
 *       persist for at most one minute. Authority/permission changes are rare
 *       and a sub-minute propagation delay is acceptable; correctness of writes
 *       is additionally guaranteed by explicit eviction on the grant/revoke path
 *       (see {@code RolePermissionAdminUseCase.replaceRolePermissions}).</li>
 *   <li><b>maximumSize = 10_000</b> — bounded so a pathological number of
 *       distinct role-id sets can never exhaust heap.</li>
 *   <li><b>recordStats()</b> — exposes hit/miss/eviction stats for the
 *       actuator {@code caches} / metrics surface.</li>
 * </ul>
 *
 * <p>Only the names registered in {@link CacheNames} are pre-created. Dynamic
 * creation is left ENABLED (Caffeine default) so an accidental new
 * {@code @Cacheable("typo")} does not hard-fail a request, but every legitimate
 * cache MUST be added to {@link CacheNames} with a documented tenant-safety
 * argument and registered below.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Short TTL — the backstop against any missed eviction (see class doc). */
    static final Duration TTL = Duration.ofSeconds(60);

    /** Hard upper bound on distinct entries per cache (heap safety). */
    static final long MAX_SIZE = 10_000;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                CacheNames.ROLE_PERMISSION_CODES);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(TTL)
                .maximumSize(MAX_SIZE)
                .recordStats());
        return manager;
    }
}

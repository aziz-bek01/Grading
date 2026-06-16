package uz.hrlab.grading.common.cache;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import java.lang.reflect.Method;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Offline (no-Docker) wiring + behaviour tests for the Caffeine cache layer.
 *
 * <p>Runs in the fast unit pack ({@code -DexcludedGroups=integration}) — it
 * constructs {@link CacheConfig} directly and exercises
 * {@link RolePermissionKeyGenerator} as POJOs, so no Spring context or database
 * is required.
 */
class CacheConfigTest {

    private final CacheConfig config = new CacheConfig();

    @Test
    void cacheManagerRegistersTheRolePermissionCacheWithBoundedShortTtlPolicy() {
        CacheManager manager = config.cacheManager();

        assertThat(manager).isInstanceOf(CaffeineCacheManager.class);
        Cache cache = manager.getCache(CacheNames.ROLE_PERMISSION_CODES);
        assertThat(cache)
                .as("the role-permission cache must be pre-registered")
                .isNotNull();

        // Policy intent is asserted on the config constants (the single source the
        // @Bean builds from) so a future loosening of the TTL/size fails the build.
        assertThat(CacheConfig.TTL.getSeconds())
                .as("TTL must stay short — it is the backstop against a missed evict")
                .isLessThanOrEqualTo(60);
        assertThat(CacheConfig.MAX_SIZE)
                .as("cache must be bounded (heap safety)")
                .isPositive();
    }

    @Test
    void keyGeneratorSortsAndDeduplicatesSoOrderDoesNotMatter() throws Exception {
        RolePermissionKeyGenerator gen = new RolePermissionKeyGenerator();
        UUID a = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        UUID b = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
        Method m = dummyMethod();

        Object key1 = gen.generate(this, m, List.of(a, b));
        Object key2 = gen.generate(this, m, List.of(b, a, a)); // reordered + dup

        assertThat(key1)
                .as("same role set in any order/dups must yield the same key")
                .isEqualTo(key2)
                .isEqualTo(new TreeSet<>(List.of(a, b)));
        assertThat(key1.hashCode()).isEqualTo(key2.hashCode());
    }

    @Test
    void keyGeneratorRejectsNonUuidOrWrongArity() throws Exception {
        RolePermissionKeyGenerator gen = new RolePermissionKeyGenerator();
        Method m = dummyMethod();

        // Wrong arity — must fail loud, never silently mis-key.
        assertThatThrownBy(() -> gen.generate(this, m, "x", "y"))
                .isInstanceOf(IllegalStateException.class);
        // Non-UUID element — must fail loud.
        assertThatThrownBy(() -> gen.generate(this, m, List.of("not-a-uuid")))
                .isInstanceOf(IllegalStateException.class);
    }

    /** Any single-arg method works — KeyGenerator only inspects the params array. */
    @SuppressWarnings("unused")
    private void sample(List<UUID> roleIds) { }

    private Method dummyMethod() throws NoSuchMethodException {
        return CacheConfigTest.class.getDeclaredMethod("sample", List.class);
    }
}

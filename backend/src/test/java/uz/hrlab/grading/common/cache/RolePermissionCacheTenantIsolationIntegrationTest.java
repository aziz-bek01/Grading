package uz.hrlab.grading.common.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.access.infrastructure.PermissionJpaEntity;
import uz.hrlab.grading.access.infrastructure.PermissionRepository;
import uz.hrlab.grading.access.infrastructure.RoleJpaEntity;
import uz.hrlab.grading.access.infrastructure.RolePermissionId;
import uz.hrlab.grading.access.infrastructure.RolePermissionJpaEntity;
import uz.hrlab.grading.access.infrastructure.RolePermissionRepository;
import uz.hrlab.grading.access.infrastructure.RoleRepository;
import uz.hrlab.grading.access.domain.RoleScope;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BLOCKING tenant-isolation proof for the Caffeine RBAC-expansion cache
 * ({@link CacheNames#ROLE_PERMISSION_CODES}, backing
 * {@code RolePermissionRepository.findPermissionCodesByRoleIds}).
 *
 * <p>The cache runs on every authenticated request. This test pins the
 * non-negotiable contract: <b>caching can NEVER serve one tenant's permission
 * set to another tenant.</b> Two tenants are given DISJOINT global roles, each
 * granting a different permission; we exercise the cached path for each and
 * assert every tenant sees ITS OWN codes and never the other's — including on a
 * warm (cache-hit) second call, which is exactly where a key-collision leak
 * would surface.
 *
 * <p>Tagged {@code tenant-isolation} so it runs in the CI release gate
 * (stage 10), and {@code integration} so it is excluded from the offline unit
 * pack (it needs the Testcontainers Postgres for the real {@code role_permissions}
 * schema). Locally it is skipped when Docker is absent (see
 * {@link AbstractIntegrationTest}).
 */
@Tag("tenant-isolation")
@Tag("integration")
class RolePermissionCacheTenantIsolationIntegrationTest extends AbstractIntegrationTest {

    @Autowired RoleRepository roles;
    @Autowired PermissionRepository permissions;
    @Autowired RolePermissionRepository rolePermissions;
    @Autowired CacheManager cacheManager;

    // Tenant A's world.
    private UUID roleA;
    private static final String PERM_A = "CACHE_ISO_PERM_A";
    // Tenant B's world.
    private UUID roleB;
    private static final String PERM_B = "CACHE_ISO_PERM_B";

    @BeforeEach
    void clearCacheAndSeed() {
        // Start from a cold cache so prior tests / classes cannot mask a leak.
        Cache cache = cacheManager.getCache(CacheNames.ROLE_PERMISSION_CODES);
        assertThat(cache).as("cache must be registered by CacheConfig").isNotNull();
        cache.clear();

        // Two DISTINCT global roles, each granting exactly one DISTINCT permission.
        // Roles + permissions are GLOBAL control-plane rows (no tenant_id) — the
        // very property that makes role-id-keyed caching tenant-safe. A unique
        // suffix avoids clashing with the seeded catalogue across reruns.
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        roleA = seedRoleWithPermission("ROLE_CACHE_A_" + suffix, PERM_A + "_" + suffix);
        roleB = seedRoleWithPermission("ROLE_CACHE_B_" + suffix, PERM_B + "_" + suffix);
    }

    /**
     * The core leak proof. Tenant A resolves authority from {@code [roleA]};
     * Tenant B from {@code [roleB]}. Each MUST see only its own permission code,
     * even after the value is cached. We deliberately call A → B → A → B so the
     * second A/B calls are served from the cache: a key collision (the failure
     * we are guarding against) would hand B's warm value back for A's key, or
     * vice-versa.
     */
    @Test
    void cachedRbacExpansionNeverBleedsAcrossTenants() {
        List<String> firstA = rolePermissions.findPermissionCodesByRoleIds(List.of(roleA));
        List<String> firstB = rolePermissions.findPermissionCodesByRoleIds(List.of(roleB));

        // Cold reads: each tenant's role expands to exactly its own permission.
        assertThat(firstA).containsExactlyInAnyOrder(permCodeFor(roleA));
        assertThat(firstB).containsExactlyInAnyOrder(permCodeFor(roleB));

        // Warm reads (now served from the cache): STILL isolated.
        List<String> warmA = rolePermissions.findPermissionCodesByRoleIds(List.of(roleA));
        List<String> warmB = rolePermissions.findPermissionCodesByRoleIds(List.of(roleB));

        assertThat(warmA)
                .as("tenant A's warm (cached) read must return A's codes only")
                .containsExactlyInAnyOrder(permCodeFor(roleA))
                .doesNotContain(permCodeFor(roleB));
        assertThat(warmB)
                .as("tenant B's warm (cached) read must return B's codes only")
                .containsExactlyInAnyOrder(permCodeFor(roleB))
                .doesNotContain(permCodeFor(roleA));
    }

    /**
     * Order- and duplicate-independence — repeats or a different ordering in the
     * role-id list must not change the cached value (the
     * {@link RolePermissionKeyGenerator} sorts + dedups). Two tenants that happen
     * to reference the SAME global role legitimately share the value — that is
     * correct (identical global data), and is the only way a key is ever shared.
     */
    @Test
    void keyIsOrderAndDuplicateIndependent() {
        List<String> canonical = rolePermissions.findPermissionCodesByRoleIds(List.of(roleA, roleB));
        // Same set, different order + a duplicate → same cached value.
        List<String> reordered = rolePermissions.findPermissionCodesByRoleIds(List.of(roleB, roleA, roleA));

        assertThat(reordered).containsExactlyInAnyOrderElementsOf(canonical);
        assertThat(canonical).containsExactlyInAnyOrder(permCodeFor(roleA), permCodeFor(roleB));

        Cache.ValueWrapper wrapped = cacheManager
                .getCache(CacheNames.ROLE_PERMISSION_CODES)
                .get(new java.util.TreeSet<>(List.of(roleA, roleB)));
        assertThat(wrapped)
                .as("the canonical (sorted) key must be the cache key actually stored")
                .isNotNull();
    }

    // ----------------------------------------------------------------- helpers

    private UUID seedRoleWithPermission(String roleCode, String permCode) {
        UUID roleId = UUID.randomUUID();
        roles.save(new RoleJpaEntity(roleId, roleCode, roleCode, RoleScope.PLATFORM));
        PermissionJpaEntity perm = permissions.save(new PermissionJpaEntity(
                UUID.randomUUID(), permCode, "CACHE_ISO", "TEST", "cache isolation test perm"));
        rolePermissions.save(new RolePermissionJpaEntity(new RolePermissionId(roleId, perm.getId())));
        permCodeByRole.put(roleId, permCode);
        return roleId;
    }

    private final java.util.Map<UUID, String> permCodeByRole = new java.util.HashMap<>();

    private String permCodeFor(UUID roleId) {
        return permCodeByRole.get(roleId);
    }
}

package uz.hrlab.grading.tenancy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure unit test — no Spring, no DB. Holder lifecycle + requireActive contract. */
class TenantContextTest {

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void requireActiveThrowsWhenNothingSet() {
        assertThatThrownBy(TenantContextHolder::requireActive)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requireActiveThrowsWhenTenantIdMissing() {
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), null, Set.of(), Set.of(), Set.of(), Set.of(), false, "ru-RU"));
        assertThatThrownBy(TenantContextHolder::requireActive)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requireActiveReturnsContextWhenSet() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        TenantContext ctx = new TenantContext(userId, tenantId,
                Set.of(), Set.of("HRLAB_SUPER_ADMIN"), Set.of("TENANT_CREATE"),
                Set.of(), false, "ru-RU");
        TenantContextHolder.set(ctx);

        TenantContext resolved = TenantContextHolder.requireActive();
        assertThat(resolved.userId()).isEqualTo(userId);
        assertThat(resolved.tenantId()).isEqualTo(tenantId);
        assertThat(resolved.hasRole("HRLAB_SUPER_ADMIN")).isTrue();
        assertThat(resolved.hasPermission("TENANT_CREATE")).isTrue();
        assertThat(resolved.hasPermission("SALARY_VIEW")).isFalse();
    }

    @Test
    void clearRemovesContext() {
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), UUID.randomUUID(),
                Set.of(), Set.of(), Set.of(), Set.of(), false, "ru-RU"));
        TenantContextHolder.clear();
        assertThat(TenantContextHolder.get()).isNull();
    }

    @Test
    void contextIsIsolatedPerThread() throws Exception {
        UUID outerTenant = UUID.randomUUID();
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), outerTenant,
                Set.of(), Set.of(), Set.of(), Set.of(), false, "ru-RU"));

        UUID[] innerSeen = new UUID[1];
        Thread worker = new Thread(() -> {
            TenantContext c = TenantContextHolder.get();
            innerSeen[0] = c == null ? null : c.tenantId();
        });
        worker.start();
        worker.join();

        assertThat(innerSeen[0]).isNull();
        assertThat(TenantContextHolder.requireActive().tenantId()).isEqualTo(outerTenant);
    }
}

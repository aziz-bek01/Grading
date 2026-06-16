package uz.hrlab.grading.integration.storage;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SECURITY + migrate-profile-safety unit test for {@link MinioObjectStorageAdapter}.
 *
 * <p>Runs WITHOUT a container (fast PR feedback). Proves:
 * <ol>
 *   <li>The constructor performs NO network I/O and does not throw even when the
 *       configured MinIO endpoint is unreachable — so the one-shot {@code migrate}
 *       profile can boot this bean and exit cleanly (no hung deploy).</li>
 *   <li>The signed-URL TTL guard rejects null/zero/negative and anything above
 *       {@link ObjectStorageAdapter#MAX_SIGNED_URL_TTL} (60s) BEFORE any network
 *       call — the same hardened rule as the local adapter (integration-review F1).</li>
 *   <li>The properties' {@code toString} never leaks the access/secret key.</li>
 * </ol>
 */
@Tag("security")
class MinioObjectStorageAdapterUnitTest {

    /** Points at a black-hole address so any accidental I/O would fail loudly/slowly. */
    private static MinioStorageProperties unreachableProperties() {
        MinioStorageProperties props = new MinioStorageProperties();
        // 192.0.2.0/24 is TEST-NET-1 (RFC 5737) — guaranteed non-routable.
        props.setEndpoint("http://192.0.2.1:9000");
        props.setAccessKey("unit-access");
        props.setSecretKey("unit-secret");
        props.setBucket("grading");
        props.setRegion("us-east-1");
        return props;
    }

    @Test
    void constructorPerformsNoNetworkIo() {
        // Constructing the adapter against an unreachable endpoint must return
        // instantly without touching the network. If the constructor opened a
        // connection it would block/throw — proving the lazy-client guarantee.
        long startNanos = System.nanoTime();
        MinioObjectStorageAdapter adapter =
                new MinioObjectStorageAdapter(unreachableProperties());
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        assertThat(adapter).isNotNull();
        // No client may be built yet — it is memoized lazily on first use.
        assertThat(adapter.clientInitialized()).isFalse();
        // Construction is a pure local operation; allow generous slack for CI noise.
        assertThat(elapsedMs).isLessThan(2_000);
    }

    @Test
    void constructorDoesNotThrowWhenMinioDown() {
        // The migrator instantiates every bean; this one must never throw at
        // construction just because MinIO is unreachable.
        assertThatCode(() -> new MinioObjectStorageAdapter(unreachableProperties()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsExpiryAbove60SecondsBeforeAnyNetworkCall() {
        MinioObjectStorageAdapter adapter =
                new MinioObjectStorageAdapter(unreachableProperties());
        String path = namespacedPath();

        // 61s — just over MAX_SIGNED_URL_TTL — rejected by the guard, not the SDK.
        assertThatThrownBy(() -> adapter.signedDownloadUrl(path, Duration.ofSeconds(61)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("60 seconds");
        // 5min was the OLD ceiling — still rejected under the tightened rule.
        assertThatThrownBy(() -> adapter.signedDownloadUrl(path, Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("60 seconds");
        // The guard short-circuits before building the client, so MinIO being
        // unreachable is irrelevant — no client should have been created.
        assertThat(adapter.clientInitialized()).isFalse();
    }

    @Test
    void rejectsNullZeroNegativeExpiry() {
        MinioObjectStorageAdapter adapter =
                new MinioObjectStorageAdapter(unreachableProperties());
        String path = namespacedPath();

        assertThatThrownBy(() -> adapter.signedDownloadUrl(path, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> adapter.signedDownloadUrl(path, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> adapter.signedDownloadUrl(path, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void rejectsNonNamespacedPathBeforeAnyNetworkCall() {
        MinioObjectStorageAdapter adapter =
                new MinioObjectStorageAdapter(unreachableProperties());
        // Path validation happens first in every method, before the client is built.
        assertThatThrownBy(() -> adapter.signedDownloadUrl("imports/a/b/c", Duration.ofSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(adapter.clientInitialized()).isFalse();
    }

    @Test
    void propertiesToStringDoesNotLeakCredentials() {
        MinioStorageProperties props = unreachableProperties();
        String rendered = props.toString();
        assertThat(rendered).doesNotContain("unit-access");
        assertThat(rendered).doesNotContain("unit-secret");
        assertThat(rendered).contains("REDACTED");
    }

    private static String namespacedPath() {
        return ObjectStoragePath.forImportOriginal(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }
}

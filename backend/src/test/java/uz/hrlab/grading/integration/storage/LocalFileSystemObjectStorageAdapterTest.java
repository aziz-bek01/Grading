package uz.hrlab.grading.integration.storage;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("security")
class LocalFileSystemObjectStorageAdapterTest {

    @Test
    void storeRetrieveAndDeleteRoundTrip(@org.junit.jupiter.api.io.TempDir Path tmp) {
        LocalFileSystemObjectStorageAdapter adapter = new LocalFileSystemObjectStorageAdapter(tmp);
        UUID t = UUID.randomUUID();
        UUID p = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        String path = ObjectStoragePath.forImportOriginal(t, p, b);
        byte[] data = "hello".getBytes();
        adapter.store(data, path, Map.of("checksum_sha256", "abc"));
        assertThat(adapter.retrieve(path)).isEqualTo(data);
        assertThat(adapter.readMetadata(path)).containsEntry("checksum_sha256", "abc");
        adapter.delete(path);
        assertThat(Files.exists(tmp.resolve(path))).isFalse();
    }

    @Test
    void acceptsExactly60Seconds(@org.junit.jupiter.api.io.TempDir Path tmp) {
        LocalFileSystemObjectStorageAdapter adapter = new LocalFileSystemObjectStorageAdapter(tmp);
        UUID t = UUID.randomUUID();
        UUID p = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        String path = ObjectStoragePath.forImportOriginal(t, p, b);
        adapter.store(new byte[]{1, 2, 3}, path, Map.of());
        String url = adapter.signedDownloadUrl(path, Duration.ofSeconds(60));
        assertThat(url).startsWith("file:///signed/");
    }

    @Test
    void rejectsExpiryAbove60Seconds(@org.junit.jupiter.api.io.TempDir Path tmp) {
        LocalFileSystemObjectStorageAdapter adapter = new LocalFileSystemObjectStorageAdapter(tmp);
        UUID t = UUID.randomUUID();
        UUID p = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        String path = ObjectStoragePath.forImportOriginal(t, p, b);
        adapter.store(new byte[]{1}, path, Map.of());
        // 61s — just over the new MAX_SIGNED_URL_TTL — must reject.
        assertThatThrownBy(() -> adapter.signedDownloadUrl(path, Duration.ofSeconds(61)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("60 seconds");
        // 5min was the OLD ceiling — still must reject under the tightened rule.
        assertThatThrownBy(() -> adapter.signedDownloadUrl(path, Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("60 seconds");
    }

    @Test
    void storeRejectsNonNamespacedPath(@org.junit.jupiter.api.io.TempDir Path tmp) {
        LocalFileSystemObjectStorageAdapter adapter = new LocalFileSystemObjectStorageAdapter(tmp);
        assertThatThrownBy(() -> adapter.store(new byte[]{1}, "imports/a/b/c", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

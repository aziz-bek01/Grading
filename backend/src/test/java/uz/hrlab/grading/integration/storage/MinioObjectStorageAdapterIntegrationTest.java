package uz.hrlab.grading.integration.storage;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.MinIOContainer;
import uz.hrlab.grading.RequiresDocker;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CI-only Testcontainers integration test for {@link MinioObjectStorageAdapter}
 * (run on the CI integration job; skipped offline / when Docker is unavailable).
 *
 * <p>Exercises the real MinIO SDK end-to-end:
 * <ul>
 *   <li>store -> retrieve -> delete round-trip (bytes survive intact);</li>
 *   <li>tenant/project namespace rejection (non-namespaced path is refused
 *       BEFORE any S3 call);</li>
 *   <li>ensureBucket idempotency (two stores against the same fresh bucket
 *       succeed; the second does not fail on "bucket already exists");</li>
 *   <li>presigned-URL TTL guard (&gt;60s rejected; exactly 60s issues a URL).</li>
 * </ul>
 */
@Tag("integration")
@Tag("security")
@ExtendWith(RequiresDocker.class)
class MinioObjectStorageAdapterIntegrationTest {

    private static final String ACCESS_KEY = "grading_minio";
    private static final String SECRET_KEY = "grading_minio_pwd";
    private static final String BUCKET = "grading-it";

    private static MinIOContainer minio;
    private static MinioObjectStorageAdapter adapter;

    @BeforeAll
    static void startContainer() {
        minio = new MinIOContainer("minio/minio:RELEASE.2024-08-29T01-40-52Z")
                .withUserName(ACCESS_KEY)
                .withPassword(SECRET_KEY);
        minio.start();

        MinioStorageProperties props = new MinioStorageProperties();
        props.setEndpoint(minio.getS3URL());
        props.setAccessKey(minio.getUserName());
        props.setSecretKey(minio.getPassword());
        props.setBucket(BUCKET);
        props.setRegion("us-east-1");
        adapter = new MinioObjectStorageAdapter(props);
    }

    @AfterAll
    static void stopContainer() {
        if (minio != null) {
            minio.stop();
        }
    }

    @Test
    void storeRetrieveAndDeleteRoundTrip() {
        String path = namespacedPath();
        byte[] data = "hello-minio-round-trip".getBytes();

        String key = adapter.store(data, path, Map.of(
                "content-type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "checksum_sha256", "abc123"));
        assertThat(key).isEqualTo(path);

        byte[] back = adapter.retrieve(path);
        assertThat(back).isEqualTo(data);

        adapter.delete(path);
        // After delete, retrieve must fail (object gone) — wrapped, not leaking creds.
        assertThatThrownBy(() -> adapter.retrieve(path))
                .isInstanceOf(MinioObjectStorageAdapter.ObjectStorageException.class)
                .extracting(Throwable::getMessage, org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .doesNotContain(SECRET_KEY)
                .doesNotContain(ACCESS_KEY);
    }

    @Test
    void rejectsNonNamespacedPath() {
        assertThatThrownBy(() -> adapter.store(new byte[]{1}, "imports/a/b/c", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ensureBucketIsIdempotentAcrossMultipleStores() {
        // Two independent stores into the same (freshly-created) bucket. The
        // second must NOT fail with "bucket already exists" — ensureBucket is
        // memoized + tolerant of concurrent creation.
        String first = namespacedPath();
        String second = namespacedPath();
        adapter.store(new byte[]{1, 2, 3}, first, Map.of());
        adapter.store(new byte[]{4, 5, 6}, second, Map.of());

        assertThat(adapter.retrieve(first)).containsExactly(1, 2, 3);
        assertThat(adapter.retrieve(second)).containsExactly(4, 5, 6);

        adapter.delete(first);
        adapter.delete(second);
    }

    @Test
    void presignedUrlRejectsTtlAbove60Seconds() {
        String path = namespacedPath();
        adapter.store(new byte[]{9}, path, Map.of());

        assertThatThrownBy(() -> adapter.signedDownloadUrl(path, Duration.ofSeconds(61)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("60 seconds");
        assertThatThrownBy(() -> adapter.signedDownloadUrl(path, Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("60 seconds");

        // Exactly 60s is allowed and yields a usable presigned URL.
        String url = adapter.signedDownloadUrl(path, Duration.ofSeconds(60));
        assertThat(url).contains(path);

        adapter.delete(path);
    }

    private static String namespacedPath() {
        return ObjectStoragePath.forImportOriginal(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }
}

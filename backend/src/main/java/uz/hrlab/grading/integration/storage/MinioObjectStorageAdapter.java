package uz.hrlab.grading.integration.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * MinIO/S3 implementation of {@link ObjectStorageAdapter}.
 *
 * <p>MVP 2 Phase 2 ships the *contract* and *configuration plumbing*. The
 * actual S3 SDK call sites are wired in MVP 2 Phase 3 once devops-sre has
 * provisioned the bucket policy + KMS key + lifecycle rules (integration
 * blueprint §7). For now the bean is registered only when explicitly
 * activated via {@code grading.storage.type=minio} and throws on use so a
 * misconfigured environment fails fast.
 */
@Component
@ConditionalOnProperty(name = "grading.storage.type", havingValue = "minio")
public class MinioObjectStorageAdapter implements ObjectStorageAdapter {

    private final MinioStorageProperties properties;

    public MinioObjectStorageAdapter(MinioStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public String store(byte[] data, String objectPath, Map<String, String> metadata) {
        ObjectStoragePath.validate(objectPath);
        throw new UnsupportedOperationException(
                "MinIO adapter wiring is delivered in MVP 2 Phase 3 (DevOps deps: bucket + KMS). " +
                "Activate the local adapter with grading.storage.type=local for now.");
    }

    @Override
    public byte[] retrieve(String storageKey) {
        ObjectStoragePath.validate(storageKey);
        throw new UnsupportedOperationException("MinIO adapter wiring pending (MVP 2 Phase 3)");
    }

    @Override
    public String signedDownloadUrl(String storageKey, Duration expiry) {
        ObjectStoragePath.validate(storageKey);
        if (expiry == null || expiry.isNegative() || expiry.isZero()) {
            throw new IllegalArgumentException("signed URL expiry must be positive");
        }
        if (expiry.compareTo(MAX_SIGNED_URL_TTL) > 0) {
            throw new IllegalArgumentException(
                    "Signed URL TTL must not exceed 60 seconds "
                            + "(security-blueprint §6, integration-review F1)");
        }
        throw new UnsupportedOperationException("MinIO adapter wiring pending (MVP 2 Phase 3)");
    }

    @Override
    public void delete(String storageKey) {
        ObjectStoragePath.validate(storageKey);
        throw new UnsupportedOperationException("MinIO adapter wiring pending (MVP 2 Phase 3)");
    }

    public MinioStorageProperties properties() { return properties; }
}

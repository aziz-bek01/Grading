package uz.hrlab.grading.integration.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * MinIO/S3 implementation of {@link ObjectStorageAdapter} — activated only when
 * {@code grading.storage.type=minio}. Mirrors the semantics and exception style
 * of {@link LocalFileSystemObjectStorageAdapter}: every method validates the
 * tenant/project namespace via {@link ObjectStoragePath#validate(String)},
 * {@code retrieve} buffers the object fully into a {@code byte[]}, and the
 * signed-URL TTL guard rejects anything above {@link #MAX_SIGNED_URL_TTL}.
 *
 * <h3>Laziness (migrate-profile safety — CRITICAL)</h3>
 * The one-shot Liquibase migrator boots the FULL Spring context under the
 * {@code migrate} profile and must then exit. A prior incident showed that any
 * bean which starts a thread or performs blocking network I/O at construction
 * time hangs that deploy. This adapter therefore does <strong>no network
 * I/O in its constructor and has no {@code @PostConstruct}</strong>: the
 * {@link MinioClient} is built lazily and memoized on first use
 * ({@link #client()}), and the bucket is ensured lazily on the first
 * {@link #store} ({@link #ensureBucket()}). The migrator can instantiate this
 * bean with MinIO unreachable and shut down cleanly. This is asserted by
 * {@code MinioObjectStorageAdapterUnitTest#constructorPerformsNoNetworkIo}.
 */
@Component
@ConditionalOnProperty(name = "grading.storage.type", havingValue = "minio")
public class MinioObjectStorageAdapter implements ObjectStorageAdapter {

    /** Default content type when the caller supplies no {@code content-type} metadata. */
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    /** Metadata key (case-insensitive) interpreted as the S3 object content type. */
    private static final String CONTENT_TYPE_KEY = "content-type";

    /**
     * S3/MinIO error codes that mean "the bucket already exists" — tolerated so
     * {@link #ensureBucket()} is idempotent under concurrent first-store races.
     */
    private static final Set<String> BUCKET_ALREADY_EXISTS_CODES = Set.of(
            "BucketAlreadyOwnedByYou", "BucketAlreadyExists");

    private final MinioStorageProperties properties;

    /**
     * Memoized client — guarded by {@link #clientLock}. {@code volatile} so the
     * double-checked read in {@link #client()} is safe. NEVER initialised in the
     * constructor (see class javadoc — migrate-profile safety).
     */
    private volatile MinioClient client;
    private final Object clientLock = new Object();

    /** True once {@link #ensureBucket()} has confirmed/created the bucket. */
    private volatile boolean bucketEnsured;
    private final Object bucketLock = new Object();

    public MinioObjectStorageAdapter(MinioStorageProperties properties) {
        // Constructor does NO network I/O — just holds config. The MinioClient is
        // built lazily in client() on first use so the `migrate` profile can boot
        // this bean with MinIO unreachable and still exit.
        this.properties = properties;
    }

    @Override
    public String store(byte[] data, String objectPath, Map<String, String> metadata) {
        ObjectStoragePath.validate(objectPath);
        ensureBucket();
        String contentType = contentTypeFrom(metadata);
        try (InputStream in = new ByteArrayInputStream(data)) {
            PutObjectArgs.Builder builder = PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectPath)
                    .stream(in, data.length, -1)
                    .contentType(contentType);
            Map<String, String> userMetadata = userMetadataFrom(metadata);
            if (!userMetadata.isEmpty()) {
                builder.userMetadata(userMetadata);
            }
            client().putObject(builder.build());
            return objectPath;
        } catch (Exception e) {
            throw wrap("store", objectPath, e);
        }
    }

    @Override
    public byte[] retrieve(String storageKey) {
        ObjectStoragePath.validate(storageKey);
        try (GetObjectResponse response = client().getObject(GetObjectArgs.builder()
                .bucket(properties.getBucket())
                .object(storageKey)
                .build())) {
            // Buffer fully into a byte[] — matches the local adapter contract
            // (the authenticated API streams these bytes back to the client).
            return response.readAllBytes();
        } catch (Exception e) {
            throw wrap("retrieve", storageKey, e);
        }
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
        try {
            return client().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.getBucket())
                    .object(storageKey)
                    .expiry((int) expiry.getSeconds())
                    .build());
        } catch (Exception e) {
            throw wrap("signedDownloadUrl", storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        ObjectStoragePath.validate(storageKey);
        try {
            client().removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(storageKey)
                    .build());
        } catch (Exception e) {
            throw wrap("delete", storageKey, e);
        }
    }

    public MinioStorageProperties properties() { return properties; }

    // ------------------------------------------------------------------
    //  Lazy wiring — NO network I/O until first real operation.
    // ------------------------------------------------------------------

    /**
     * Lazily builds and memoizes the {@link MinioClient}. Constructing the client
     * is a pure local operation (no handshake / no connection), but we still defer
     * it to first use so the bean is cheap to instantiate under the {@code migrate}
     * profile. Double-checked locking keeps it single-instance under concurrency.
     */
    private MinioClient client() {
        MinioClient local = client;
        if (local == null) {
            synchronized (clientLock) {
                local = client;
                if (local == null) {
                    local = MinioClient.builder()
                            .endpoint(properties.getEndpoint())
                            .credentials(properties.getAccessKey(), properties.getSecretKey())
                            .region(properties.getRegion())
                            .build();
                    client = local;
                }
            }
        }
        return local;
    }

    /**
     * Ensures the target bucket exists. Called lazily from {@link #store} — NEVER
     * from the constructor. Idempotent: tolerates a concurrent creation race where
     * a parallel caller (or another replica) created the bucket between our
     * {@code bucketExists} check and {@code makeBucket} call.
     */
    private void ensureBucket() {
        if (bucketEnsured) {
            return;
        }
        synchronized (bucketLock) {
            if (bucketEnsured) {
                return;
            }
            String bucket = properties.getBucket();
            try {
                boolean exists = client().bucketExists(BucketExistsArgs.builder()
                        .bucket(bucket)
                        .build());
                if (!exists) {
                    client().makeBucket(MakeBucketArgs.builder()
                            .bucket(bucket)
                            .region(properties.getRegion())
                            .build());
                }
                bucketEnsured = true;
            } catch (ErrorResponseException e) {
                // Concurrent creation: another caller/replica won the race.
                if (e.errorResponse() != null
                        && BUCKET_ALREADY_EXISTS_CODES.contains(e.errorResponse().code())) {
                    bucketEnsured = true;
                    return;
                }
                throw wrap("ensureBucket", bucket, e);
            } catch (Exception e) {
                throw wrap("ensureBucket", bucket, e);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    /** Resolves the content type from metadata (case-insensitive key), else default. */
    private static String contentTypeFrom(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return DEFAULT_CONTENT_TYPE;
        }
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (entry.getKey() != null && CONTENT_TYPE_KEY.equalsIgnoreCase(entry.getKey().trim())) {
                String value = entry.getValue();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return DEFAULT_CONTENT_TYPE;
    }

    /**
     * Builds the S3 user-metadata map from caller metadata, excluding the
     * content-type entry (which becomes the object content type, not user
     * metadata) and any null keys/values.
     */
    private static Map<String, String> userMetadataFrom(Map<String, String> metadata) {
        java.util.Map<String, String> out = new java.util.HashMap<>();
        if (metadata == null) {
            return out;
        }
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || value == null) {
                continue;
            }
            if (CONTENT_TYPE_KEY.equalsIgnoreCase(key.trim())) {
                continue;
            }
            out.put(key, value);
        }
        return out;
    }

    /**
     * Wraps any MinIO/SDK failure in a clear runtime exception. CRITICAL: the
     * message must NOT leak the endpoint, access key or secret key — only the
     * operation, the object key (already tenant/project-namespaced, never a
     * secret) and the underlying exception type are surfaced. The cause is
     * preserved for server-side diagnostics but the secret-bearing properties
     * are never interpolated into the message.
     */
    private RuntimeException wrap(String operation, String objectKey, Exception cause) {
        String type = cause == null ? "unknown" : cause.getClass().getSimpleName();
        return new ObjectStorageException(
                "MinIO " + operation + " failed for key '" + objectKey + "' (" + type + ")", cause);
    }

    /** Runtime failure from the MinIO adapter — never carries endpoint creds/secrets. */
    public static final class ObjectStorageException extends RuntimeException {
        public ObjectStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Test seam: a subclass/test can verify the client is built lazily. */
    boolean clientInitialized() {
        return client != null;
    }
}

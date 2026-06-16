package uz.hrlab.grading.integration.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the MinIO/S3 adapter — {@code grading.storage.minio.*}.
 *
 * <p>Property names are kebab-case so Spring relaxed binding maps the
 * environment variables wired in {@code application.yml}:
 * <pre>
 *   GRADING_STORAGE_MINIO_ENDPOINT    -> grading.storage.minio.endpoint    -> endpoint
 *   GRADING_STORAGE_MINIO_ACCESS_KEY  -> grading.storage.minio.access-key  -> accessKey
 *   GRADING_STORAGE_MINIO_SECRET_KEY  -> grading.storage.minio.secret-key  -> secretKey
 *   GRADING_STORAGE_MINIO_BUCKET      -> grading.storage.minio.bucket      -> bucket
 *   GRADING_STORAGE_MINIO_REGION      -> grading.storage.minio.region      -> region
 * </pre>
 *
 * <p>{@code toString} deliberately REDACTS {@code accessKey}/{@code secretKey}
 * so a config dump (e.g. accidental {@code log.info("{}", props)}) never leaks
 * credentials — the same rule the adapter's exception wrapper enforces.
 */
@Component
@ConfigurationProperties(prefix = "grading.storage.minio")
public class MinioStorageProperties {

    private String endpoint = "http://localhost:9000";
    private String accessKey = "grading_minio";
    private String secretKey = "grading_minio_pwd";
    private String bucket = "grading";
    private String region = "us-east-1";

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    /** Redacts credentials so a config dump never leaks accessKey/secretKey. */
    @Override
    public String toString() {
        return "MinioStorageProperties{endpoint=" + endpoint
                + ", accessKey=***REDACTED***"
                + ", secretKey=***REDACTED***"
                + ", bucket=" + bucket
                + ", region=" + region + "}";
    }
}

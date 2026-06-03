package uz.hrlab.grading.integration.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configuration for the MinIO/S3 adapter — {@code grading.storage.minio.*}. */
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
}

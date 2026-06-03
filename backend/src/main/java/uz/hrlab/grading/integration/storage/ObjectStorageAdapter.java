package uz.hrlab.grading.integration.storage;

import java.time.Duration;
import java.util.Map;

/**
 * Tenant/project-scoped object storage abstraction (integration-blueprint §7).
 *
 * <p>Implementations MUST enforce:
 * <ul>
 *   <li>Object path begins with {@code tenants/{tenantId}/projects/{projectId}/}
 *       — see {@link ObjectStoragePath#validate(String)}.</li>
 *   <li>{@code signedDownloadUrl} TTL ≤ {@link #MAX_SIGNED_URL_TTL} (60 seconds
 *       per security-blueprint §6 + integration-blueprint §13.3 hardened in
 *       MVP 2 Phase 2 integration review F1). The caller must already have
 *       verified permission BEFORE issuing the signed URL.</li>
 *   <li>No public buckets. No anonymous access. All access via signed URL.</li>
 * </ul>
 */
public interface ObjectStorageAdapter {

    /**
     * Maximum allowed TTL for a signed download URL.
     *
     * <p>Tightened from 5 minutes to 60 seconds per MVP 2 Phase 2 integration
     * review finding F1: shorter expiry minimises the blast radius if a signed
     * URL is captured in a browser history, proxy log, or screenshot.
     */
    Duration MAX_SIGNED_URL_TTL = Duration.ofSeconds(60);

    /** Stores bytes at the given namespaced path, returns the storage key. */
    String store(byte[] data, String objectPath, Map<String, String> metadata);

    /** Retrieves stored bytes by storage key. */
    byte[] retrieve(String storageKey);

    /**
     * Issues a time-limited signed URL.
     *
     * @param expiry max {@link #MAX_SIGNED_URL_TTL} (60 seconds); longer is
     *               rejected with {@link IllegalArgumentException}.
     */
    String signedDownloadUrl(String storageKey, Duration expiry);

    /** Soft delete — moves object to lifecycle deletion queue. */
    void delete(String storageKey);
}

package uz.hrlab.grading.integration.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local-filesystem implementation of {@link ObjectStorageAdapter} —
 * used in tests and in the dev profile when MinIO is not available.
 *
 * <p>Files are stored under a single root directory; the namespace pattern is
 * still enforced via {@link ObjectStoragePath#validate(String)}. Signed URLs
 * are simulated with an in-memory token map; TTL is enforced exactly like the
 * MinIO adapter.
 */
@Component
@ConditionalOnProperty(name = "grading.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileSystemObjectStorageAdapter implements ObjectStorageAdapter {

    private final Path root;
    private final Map<String, SignedToken> signedTokens = new ConcurrentHashMap<>();

    public LocalFileSystemObjectStorageAdapter() {
        try {
            this.root = Files.createTempDirectory("grading-objstore-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Test/utility constructor — accepts a caller-managed root. */
    public LocalFileSystemObjectStorageAdapter(Path root) {
        this.root = root;
    }

    @Override
    public String store(byte[] data, String objectPath, Map<String, String> metadata) {
        ObjectStoragePath.validate(objectPath);
        Path full = root.resolve(objectPath);
        try {
            Files.createDirectories(full.getParent());
            Files.write(full, data,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            // metadata stored as sibling .meta — kept simple for the local adapter
            if (metadata != null && !metadata.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                metadata.forEach((k, v) -> sb.append(k).append('=').append(v == null ? "" : v).append('\n'));
                Files.writeString(Paths.get(full.toString() + ".meta"), sb.toString());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return objectPath;
    }

    @Override
    public byte[] retrieve(String storageKey) {
        ObjectStoragePath.validate(storageKey);
        try {
            return Files.readAllBytes(root.resolve(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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
        String token = UUID.randomUUID().toString();
        signedTokens.put(token,
                new SignedToken(storageKey, System.currentTimeMillis() + expiry.toMillis()));
        return "file:///signed/" + token;
    }

    @Override
    public void delete(String storageKey) {
        ObjectStoragePath.validate(storageKey);
        try {
            Files.deleteIfExists(root.resolve(storageKey));
            Files.deleteIfExists(Paths.get(root.resolve(storageKey).toString() + ".meta"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Test helper — verifies a token is still valid + bound to a key. */
    public boolean isSignedTokenValid(String token, String storageKey) {
        SignedToken t = signedTokens.get(token);
        return t != null && t.storageKey.equals(storageKey) && t.expiresAtMs > System.currentTimeMillis();
    }

    /** Test helper — exposes the underlying root for path assertions. */
    public Path root() { return root; }

    /** Test helper — returns a copy of metadata stored for the given path. */
    public Map<String, String> readMetadata(String storageKey) {
        Path meta = Paths.get(root.resolve(storageKey).toString() + ".meta");
        Map<String, String> result = new HashMap<>();
        try {
            if (!Files.exists(meta)) return result;
            for (String line : Files.readAllLines(meta)) {
                int eq = line.indexOf('=');
                if (eq > 0) result.put(line.substring(0, eq), line.substring(eq + 1));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return result;
    }

    private record SignedToken(String storageKey, long expiresAtMs) { }
}

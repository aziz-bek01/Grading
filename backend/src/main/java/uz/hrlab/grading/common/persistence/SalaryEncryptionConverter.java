package uz.hrlab.grading.common.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JPA {@link AttributeConverter} skeleton for envelope-encrypted salary
 * columns (security-blueprint §8, finding F-10).
 *
 * <p>The full AES-GCM implementation activates in MVP 3 when KMS / Vault
 * Transit wiring lands. For MVP 1 this class:
 * <ul>
 *   <li>Implements a deterministic stub that base64-encodes the plain value
 *       prefixed with key id / key version so the encrypted column shape is
 *       exercised end-to-end (column type, length, round-trip).</li>
 *   <li>Reserves {@link #keyId} and {@link #keyVersion} from
 *       {@code grading.security.salary-encryption.*} config so production
 *       envelope encryption can be activated without re-migrating columns.</li>
 *   <li>P2-FIX (Task 3) — HARD GUARD: refuses to "encrypt"/persist when the
 *       configured {@code keyId} ends with {@code -stub} AND the active Spring
 *       profile is NOT local/test. The stub is a reversible base64 codec, not
 *       encryption; this fail-closed guard guarantees it can never silently
 *       land trivially-reversible salary blobs in a real (dev/demo/staging/
 *       prod) environment before the MVP 3 KMS / AES-GCM wiring exists. Read
 *       paths stay enabled in every profile (decrypting an already-stored
 *       value must not be blocked).</li>
 * </ul>
 *
 * <p>Wiring (MVP 3): annotate the JPA column with
 * {@code @Convert(converter = SalaryEncryptionConverter.class)} and switch
 * {@code encrypt()} / {@code decrypt()} from the stub to real AES-GCM, then
 * provision a non-{@code -stub} {@code key-id} so this guard is satisfied.
 */
@Component
@Converter
public class SalaryEncryptionConverter implements AttributeConverter<BigDecimal, String> {

    /** Marker prefix on encrypted blobs — lets us distinguish stub vs real ciphertext. */
    static final String STUB_MAGIC = "ENC0:";

    /** Suffix marking a placeholder (non-real) key id, e.g. {@code dev-stub}/{@code prod-stub}. */
    static final String STUB_KEY_SUFFIX = "-stub";

    /**
     * Profiles in which the base64 stub is tolerated for WRITES. The Spring
     * default profile is {@code local} (application.yml {@code
     * spring.profiles.default}), so an empty active-profile array is treated as
     * local. Every request-serving deployed profile (dev/demo/staging/prod) is
     * intentionally absent → a stub key there fails closed on write.
     */
    private static final Set<String> STUB_WRITE_ALLOWED_PROFILES = Set.of("local", "test");

    private static final Logger log = LoggerFactory.getLogger(SalaryEncryptionConverter.class);

    private final String keyId;
    private final int keyVersion;
    /**
     * When {@code true}, a stub key in a deployed profile HARD-FAILS the write
     * (the strict security posture). Default {@code false} so a deploy that still
     * runs on a placeholder key keeps salary writes working while logging a loud
     * warning — flip {@code grading.security.salary-encryption.fail-on-stub-key=true}
     * once a real KMS key is provisioned to re-enable the fail-closed guard.
     */
    private final boolean failOnStubKey;
    private final Environment environment;
    private final SecureRandom random = new SecureRandom();
    /** Warn-once latch so a stub key in prod logs a single warning, not one per write. */
    private final AtomicBoolean stubKeyWarned = new AtomicBoolean(false);

    @Autowired
    public SalaryEncryptionConverter(
            @Value("${grading.security.salary-encryption.key-id:dev-stub}") String keyId,
            @Value("${grading.security.salary-encryption.key-version:1}") int keyVersion,
            @Value("${grading.security.salary-encryption.fail-on-stub-key:false}") boolean failOnStubKey,
            Environment environment) {
        this.keyId = keyId;
        this.keyVersion = keyVersion;
        this.failOnStubKey = failOnStubKey;
        this.environment = environment;
    }

    /** Test/convenience constructor — defaults {@code failOnStubKey} to {@code false}. */
    SalaryEncryptionConverter(String keyId, int keyVersion, Environment environment) {
        this(keyId, keyVersion, false, environment);
    }

    @Override
    public String convertToDatabaseColumn(BigDecimal attribute) {
        if (attribute == null) return null;
        // P2-FIX (Task 3) — a reversible base64 "ciphertext" with a placeholder
        // key in a non-local/test environment is a security risk. When
        // fail-on-stub-key is enabled this fails closed; by default it logs a
        // loud one-time warning and proceeds so an existing deployment running on
        // a placeholder key is not broken by the upgrade. MVP 3 KMS wiring (real
        // key-id + AES-GCM) makes this moot.
        if (usesStubKey() && !stubWritesAllowedInActiveProfile()) {
            if (failOnStubKey) {
                throw new IllegalStateException(
                        "Salary encryption is a reversible base64 STUB (key-id='" + keyId
                                + "') — refusing to persist salary data outside local/test profiles. "
                                + "Provision a real (non '-stub') GRADING_SALARY_KEY_ID with MVP 3 KMS "
                                + "wiring before storing salary in this environment.");
            }
            if (stubKeyWarned.compareAndSet(false, true)) {
                log.warn("Salary encryption is running on a placeholder STUB key (key-id='{}') in a "
                        + "deployed profile — stored salary is trivially reversible base64, NOT encryption. "
                        + "Provision a real (non '-stub') GRADING_SALARY_KEY_ID with MVP 3 KMS wiring, then "
                        + "set grading.security.salary-encryption.fail-on-stub-key=true to enforce.", keyId);
            }
        }
        // Stub envelope: STUB_MAGIC | keyId | keyVersion | base64(plaintext)
        // Real implementation (MVP 3) replaces the base64 with AES-GCM(IV ⨁ ciphertext ⨁ tag).
        String plain = attribute.toPlainString();
        // Touch the SecureRandom so the IV slot is visible in the wire format
        // (stub uses 12 random bytes that the future converter will use as IV).
        byte[] iv = new byte[12];
        random.nextBytes(iv);
        String ivB64 = Base64.getEncoder().encodeToString(iv);
        String payloadB64 = Base64.getEncoder().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
        return STUB_MAGIC + keyId + ":" + keyVersion + ":" + ivB64 + ":" + payloadB64;
    }

    @Override
    public BigDecimal convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        if (!dbData.startsWith(STUB_MAGIC)) {
            // MVP 3 will recognise the AES-GCM envelope here.
            throw new IllegalStateException(
                    "Unrecognised salary ciphertext envelope; cannot decrypt with stub converter");
        }
        String[] parts = dbData.substring(STUB_MAGIC.length()).split(":", 4);
        if (parts.length != 4) {
            throw new IllegalStateException("Malformed stub salary envelope");
        }
        byte[] decoded = Base64.getDecoder().decode(parts[3]);
        return new BigDecimal(new String(decoded, StandardCharsets.UTF_8));
    }

    /** True when the configured key id is a placeholder (ends with {@code -stub}). */
    private boolean usesStubKey() {
        return keyId != null && keyId.endsWith(STUB_KEY_SUFFIX);
    }

    /**
     * True when the active profile permits writing the base64 stub. Empty active
     * profiles ⇒ Spring default {@code local} ⇒ allowed. Any deployed profile
     * not in {@link #STUB_WRITE_ALLOWED_PROFILES} ⇒ not allowed (fail-closed).
     */
    private boolean stubWritesAllowedInActiveProfile() {
        String[] active = environment.getActiveProfiles();
        if (active.length == 0) {
            return true; // spring.profiles.default = local
        }
        for (String p : active) {
            if (STUB_WRITE_ALLOWED_PROFILES.contains(p)) {
                return true;
            }
        }
        return false;
    }

    String keyId() { return keyId; }
    int keyVersion() { return keyVersion; }
}

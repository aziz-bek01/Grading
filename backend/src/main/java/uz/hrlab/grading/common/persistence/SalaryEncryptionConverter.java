package uz.hrlab.grading.common.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

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
 *   <li>Refuses to operate in any profile where {@code keyId == "dev-stub"}
 *       outside local/test once an explicit guard is added in MVP 3 —
 *       intentionally not enforced here so the skeleton is harmless.</li>
 * </ul>
 *
 * <p>Wiring (MVP 3): annotate the JPA column with
 * {@code @Convert(converter = SalaryEncryptionConverter.class)} and switch
 * {@code encrypt()} / {@code decrypt()} from the stub to real AES-GCM.
 */
@Component
@Converter
public class SalaryEncryptionConverter implements AttributeConverter<BigDecimal, String> {

    /** Marker prefix on encrypted blobs — lets us distinguish stub vs real ciphertext. */
    static final String STUB_MAGIC = "ENC0:";

    private final String keyId;
    private final int keyVersion;
    private final SecureRandom random = new SecureRandom();

    public SalaryEncryptionConverter(
            @Value("${grading.security.salary-encryption.key-id:dev-stub}") String keyId,
            @Value("${grading.security.salary-encryption.key-version:1}") int keyVersion) {
        this.keyId = keyId;
        this.keyVersion = keyVersion;
    }

    @Override
    public String convertToDatabaseColumn(BigDecimal attribute) {
        if (attribute == null) return null;
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

    String keyId() { return keyId; }
    int keyVersion() { return keyVersion; }
}

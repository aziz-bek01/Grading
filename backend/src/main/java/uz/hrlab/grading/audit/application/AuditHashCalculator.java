package uz.hrlab.grading.audit.application;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

/**
 * SINGLE SOURCE OF TRUTH for the audit hash chain
 * ({@code hash_current = SHA-256(canonical(record) || hash_prev)},
 * security-blueprint §9.2).
 *
 * <p>Both the WRITE path ({@code JpaAuditService}) and the VERIFY path
 * ({@code AuditChainVerifier}) call {@link #compute(AuditHashInput)} — extracted
 * here precisely so a verifier can never hash a row differently than the writer
 * did. A verifier that diverges from the writer is worse than useless, so there
 * is exactly one canonical string builder and one digest call.
 *
 * <p><b>Round-trip determinism.</b> A recomputation reads its inputs back from
 * PostgreSQL, where three representations differ from the in-memory write values
 * and must be normalized so an INTACT chain re-hashes identically:
 * <ul>
 *   <li><b>JSON keys / whitespace</b> — {@code before_json}/{@code after_json}
 *       are {@code jsonb}; PostgreSQL normalizes whitespace and reorders object
 *       keys. Both paths funnel through {@link #canonicalJson(String)} (parse →
 *       re-serialize with recursively sorted keys) so the hashed form is stable.</li>
 *   <li><b>JSON numbers</b> (M2) — {@code jsonb} stores numbers as {@code
 *       numeric}, so {@code 1.0E10} comes back {@code 10000000000} and
 *       {@code 1234.50} keeps its scale. The mapper parses every JSON number as
 *       {@link java.math.BigDecimal} ({@link DeserializationFeature#USE_BIG_DECIMAL_FOR_FLOATS})
 *       and writes it in plain (non-scientific) form
 *       ({@link JsonGenerator.Feature#WRITE_BIGDECIMAL_AS_PLAIN}); because the
 *       writer path ALSO funnels its JSON through the same string normalizer,
 *       writer and verifier converge on one canonical numeric text. (Today's
 *       redacted snapshots carry only strings/ints; this hardens the format for
 *       future decimal snapshots.)</li>
 *   <li><b>Timestamp</b> — {@code created_at} is {@code TIMESTAMPTZ}
 *       (microsecond, instant-based); the JDBC driver may return a different zone
 *       offset. {@link #canonicalTimestamp(OffsetDateTime)} renders the instant
 *       in UTC truncated to microseconds, so the offset is irrelevant. The writer
 *       generates {@code created_at} already at UTC/µs precision.</li>
 * </ul>
 *
 * <p><b>L1 (defence in depth):</b> {@code hash_format_version} is part of the
 * hashed field set, so a silent version downgrade of a current-format row cannot
 * hide a payload tamper within the recomputed population.
 */
@Component
public class AuditHashCalculator {

    /**
     * Canonical hash format this calculator implements — stamped on
     * {@code system_audit_log.hash_format_version} for every NEW row and used by
     * the verifier to decide which rows it can content-recompute. Bump ONLY when
     * the canonical serialization in {@link #compute(AuditHashInput)} changes in
     * a way that alters the digest; older rows keep their stamped version and are
     * treated by the verifier as linkage-only.
     *
     * <p>v1 = legacy (pre-E10: ObjectNode-insertion-order JSON + JVM-zone
     * nanosecond timestamp — not recomputable from storage).
     * v2 = recursively key-sorted, numerically-canonical JSON + UTC/microsecond
     * timestamp + version bound into the digest.
     */
    public static final short HASH_FORMAT_VERSION = 2;

    private final ObjectMapper objectMapper;

    public AuditHashCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                // Deterministic key ordering so the canonical JSON is reproducible.
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                // M2: parse every JSON number as BigDecimal so a jsonb numeric
                // round-trip (scientific/trailing-zero/large-int) is deterministic.
                .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)
                // M2: emit BigDecimal in plain (non-scientific) form.
                .configure(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN, true);
    }

    /**
     * Canonical JSON string for STORAGE (writer path). Recursively KEY-SORTED,
     * numerically canonical, compact. {@code null} stays {@code null}.
     *
     * <p>Funnels through the SAME {@link #canonicalJson(String)} normalizer the
     * verifier uses (serialize the node, then normalize the text) so the writer
     * and verifier can never diverge on key order OR number formatting.
     */
    public String canonicalJson(JsonNode node) {
        if (node == null) {
            return null;
        }
        try {
            return canonicalJson(objectMapper.writeValueAsString(node));
        } catch (Exception ex) {
            // Never fail an audit write on a serialization edge — store null.
            return null;
        }
    }

    /**
     * Canonical JSON string from a RAW column value (verifier path, and — via the
     * overload above — the writer path too). Re-parses (numbers → BigDecimal) then
     * re-serializes with recursively sorted keys and plain numbers so a {@code
     * jsonb} round-trip cannot change the hash. If the value is not parseable JSON
     * the raw string is used verbatim (defensive — never throw during verify).
     */
    private String canonicalJson(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            Object plain = objectMapper.readValue(raw, Object.class);
            return objectMapper.writeValueAsString(plain);
        } catch (Exception ex) {
            return raw;
        }
    }

    /**
     * Instant-based, offset-independent, microsecond-precision rendering of a
     * timestamp. {@code null} → empty string (matches the null handling of the
     * other fields).
     */
    private String canonicalTimestamp(OffsetDateTime createdAt) {
        if (createdAt == null) {
            return "";
        }
        return createdAt.toInstant()
                .atOffset(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS)
                .toString();
    }

    /**
     * Computes {@code hash_current} for one row. Field order and separators are
     * the immutable wire format of the chain — DO NOT reorder or reformat, it
     * would change every hash.
     */
    public String compute(AuditHashInput in) {
        StringBuilder sb = new StringBuilder();
        sb.append(safe(in.id()));
        sb.append('|').append(safe(in.tenantId()));
        sb.append('|').append(safe(in.projectId()));
        sb.append('|').append(safe(in.actorUserId()));
        sb.append('|').append(in.action() == null ? "" : in.action());
        sb.append('|').append(in.entityType() == null ? "" : in.entityType());
        sb.append('|').append(safe(in.entityId()));
        sb.append('|').append(nullToEmpty(canonicalJson(in.beforeJson())));
        sb.append('|').append(nullToEmpty(canonicalJson(in.afterJson())));
        sb.append('|').append(canonicalTimestamp(in.createdAt()));
        sb.append('|').append(in.hashFormatVersion());   // L1 — version bound into the digest
        sb.append('|').append(in.prevHash() == null ? "" : in.prevHash());
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is mandatory in every JDK; if missing fail closed.
            throw new IllegalStateException("SHA-256 missing", ex);
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String safe(Object obj) {
        return obj == null ? "" : obj.toString();
    }
}

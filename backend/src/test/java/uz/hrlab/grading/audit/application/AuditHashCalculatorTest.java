package uz.hrlab.grading.audit.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MVP1-E10-1 — locks the canonicalization guarantees that make the writer and
 * the verifier hash IDENTICALLY across a PostgreSQL round-trip. If any of these
 * regress, an intact chain would falsely verify as BROKEN.
 */
@Tag("audit")
class AuditHashCalculatorTest {

    private final AuditHashCalculator calc = new AuditHashCalculator(new ObjectMapper());

    private final UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID tenant = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID actor = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private AuditHashInput input(String beforeJson, String afterJson,
                                 OffsetDateTime createdAt, String prevHash) {
        return input(beforeJson, afterJson, createdAt, prevHash, (short) 2);
    }

    private AuditHashInput input(String beforeJson, String afterJson,
                                 OffsetDateTime createdAt, String prevHash, short version) {
        return new AuditHashInput(id, tenant, null, actor, "TENANT_CREATED",
                "Tenant", tenant, beforeJson, afterJson, createdAt, version, prevHash);
    }

    @Test
    void isDeterministic() {
        OffsetDateTime ts = OffsetDateTime.parse("2026-07-15T10:00:00Z");
        String a = calc.compute(input(null, "{\"a\":1}", ts, null));
        String b = calc.compute(input(null, "{\"a\":1}", ts, null));
        assertThat(a).isEqualTo(b).hasSize(64); // SHA-256 hex
    }

    @Test
    void isIndependentOfJsonKeyOrder() {
        OffsetDateTime ts = OffsetDateTime.parse("2026-07-15T10:00:00Z");
        // Same logical content, different key order + whitespace — as a JSONB
        // round-trip would produce.
        String canonical = calc.compute(input(null, "{\"a\":1,\"b\":2}", ts, null));
        String reordered = calc.compute(input(null, "{ \"b\": 2, \"a\": 1 }", ts, null));
        assertThat(reordered).isEqualTo(canonical);
    }

    @Test
    void isIndependentOfTimestampZoneOffset() {
        // Same instant, two offsets — the JDBC driver may hand either back.
        OffsetDateTime utc = OffsetDateTime.parse("2026-07-15T10:00:00Z");
        OffsetDateTime plus5 = utc.withOffsetSameInstant(ZoneOffset.ofHours(5));
        assertThat(calc.compute(input(null, "{\"a\":1}", plus5, null)))
                .isEqualTo(calc.compute(input(null, "{\"a\":1}", utc, null)));
    }

    @Test
    void truncatesSubMicrosecondPrecision() {
        // Nanos below microsecond resolution are dropped (TIMESTAMPTZ is µs).
        OffsetDateTime micros = OffsetDateTime.parse("2026-07-15T10:00:00.123456Z");
        OffsetDateTime nanos = OffsetDateTime.parse("2026-07-15T10:00:00.123456789Z");
        assertThat(calc.compute(input(null, null, nanos, null)))
                .isEqualTo(calc.compute(input(null, null, micros, null)));
    }

    @Test
    void nullJsonIsHandledAndDistinctFromPresent() {
        OffsetDateTime ts = OffsetDateTime.parse("2026-07-15T10:00:00Z");
        String bothNull = calc.compute(input(null, null, ts, null));
        String afterSet = calc.compute(input(null, "{\"a\":1}", ts, null));
        assertThat(bothNull).isNotBlank().isNotEqualTo(afterSet);
    }

    @Test
    void prevHashChangesTheHash() {
        OffsetDateTime ts = OffsetDateTime.parse("2026-07-15T10:00:00Z");
        String genesis = calc.compute(input(null, "{\"a\":1}", ts, null));
        String linked = calc.compute(input(null, "{\"a\":1}", ts, "abc123"));
        assertThat(linked).isNotEqualTo(genesis);
    }

    // ------------------------------------------------------------ M2 (numbers)

    @Test
    void scientificAndPlainNumbersHashEqual() {
        // The jsonb round-trip returns 1.0E10 as 10000000000; the canonicalizer
        // must map both to the same text so an intact chain does not false-BROKEN.
        OffsetDateTime ts = OffsetDateTime.parse("2026-07-15T10:00:00Z");
        String sci = calc.compute(input(null, "{\"n\":1.0E10}", ts, null));
        String plain = calc.compute(input(null, "{\"n\":10000000000}", ts, null));
        assertThat(sci).isEqualTo(plain);
    }

    @Test
    void decimalAndLargeIntPayloadHashesDeterministically() {
        OffsetDateTime ts = OffsetDateTime.parse("2026-07-15T10:00:00Z");
        String json = "{\"amount\":1234.50,\"big\":12345678901234567890}";
        assertThat(calc.compute(input(null, json, ts, null)))
                .isEqualTo(calc.compute(input(null, json, ts, null)))
                .hasSize(64);
    }

    // -------------------------------------------------------------- L1 (version)

    @Test
    void hashFormatVersionIsBoundIntoTheHash() {
        OffsetDateTime ts = OffsetDateTime.parse("2026-07-15T10:00:00Z");
        String v2 = calc.compute(input(null, "{\"a\":1}", ts, null, (short) 2));
        String v1 = calc.compute(input(null, "{\"a\":1}", ts, null, (short) 1));
        assertThat(v2).isNotEqualTo(v1);
    }

    @Test
    void canonicalJsonSortsKeysForStorage() {
        var node = new ObjectMapper().createObjectNode();
        node.put("b", 2);
        node.put("a", 1);
        assertThat(calc.canonicalJson(node)).isEqualTo("{\"a\":1,\"b\":2}");
        assertThat(calc.canonicalJson((com.fasterxml.jackson.databind.JsonNode) null)).isNull();
    }
}

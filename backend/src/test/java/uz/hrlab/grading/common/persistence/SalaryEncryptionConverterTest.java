package uz.hrlab.grading.common.persistence;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stub-mode round-trip test for {@link SalaryEncryptionConverter}
 * (security-blueprint §8, finding F-10).
 *
 * <p>The full AES-GCM behaviour activates in MVP 3 — this test locks the
 * skeleton: converter is wireable, round-trips a {@link BigDecimal}, embeds
 * the key id + key version in the wire format, and does NOT leak the plain
 * value as the column value.
 */
@Tag("salary")
class SalaryEncryptionConverterTest {

    @Test
    void roundTripsBigDecimalThroughTheStubEnvelope() {
        SalaryEncryptionConverter converter = new SalaryEncryptionConverter("dev-stub", 1);
        BigDecimal plain = new BigDecimal("1234567.89");

        String envelope = converter.convertToDatabaseColumn(plain);
        BigDecimal decoded = converter.convertToEntityAttribute(envelope);

        assertThat(decoded).isEqualByComparingTo(plain);
    }

    @Test
    void envelopeStartsWithMarkerAndEmbedsKeyIdAndVersion() {
        SalaryEncryptionConverter converter = new SalaryEncryptionConverter("dev-stub", 7);
        String envelope = converter.convertToDatabaseColumn(new BigDecimal("999.00"));

        assertThat(envelope).startsWith("ENC0:dev-stub:7:");
    }

    @Test
    void envelopeDoesNotContainPlainValueLiterally() {
        SalaryEncryptionConverter converter = new SalaryEncryptionConverter("dev-stub", 1);
        String envelope = converter.convertToDatabaseColumn(new BigDecimal("4242000.00"));

        // base64 is applied so the literal digits must not appear.
        assertThat(envelope).doesNotContain("4242000.00");
    }

    @Test
    void nullPassesThroughUnchanged() {
        SalaryEncryptionConverter converter = new SalaryEncryptionConverter("dev-stub", 1);
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void unrecognisedEnvelopeFailsClosed() {
        SalaryEncryptionConverter converter = new SalaryEncryptionConverter("dev-stub", 1);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                converter.convertToEntityAttribute("raw-plaintext-not-encrypted"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("envelope");
    }
}

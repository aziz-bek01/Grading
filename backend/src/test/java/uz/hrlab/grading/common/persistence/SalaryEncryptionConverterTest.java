package uz.hrlab.grading.common.persistence;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stub-mode round-trip + guard tests for {@link SalaryEncryptionConverter}
 * (security-blueprint §8, finding F-10; P2-FIX Task 3).
 *
 * <p>The full AES-GCM behaviour activates in MVP 3 — this test locks the
 * skeleton: converter is wireable, round-trips a {@link BigDecimal}, embeds
 * the key id + key version in the wire format, does NOT leak the plain value,
 * AND (Task 3) refuses to persist a reversible base64 stub blob with a
 * {@code -stub} key id outside the local/test profiles.
 */
@Tag("salary")
class SalaryEncryptionConverterTest {

    private static MockEnvironment withProfiles(String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return env;
    }

    /** Local/test profile where stub writes are tolerated. */
    private static MockEnvironment localProfile() {
        return withProfiles("local");
    }

    /** Empty active profiles ⇒ Spring default 'local' ⇒ stub writes allowed. */
    private static MockEnvironment noProfile() {
        return new MockEnvironment();
    }

    /** A deployed, request-serving profile where stub writes must fail closed. */
    private static MockEnvironment prodLikeProfile() {
        return withProfiles("prod");
    }

    @Test
    void roundTripsBigDecimalThroughTheStubEnvelope() {
        SalaryEncryptionConverter converter =
                new SalaryEncryptionConverter("dev-stub", 1, localProfile());
        BigDecimal plain = new BigDecimal("1234567.89");

        String envelope = converter.convertToDatabaseColumn(plain);
        BigDecimal decoded = converter.convertToEntityAttribute(envelope);

        assertThat(decoded).isEqualByComparingTo(plain);
    }

    @Test
    void envelopeStartsWithMarkerAndEmbedsKeyIdAndVersion() {
        SalaryEncryptionConverter converter =
                new SalaryEncryptionConverter("dev-stub", 7, localProfile());
        String envelope = converter.convertToDatabaseColumn(new BigDecimal("999.00"));

        assertThat(envelope).startsWith("ENC0:dev-stub:7:");
    }

    @Test
    void envelopeDoesNotContainPlainValueLiterally() {
        SalaryEncryptionConverter converter =
                new SalaryEncryptionConverter("dev-stub", 1, localProfile());
        String envelope = converter.convertToDatabaseColumn(new BigDecimal("4242000.00"));

        // base64 is applied so the literal digits must not appear.
        assertThat(envelope).doesNotContain("4242000.00");
    }

    @Test
    void nullPassesThroughUnchanged() {
        SalaryEncryptionConverter converter =
                new SalaryEncryptionConverter("dev-stub", 1, localProfile());
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void unrecognisedEnvelopeFailsClosed() {
        SalaryEncryptionConverter converter =
                new SalaryEncryptionConverter("dev-stub", 1, localProfile());

        assertThatThrownBy(() ->
                converter.convertToEntityAttribute("raw-plaintext-not-encrypted"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("envelope");
    }

    // --- P2-FIX (Task 3): stub-key write guard ---

    @Test
    void refusesToPersistStubKeyInDeployedProfileWhenFailOnStubKeyEnabled() {
        SalaryEncryptionConverter converter =
                new SalaryEncryptionConverter("prod-stub", 1, true, prodLikeProfile());

        assertThatThrownBy(() ->
                converter.convertToDatabaseColumn(new BigDecimal("100.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STUB")
                .hasMessageContaining("prod-stub");
    }

    @Test
    void warnsButAllowsStubKeyInDeployedProfileByDefault() {
        // Default posture (fail-on-stub-key=false): an existing deployment still
        // on a placeholder key keeps working (a loud warning is logged) rather
        // than breaking salary writes on upgrade.
        SalaryEncryptionConverter converter =
                new SalaryEncryptionConverter("prod-stub", 1, prodLikeProfile());

        assertThat(converter.convertToDatabaseColumn(new BigDecimal("100.00")))
                .startsWith("ENC0:prod-stub:");
    }

    @Test
    void allowsStubKeyWriteInTestProfile() {
        SalaryEncryptionConverter converter = new SalaryEncryptionConverter(
                "dev-stub", 1, withProfiles("test"));

        assertThat(converter.convertToDatabaseColumn(new BigDecimal("100.00")))
                .startsWith("ENC0:dev-stub:");
    }

    @Test
    void allowsStubKeyWriteWhenNoExplicitProfileDefaultsToLocal() {
        SalaryEncryptionConverter converter =
                new SalaryEncryptionConverter("dev-stub", 1, noProfile());

        assertThat(converter.convertToDatabaseColumn(new BigDecimal("100.00")))
                .startsWith("ENC0:dev-stub:");
    }

    @Test
    void allowsRealKeyWriteInDeployedProfile() {
        // A non '-stub' key id is the MVP 3 KMS-provisioned case: the guard does
        // not fire even in a deployed profile.
        SalaryEncryptionConverter converter =
                new SalaryEncryptionConverter("kms-key-2026", 1, prodLikeProfile());

        assertThat(converter.convertToDatabaseColumn(new BigDecimal("100.00")))
                .startsWith("ENC0:kms-key-2026:");
    }

    @Test
    void decryptStaysEnabledEvenWithStubKeyInDeployedProfile() {
        // Read path is never blocked: a previously-stored value must remain
        // readable regardless of profile.
        SalaryEncryptionConverter writer =
                new SalaryEncryptionConverter("dev-stub", 1, localProfile());
        String envelope = writer.convertToDatabaseColumn(new BigDecimal("555.55"));

        SalaryEncryptionConverter readerInProd =
                new SalaryEncryptionConverter("dev-stub", 1, prodLikeProfile());
        assertThat(readerInProd.convertToEntityAttribute(envelope))
                .isEqualByComparingTo(new BigDecimal("555.55"));
    }
}

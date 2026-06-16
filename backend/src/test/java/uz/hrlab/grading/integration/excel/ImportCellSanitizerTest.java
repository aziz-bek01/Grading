package uz.hrlab.grading.integration.excel;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SECURITY TEST — import-side formula-injection neutralisation
 * (Batch 5, the symmetric half of {@link ExcelFormulaInjectionTest}).
 *
 * <p>Asserts the import sanitiser mirrors {@link SafeCellWriter} exactly:
 * same dangerous-prefix set, same apostrophe fix, idempotent on already-safe
 * values, and non-destructive to legitimate text/number/leading-zero values.
 */
@Tag("security")
class ImportCellSanitizerTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "=cmd|'/C calc'!A1",
            "=SUM(1+1)",
            "=HYPERLINK(\"http://evil\",\"x\")",
            "+1+1",
            "-1-2",
            "@SUM(1+1)",
            "\t=cmd",
            "\r=cmd",
            "\n=cmd"
    })
    void dangerousPrefixesAreNeutralisedOnRead(String payload) {
        String safe = ImportCellSanitizer.sanitizeText(payload);
        assertThat(safe).startsWith("'");
        assertThat(safe.substring(1)).isEqualTo(payload);
        assertThat(ImportCellSanitizer.wouldSanitize(payload)).isTrue();
    }

    @Test
    void leadingSpacesDoNotBypassNeutralisation() {
        assertThat(ImportCellSanitizer.sanitizeText("   =CMD()")).startsWith("'");
    }

    @Test
    void rulesetMirrorsSafeCellWriterExactly() {
        // Every value the export writer sanitises, the import reader must
        // sanitise identically (and vice-versa) — otherwise a round trip is
        // unstable.
        for (String v : new String[] {
                "=x", "+x", "-x", "@x", "\tx", "\rx", "\nx",
                "   =x", "plain", "a+b", "1234", "0042", "-5.5", "", " "}) {
            assertThat(ImportCellSanitizer.sanitizeText(v))
                    .as("import vs export agreement for: %s", v)
                    .isEqualTo(SafeCellWriter.sanitize(v));
        }
    }

    @Test
    void alreadySanitisedValueIsIdempotent() {
        // Sanitising an export-written value (already apostrophe-prefixed) on a
        // re-import must be a no-op — no double prefix on the round trip.
        String once = ImportCellSanitizer.sanitizeText("=cmd|'/C calc'!A1");
        String twice = ImportCellSanitizer.sanitizeText(once);
        assertThat(once).isEqualTo("'=cmd|'/C calc'!A1");
        assertThat(twice).isEqualTo(once);
        assertThat(ImportCellSanitizer.wouldSanitize(once)).isFalse();
    }

    @Test
    void legitimateTextIsPreserved() {
        assertThat(ImportCellSanitizer.sanitizeText("Project Alpha")).isEqualTo("Project Alpha");
        assertThat(ImportCellSanitizer.sanitizeText("Sales & Marketing")).isEqualTo("Sales & Marketing");
        assertThat(ImportCellSanitizer.sanitizeText("a+b")).isEqualTo("a+b"); // operator not at start
        assertThat(ImportCellSanitizer.wouldSanitize("Project Alpha")).isFalse();
    }

    @Test
    void leadingZeroCodesAndPlainNumbersArePreserved() {
        // Leading-zero codes (e.g. employee/cost-centre codes) start with a
        // digit — never a dangerous prefix — so they are untouched.
        assertThat(ImportCellSanitizer.sanitizeText("0042")).isEqualTo("0042");
        assertThat(ImportCellSanitizer.sanitizeText("00998901234567")).isEqualTo("00998901234567");
        assertThat(ImportCellSanitizer.sanitizeText("1234")).isEqualTo("1234");
    }

    @Test
    void nullAndEmptyAreUntouched() {
        assertThat(ImportCellSanitizer.sanitizeText(null)).isNull();
        assertThat(ImportCellSanitizer.sanitizeText("")).isEqualTo("");
    }
}

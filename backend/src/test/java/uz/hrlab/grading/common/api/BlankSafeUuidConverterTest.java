package uz.hrlab.grading.common.api;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit verification of {@link BlankSafeUuidConverter} — the global fix for blank
 * optional UUID request params (e.g. {@code ?projectId=}) crashing as a DB
 * {@code 22P02} -> 409. Runs without the Spring context.
 */
@Tag("validation")
class BlankSafeUuidConverterTest {

    private final BlankSafeUuidConverter converter = new BlankSafeUuidConverter();

    @Test
    void nullInputBecomesNull() {
        assertThat(converter.convert(null)).isNull();
    }

    @Test
    void emptyStringBecomesNull() {
        assertThat(converter.convert("")).isNull();
    }

    @Test
    void whitespaceOnlyBecomesNull() {
        assertThat(converter.convert("   ")).isNull();
    }

    @Test
    void validUuidIsParsed() {
        UUID expected = UUID.fromString("3f2504e0-4f89-41d3-9a0c-0305e82c3301");
        assertThat(converter.convert("3f2504e0-4f89-41d3-9a0c-0305e82c3301"))
                .isEqualTo(expected);
    }

    @Test
    void surroundingWhitespaceIsTrimmedThenParsed() {
        UUID expected = UUID.fromString("3f2504e0-4f89-41d3-9a0c-0305e82c3301");
        assertThat(converter.convert("  3f2504e0-4f89-41d3-9a0c-0305e82c3301  "))
                .isEqualTo(expected);
    }

    @Test
    void malformedNonBlankInputThrowsIllegalArgument() {
        assertThatThrownBy(() -> converter.convert("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

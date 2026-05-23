package uz.hrlab.grading.common.logging;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MaskingPatternLayout} — proves that salary, password,
 * token, and Bearer values are redacted before they hit any appender
 * (security-blueprint §14 LOG-2, finding F-13).
 */
@Tag("salary")
class MaskingPatternLayoutTest {

    private final MaskingPatternLayout layout = new MaskingPatternLayout();

    @Test
    void redactsBearerToken() {
        String input = "Authorization: Bearer eyJhbGciOi.JzdWIiOi.signature-value";
        assertThat(layout.maskAll(input)).isEqualTo("Authorization: Bearer ***");
    }

    @Test
    void redactsSalaryKeyValueInPlainTextLog() {
        String input = "DEBUG  com.example - importing row salary=1234567.89 currency=UZS";
        String masked = layout.maskAll(input);
        assertThat(masked).contains("salary=<masked>");
        assertThat(masked).doesNotContain("1234567.89");
    }

    @Test
    void redactsSalaryFieldInJsonPayload() {
        String input = "raw payload {\"id\":\"abc\",\"monthly_salary\":1234567.89,\"name\":\"x\"}";
        String masked = layout.maskAll(input);
        assertThat(masked).contains("\"monthly_salary\":\"<masked>\"");
        assertThat(masked).doesNotContain("1234567.89");
        // unrelated fields preserved
        assertThat(masked).contains("\"id\":\"abc\"");
        assertThat(masked).contains("\"name\":\"x\"");
    }

    @Test
    void redactsPasswordAndApiKeyJsonFields() {
        String input = "{\"password\":\"hunter2\",\"api_key\":\"sk-live-123\"}";
        String masked = layout.maskAll(input);
        assertThat(masked).contains("\"password\":\"<masked>\"");
        assertThat(masked).contains("\"api_key\":\"<masked>\"");
        assertThat(masked).doesNotContain("hunter2").doesNotContain("sk-live-123");
    }

    @Test
    void leavesNonSensitiveTextUntouched() {
        String input = "User 1234 created project 'Acme Grading 2026'";
        assertThat(layout.maskAll(input)).isEqualTo(input);
    }

    @Test
    void customMaskFromConfigIsApplied() {
        layout.addMask("(?i)CardNumber=\\d+");
        String input = "txn=42 CardNumber=4111111111111111 status=ok";
        String masked = layout.maskAll(input);
        assertThat(masked).doesNotContain("4111111111111111");
    }
}

package uz.hrlab.grading.common.logging;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Logback {@link PatternLayout} that redacts sensitive substrings from every
 * log line (security-blueprint §14 LOG-2, finding F-13).
 *
 * <p>Active patterns (case-insensitive):
 * <ul>
 *   <li>{@code Bearer <token>} → {@code Bearer ***}</li>
 *   <li>{@code "salary"|"compensation"|"fixed_pay"|"variable_pay"|
 *       "total_cash"|"total_compensation"|"monthly_salary": <value>} (in any
 *       structured field) → {@code "<field>":"<masked>"}</li>
 *   <li>{@code password|token|secret|api_key} JSON values → masked</li>
 *   <li>Bare numeric salary patterns following an explicit salary keyword
 *       within a log line (e.g. {@code salary=12345.67}) → masked</li>
 * </ul>
 *
 * <p>Additional regexes can be added via {@code <masks>} elements in
 * {@code logback-spring.xml}:
 * <pre>{@code
 * <layout class="uz.hrlab.grading.common.logging.MaskingPatternLayout">
 *   <pattern>%d %-5level %logger - %msg%n</pattern>
 *   <mask>(?i)customRegex=\\S+</mask>
 * </layout>
 * }</pre>
 */
public class MaskingPatternLayout extends PatternLayout {

    private static final List<Pattern> DEFAULT_MASKS = List.of(
            // Bearer tokens
            Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._\\-]+"),
            // Structured JSON fields with sensitive names
            Pattern.compile("(?i)\"(salary|compensation|fixed_pay|variable_pay|total_cash|" +
                    "total_compensation|monthly_salary|monthly_pay|password|token|api[_-]?key|secret)\"" +
                    "\\s*:\\s*(?:\"[^\"]*\"|[\\-+0-9.eE]+|null)"),
            // Key=value style in plain text logs (e.g. salary=1234.56 or password=hunter2)
            Pattern.compile("(?i)\\b(salary|compensation|password|token|secret|api[_-]?key)\\s*=\\s*\\S+")
    );

    private final List<Pattern> extraMasks = new ArrayList<>();

    /** Called by Logback for each {@code <mask>regex</mask>} element. */
    public void addMask(String regex) {
        if (regex != null && !regex.isBlank()) {
            extraMasks.add(Pattern.compile(regex));
        }
    }

    @Override
    public String doLayout(ILoggingEvent event) {
        String formatted = super.doLayout(event);
        return maskAll(formatted);
    }

    /** Visible for testing — applies all masks to an arbitrary input string. */
    public String maskAll(String message) {
        if (message == null || message.isEmpty()) return message;
        String result = message;
        for (Pattern p : DEFAULT_MASKS) {
            result = applyMask(result, p);
        }
        for (Pattern p : extraMasks) {
            result = applyMask(result, p);
        }
        return result;
    }

    private static String applyMask(String input, Pattern pattern) {
        java.util.regex.Matcher matcher = pattern.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String original = matcher.group();
            String replacement;
            if (original.regionMatches(true, 0, "Bearer", 0, 6)) {
                replacement = "Bearer ***";
            } else if (original.contains("=")) {
                String key = original.substring(0, original.indexOf('=') + 1);
                replacement = key + "<masked>";
            } else {
                // Structured JSON field
                int colon = original.indexOf(':');
                String key = colon > 0 ? original.substring(0, colon + 1) : "\"<masked>\":";
                replacement = key + "\"<masked>\"";
            }
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}

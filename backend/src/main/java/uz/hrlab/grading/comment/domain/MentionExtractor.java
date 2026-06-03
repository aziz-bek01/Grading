package uz.hrlab.grading.comment.domain;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts {@code @[uuid]} mentions from comment bodies. The frontend
 * @-picker produces a marker like {@code @[c0ffee00-0000-0000-0000-...]}
 * so the backend resolves to a stable user-id set without trusting names.
 */
public final class MentionExtractor {

    private static final Pattern MENTION =
            Pattern.compile("@\\[([0-9a-fA-F-]{36})\\]");

    private MentionExtractor() { }

    public static Set<UUID> extract(String body) {
        if (body == null || body.isEmpty()) return Set.of();
        Set<UUID> out = new LinkedHashSet<>();
        Matcher m = MENTION.matcher(body);
        while (m.find()) {
            try {
                out.add(UUID.fromString(m.group(1)));
            } catch (IllegalArgumentException ignored) {
                // Skip malformed markers.
            }
        }
        return out;
    }
}

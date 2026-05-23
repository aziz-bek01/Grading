package uz.hrlab.grading.audit.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Redacts entity snapshots before they are placed in the audit log
 * {@code before_json} / {@code after_json} columns (security-blueprint §9.3 +
 * F-309).
 *
 * <p>Policy:
 * <ul>
 *   <li>Long-text multilingual maps NEVER reproduced verbatim. Each field is
 *       summarized as {@code <field>.<locale>.preview} (first {@value
 *       #PREVIEW_CHARS} chars) + {@code <field>.<locale>.length} (full
 *       length).</li>
 *   <li>Only non-sensitive scalar metadata (id, status, revisionNumber,
 *       positionId, projectId, dates) is emitted as-is.</li>
 *   <li>Salary fields are NEVER accepted by this helper — Phase 3 modules
 *       carry none today, but the policy is enforced by the allowlist nature
 *       of the API: callers explicitly choose which fields to put in the
 *       snapshot map.</li>
 *   <li>{@code reason} strings supplied by users are truncated to the first
 *       {@value #PREVIEW_CHARS} chars (suffix "..." appended when over the
 *       limit).</li>
 * </ul>
 *
 * <p>This helper lives in the {@code audit.application} layer and intentionally
 * carries NO dependency on any other module's JPA / domain classes. Each
 * module's use case is responsible for producing the field map (allowlist) and
 * passing it through {@link #snapshot(Map)} or, for the common multilingual
 * shape, via {@link Builder}.
 */
@Component
public class AuditJsonRedactor {

    /** Long-text preview cap (security-blueprint §9.3). */
    public static final int PREVIEW_CHARS = 100;

    private final ObjectMapper objectMapper;

    public AuditJsonRedactor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Returns the reason truncated to {@value #PREVIEW_CHARS} chars (suffix
     * "..." appended when truncated). {@code null} stays {@code null}.
     */
    public String redactReason(String reason) {
        if (reason == null) return null;
        String trimmed = reason.trim();
        if (trimmed.length() <= PREVIEW_CHARS) return trimmed;
        return trimmed.substring(0, PREVIEW_CHARS) + "...";
    }

    /** Converts the prepared snapshot map into a {@link JsonNode}. */
    public JsonNode snapshot(Map<String, Object> fields) {
        if (fields == null) return null;
        return objectMapper.valueToTree(fields);
    }

    /** Convenience for callers that want an empty-but-not-null JSON object. */
    public ObjectNode emptyObject() { return objectMapper.createObjectNode(); }

    /** Returns a new builder seeded with no fields. */
    public Builder builder() { return new Builder(); }

    /**
     * Fluent builder that helps modules emit the canonical metadata-only
     * snapshot shape (id, status, etc.) and the long-text-preview shape for
     * multilingual maps.
     */
    public final class Builder {
        private final Map<String, Object> root = new LinkedHashMap<>();

        private Builder() { }

        public Builder put(String key, Object value) {
            root.put(key, value == null ? null : value.toString());
            return this;
        }

        public Builder putRaw(String key, Object value) {
            root.put(key, value);
            return this;
        }

        /**
         * Adds field.preview (first {@value #PREVIEW_CHARS} chars) + length
         * for each locale entry.
         */
        public Builder addI18nPreviews(String fieldName, Map<String, String> i18n) {
            if (i18n == null || i18n.isEmpty()) return this;
            for (Map.Entry<String, String> e : i18n.entrySet()) {
                String loc = e.getKey();
                String val = e.getValue() == null ? "" : e.getValue();
                root.put(fieldName + "." + loc + ".preview",
                        val.length() <= PREVIEW_CHARS ? val
                                : val.substring(0, PREVIEW_CHARS) + "...");
                root.put(fieldName + "." + loc + ".length", val.length());
            }
            return this;
        }

        public JsonNode build() {
            return objectMapper.valueToTree(root);
        }
    }
}

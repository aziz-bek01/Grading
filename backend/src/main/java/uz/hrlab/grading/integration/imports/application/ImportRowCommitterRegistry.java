package uz.hrlab.grading.integration.imports.application;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registers all available {@link ImportRowCommitter} beans and exposes
 * lookup by template code (PO-11). Spring auto-collects every committer
 * from the application context.
 *
 * <p>Templates without a committer in MVP 2 Phase 3
 * ({@code JOB_PROFILE_V1}, {@code METHODOLOGY_FACTORS_V1}) are NOT silently
 * ignored — the caller must either look up via {@link #findCommitter(String)}
 * or invoke {@link #requireCommitter(String)} which throws
 * {@link UnsupportedOperationException} so the failure is explicit.
 */
@Component
public class ImportRowCommitterRegistry {

    private final Map<String, ImportRowCommitter> byTemplateCode = new LinkedHashMap<>();

    public ImportRowCommitterRegistry(List<ImportRowCommitter> committers) {
        for (ImportRowCommitter c : committers) {
            byTemplateCode.put(c.templateCode(), c);
        }
    }

    public Optional<ImportRowCommitter> findCommitter(String templateCode) {
        return Optional.ofNullable(byTemplateCode.get(templateCode));
    }

    public ImportRowCommitter requireCommitter(String templateCode) {
        ImportRowCommitter c = byTemplateCode.get(templateCode);
        if (c == null) {
            throw new UnsupportedOperationException(
                    "No commit DAO registered for template '" + templateCode
                            + "' — deferred to a future MVP 2 phase");
        }
        return c;
    }
}

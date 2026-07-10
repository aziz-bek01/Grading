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
 * <p>A template without a registered committer is NOT silently ignored — the
 * caller must either look up via {@link #findCommitter(String)} or invoke
 * {@link #requireCommitter(String)} which throws
 * {@link UnsupportedOperationException} so the failure is explicit. (All MVP 2
 * Phase 2 shipping templates — ORG_STRUCTURE_V1, POSITION_CATALOG_V1,
 * JOB_PROFILE_V1, METHODOLOGY_FACTORS_V1, GRADE_BANDS_V1 — now have committers.)
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

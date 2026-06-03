package uz.hrlab.grading.integration.validation;

import uz.hrlab.grading.integration.imports.domain.ImportErrorLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Aggregate result of running the 5-level pipeline against a parsed file. */
public final class ValidationResult {

    private final List<ValidationError> findings = new ArrayList<>();

    public ValidationResult add(ValidationError error) {
        findings.add(error);
        return this;
    }

    public List<ValidationError> findings() {
        return Collections.unmodifiableList(findings);
    }

    public long countByLevel(ImportErrorLevel level) {
        return findings.stream().filter(f -> f.level() == level).count();
    }

    public boolean hasBlockers() {
        return findings.stream().anyMatch(f -> f.level() == ImportErrorLevel.BLOCKER);
    }

    public boolean hasErrors() {
        return findings.stream().anyMatch(f -> f.level() == ImportErrorLevel.ERROR);
    }

    public boolean hasOnlyWarningsOrInfo() {
        return !hasBlockers() && !hasErrors();
    }
}

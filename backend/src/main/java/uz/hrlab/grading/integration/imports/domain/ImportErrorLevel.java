package uz.hrlab.grading.integration.imports.domain;

/** Severity of an import error (integration-blueprint §12.1). */
public enum ImportErrorLevel {
    /** Stops the entire batch. Commit disabled. */
    BLOCKER,
    /** Row excluded from commit. Other rows may proceed. */
    ERROR,
    /** Row imported but flagged in summary. */
    WARNING,
    /** Informational, no action. */
    INFO
}

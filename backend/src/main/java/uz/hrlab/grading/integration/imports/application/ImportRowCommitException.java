package uz.hrlab.grading.integration.imports.application;

import uz.hrlab.grading.common.exception.BaseDomainException;

/**
 * Thrown by an {@link ImportRowCommitter} when a parsed row cannot be
 * materialized (FK miss, duplicate code, domain validation failure).
 *
 * <p>The caller (CommitImportBatchUseCase) converts the exception to an
 * ImportError row + flags the ImportBatchRow as FAILED. Other rows in the
 * batch continue.
 */
public class ImportRowCommitException extends BaseDomainException {

    public ImportRowCommitException(String code, String message) {
        super(code, message);
    }
}

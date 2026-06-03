package uz.hrlab.grading.integration.imports.application;

import java.util.Map;
import java.util.UUID;

/**
 * Strategy contract for materializing one parsed Excel row into a core
 * domain entity (PO-11).
 *
 * <p>Implementations are template-specific (one per
 * {@link uz.hrlab.grading.integration.imports.domain.ImportTemplateCode}).
 * Multi-tenant + ABAC + audit responsibilities:
 * <ul>
 *   <li>tenantId comes from {@code ctx.tenantId()} — never from row data.</li>
 *   <li>Cross-FK consistency (e.g. parent department exists in this tenant +
 *       project) must be re-validated at commit time.</li>
 *   <li>Implementations emit per-entity audit events (DEPARTMENT_CREATED,
 *       POSITION_CREATED, GRADE_BAND_UPSERTED, …) — the batch-level audit is
 *       still emitted by {@link CommitImportBatchUseCase}.</li>
 *   <li>Committer must throw {@link ImportRowCommitException} on validation /
 *       FK / domain failure; the caller will translate to ImportError +
 *       row status FAILED.</li>
 * </ul>
 */
public interface ImportRowCommitter {

    /**
     * @return the {@link uz.hrlab.grading.integration.imports.domain.ImportTemplateCode}
     *         this committer handles.
     */
    String templateCode();

    /**
     * Commit a single parsed row.
     *
     * @param parsedRow header-keyed Map (header→value) — never null, may be empty.
     * @param ctx commit context — tenant, project, batch metadata.
     * @return a result record carrying targetEntityType + entityId.
     * @throws ImportRowCommitException on any commit-time validation failure
     *         or FK resolution miss.
     */
    CommitResult commit(Map<String, String> parsedRow, ImportRowCommitContext ctx);

    /** Outcome of a successful row commit. */
    record CommitResult(String targetEntityType, UUID entityId, String auditAction) { }
}

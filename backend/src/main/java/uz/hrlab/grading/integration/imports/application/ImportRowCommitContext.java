package uz.hrlab.grading.integration.imports.application;

import java.util.UUID;

/**
 * Per-batch commit context handed to every {@link ImportRowCommitter}.
 *
 * <p>tenantId is the AUTHENTICATED tenant (from {@link
 * uz.hrlab.grading.tenancy.application.TenantContextHolder}); never from
 * the row's {@code tenant_id} column — the row never carries one.
 */
public record ImportRowCommitContext(
        UUID tenantId,
        UUID projectId,
        UUID actorUserId,
        UUID importBatchId,
        String traceId
) { }

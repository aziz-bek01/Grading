package uz.hrlab.grading.access.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * D-1 — one row in the Audit Reader API response
 * ({@code GET /api/v1/audit}). Mirrors {@code public.system_audit_log} fields
 * the frontend timeline widget needs.
 *
 * <p>{@code hashCurrent} is surfaced so the frontend integrity widget can
 * compare against the next row's {@code hashPrev} and visually flag tampering
 * (security-blueprint §9.2). {@code beforeJson}/{@code afterJson} payloads are
 * intentionally OMITTED at MVP 1 — they may contain redacted-but-still-sensitive
 * domain fields; exposing them needs its own permission gate, tracked as a
 * Sprint E backlog item alongside {@code AUDIT_EXPORT_REQUESTED} (CSV).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditEventResponse(
        UUID id,
        String action,
        UUID tenantId,
        UUID projectId,
        UUID actorUserId,
        String entityType,
        UUID entityId,
        String reason,
        String ipAddress,
        String userAgent,
        String correlationId,
        OffsetDateTime createdAt,
        String hashCurrent
) {
}

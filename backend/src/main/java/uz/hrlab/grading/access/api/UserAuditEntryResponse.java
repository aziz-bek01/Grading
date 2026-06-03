package uz.hrlab.grading.access.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One audit row surfaced by {@code GET /api/v1/users/{id}/audit}.
 *
 * <p>{@code hashCurrent} is returned so a frontend integrity widget can
 * compare against the next row's {@code hashPrev} and visually highlight
 * tampering. {@code actor} ID is intentionally omitted at MVP 1 — exposing
 * who made the change requires its own permission gate (audit-blueprint §3),
 * tracked as a backlog item.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserAuditEntryResponse(
        UUID id,
        String action,
        UUID tenantId,
        OffsetDateTime createdAt,
        String hashCurrent
) {
}

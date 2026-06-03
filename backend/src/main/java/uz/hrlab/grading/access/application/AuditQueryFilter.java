package uz.hrlab.grading.access.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * D-1 — value object carrying the optional filter axes for
 * {@code GET /api/v1/audit}. Lives in {@code application} (NOT in
 * {@code ..api..}) so the ArchUnit rule
 * {@code queryFilterDtosMustNotDeclareTenantIdField} (architecture §13 /
 * security-blueprint §13) does not apply — the field is permitted here
 * because non-super-admins always have it OVERWRITTEN by the policy with
 * their own active tenant; only HRLab Super Admin keeps the value verbatim
 * (cross-tenant read).
 *
 * <p>All fields nullable: a null skips the predicate.
 */
public record AuditQueryFilter(
        UUID tenantId,
        UUID actorUserId,
        String action,
        String entityType,
        UUID entityId,
        OffsetDateTime from,
        OffsetDateTime to,
        int page,
        int size
) {
}

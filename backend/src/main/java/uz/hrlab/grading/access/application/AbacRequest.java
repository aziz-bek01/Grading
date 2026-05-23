package uz.hrlab.grading.access.application;

import uz.hrlab.grading.tenancy.application.TenantContext;

import java.util.UUID;

/**
 * Input to a scope policy. Carries the active {@link TenantContext}, the
 * entity type/id, and optional scoping ids (projectId, departmentId, status).
 *
 * <p>Records are used so policies are easy to construct in tests.
 */
public record AbacRequest(
        TenantContext context,
        String entityType,
        UUID entityId,
        UUID projectId,
        UUID departmentId,
        String status
) {
    public static AbacRequest of(TenantContext ctx, String type, UUID id, UUID projectId,
                                 UUID departmentId, String status) {
        return new AbacRequest(ctx, type, id, projectId, departmentId, status);
    }
}

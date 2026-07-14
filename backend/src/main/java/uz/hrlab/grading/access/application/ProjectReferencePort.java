package uz.hrlab.grading.access.application;

import java.util.UUID;

/**
 * Outbound port (hexagonal boundary) the access module uses to consult the
 * project module WITHOUT importing it — the static dependency arrow stays
 * project → access (access is the low-level authorization kernel that the
 * project/methodology/integration/reporting/evaluation modules all depend on).
 * The implementation ({@code project.infrastructure.AccessProjectReferenceAdapter})
 * lives in the project module and is injected by Spring.
 *
 * <p>Used by {@link SetUserProjectAssignmentsUseCase} to validate that every
 * requested project belongs to the active tenant BEFORE writing any assignment
 * row (anti-BOLA). The single lookup is tenant-bound; the adapter delegates
 * verbatim to the tenant-aware {@code ProjectRepository.existsByIdAndTenantId},
 * so behaviour is byte-for-byte identical — this port only inverts the
 * compile-time arrow.
 */
public interface ProjectReferencePort {

    /**
     * Tenant-bound existence check: true when {@code projectId} exists AND
     * belongs to {@code tenantId}. Never a bare {@code findById}
     * (security-blueprint §5.2, F-01).
     */
    boolean existsInTenant(UUID projectId, UUID tenantId);
}

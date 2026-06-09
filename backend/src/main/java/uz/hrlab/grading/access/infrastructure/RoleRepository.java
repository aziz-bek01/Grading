package uz.hrlab.grading.access.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<RoleJpaEntity, UUID> {
    Optional<RoleJpaEntity> findByCode(String code);

    /**
     * Ordered role catalog for {@code GET /api/v1/roles} (slice E1).
     *
     * <p>Today every seeded role is a system role (no {@code is_system} /
     * {@code tenant_id} columns yet — those arrive in slice E3). The query
     * therefore returns ALL roles so the catalog is forward-compatible: once
     * tenant-scoped custom roles exist this finder is replaced by a
     * {@code WHERE is_system = true OR tenant_id = :tenantId} variant without
     * touching the use case's response mapping.
     *
     * <p>Ordered by {@code scope} (PLATFORM before TENANT) then {@code code}
     * for a stable, deterministic listing.
     */
    List<RoleJpaEntity> findAllByOrderByScopeAscCodeAsc();
}

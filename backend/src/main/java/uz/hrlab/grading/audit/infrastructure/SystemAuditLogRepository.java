package uz.hrlab.grading.audit.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@code public.system_audit_log}.
 *
 * <p>Intentionally extends {@link Repository} (not {@link JpaRepository}) so
 * standard delete/update methods are NOT inherited — only the explicit
 * insert / read operations declared here are allowed (security-blueprint
 * §20.1 audit hash chaining + §9.1 append-only). The companion
 * {@code AuditAppendOnlyTest} verifies this contract.
 */
public interface SystemAuditLogRepository extends Repository<SystemAuditLogJpaEntity, UUID> {

    /** Insert is the ONLY mutating operation exposed. */
    <S extends SystemAuditLogJpaEntity> S save(S entity);

    Optional<SystemAuditLogJpaEntity> findById(UUID id);

    long count();

    @Query("select sa from SystemAuditLogJpaEntity sa where sa.tenantId = :tenantId " +
            "order by sa.createdAt desc")
    java.util.List<SystemAuditLogJpaEntity> findByTenantIdOrderByCreatedAtDesc(@Param("tenantId") UUID tenantId);

    /** Last hash for the chain (per tenant or null tenant for platform events). */
    @Query("select sa.hashCurrent from SystemAuditLogJpaEntity sa " +
            "where (:tenantId is null and sa.tenantId is null) or sa.tenantId = :tenantId " +
            "order by sa.createdAt desc limit 1")
    Optional<String> findLastHash(@Param("tenantId") UUID tenantId);
}

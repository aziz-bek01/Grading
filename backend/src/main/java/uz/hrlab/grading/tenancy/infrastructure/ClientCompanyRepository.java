package uz.hrlab.grading.tenancy.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Client companies are tenant-scoped data: every row carries a
 * {@code tenant_id} and the only legal access path is tenant-aware
 * (security-blueprint §5.2, finding F-01).
 *
 * <p>Extends {@link TenantAwareRepository} so the BOLA-prone
 * {@code findById}/{@code findAll}/{@code delete} methods inherited from
 * {@code JpaRepository} are NOT available. ArchUnit enforces the inverse: no
 * tenant-data repository may extend {@code JpaRepository}.
 */
public interface ClientCompanyRepository
        extends TenantAwareRepository<ClientCompanyJpaEntity, UUID> {

    Optional<ClientCompanyJpaEntity> findByTenantId(UUID tenantId);

    boolean existsByTenantId(UUID tenantId);

    /**
     * Cross-tenant catalog used by {@code GET /api/v1/admin/clients}. Only
     * HRLAB_SUPER_ADMIN reaches this — other roles are routed through
     * {@link #searchInTenantIds(Collection, String, Pageable)}.
     *
     * <p>{@code search} matches case-insensitively against {@code legal_name},
     * {@code brand_name} and {@code industry}.
     *
     * <p><b>Why two physical {@code @Query} methods + a {@code default} router:</b>
     * the PostgreSQL JDBC driver cannot infer the SQL type of an untyped
     * {@code NULL} bound into {@code lower(?)} / {@code concat('%', ?, '%')}
     * — it defaults to {@code bytea} and the server rejects with
     * {@code function lower(bytea) does not exist}. Branching at the
     * repository level keeps the search term out of the SQL entirely when it
     * is absent, so no untyped {@code NULL} is ever bound.
     */
    default Page<ClientCompanyJpaEntity> searchAll(String search, Pageable pageable) {
        if (search == null || search.isBlank()) {
            return searchAllNoText(pageable);
        }
        return searchAllWithText(search, pageable);
    }

    @Query("select c from ClientCompanyJpaEntity c")
    Page<ClientCompanyJpaEntity> searchAllNoText(Pageable pageable);

    @Query("""
            select c from ClientCompanyJpaEntity c
            where lower(c.legalName) like lower(concat('%', :search, '%'))
               or lower(c.brandName) like lower(concat('%', :search, '%'))
               or lower(c.industry)  like lower(concat('%', :search, '%'))
            """)
    Page<ClientCompanyJpaEntity> searchAllWithText(@Param("search") String search, Pageable pageable);

    /**
     * Non-super-admin scope: only client_companies whose tenant is in the
     * caller's membership set. Same null-safe branching pattern as
     * {@link #searchAll(String, Pageable)} — see that method's javadoc for
     * the {@code lower(bytea)} rationale.
     */
    default Page<ClientCompanyJpaEntity> searchInTenantIds(Collection<UUID> tenantIds,
                                                           String search,
                                                           Pageable pageable) {
        if (search == null || search.isBlank()) {
            return searchInTenantIdsNoText(tenantIds, pageable);
        }
        return searchInTenantIdsWithText(tenantIds, search, pageable);
    }

    @Query("""
            select c from ClientCompanyJpaEntity c
            where c.tenantId in :tenantIds
            """)
    Page<ClientCompanyJpaEntity> searchInTenantIdsNoText(
            @Param("tenantIds") Collection<UUID> tenantIds,
            Pageable pageable);

    @Query("""
            select c from ClientCompanyJpaEntity c
            where c.tenantId in :tenantIds
              and (lower(c.legalName) like lower(concat('%', :search, '%'))
                   or lower(c.brandName) like lower(concat('%', :search, '%'))
                   or lower(c.industry)  like lower(concat('%', :search, '%')))
            """)
    Page<ClientCompanyJpaEntity> searchInTenantIdsWithText(
            @Param("tenantIds") Collection<UUID> tenantIds,
            @Param("search") String search,
            Pageable pageable);

    List<ClientCompanyJpaEntity> findAllByTenantIdIn(Collection<UUID> tenantIds);

    /**
     * Admin-only id lookup — caller passes the membership set so this is
     * still tenant-bound (no BOLA). Super Admin passes a sentinel set of
     * "all tenants resolved via {@link TenantRepository#findAll()}" which
     * the use case turns into a plain {@code id} match using
     * {@link #findByIdAndTenantIdIn(UUID, Collection)} with a non-empty
     * collection.
     */
    Optional<ClientCompanyJpaEntity> findByIdAndTenantIdIn(UUID id,
                                                           Collection<UUID> tenantIds);

    /**
     * Super-admin id lookup — bypasses tenant scoping. Returns empty
     * Optional when the id is unknown. Must NEVER be called outside the
     * admin namespace; callers MUST verify HRLAB_SUPER_ADMIN before invoking
     * (architecture §13.1 — admin endpoints are the only allowed surface
     * for unscoped lookups).
     */
    @org.springframework.data.jpa.repository.Query("""
            select c from ClientCompanyJpaEntity c where c.id = :id
            """)
    Optional<ClientCompanyJpaEntity> findByIdForSuperAdmin(
            @org.springframework.data.repository.query.Param("id") UUID id);
}

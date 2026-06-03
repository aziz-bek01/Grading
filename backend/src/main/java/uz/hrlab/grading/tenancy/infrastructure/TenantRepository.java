package uz.hrlab.grading.tenancy.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.hrlab.grading.tenancy.domain.TenantStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenants are control-plane data — repository may expose {@code findById}.
 * Tenant business-data repositories MUST NOT (see security blueprint §5.2).
 */
public interface TenantRepository extends JpaRepository<TenantJpaEntity, UUID> {

    Optional<TenantJpaEntity> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /**
     * Paged tenant catalog used by {@code GET /api/v1/admin/tenants}. All
     * filters are {@code null}-safe; {@code search} matches case-insensitively
     * against {@code slug} and {@code display_name}.
     *
     * <p>SECURITY: cross-tenant listing — only HRLAB_SUPER_ADMIN reaches this
     * method. Non-super-admins are scoped via {@link #searchInIds}.
     *
     * <p><b>{@code lower(bytea)} guard:</b> the search term is branched at the
     * repository level (default dispatcher → two physical {@code @Query}
     * methods) so PostgreSQL's JDBC driver never binds an untyped
     * {@code NULL} into a {@code lower(?)} call. See
     * {@code ClientCompanyRepository.searchAll} for the full rationale.
     */
    default Page<TenantJpaEntity> search(String search, TenantStatus status, Pageable pageable) {
        if (search == null || search.isBlank()) {
            return searchNoText(status, pageable);
        }
        return searchWithText(search, status, pageable);
    }

    @Query("""
            select t from TenantJpaEntity t
            where (:status is null or t.status = :status)
            """)
    Page<TenantJpaEntity> searchNoText(@Param("status") TenantStatus status, Pageable pageable);

    @Query("""
            select t from TenantJpaEntity t
            where (:status is null or t.status = :status)
              and (lower(t.slug) like lower(concat('%', :search, '%'))
                   or lower(t.displayName) like lower(concat('%', :search, '%')))
            """)
    Page<TenantJpaEntity> searchWithText(@Param("search") String search,
                                         @Param("status") TenantStatus status,
                                         Pageable pageable);

    /**
     * Non-super-admin tenant catalog: only tenants the caller has a membership
     * in are returned. {@code ids} is the resolved set from
     * {@code TenantContext.tenantMemberships()}.
     */
    default Page<TenantJpaEntity> searchInIds(Collection<UUID> ids,
                                              String search,
                                              TenantStatus status,
                                              Pageable pageable) {
        if (search == null || search.isBlank()) {
            return searchInIdsNoText(ids, status, pageable);
        }
        return searchInIdsWithText(ids, search, status, pageable);
    }

    @Query("""
            select t from TenantJpaEntity t
            where t.id in :ids
              and (:status is null or t.status = :status)
            """)
    Page<TenantJpaEntity> searchInIdsNoText(@Param("ids") Collection<UUID> ids,
                                            @Param("status") TenantStatus status,
                                            Pageable pageable);

    @Query("""
            select t from TenantJpaEntity t
            where t.id in :ids
              and (:status is null or t.status = :status)
              and (lower(t.slug) like lower(concat('%', :search, '%'))
                   or lower(t.displayName) like lower(concat('%', :search, '%')))
            """)
    Page<TenantJpaEntity> searchInIdsWithText(@Param("ids") Collection<UUID> ids,
                                              @Param("search") String search,
                                              @Param("status") TenantStatus status,
                                              Pageable pageable);

    List<TenantJpaEntity> findAllByIdIn(Collection<UUID> ids);
}

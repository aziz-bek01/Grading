package uz.hrlab.grading.common.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Base repository for tenant-scoped business data
 * (security-blueprint §5.2, finding F-01, defect D-001).
 *
 * <p>Intentionally extends Spring Data's bare {@link Repository} (not
 * {@code JpaRepository}) so the BOLA-prone {@code findById}/{@code findAll}/
 * {@code delete} methods are NOT inherited. Tenant-scoped repositories MUST
 * extend this interface so the only legal access paths are tenant-aware.
 *
 * <p>Methods exposed:
 * <ul>
 *   <li>{@link #findByIdAndTenantId(UUID, UUID)} — canonical anti-BOLA lookup</li>
 *   <li>{@link #findAllByTenantId(UUID, Pageable)} — tenant-scoped paged list</li>
 *   <li>{@link #existsByIdAndTenantId(UUID, UUID)} — existence check (still
 *       tenant-bound)</li>
 *   <li>{@link #save(Object)} — insert/update (the application service is
 *       responsible for setting {@code tenant_id} from the security context
 *       on every write)</li>
 *   <li>{@link #count()} — exposed only for diagnostics; concrete repos may
 *       narrow to {@code countByTenantId} as needed.</li>
 * </ul>
 *
 * <p><b>Hard rule</b>: do NOT declare {@code findById(ID)} or {@code findAll()}
 * on subtypes — the ArchUnit suite enforces this. If you need to fetch a
 * single record by id, use {@code findByIdAndTenantId(id, tenantId)}.
 *
 * @param <T>  JPA entity type
 * @param <ID> primary key type (typically {@link UUID})
 */
@NoRepositoryBean
public interface TenantAwareRepository<T, ID> extends Repository<T, ID> {

    /**
     * Canonical tenant-aware lookup. Returns {@link Optional#empty()} when the
     * id either does not exist OR belongs to a different tenant (the caller
     * cannot distinguish these cases — that is by design, per security-blueprint
     * §11 / 404-for-cross-tenant-probing).
     */
    Optional<T> findByIdAndTenantId(ID id, UUID tenantId);

    /** Page over all rows belonging to the given tenant. */
    Page<T> findAllByTenantId(UUID tenantId, Pageable pageable);

    /** Tenant-bound existence check. */
    boolean existsByIdAndTenantId(ID id, UUID tenantId);

    /**
     * Save (insert or update). Callers MUST ensure the entity's tenant_id is
     * set from the active {@code TenantContext} on first persist — never from
     * the request body.
     */
    <S extends T> S save(S entity);

    /** Total row count (diagnostic only; concrete repos may shadow with countByTenantId). */
    long count();
}

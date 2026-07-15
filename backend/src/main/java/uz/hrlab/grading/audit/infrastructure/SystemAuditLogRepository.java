package uz.hrlab.grading.audit.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
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
 *
 * <p><b>D-5 note:</b> the entity has a composite PK {@code (id, created_at)}
 * because the table is RANGE-partitioned (006-audit-logs-partitioning.yaml).
 * Single-arg {@code findById(UUID)} no longer matches the entity id type and
 * has been replaced by {@link #findFirstByIdOrderByCreatedAtDesc(UUID)} —
 * used only by tests that need to locate a row by its surrogate id when the
 * write {@code created_at} is unknown.
 */
public interface SystemAuditLogRepository extends Repository<SystemAuditLogJpaEntity, SystemAuditLogId> {

    /** Insert is the ONLY mutating operation exposed. */
    <S extends SystemAuditLogJpaEntity> S save(S entity);

    /**
     * Locate the audit row matching {@code id}. Since the PK is composite and
     * a surrogate id is unique by construction (UUID v4), at most ONE row can
     * exist; the {@code ORDER BY created_at DESC} is purely a defensive
     * tie-break in case of an extreme clock skew that could place duplicate
     * surrogate ids into the same row insert (must never happen in production
     * but keeps the API total).
     */
    @Query("select sa from SystemAuditLogJpaEntity sa where sa.id = :id " +
            "order by sa.createdAt desc limit 1")
    Optional<SystemAuditLogJpaEntity> findFirstByIdOrderByCreatedAtDesc(@Param("id") UUID id);

    long count();

    @Query("select sa from SystemAuditLogJpaEntity sa where sa.tenantId = :tenantId " +
            "order by sa.createdAt desc")
    java.util.List<SystemAuditLogJpaEntity> findByTenantIdOrderByCreatedAtDesc(@Param("tenantId") UUID tenantId);

    /**
     * PERF — bounded "last activity" probe. The dashboard portfolio widget and the
     * admin tenant-stats endpoint only need the SINGLE newest audit row's
     * {@code created_at}; the unbounded {@link #findByTenantIdOrderByCreatedAtDesc}
     * materialised the ENTIRE tenant audit feed into memory just to read the head.
     * This pins {@code limit 1} so the (tenant_id, created_at DESC) index returns
     * exactly one row. Same ordering as the list variant → identical head element.
     */
    @Query("select sa from SystemAuditLogJpaEntity sa where sa.tenantId = :tenantId " +
            "order by sa.createdAt desc limit 1")
    Optional<SystemAuditLogJpaEntity> findFirstByTenantIdOrderByCreatedAtDesc(@Param("tenantId") UUID tenantId);

    /**
     * Last hash for the chain (per tenant or null tenant for platform events).
     *
     * <p><b>M1 (deterministic tiebreak):</b> ordered by {@code created_at DESC,
     * id DESC}. {@code created_at} is now UTC/microsecond-truncated, so two
     * appends in the same microsecond are possible; without a stable secondary
     * key the writer could read the wrong predecessor under the advisory lock and
     * FORK the chain. The {@code id} tiebreak makes "the newest row" a total,
     * deterministic order that the verifier's link walk agrees with.
     */
    @Query("select sa.hashCurrent from SystemAuditLogJpaEntity sa " +
            "where (:tenantId is null and sa.tenantId is null) or sa.tenantId = :tenantId " +
            "order by sa.createdAt desc, sa.id desc limit 1")
    Optional<String> findLastHash(@Param("tenantId") UUID tenantId);

    /**
     * MVP1-E10-1 — audit hash-chain integrity verifier, PASS 1 (chain-order
     * reconstruction). Returns a LIGHTWEIGHT projection (NO payload) of one
     * keyset page of a tenant's chain. The verifier loads the whole bounded chain
     * this way to build the {@code hash_prev → hash_current} link map — chain
     * order is derived from the LINKS, not wall-clock — then fetches payloads a
     * page at a time in pass 2, so payload memory stays O(page).
     *
     * <p>The {@code created_at ASC, id ASC} order is used ONLY to page the load
     * deterministically and to pick a stable "earliest" offender for reports; the
     * authoritative sequence is the link walk, so same-microsecond ties and clock
     * backsteps cannot misorder the verification.
     *
     * <p><b>Tenant isolation:</b> {@code system_audit_log} is a control-plane
     * table with a nullable {@code tenant_id} and NO row-level security
     * (database-blueprint §15.3), so the explicit {@code sa.tenantId = :tenantId}
     * predicate is the isolation boundary — identical to the Audit Reader API
     * ({@code ListAuditEventsQuery}). The caller always supplies the tenant id
     * from {@code TenantContext}, never from request input.
     *
     * <p><b>Bounds are always non-null</b> ({@code fromInclusive}/{@code
     * toInclusive} windowing + {@code cursor*} keyset) — never binding a NULL to
     * a {@code TIMESTAMPTZ} comparison side-steps PostgreSQL's "could not
     * determine data type of parameter" on untyped nulls.
     */
    @Query("""
            select new uz.hrlab.grading.audit.infrastructure.AuditChainNode(
                       sa.id, sa.hashPrev, sa.hashCurrent, sa.hashFormatVersion, sa.createdAt)
            from SystemAuditLogJpaEntity sa
            where sa.tenantId = :tenantId
              and sa.createdAt >= :fromInclusive
              and sa.createdAt <= :toInclusive
              and (sa.createdAt > :cursorCreatedAt
                   or (sa.createdAt = :cursorCreatedAt and sa.id > :cursorId))
            order by sa.createdAt asc, sa.id asc
            """)
    List<AuditChainNode> findChainNodes(
            @Param("tenantId")        UUID tenantId,
            @Param("fromInclusive")   OffsetDateTime fromInclusive,
            @Param("toInclusive")     OffsetDateTime toInclusive,
            @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
            @Param("cursorId")        UUID cursorId,
            Pageable pageable);

    /**
     * MVP1-E10-1 — verifier PASS 2. Full rows (incl. {@code before_json}/{@code
     * after_json}) for a page of ids in the authoritative link order, so content
     * recompute reads payload memory O(page). Tenant-scoped (defence in depth).
     */
    @Query("""
            select sa from SystemAuditLogJpaEntity sa
            where sa.tenantId = :tenantId
              and sa.id in :ids
            """)
    List<SystemAuditLogJpaEntity> findPayloadsByIds(
            @Param("tenantId") UUID tenantId,
            @Param("ids")      Collection<UUID> ids);

    /**
     * MVP1-E10-1 — total rows in a tenant's chain within {@code [fromInclusive,
     * toInclusive]}. Used by the verifier to report {@code chain_length} and to
     * decide whether a run was truncated by the max-rows cap. Same non-null
     * bound rationale as {@link #findChainNodes}.
     */
    @Query("""
            select count(sa) from SystemAuditLogJpaEntity sa
            where sa.tenantId = :tenantId
              and sa.createdAt >= :fromInclusive
              and sa.createdAt <= :toInclusive
            """)
    long countChain(
            @Param("tenantId")      UUID tenantId,
            @Param("fromInclusive") OffsetDateTime fromInclusive,
            @Param("toInclusive")   OffsetDateTime toInclusive);

    /**
     * P2-FIX (Task 4) — serialize hash-chain appends WITHIN a tenant.
     *
     * <p>The append is read-then-write ({@code findLastHash} → compute
     * {@code hash_current} → {@code save}). Two concurrent audited actions in the
     * same tenant could otherwise read the same {@code prevHash} and both chain
     * off it, forking/corrupting the chain. Taking a per-tenant PostgreSQL
     * transaction-scoped advisory lock at the START of the REQUIRES_NEW audit
     * transaction makes appends serialize per tenant; the lock auto-releases on
     * commit/rollback. Different tenants hash to different keys and never block
     * each other. No schema change and no impact on append-only behavior.
     *
     * <p>{@code :lockKey} is the tenant id text (or a fixed sentinel for the
     * null-tenant control-plane chain) so the same chain always maps to the same
     * advisory-lock slot. {@code hashtext} yields the required {@code int4} key.
     */
    @Query(value = "SELECT pg_advisory_xact_lock(hashtext(:lockKey))", nativeQuery = true)
    void acquireTenantAuditChainLock(@Param("lockKey") String lockKey);

    /**
     * D-1 — page-able cross-axis filter for the Audit Reader API
     * (GET /api/v1/audit). Every filter parameter is optional; null skips
     * that predicate. The tenant scope check happens in the application
     * layer ({@code ListAuditEventsQuery}) — non-super-admins always pass
     * their own active tenant id and never see null-tenant control-plane
     * rows. {@code Pageable} sort is ignored: rows are always returned
     * newest-first via the explicit JPQL {@code order by}.
     *
     * <p><b>{@code from}/{@code to} branching:</b> PostgreSQL's JDBC driver
     * cannot determine the data type of an untyped {@code NULL} bound into
     * {@code sa.createdAt >= ?} ({@code TIMESTAMPTZ}) and the server fails
     * with {@code could not determine data type of parameter}. The default
     * dispatcher routes to one of four physical {@code @Query} methods,
     * keeping the {@code from}/{@code to} parameter binding out of the SQL
     * entirely when the value is {@code null}. UUID/String filters retain
     * the {@code :x is null or ...} guard because their bound types
     * ({@code uuid}, {@code text}) are unambiguously inferred by the driver.
     */
    default Page<SystemAuditLogJpaEntity> search(
            UUID tenantId, UUID actorUserId, String action, String entityType,
            UUID entityId, OffsetDateTime from, OffsetDateTime to, Pageable pageable) {
        boolean hasFrom = from != null;
        boolean hasTo = to != null;
        if (hasFrom && hasTo) {
            return searchWithFromAndTo(tenantId, actorUserId, action, entityType,
                    entityId, from, to, pageable);
        }
        if (hasFrom) {
            return searchWithFrom(tenantId, actorUserId, action, entityType,
                    entityId, from, pageable);
        }
        if (hasTo) {
            return searchWithTo(tenantId, actorUserId, action, entityType,
                    entityId, to, pageable);
        }
        return searchNoRange(tenantId, actorUserId, action, entityType,
                entityId, pageable);
    }

    @Query("""
            select sa from SystemAuditLogJpaEntity sa
            where (:tenantId is null or sa.tenantId = :tenantId)
              and (:actorUserId is null or sa.actorUserId = :actorUserId)
              and (:action is null or sa.action = :action)
              and (:entityType is null or sa.entityType = :entityType)
              and (:entityId is null or sa.entityId = :entityId)
            order by sa.createdAt desc
            """)
    Page<SystemAuditLogJpaEntity> searchNoRange(
            @Param("tenantId")    UUID tenantId,
            @Param("actorUserId") UUID actorUserId,
            @Param("action")      String action,
            @Param("entityType")  String entityType,
            @Param("entityId")    UUID entityId,
            Pageable pageable);

    @Query("""
            select sa from SystemAuditLogJpaEntity sa
            where (:tenantId is null or sa.tenantId = :tenantId)
              and (:actorUserId is null or sa.actorUserId = :actorUserId)
              and (:action is null or sa.action = :action)
              and (:entityType is null or sa.entityType = :entityType)
              and (:entityId is null or sa.entityId = :entityId)
              and sa.createdAt >= :from
            order by sa.createdAt desc
            """)
    Page<SystemAuditLogJpaEntity> searchWithFrom(
            @Param("tenantId")    UUID tenantId,
            @Param("actorUserId") UUID actorUserId,
            @Param("action")      String action,
            @Param("entityType")  String entityType,
            @Param("entityId")    UUID entityId,
            @Param("from")        OffsetDateTime from,
            Pageable pageable);

    @Query("""
            select sa from SystemAuditLogJpaEntity sa
            where (:tenantId is null or sa.tenantId = :tenantId)
              and (:actorUserId is null or sa.actorUserId = :actorUserId)
              and (:action is null or sa.action = :action)
              and (:entityType is null or sa.entityType = :entityType)
              and (:entityId is null or sa.entityId = :entityId)
              and sa.createdAt <= :to
            order by sa.createdAt desc
            """)
    Page<SystemAuditLogJpaEntity> searchWithTo(
            @Param("tenantId")    UUID tenantId,
            @Param("actorUserId") UUID actorUserId,
            @Param("action")      String action,
            @Param("entityType")  String entityType,
            @Param("entityId")    UUID entityId,
            @Param("to")          OffsetDateTime to,
            Pageable pageable);

    @Query("""
            select sa from SystemAuditLogJpaEntity sa
            where (:tenantId is null or sa.tenantId = :tenantId)
              and (:actorUserId is null or sa.actorUserId = :actorUserId)
              and (:action is null or sa.action = :action)
              and (:entityType is null or sa.entityType = :entityType)
              and (:entityId is null or sa.entityId = :entityId)
              and sa.createdAt >= :from
              and sa.createdAt <= :to
            order by sa.createdAt desc
            """)
    Page<SystemAuditLogJpaEntity> searchWithFromAndTo(
            @Param("tenantId")    UUID tenantId,
            @Param("actorUserId") UUID actorUserId,
            @Param("action")      String action,
            @Param("entityType")  String entityType,
            @Param("entityId")    UUID entityId,
            @Param("from")        OffsetDateTime from,
            @Param("to")          OffsetDateTime to,
            Pageable pageable);
}

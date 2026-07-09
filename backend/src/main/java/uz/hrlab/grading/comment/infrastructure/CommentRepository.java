package uz.hrlab.grading.comment.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.hrlab.grading.comment.domain.CommentEntityType;
import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;

import java.util.List;
import java.util.UUID;

public interface CommentRepository
        extends TenantAwareRepository<CommentJpaEntity, UUID> {

    List<CommentJpaEntity>
            findAllByTenantIdAndEntityTypeAndEntityIdAndDeletedAtIsNullOrderByCreatedAtAsc(
                    UUID tenantId, CommentEntityType entityType, UUID entityId);

    /**
     * Mentions inbox — native query using PostgreSQL array containment.
     * Spring Data JPQL has no first-class array operator, so we use SQL.
     *
     * <p>DB-20 — the predicate is the array-CONTAINMENT form
     * ({@code mentioned_user_ids @> ARRAY[:userId]::uuid[]}) so PostgreSQL can serve
     * it from the {@code idx_comments_mentions_gin} GIN index. The previous
     * {@code :userId = ANY(mentioned_user_ids)} scalar-vs-array form is NOT
     * GIN-indexable and seq-scanned every {@code /my-mentions} load. Semantics are
     * identical: both match rows whose array contains {@code userId} (a NULL array
     * matches neither). The explicit {@code ::uuid[]} cast pins the element type so
     * the operator resolves against the uuid[] GIN index.
     */
    @Query(value = """
            SELECT * FROM comments
             WHERE tenant_id = :tenantId
               AND deleted_at IS NULL
               AND mentioned_user_ids @> ARRAY[:userId]::uuid[]
             ORDER BY created_at DESC
            """, nativeQuery = true)
    Page<CommentJpaEntity> findMentions(@Param("tenantId") UUID tenantId,
                                        @Param("userId") UUID userId,
                                        Pageable pageable);
}

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
     */
    @Query(value = """
            SELECT * FROM comments
             WHERE tenant_id = :tenantId
               AND deleted_at IS NULL
               AND :userId = ANY(mentioned_user_ids)
             ORDER BY created_at DESC
            """, nativeQuery = true)
    Page<CommentJpaEntity> findMentions(@Param("tenantId") UUID tenantId,
                                        @Param("userId") UUID userId,
                                        Pageable pageable);
}

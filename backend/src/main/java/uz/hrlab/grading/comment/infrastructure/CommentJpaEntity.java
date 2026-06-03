package uz.hrlab.grading.comment.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import uz.hrlab.grading.comment.domain.CommentEntityType;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "comments")
public class CommentJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "project_id")
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 64, updatable = false)
    private CommentEntityType entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @Column(name = "author_user_id", nullable = false, updatable = false)
    private UUID authorUserId;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    /**
     * Hibernate maps Set<UUID> to {@code uuid[]} natively only on PostgreSQL
     * dialect — we store as a JSON-ish array via the simple converter pattern.
     * Use {@link Column#columnDefinition} = "uuid[]" so the DB type stays
     * native; Hibernate 6 handles the binding for {@code @JdbcTypeCode(SqlTypes.ARRAY)}.
     */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.ARRAY)
    @Column(name = "mentioned_user_ids", columnDefinition = "uuid[]")
    private Set<UUID> mentionedUserIds = new LinkedHashSet<>();

    @Column(name = "parent_comment_id")
    private UUID parentCommentId;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    protected CommentJpaEntity() { }

    public CommentJpaEntity(UUID id, UUID tenantId, UUID projectId,
                            CommentEntityType entityType, UUID entityId,
                            UUID authorUserId, String body,
                            Set<UUID> mentionedUserIds, UUID parentCommentId) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.authorUserId = authorUserId;
        this.body = body;
        this.mentionedUserIds = mentionedUserIds == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(mentionedUserIds);
        this.parentCommentId = parentCommentId;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public CommentEntityType getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public UUID getAuthorUserId() { return authorUserId; }
    public String getBody() { return body; }
    public Set<UUID> getMentionedUserIds() { return mentionedUserIds; }
    public UUID getParentCommentId() { return parentCommentId; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }

    public void setBody(String body) { this.body = body; }
    public void setMentionedUserIds(Set<UUID> ids) {
        this.mentionedUserIds = ids == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(ids);
    }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
}

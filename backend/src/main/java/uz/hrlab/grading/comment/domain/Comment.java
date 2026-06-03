package uz.hrlab.grading.comment.domain;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public final class Comment {

    private final UUID id;
    private final UUID tenantId;
    private final UUID projectId;
    private final CommentEntityType entityType;
    private final UUID entityId;
    private final UUID authorUserId;
    private final String body;
    private final Set<UUID> mentionedUserIds;
    private final UUID parentCommentId;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
    private final OffsetDateTime deletedAt;

    public Comment(UUID id, UUID tenantId, UUID projectId,
                   CommentEntityType entityType, UUID entityId,
                   UUID authorUserId, String body, Set<UUID> mentionedUserIds,
                   UUID parentCommentId, OffsetDateTime createdAt,
                   OffsetDateTime updatedAt, OffsetDateTime deletedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.authorUserId = authorUserId;
        this.body = body;
        this.mentionedUserIds = mentionedUserIds == null ? Set.of() : Set.copyOf(mentionedUserIds);
        this.parentCommentId = parentCommentId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID projectId() { return projectId; }
    public CommentEntityType entityType() { return entityType; }
    public UUID entityId() { return entityId; }
    public UUID authorUserId() { return authorUserId; }
    public String body() { return body; }
    public Set<UUID> mentionedUserIds() { return mentionedUserIds; }
    public UUID parentCommentId() { return parentCommentId; }
    public OffsetDateTime createdAt() { return createdAt; }
    public OffsetDateTime updatedAt() { return updatedAt; }
    public OffsetDateTime deletedAt() { return deletedAt; }
    public boolean isDeleted() { return deletedAt != null; }
}

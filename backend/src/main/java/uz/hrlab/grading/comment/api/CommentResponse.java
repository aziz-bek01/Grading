package uz.hrlab.grading.comment.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import uz.hrlab.grading.comment.domain.Comment;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommentResponse(
        UUID id,
        UUID projectId,
        String entityType,
        UUID entityId,
        UUID authorUserId,
        String body,
        Set<UUID> mentionedUserIds,
        UUID parentCommentId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deletedAt
) {
    public static CommentResponse from(Comment c) {
        return new CommentResponse(c.id(), c.projectId(), c.entityType().name(),
                c.entityId(), c.authorUserId(), c.body(), c.mentionedUserIds(),
                c.parentCommentId(), c.createdAt(), c.updatedAt(), c.deletedAt());
    }
}

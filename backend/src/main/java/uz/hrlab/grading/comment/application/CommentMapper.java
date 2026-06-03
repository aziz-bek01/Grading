package uz.hrlab.grading.comment.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.comment.domain.Comment;
import uz.hrlab.grading.comment.infrastructure.CommentJpaEntity;

@Component
public class CommentMapper {

    public Comment toDomain(CommentJpaEntity e) {
        return new Comment(e.getId(), e.getTenantId(), e.getProjectId(),
                e.getEntityType(), e.getEntityId(), e.getAuthorUserId(),
                e.getBody(), e.getMentionedUserIds(), e.getParentCommentId(),
                e.getCreatedAt(), e.getUpdatedAt(), e.getDeletedAt());
    }
}

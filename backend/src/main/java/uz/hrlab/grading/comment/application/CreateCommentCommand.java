package uz.hrlab.grading.comment.application;

import uz.hrlab.grading.comment.domain.CommentEntityType;

import java.util.UUID;

public record CreateCommentCommand(
        UUID projectId,
        CommentEntityType entityType,
        UUID entityId,
        String body,
        UUID parentCommentId
) { }

package uz.hrlab.grading.comment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.hrlab.grading.comment.domain.CommentEntityType;

import java.util.UUID;

public record CreateCommentRequest(
        UUID projectId,
        @NotNull CommentEntityType entityType,
        @NotNull UUID entityId,
        @NotBlank @Size(min = 1, max = 5000) String body,
        UUID parentCommentId
) { }

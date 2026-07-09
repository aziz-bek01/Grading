package uz.hrlab.grading.comment.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.comment.domain.Comment;
import uz.hrlab.grading.comment.domain.MentionExtractor;
import uz.hrlab.grading.comment.infrastructure.CommentJpaEntity;
import uz.hrlab.grading.comment.infrastructure.CommentRepository;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.Set;
import java.util.UUID;

@Service
public class UpdateCommentUseCase {

    private static final int MAX_BODY_LENGTH = 5000;

    private final CommentRepository comments;
    private final AuditService audit;
    private final CommentMapper mapper;

    public UpdateCommentUseCase(CommentRepository comments, AuditService audit, CommentMapper mapper) {
        this.comments = comments;
        this.audit = audit;
        this.mapper = mapper;
    }

    @Transactional
    public Comment update(UUID id, String newBody) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.COMMENT_EDIT);
        if (newBody == null || newBody.isBlank()) {
            throw new ValidationException("BODY_REQUIRED");
        }
        if (newBody.length() > MAX_BODY_LENGTH) {
            throw new ValidationException("BODY_TOO_LONG");
        }
        CommentJpaEntity entity = comments.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (entity.getDeletedAt() != null) {
            throw new ValidationException("COMMENT_DELETED");
        }
        // Own-only edit.
        if (!entity.getAuthorUserId().equals(ctx.userId())) {
            throw new PermissionDeniedException();
        }
        entity.setBody(newBody);
        Set<UUID> mentions = MentionExtractor.extract(newBody);
        entity.setMentionedUserIds(mentions);
        comments.save(entity);

        audit.record(AuditEvent.builder(ctx)
                .projectId(entity.getProjectId())
                .action(AuditAction.COMMENT_UPDATED)
                .entityType("Comment")
                .entityId(entity.getId())
                .build());
        return mapper.toDomain(entity);
    }
}

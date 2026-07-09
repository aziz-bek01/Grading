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
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.Set;
import java.util.UUID;

@Service
public class CreateCommentUseCase {

    private static final int MAX_BODY_LENGTH = 5000;

    private final CommentRepository comments;
    private final AuditService audit;
    private final CommentMapper mapper;

    public CreateCommentUseCase(CommentRepository comments, AuditService audit, CommentMapper mapper) {
        this.comments = comments;
        this.audit = audit;
        this.mapper = mapper;
    }

    @Transactional
    public Comment create(CreateCommentCommand cmd) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.COMMENT_CREATE);
        if (cmd == null || cmd.entityType() == null || cmd.entityId() == null
                || cmd.body() == null || cmd.body().isBlank()) {
            throw new ValidationException("MISSING_FIELDS");
        }
        if (cmd.body().length() > MAX_BODY_LENGTH) {
            throw new ValidationException("BODY_TOO_LONG");
        }
        // Optional parent check — must belong to the same tenant + entity.
        if (cmd.parentCommentId() != null) {
            CommentJpaEntity parent = comments
                    .findByIdAndTenantId(cmd.parentCommentId(), ctx.tenantId())
                    .orElseThrow(TenantAccessDeniedException::new);
            if (parent.getEntityType() != cmd.entityType()
                    || !parent.getEntityId().equals(cmd.entityId())) {
                throw new ValidationException("PARENT_COMMENT_MISMATCH");
            }
            // DB trigger enforces 1-level depth as defense in depth.
        }

        Set<UUID> mentions = MentionExtractor.extract(cmd.body());

        CommentJpaEntity entity = new CommentJpaEntity(
                UUID.randomUUID(), ctx.tenantId(), cmd.projectId(),
                cmd.entityType(), cmd.entityId(), ctx.userId(),
                cmd.body(), mentions, cmd.parentCommentId());
        comments.save(entity);

        audit.record(AuditEvent.builder(ctx)
                .projectId(cmd.projectId())
                .action(AuditAction.COMMENT_CREATED)
                .entityType("Comment")
                .entityId(entity.getId())
                .reason("target=" + cmd.entityType() + ":" + cmd.entityId())
                .build());

        for (UUID mentionedUser : mentions) {
            audit.record(AuditEvent.builder(ctx)
                    .projectId(cmd.projectId())
                    .action(AuditAction.COMMENT_MENTION)
                    .entityType("Comment")
                    .entityId(entity.getId())
                    .reason("mentioned=" + mentionedUser)
                    .build());
        }
        return mapper.toDomain(entity);
    }
}

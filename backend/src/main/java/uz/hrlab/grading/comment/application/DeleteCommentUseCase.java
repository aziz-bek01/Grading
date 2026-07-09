package uz.hrlab.grading.comment.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.comment.infrastructure.CommentJpaEntity;
import uz.hrlab.grading.comment.infrastructure.CommentRepository;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Soft-delete. Own comments only; Project Manager (PROJECT_EDIT) can delete
 * any comment in their project.
 */
@Service
public class DeleteCommentUseCase {

    private final CommentRepository comments;
    private final AuditService audit;

    public DeleteCommentUseCase(CommentRepository comments, AuditService audit) {
        this.comments = comments;
        this.audit = audit;
    }

    @Transactional
    public void delete(UUID id) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.COMMENT_DELETE);
        CommentJpaEntity entity = comments.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        boolean isOwner = ctx.userId() != null && ctx.userId().equals(entity.getAuthorUserId());
        boolean isProjectManager = ctx.hasPermission(PermissionCodes.PROJECT_EDIT);
        if (!isOwner && !isProjectManager) {
            throw new PermissionDeniedException();
        }
        if (entity.getDeletedAt() != null) return; // idempotent
        entity.setDeletedAt(OffsetDateTime.now());
        comments.save(entity);

        audit.record(AuditEvent.builder(ctx)
                .projectId(entity.getProjectId())
                .action(AuditAction.COMMENT_DELETED)
                .entityType("Comment")
                .entityId(entity.getId())
                .build());
    }
}

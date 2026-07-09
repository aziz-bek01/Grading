package uz.hrlab.grading.comment.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.comment.domain.Comment;
import uz.hrlab.grading.comment.domain.CommentEntityType;
import uz.hrlab.grading.comment.infrastructure.CommentRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.UUID;

@Service
public class ListCommentsByEntityQuery {

    private final CommentRepository comments;
    private final CommentMapper mapper;

    public ListCommentsByEntityQuery(CommentRepository comments, CommentMapper mapper) {
        this.comments = comments;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<Comment> list(CommentEntityType entityType, UUID entityId) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.COMMENT_READ);
        return comments
                .findAllByTenantIdAndEntityTypeAndEntityIdAndDeletedAtIsNullOrderByCreatedAtAsc(
                        ctx.tenantId(), entityType, entityId)
                .stream().map(mapper::toDomain).toList();
    }
}

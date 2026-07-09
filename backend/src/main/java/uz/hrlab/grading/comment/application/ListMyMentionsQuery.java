package uz.hrlab.grading.comment.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.comment.domain.Comment;
import uz.hrlab.grading.comment.infrastructure.CommentRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

@Service
public class ListMyMentionsQuery {

    private final CommentRepository comments;
    private final CommentMapper mapper;

    public ListMyMentionsQuery(CommentRepository comments, CommentMapper mapper) {
        this.comments = comments;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Page<Comment> list(Pageable pageable) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.COMMENT_READ);
        Pageable bounded = pageable;
        if (pageable == null) bounded = PageRequest.of(0, 20);
        if (bounded.getPageSize() > 100) bounded = PageRequest.of(bounded.getPageNumber(), 100);
        return comments.findMentions(ctx.tenantId(), ctx.userId(), bounded)
                .map(mapper::toDomain);
    }
}

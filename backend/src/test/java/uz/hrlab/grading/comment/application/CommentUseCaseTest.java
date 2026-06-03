package uz.hrlab.grading.comment.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.comment.domain.Comment;
import uz.hrlab.grading.comment.domain.CommentEntityType;
import uz.hrlab.grading.comment.infrastructure.CommentJpaEntity;
import uz.hrlab.grading.comment.infrastructure.CommentRepository;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@Tag("workflow")
class CommentUseCaseTest {

    private CommentRepository comments;
    private AuditService audit;
    private CommentMapper mapper;
    private CreateCommentUseCase createUseCase;
    private UpdateCommentUseCase updateUseCase;
    private DeleteCommentUseCase deleteUseCase;

    private UUID tenantId;
    private UUID userId;
    private UUID otherUserId;

    @BeforeEach
    void setup() {
        comments = mock(CommentRepository.class);
        audit = mock(AuditService.class);
        mapper = new CommentMapper();
        createUseCase = new CreateCommentUseCase(comments, audit, mapper);
        updateUseCase = new UpdateCommentUseCase(comments, audit, mapper);
        deleteUseCase = new DeleteCommentUseCase(comments, audit);

        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();

        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(), Set.of(),
                Set.of("COMMENT_CREATE", "COMMENT_EDIT", "COMMENT_DELETE", "COMMENT_READ"),
                Set.of(), false, "ru-RU"));

        given(comments.save(any())).willAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createExtractsMentions() {
        UUID mention = UUID.randomUUID();
        var cmd = new CreateCommentCommand(UUID.randomUUID(), CommentEntityType.POSITION,
                UUID.randomUUID(), "Hello @[" + mention + "], please review.", null);
        Comment result = createUseCase.create(cmd);
        assertThat(result.mentionedUserIds()).containsExactly(mention);
    }

    @Test
    void createRejectsEmptyBody() {
        var cmd = new CreateCommentCommand(UUID.randomUUID(), CommentEntityType.POSITION,
                UUID.randomUUID(), "", null);
        assertThatThrownBy(() -> createUseCase.create(cmd))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void updateOwnComment_succeeds() {
        UUID commentId = UUID.randomUUID();
        CommentJpaEntity own = new CommentJpaEntity(commentId, tenantId, UUID.randomUUID(),
                CommentEntityType.POSITION, UUID.randomUUID(), userId,
                "old", new LinkedHashSet<>(), null);
        given(comments.findByIdAndTenantId(commentId, tenantId)).willReturn(Optional.of(own));
        Comment result = updateUseCase.update(commentId, "updated body");
        assertThat(result.body()).isEqualTo("updated body");
    }

    @Test
    void updateForeignComment_denied() {
        UUID commentId = UUID.randomUUID();
        CommentJpaEntity foreign = new CommentJpaEntity(commentId, tenantId, UUID.randomUUID(),
                CommentEntityType.POSITION, UUID.randomUUID(), otherUserId,
                "old", new LinkedHashSet<>(), null);
        given(comments.findByIdAndTenantId(commentId, tenantId)).willReturn(Optional.of(foreign));
        assertThatThrownBy(() -> updateUseCase.update(commentId, "x updated"))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void deleteIsSoftDelete() {
        UUID commentId = UUID.randomUUID();
        CommentJpaEntity own = new CommentJpaEntity(commentId, tenantId, UUID.randomUUID(),
                CommentEntityType.POSITION, UUID.randomUUID(), userId,
                "body", new LinkedHashSet<>(), null);
        given(comments.findByIdAndTenantId(commentId, tenantId)).willReturn(Optional.of(own));

        deleteUseCase.delete(commentId);
        assertThat(own.getDeletedAt()).isNotNull();
        // Body preserved — soft delete only flips deletedAt.
        assertThat(own.getBody()).isEqualTo("body");
    }

    @Test
    void projectManagerOverride_canDeleteOthers() {
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(), Set.of(),
                Set.of("COMMENT_DELETE", "PROJECT_EDIT"),
                Set.of(), false, "ru-RU"));
        UUID commentId = UUID.randomUUID();
        CommentJpaEntity foreign = new CommentJpaEntity(commentId, tenantId, UUID.randomUUID(),
                CommentEntityType.POSITION, UUID.randomUUID(), otherUserId,
                "body", new LinkedHashSet<>(), null);
        given(comments.findByIdAndTenantId(commentId, tenantId)).willReturn(Optional.of(foreign));
        deleteUseCase.delete(commentId);
        assertThat(foreign.getDeletedAt()).isNotNull();
    }
}

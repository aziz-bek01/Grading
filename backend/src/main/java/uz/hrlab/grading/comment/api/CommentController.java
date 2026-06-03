package uz.hrlab.grading.comment.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.hrlab.grading.comment.application.CreateCommentCommand;
import uz.hrlab.grading.comment.application.CreateCommentUseCase;
import uz.hrlab.grading.comment.application.DeleteCommentUseCase;
import uz.hrlab.grading.comment.application.ListCommentsByEntityQuery;
import uz.hrlab.grading.comment.application.ListMyMentionsQuery;
import uz.hrlab.grading.comment.application.UpdateCommentUseCase;
import uz.hrlab.grading.comment.domain.CommentEntityType;
import uz.hrlab.grading.common.api.PageResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/comments")
public class CommentController {

    private final CreateCommentUseCase createUseCase;
    private final UpdateCommentUseCase updateUseCase;
    private final DeleteCommentUseCase deleteUseCase;
    private final ListCommentsByEntityQuery listByEntity;
    private final ListMyMentionsQuery mentionsQuery;

    public CommentController(CreateCommentUseCase createUseCase,
                             UpdateCommentUseCase updateUseCase,
                             DeleteCommentUseCase deleteUseCase,
                             ListCommentsByEntityQuery listByEntity,
                             ListMyMentionsQuery mentionsQuery) {
        this.createUseCase = createUseCase;
        this.updateUseCase = updateUseCase;
        this.deleteUseCase = deleteUseCase;
        this.listByEntity = listByEntity;
        this.mentionsQuery = mentionsQuery;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('COMMENT_CREATE')")
    public ResponseEntity<CommentResponse> create(@Valid @RequestBody CreateCommentRequest req) {
        var cmd = new CreateCommentCommand(req.projectId(), req.entityType(),
                req.entityId(), req.body(), req.parentCommentId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommentResponse.from(createUseCase.create(cmd)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('COMMENT_READ')")
    public List<CommentResponse> list(@RequestParam CommentEntityType entityType,
                                      @RequestParam UUID entityId) {
        return listByEntity.list(entityType, entityId).stream()
                .map(CommentResponse::from).toList();
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('COMMENT_EDIT')")
    public CommentResponse update(@PathVariable UUID id,
                                  @Valid @RequestBody UpdateCommentRequest req) {
        return CommentResponse.from(updateUseCase.update(id, req.body()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('COMMENT_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        deleteUseCase.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my-mentions")
    @PreAuthorize("hasAuthority('COMMENT_READ')")
    public PageResponse<CommentResponse> myMentions(Pageable pageable) {
        return PageResponse.of(mentionsQuery.list(pageable), CommentResponse::from);
    }
}

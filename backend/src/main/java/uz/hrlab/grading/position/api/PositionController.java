package uz.hrlab.grading.position.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.hrlab.grading.common.api.PageResponse;
import uz.hrlab.grading.position.application.ArchivePositionUseCase;
import uz.hrlab.grading.position.application.CreatePositionCommand;
import uz.hrlab.grading.position.application.CreatePositionUseCase;
import uz.hrlab.grading.position.application.FindPositionQuery;
import uz.hrlab.grading.position.application.UpdatePositionCommand;
import uz.hrlab.grading.position.application.UpdatePositionUseCase;
import uz.hrlab.grading.position.domain.Position;
import uz.hrlab.grading.position.domain.PositionStatus;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/positions")
public class PositionController {

    private final CreatePositionUseCase createUseCase;
    private final UpdatePositionUseCase updateUseCase;
    private final ArchivePositionUseCase archiveUseCase;
    private final FindPositionQuery findQuery;

    public PositionController(CreatePositionUseCase createUseCase,
                              UpdatePositionUseCase updateUseCase,
                              ArchivePositionUseCase archiveUseCase,
                              FindPositionQuery findQuery) {
        this.createUseCase = createUseCase;
        this.updateUseCase = updateUseCase;
        this.archiveUseCase = archiveUseCase;
        this.findQuery = findQuery;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('POSITION_CREATE')")
    public ResponseEntity<PositionResponse> create(@Valid @RequestBody CreatePositionRequest req) {
        Position p = createUseCase.create(new CreatePositionCommand(
                req.projectId(), req.departmentId(), req.code(), req.titleI18n(),
                req.function(), req.category(), req.jobFamily(), req.jobLevel()));
        return ResponseEntity.status(HttpStatus.CREATED).body(PositionResponse.from(p));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('POSITION_READ')")
    public PageResponse<PositionResponse> list(
            @RequestParam UUID projectId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false, defaultValue = "false") boolean includeSubtree,
            @RequestParam(required = false) PositionStatus status,
            @RequestParam(required = false) String jobFamily,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // T4 — includeSubtree expands departmentId to its subtree (one existing
        // closure call) and reuses searchInDepartments; default false ⇒ unchanged.
        Page<Position> result = findQuery.list(
                projectId, departmentId, includeSubtree, status, jobFamily, page, size);
        return PageResponse.of(result, PositionResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('POSITION_READ')")
    public PositionResponse getById(@PathVariable UUID id) {
        return PositionResponse.from(findQuery.findById(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('POSITION_EDIT')")
    public PositionResponse update(@PathVariable UUID id,
                                   @Valid @RequestBody UpdatePositionRequest req) {
        Position p = updateUseCase.update(id, new UpdatePositionCommand(
                req.departmentId(), req.titleI18n(), req.function(), req.category(),
                req.jobFamily(), req.jobLevel()));
        return PositionResponse.from(p);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('POSITION_EDIT')")
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        archiveUseCase.archive(id);
        return ResponseEntity.noContent().build();
    }
}

package uz.hrlab.grading.project.api;

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
import uz.hrlab.grading.project.application.ArchiveProjectUseCase;
import uz.hrlab.grading.project.application.CreateProjectCommand;
import uz.hrlab.grading.project.application.CreateProjectUseCase;
import uz.hrlab.grading.project.application.FindProjectQuery;
import uz.hrlab.grading.project.application.LockProjectUseCase;
import uz.hrlab.grading.project.application.UpdateProjectCommand;
import uz.hrlab.grading.project.application.UpdateProjectUseCase;
import uz.hrlab.grading.project.domain.Project;

import java.util.UUID;

/**
 * Project API (architecture §13.2, role-permissions-matrix §4).
 *
 * <p>{@code @PreAuthorize} guards check RBAC permission codes; ABAC is enforced
 * inside the use cases via {@link uz.hrlab.grading.access.application.AbacGate}.
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final CreateProjectUseCase createUseCase;
    private final UpdateProjectUseCase updateUseCase;
    private final ArchiveProjectUseCase archiveUseCase;
    private final LockProjectUseCase lockUseCase;
    private final FindProjectQuery findQuery;

    public ProjectController(CreateProjectUseCase createUseCase,
                             UpdateProjectUseCase updateUseCase,
                             ArchiveProjectUseCase archiveUseCase,
                             LockProjectUseCase lockUseCase,
                             FindProjectQuery findQuery) {
        this.createUseCase = createUseCase;
        this.updateUseCase = updateUseCase;
        this.archiveUseCase = archiveUseCase;
        this.lockUseCase = lockUseCase;
        this.findQuery = findQuery;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PROJECT_CREATE')")
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest req) {
        Project p = createUseCase.create(new CreateProjectCommand(
                req.code(), req.nameI18n(), req.description(), req.startDate(), req.endDate()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectResponse.from(p));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PROJECT_READ')")
    public PageResponse<ProjectResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Project> result = findQuery.list(page, size);
        return PageResponse.of(result, ProjectResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PROJECT_READ')")
    public ProjectResponse getById(@PathVariable UUID id) {
        return ProjectResponse.from(findQuery.findById(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('PROJECT_EDIT')")
    public ProjectResponse update(@PathVariable UUID id,
                                  @Valid @RequestBody UpdateProjectRequest req) {
        Project p = updateUseCase.update(id, new UpdateProjectCommand(
                req.nameI18n(), req.description(), req.startDate(), req.endDate()));
        return ProjectResponse.from(p);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('PROJECT_EDIT')")
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        archiveUseCase.archive(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/lock")
    @PreAuthorize("hasAnyAuthority('METHODOLOGY_LOCK', 'PROJECT_EDIT')")
    public ResponseEntity<Void> lock(@PathVariable UUID id) {
        lockUseCase.lock(id);
        return ResponseEntity.noContent().build();
    }
}

package uz.hrlab.grading.organization.api;

import jakarta.validation.Valid;
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
import uz.hrlab.grading.organization.application.ArchiveDepartmentUseCase;
import uz.hrlab.grading.organization.application.CreateDepartmentCommand;
import uz.hrlab.grading.organization.application.CreateDepartmentUseCase;
import uz.hrlab.grading.organization.application.FindDepartmentQuery;
import uz.hrlab.grading.organization.application.UpdateDepartmentCommand;
import uz.hrlab.grading.organization.application.UpdateDepartmentUseCase;
import uz.hrlab.grading.organization.domain.Department;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {

    private final CreateDepartmentUseCase createUseCase;
    private final UpdateDepartmentUseCase updateUseCase;
    private final ArchiveDepartmentUseCase archiveUseCase;
    private final FindDepartmentQuery findQuery;

    public DepartmentController(CreateDepartmentUseCase createUseCase,
                                UpdateDepartmentUseCase updateUseCase,
                                ArchiveDepartmentUseCase archiveUseCase,
                                FindDepartmentQuery findQuery) {
        this.createUseCase = createUseCase;
        this.updateUseCase = updateUseCase;
        this.archiveUseCase = archiveUseCase;
        this.findQuery = findQuery;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ORG_EDIT')")
    public ResponseEntity<DepartmentResponse> create(@Valid @RequestBody CreateDepartmentRequest req) {
        Department d = createUseCase.create(new CreateDepartmentCommand(
                req.projectId(), req.parentId(), req.code(), req.nameI18n(), req.type()));
        return ResponseEntity.status(HttpStatus.CREATED).body(DepartmentResponse.from(d));
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('ORG_READ')")
    public List<DepartmentTreeNodeResponse> tree(@RequestParam UUID projectId) {
        return findQuery.tree(projectId).stream()
                .map(DepartmentTreeNodeResponse::from)
                .toList();
    }

    /**
     * Defect-1 BE — server-authoritative position counts per department for a
     * project. Returns {@code direct_count} (positions exactly in the department)
     * and {@code subtree_count} (self + all descendants), so the FE no longer
     * derives counts from a capped, exact-department position page (which rolled
     * up no descendants and dropped anything past the page cap).
     */
    @GetMapping("/position-counts")
    @PreAuthorize("hasAuthority('ORG_READ')")
    public List<DepartmentPositionCountResponse> positionCounts(@RequestParam UUID projectId) {
        return findQuery.positionCounts(projectId).stream()
                .map(DepartmentPositionCountResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ORG_READ')")
    public DepartmentResponse getById(@PathVariable UUID id) {
        return DepartmentResponse.from(findQuery.findById(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('ORG_EDIT')")
    public DepartmentResponse update(@PathVariable UUID id,
                                     @Valid @RequestBody UpdateDepartmentRequest req) {
        Department d = updateUseCase.update(id, new UpdateDepartmentCommand(
                req.parentId(), req.nameI18n(), req.type()));
        return DepartmentResponse.from(d);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('ORG_EDIT')")
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        archiveUseCase.archive(id);
        return ResponseEntity.noContent().build();
    }
}

package uz.hrlab.grading.methodology.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.hrlab.grading.methodology.application.ApproveMethodologyVersionUseCase;
import uz.hrlab.grading.methodology.application.ArchiveMethodologyVersionUseCase;
import uz.hrlab.grading.methodology.application.CreateMethodologyVersionUseCase;
import uz.hrlab.grading.methodology.application.FactorCommand;
import uz.hrlab.grading.methodology.application.FactorService;
import uz.hrlab.grading.methodology.application.LockMethodologyVersionUseCase;
import uz.hrlab.grading.methodology.application.MethodologyActorNameResolver;
import uz.hrlab.grading.methodology.application.MethodologyQueries;
import uz.hrlab.grading.methodology.domain.MethodologyVersion;

import java.util.List;
import java.util.UUID;

/** MethodologyVersion-scoped endpoints + factor add/reorder under a version. */
@RestController
@RequestMapping("/api/v1/methodology-versions")
public class MethodologyVersionController {

    private final MethodologyQueries queries;
    private final ApproveMethodologyVersionUseCase approveUseCase;
    private final LockMethodologyVersionUseCase lockUseCase;
    private final ArchiveMethodologyVersionUseCase archiveUseCase;
    private final CreateMethodologyVersionUseCase newVersionUseCase;
    private final FactorService factorService;
    private final MethodologyActorNameResolver actorNames;

    public MethodologyVersionController(MethodologyQueries queries,
                                        ApproveMethodologyVersionUseCase approveUseCase,
                                        LockMethodologyVersionUseCase lockUseCase,
                                        ArchiveMethodologyVersionUseCase archiveUseCase,
                                        CreateMethodologyVersionUseCase newVersionUseCase,
                                        FactorService factorService,
                                        MethodologyActorNameResolver actorNames) {
        this.queries = queries;
        this.approveUseCase = approveUseCase;
        this.lockUseCase = lockUseCase;
        this.archiveUseCase = archiveUseCase;
        this.newVersionUseCase = newVersionUseCase;
        this.factorService = factorService;
        this.actorNames = actorNames;
    }

    private MethodologyVersionResponse toResponse(MethodologyVersion v) {
        return MethodologyVersionResponse.from(v,
                actorNames.resolve(v.approvedBy()),
                actorNames.resolve(v.lockedBy()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('METHODOLOGY_READ')")
    public MethodologyVersionResponse getById(@PathVariable UUID id) {
        return toResponse(queries.findVersionById(id));
    }

    @GetMapping("/{id}/factors")
    @PreAuthorize("hasAuthority('METHODOLOGY_READ')")
    public List<FactorResponse> listFactors(@PathVariable UUID id) {
        return queries.listFactorsByVersion(id).stream()
                .map(e -> FactorResponse.from(e.toDomain()))
                .toList();
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('METHODOLOGY_APPROVE')")
    public MethodologyVersionResponse approve(@PathVariable UUID id) {
        return toResponse(approveUseCase.approve(id));
    }

    @PostMapping("/{id}/lock")
    @PreAuthorize("hasAuthority('METHODOLOGY_LOCK')")
    public MethodologyVersionResponse lock(@PathVariable UUID id) {
        return toResponse(lockUseCase.lock(id));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('METHODOLOGY_EDIT')")
    public MethodologyVersionResponse archive(@PathVariable UUID id,
                                              @Valid @RequestBody ReasonRequest req) {
        return toResponse(archiveUseCase.archive(id, req.reason()));
    }

    @PostMapping("/{id}/create-new-version")
    @PreAuthorize("hasAuthority('METHODOLOGY_EDIT')")
    public ResponseEntity<MethodologyVersionResponse> createNewVersion(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(newVersionUseCase.createNewVersion(id)));
    }

    @PostMapping("/{id}/factors")
    @PreAuthorize("hasAuthority('METHODOLOGY_EDIT')")
    public ResponseEntity<FactorResponse> addFactor(@PathVariable UUID id,
                                                    @Valid @RequestBody FactorRequest req) {
        FactorCommand cmd = new FactorCommand(req.code(), req.nameI18n(),
                req.descriptionI18n(), req.weight(), req.maxPoints(),
                req.sortOrder(), req.required());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(FactorResponse.from(factorService.add(id, cmd)));
    }

    @PostMapping("/{id}/factors/reorder")
    @PreAuthorize("hasAuthority('METHODOLOGY_EDIT')")
    public ResponseEntity<Void> reorderFactors(@PathVariable UUID id,
                                               @Valid @RequestBody ReorderRequest req) {
        factorService.reorder(id, req.orderedIds());
        return ResponseEntity.noContent().build();
    }
}

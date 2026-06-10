package uz.hrlab.grading.methodology.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import uz.hrlab.grading.methodology.application.ArchiveMethodologyUseCase;
import uz.hrlab.grading.methodology.application.CreateMethodologyCommand;
import uz.hrlab.grading.methodology.application.CreateMethodologyFromScratchUseCase;
import uz.hrlab.grading.methodology.application.CreateMethodologyFromTemplateCommand;
import uz.hrlab.grading.methodology.application.CreateMethodologyFromTemplateUseCase;
import uz.hrlab.grading.methodology.application.MethodologyActorNameResolver;
import uz.hrlab.grading.methodology.application.MethodologyAggregate;
import uz.hrlab.grading.methodology.application.MethodologyQueries;
import uz.hrlab.grading.methodology.application.UpdateMethodologyMetadataUseCase;
import uz.hrlab.grading.common.api.PageResponse;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Methodology container endpoints. */
@RestController
@RequestMapping("/api/v1/methodologies")
public class MethodologyController {

    private final CreateMethodologyFromTemplateUseCase createFromTemplate;
    private final CreateMethodologyFromScratchUseCase createFromScratch;
    private final UpdateMethodologyMetadataUseCase updateMetadata;
    private final ArchiveMethodologyUseCase archiveUseCase;
    private final MethodologyQueries queries;
    private final MethodologyActorNameResolver actorNames;

    public MethodologyController(CreateMethodologyFromTemplateUseCase createFromTemplate,
                                 CreateMethodologyFromScratchUseCase createFromScratch,
                                 UpdateMethodologyMetadataUseCase updateMetadata,
                                 ArchiveMethodologyUseCase archiveUseCase,
                                 MethodologyQueries queries,
                                 MethodologyActorNameResolver actorNames) {
        this.createFromTemplate = createFromTemplate;
        this.createFromScratch = createFromScratch;
        this.updateMetadata = updateMetadata;
        this.archiveUseCase = archiveUseCase;
        this.queries = queries;
        this.actorNames = actorNames;
    }

    @PostMapping("/from-template")
    @PreAuthorize("hasAuthority('METHODOLOGY_CREATE')")
    public ResponseEntity<MethodologyResponse> fromTemplate(
            @Valid @RequestBody CreateMethodologyFromTemplateRequest req) {
        MethodologyAggregate agg = createFromTemplate.create(
                new CreateMethodologyFromTemplateCommand(
                        req.templateCode(), req.projectId(), req.code(),
                        req.nameI18n(), req.descriptionI18n()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(MethodologyResponse.from(agg.methodology(), agg.currentVersion().id()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('METHODOLOGY_CREATE')")
    public ResponseEntity<MethodologyResponse> create(
            @Valid @RequestBody CreateMethodologyRequest req) {
        MethodologyAggregate agg = createFromScratch.create(new CreateMethodologyCommand(
                req.projectId(), req.code(), req.nameI18n(), req.descriptionI18n(),
                req.methodologyType(), req.scoringMode(), req.targetTotalPoints()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(MethodologyResponse.from(agg.methodology(), agg.currentVersion().id()));
    }

    /** Maximum allowed page size per blueprint API-5 (F-409). */
    public static final int MAX_PAGE_SIZE = 200;

    @GetMapping
    @PreAuthorize("hasAuthority('METHODOLOGY_READ')")
    public PageResponse<MethodologyResponse> list(@RequestParam(required = false) UUID projectId,
                                                  Pageable pageable) {
        Pageable safePageable = clampPageSize(pageable);
        Page<MethodologyJpaEntity> page = queries.findByProject(projectId, safePageable);
        // Batch-load every version of the page in one query (no N+1), then enrich
        // each row with latest/active version pointers + audit timestamps.
        List<UUID> ids = page.getContent().stream()
                .map(MethodologyJpaEntity::getId).toList();
        Map<UUID, List<MethodologyVersionJpaEntity>> versionsById =
                queries.versionsByMethodologyIds(ids);
        return PageResponse.of(page, e -> MethodologyResponse.fromList(
                e, versionsById.getOrDefault(e.getId(), List.of())));
    }

    /**
     * F-409: caps {@link Pageable#getPageSize()} at {@link #MAX_PAGE_SIZE} to
     * defend against DoS from unbounded {@code ?size=N}. Preserves page
     * number + sort order.
     */
    private static Pageable clampPageSize(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, 20);
        }
        if (pageable.getPageSize() <= MAX_PAGE_SIZE) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('METHODOLOGY_READ')")
    public MethodologyResponse getById(@PathVariable UUID id) {
        // B4: carry latest_version_id on the detail path (the list path already
        // does) so the FE create-from-scratch flow can deep-link into the new v1
        // editor. findMethodologyById enforces tenant/ABAC; the latest-version
        // lookup is tenant-scoped + METHODOLOGY_READ-guarded in the same way.
        return MethodologyResponse.from(
                queries.findMethodologyById(id), queries.findLatestVersionId(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('METHODOLOGY_EDIT')")
    public MethodologyResponse update(@PathVariable UUID id,
                                      @Valid @RequestBody UpdateMethodologyMetadataRequest req) {
        return MethodologyResponse.from(updateMetadata.update(id,
                req.nameI18n(), req.descriptionI18n()));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('METHODOLOGY_EDIT')")
    public MethodologyResponse archive(@PathVariable UUID id,
                                       @Valid @RequestBody ReasonRequest req) {
        return MethodologyResponse.from(archiveUseCase.archive(id, req.reason()));
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAuthority('METHODOLOGY_READ')")
    public java.util.List<MethodologyVersionResponse> listVersions(@PathVariable UUID id) {
        return queries.listVersions(id).stream()
                .map(v -> v.toDomain())
                .map(d -> MethodologyVersionResponse.from(d,
                        actorNames.resolve(d.approvedBy()),
                        actorNames.resolve(d.lockedBy())))
                .toList();
    }
}

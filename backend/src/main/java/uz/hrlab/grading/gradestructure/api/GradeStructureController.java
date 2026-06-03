package uz.hrlab.grading.gradestructure.api;

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
import uz.hrlab.grading.gradestructure.application.ApproveGradeStructureUseCase;
import uz.hrlab.grading.gradestructure.application.ArchiveGradeStructureUseCase;
import uz.hrlab.grading.gradestructure.application.CreateGradeStructureCommand;
import uz.hrlab.grading.gradestructure.application.CreateGradeStructureFromScratchUseCase;
import uz.hrlab.grading.gradestructure.application.CreateGradeStructureFromTemplateCommand;
import uz.hrlab.grading.gradestructure.application.CreateGradeStructureFromTemplateUseCase;
import uz.hrlab.grading.gradestructure.application.CreateGradeStructureVersionUseCase;
import uz.hrlab.grading.gradestructure.application.GradeBandLookupService;
import uz.hrlab.grading.gradestructure.application.GradeCommand;
import uz.hrlab.grading.gradestructure.application.GradePyramidQuery;
import uz.hrlab.grading.gradestructure.application.GradeService;
import uz.hrlab.grading.gradestructure.application.GradeStructureQueries;
import uz.hrlab.grading.gradestructure.application.LockGradeStructureUseCase;
import uz.hrlab.grading.gradestructure.application.UpdateGradeStructureMetadataUseCase;
import uz.hrlab.grading.gradestructure.domain.GradeStructureStatus;
import uz.hrlab.grading.gradestructure.domain.GradeStructureType;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureJpaEntity;
import uz.hrlab.grading.common.api.PageResponse;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

/**
 * Grade structure endpoints. Phase 6.
 *
 * <p>All requests are tenant-scoped via the security context; tenant_id is
 * NEVER accepted from the body or path. Permission codes:
 * GRADE_READ, GRADE_EDIT, GRADE_STRUCTURE_APPROVE, GRADE_STRUCTURE_LOCK.
 */
@RestController
@RequestMapping("/api/v1/grade-structures")
public class GradeStructureController {

    public static final int MAX_PAGE_SIZE = 200;

    private final CreateGradeStructureFromTemplateUseCase fromTemplate;
    private final CreateGradeStructureFromScratchUseCase fromScratch;
    private final UpdateGradeStructureMetadataUseCase updateMetadata;
    private final ApproveGradeStructureUseCase approveUseCase;
    private final LockGradeStructureUseCase lockUseCase;
    private final ArchiveGradeStructureUseCase archiveUseCase;
    private final CreateGradeStructureVersionUseCase versionUseCase;
    private final GradeService gradeService;
    private final GradeStructureQueries queries;
    private final GradePyramidQuery pyramid;
    private final GradeBandLookupService lookup;

    public GradeStructureController(CreateGradeStructureFromTemplateUseCase fromTemplate,
                                    CreateGradeStructureFromScratchUseCase fromScratch,
                                    UpdateGradeStructureMetadataUseCase updateMetadata,
                                    ApproveGradeStructureUseCase approveUseCase,
                                    LockGradeStructureUseCase lockUseCase,
                                    ArchiveGradeStructureUseCase archiveUseCase,
                                    CreateGradeStructureVersionUseCase versionUseCase,
                                    GradeService gradeService,
                                    GradeStructureQueries queries,
                                    GradePyramidQuery pyramid,
                                    GradeBandLookupService lookup) {
        this.fromTemplate = fromTemplate;
        this.fromScratch = fromScratch;
        this.updateMetadata = updateMetadata;
        this.approveUseCase = approveUseCase;
        this.lockUseCase = lockUseCase;
        this.archiveUseCase = archiveUseCase;
        this.versionUseCase = versionUseCase;
        this.gradeService = gradeService;
        this.queries = queries;
        this.pyramid = pyramid;
        this.lookup = lookup;
    }

    @PostMapping("/from-template")
    @PreAuthorize("hasAuthority('GRADE_EDIT')")
    public ResponseEntity<GradeStructureDetailResponse> fromTemplate(
            @Valid @RequestBody CreateGradeStructureFromTemplateRequest req) {
        var agg = fromTemplate.create(new CreateGradeStructureFromTemplateCommand(
                req.templateCode(), req.projectId(), req.code(),
                req.nameI18n(), req.descriptionI18n(), req.gapPolicy()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GradeStructureDetailResponse.from(agg));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('GRADE_EDIT')")
    public ResponseEntity<GradeStructureDetailResponse> create(
            @Valid @RequestBody CreateGradeStructureRequest req) {
        var agg = fromScratch.create(new CreateGradeStructureCommand(
                req.projectId(), req.code(), req.nameI18n(), req.descriptionI18n(),
                req.structureType() == null ? GradeStructureType.CUSTOM : req.structureType(),
                req.gapPolicy()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GradeStructureDetailResponse.from(agg));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('GRADE_READ')")
    public PageResponse<GradeStructureResponse> list(@RequestParam(required = false) UUID projectId,
                                                     @RequestParam(required = false) GradeStructureStatus status,
                                                     Pageable pageable) {
        Pageable safe = clamp(pageable);
        Page<GradeStructureJpaEntity> page = queries.findByProject(projectId, status, safe);
        return PageResponse.of(page, e -> GradeStructureResponse.from(e.toDomain()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('GRADE_READ')")
    public GradeStructureDetailResponse getById(@PathVariable UUID id) {
        return GradeStructureDetailResponse.from(queries.findDetail(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('GRADE_EDIT')")
    public GradeStructureResponse update(@PathVariable UUID id,
                                         @Valid @RequestBody UpdateGradeStructureMetadataRequest req) {
        return GradeStructureResponse.from(
                updateMetadata.update(id, req.nameI18n(), req.descriptionI18n()));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('GRADE_STRUCTURE_APPROVE')")
    public GradeStructureResponse approve(@PathVariable UUID id) {
        return GradeStructureResponse.from(approveUseCase.approve(id));
    }

    @PostMapping("/{id}/lock")
    @PreAuthorize("hasAuthority('GRADE_STRUCTURE_LOCK')")
    public GradeStructureResponse lock(@PathVariable UUID id) {
        return GradeStructureResponse.from(lockUseCase.lock(id));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('GRADE_EDIT')")
    public GradeStructureResponse archive(@PathVariable UUID id,
                                          @Valid @RequestBody ReasonRequest req) {
        return GradeStructureResponse.from(archiveUseCase.archive(id, req.reason()));
    }

    @PostMapping("/{id}/create-new-version")
    @PreAuthorize("hasAuthority('GRADE_EDIT')")
    public ResponseEntity<GradeStructureResponse> createNewVersion(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GradeStructureResponse.from(versionUseCase.createNewVersion(id)));
    }

    @PostMapping("/{id}/grades")
    @PreAuthorize("hasAuthority('GRADE_EDIT')")
    public ResponseEntity<GradeResponse> addGrade(@PathVariable("id") UUID structureId,
                                                   @Valid @RequestBody GradeRequest req) {
        var g = gradeService.addGrade(structureId, new GradeCommand.Create(
                req.gradeNumber(), req.sortOrder(), req.nameI18n(), req.descriptionI18n()));
        return ResponseEntity.status(HttpStatus.CREATED).body(GradeResponse.from(g));
    }

    @GetMapping("/{id}/pyramid")
    @PreAuthorize("hasAuthority('GRADE_READ')")
    public GradePyramidResponse pyramid(@PathVariable UUID id) {
        return GradePyramidResponse.from(pyramid.pyramid(id));
    }

    @PostMapping("/{id}/preview-grade-lookup")
    @PreAuthorize("hasAuthority('GRADE_READ')")
    public PreviewLookupResponse preview(@PathVariable("id") UUID structureId,
                                          @Valid @RequestBody PreviewLookupRequest req) {
        return lookup.lookup(TenantContextHolder.requireActive().tenantId(),
                        structureId, req.score())
                .map(PreviewLookupResponse::of)
                .orElseGet(PreviewLookupResponse::miss);
    }

    private static Pageable clamp(Pageable pageable) {
        if (pageable == null) return PageRequest.of(0, 20);
        if (pageable.getPageSize() <= MAX_PAGE_SIZE) return pageable;
        return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
    }
}

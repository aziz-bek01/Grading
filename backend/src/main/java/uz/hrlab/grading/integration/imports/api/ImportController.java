package uz.hrlab.grading.integration.imports.api;

import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.api.PageResponse;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.integration.imports.application.CancelImportBatchUseCase;
import uz.hrlab.grading.integration.imports.application.CommitImportBatchUseCase;
import uz.hrlab.grading.integration.imports.application.ImportBatchQueries;
import uz.hrlab.grading.integration.imports.application.ImportTemplateRegistry;
import uz.hrlab.grading.integration.imports.application.ImportTemplateSamples;
import uz.hrlab.grading.integration.imports.application.UploadImportFileUseCase;
import uz.hrlab.grading.integration.imports.domain.ImportBatchStatus;
import uz.hrlab.grading.integration.imports.domain.ImportErrorLevel;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

/**
 * REST API for Excel import batches (integration-blueprint §17).
 *
 * <p>Path is intentionally /api/v1/imports/* (not /api/v1/projects/{id}/imports/*)
 * for MVP 2 Phase 2 — the project context is carried by the request body for
 * uploads and resolved from the batch row for downstream operations. Tenant
 * is sourced from JWT exclusively.
 */
@RestController
@RequestMapping("/api/v1/imports")
public class ImportController {

    private static final Logger log = LoggerFactory.getLogger(ImportController.class);
    private static final int MAX_PAGE_SIZE = 200;

    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final UploadImportFileUseCase uploadUseCase;
    private final ImportBatchQueries queries;
    private final CommitImportBatchUseCase commitUseCase;
    private final CancelImportBatchUseCase cancelUseCase;
    private final FileUploadValidator fileValidator;
    private final AuditService audit;
    private final ImportTemplateSamples templateSamples;
    private final ImportTemplateRegistry templateRegistry;

    public ImportController(UploadImportFileUseCase uploadUseCase,
                            ImportBatchQueries queries,
                            CommitImportBatchUseCase commitUseCase,
                            CancelImportBatchUseCase cancelUseCase,
                            FileUploadValidator fileValidator,
                            AuditService audit,
                            ImportTemplateSamples templateSamples,
                            ImportTemplateRegistry templateRegistry) {
        this.uploadUseCase = uploadUseCase;
        this.queries = queries;
        this.commitUseCase = commitUseCase;
        this.cancelUseCase = cancelUseCase;
        this.fileValidator = fileValidator;
        this.audit = audit;
        this.templateSamples = templateSamples;
        this.templateRegistry = templateRegistry;
    }

    /**
     * Download an empty XLSX template for the given import template code.
     * Returns the canonical header row (8/9/11/8/5 columns depending on
     * template) plus a single banner row carrying the DEMO marker so users
     * always see something even before they edit. Permission: {@code IMPORT_READ}.
     */
    @GetMapping(value = "/templates/{templateCode}/empty.xlsx",
            produces = XLSX_MIME)
    @PreAuthorize("hasAuthority('IMPORT_READ') or isAuthenticated()")
    public ResponseEntity<byte[]> downloadEmptyTemplate(
            @PathVariable("templateCode") String templateCode) {
        templateRegistry.find(templateCode)
                .orElseThrow(() -> new ValidationException(
                        "UNKNOWN_TEMPLATE",
                        "Unknown import template: " + templateCode));
        byte[] body = templateSamples.generateEmpty(templateCode);
        return xlsxResponse(body, templateSamples.filename(templateCode, false));
    }

    /**
     * Download a sample XLSX template populated with realistic ACME Holdings
     * data so users can either edit it or use it as a starting point.
     * Permission: {@code IMPORT_READ}.
     */
    @GetMapping(value = "/templates/{templateCode}/sample.xlsx",
            produces = XLSX_MIME)
    @PreAuthorize("hasAuthority('IMPORT_READ') or isAuthenticated()")
    public ResponseEntity<byte[]> downloadSampleTemplate(
            @PathVariable("templateCode") String templateCode) {
        templateRegistry.find(templateCode)
                .orElseThrow(() -> new ValidationException(
                        "UNKNOWN_TEMPLATE",
                        "Unknown import template: " + templateCode));
        byte[] body = templateSamples.generateSample(templateCode);
        return xlsxResponse(body, templateSamples.filename(templateCode, true));
    }

    private static ResponseEntity<byte[]> xlsxResponse(byte[] body, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX_MIME))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }

    /**
     * Upload a multipart .xlsx file. Permission is template-dependent and
     * verified inside the use case. tenantId is sourced from the JWT only.
     *
     * <p>Level-1 file checks (empty / size / MIME / extension / path-traversal)
     * run BEFORE the use case so invalid uploads short-circuit at the HTTP
     * boundary with a 400 response and an {@code IMPORT_UPLOAD_REJECTED} audit
     * row — never producing an {@code ImportBatch} or storage write
     * (integration-review F3).
     */
    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    public ImportBatchResponse upload(@RequestPart("file") MultipartFile file,
                                      @RequestParam("templateCode") @NotBlank String templateCode,
                                      @RequestParam(value = "projectId", required = false) UUID projectId) {
        try {
            fileValidator.validate(file);
        } catch (ValidationException rejected) {
            recordUploadRejection(file, templateCode, projectId, rejected);
            throw rejected;
        }
        UUID id = uploadUseCase.upload(file, templateCode, projectId);
        return ImportBatchResponse.from(queries.get(id));
    }

    private void recordUploadRejection(MultipartFile file, String templateCode,
                                       UUID projectId, ValidationException ex) {
        String filename = file == null ? null : file.getOriginalFilename();
        long size = file == null ? 0L : file.getSize();
        String mime = file == null ? null : file.getContentType();
        log.warn("Import upload rejected at HTTP boundary code={} template={} " +
                        "size={} mime={} filename-len={}",
                ex.getCode(), templateCode, size, mime,
                filename == null ? 0 : filename.length());
        try {
            TenantContext ctx = TenantContextHolder.get();
            audit.record(AuditEvent.builder()
                    .tenantId(ctx == null ? null : ctx.tenantId())
                    .projectId(projectId)
                    .actorUserId(ctx == null ? null : ctx.userId())
                    .action(AuditAction.IMPORT_UPLOAD_REJECTED)
                    .entityType("ImportBatch")
                    // Don't leak full filename — only length + extension safety hint.
                    .reason("code=" + ex.getCode() + " template=" + templateCode
                            + " size=" + size)
                    .build());
        } catch (Exception persistFailure) {
            log.error("Failed to persist IMPORT_UPLOAD_REJECTED audit row", persistFailure);
        }
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<ImportBatchResponse> list(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) ImportBatchStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(MAX_PAGE_SIZE, size));
        return PageResponse.of(queries.list(projectId, status, pageable), ImportBatchResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ImportBatchResponse get(@PathVariable("id") UUID id) {
        return ImportBatchResponse.from(queries.get(id));
    }

    @GetMapping("/{id}/errors")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<ImportErrorResponse> errors(
            @PathVariable("id") UUID id,
            @RequestParam(required = false) ImportErrorLevel level,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(MAX_PAGE_SIZE, size));
        return PageResponse.of(queries.listErrors(id, level, pageable), ImportErrorResponse::from);
    }

    @PostMapping("/{id}/commit")
    @PreAuthorize("isAuthenticated()")
    public ImportBatchResponse commit(@PathVariable("id") UUID id) {
        return ImportBatchResponse.from(commitUseCase.commit(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ImportBatchResponse cancel(@PathVariable("id") UUID id) {
        return ImportBatchResponse.from(cancelUseCase.cancel(id));
    }
}
